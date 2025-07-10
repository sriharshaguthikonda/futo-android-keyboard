package org.futo.inputmethod.latin.uix.actions

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.futo.inputmethod.latin.uix.PersistentActionState
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.getUnlockedSetting
import org.futo.inputmethod.latin.uix.isDirectBootUnlocked
import org.futo.inputmethod.latin.uix.actions.ClipboardHistoryEnabled
import org.futo.inputmethod.latin.uix.actions.ClipboardHistoryItemsToKeep
import org.futo.inputmethod.latin.uix.actions.ClipboardHistorySaveSensitive
import org.futo.inputmethod.latin.uix.actions.ClipboardHistoryTimeToKeep
import org.futo.inputmethod.latin.uix.actions.DefaultClipboardEntry
import org.futo.inputmethod.latin.uix.actions.ClipboardFileName
import org.futo.inputmethod.latin.uix.actions.clipboardFile
import org.futo.inputmethod.latin.uix.actions.BugViewerState
import org.futo.inputmethod.latin.uix.actions.BugInfo
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
internal val ClipboardIOContext = Dispatchers.IO.limitedParallelism(1)

class ClipboardHistoryManager(
    val context: Context,
    val coroutineScope: LifecycleCoroutineScope
) : PersistentActionState {
    companion object {
        val onClipboardImportedFlow = MutableSharedFlow<File>()
    }

    private val clipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    val clipboardHistory = mutableStateListOf<ClipboardEntry>()

    // Primary file
    val clipboardFile = context.clipboardFile

    // Backup in case primary gets corrupted somehow
    val clipboardFileBak = File(context.filesDir, "$ClipboardFileName.bak")

    // Temporary file used during saving, after writing we delete previous backup,
    // move primary to backup, move swap to primary
    val clipboardFileSwap = File(context.filesDir, "$ClipboardFileName.swap")

    var clipboardLoaded = false

    override suspend fun onDeviceUnlocked() {
        loadClipboard()
    }

    private val primaryClipChangedListener = object : ClipboardManager.OnPrimaryClipChangedListener {
        override fun onPrimaryClipChanged() {
            if (!context.getSettingBlocking(ClipboardHistoryEnabled)) return

            val clip = clipboardManager.primaryClip

            val text = clip?.getItemAt(0)?.coerceToText(context)?.toString()
            val uri = clip?.getItemAt(0)?.uri

            val timestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                clip?.description?.timestamp
            } else {
                null
            } ?: System.currentTimeMillis()

            val mimeTypes = List(clip?.description?.mimeTypeCount ?: 0) {
                clip?.description?.getMimeType(it)
            }.filterNotNull()

            val canSaveSensitive = context.getSetting(ClipboardHistorySaveSensitive)
            val isSensitive = clip?.description?.extras?.getBoolean(
                ClipDescription.EXTRA_IS_SENSITIVE, false
            ) == true

            // TODO: Support images and other non-text media
            if (text != null && uri == null && (!isSensitive || canSaveSensitive)) {
                val isAlreadyPinned = clipboardHistory.firstOrNull {
                    ((it.text != null && it.text == text) || (it.uri != null && it.uri == uri)) && it.pinned
                }?.pinned ?: false

                clipboardHistory.removeAll {
                    (it.text != null && it.text == text) || (it.uri != null && it.uri == uri)
                }

                val newEntry = ClipboardEntry(
                    timestamp = timestamp,
                    pinned = isAlreadyPinned,
                    text = text,
                    uri = uri,
                    mimeTypes = mimeTypes
                )
                clipboardHistory.add(newEntry)

                saveClipboard()
            }
        }
    }

    init {
        coroutineScope.launch {
            loadClipboard()

            withContext(Dispatchers.Main) {
                clipboardManager.addPrimaryClipChangedListener(primaryClipChangedListener)
            }

            onClipboardImportedFlow.collectLatest {
                coroutineScope.ensureActive()
                onClipboardImported(it)
            }
        }
    }

    private suspend fun onClipboardImported(file: File) {
        val data = decodeFile(file).map {
            // Restore all saved items
            it.copy(timestamp = System.currentTimeMillis())
        }

        withContext(Dispatchers.Main) {
            clipboardHistory.clear()
            clipboardHistory.addAll(data)
            clipboardLoaded = true
        }

        saveClipboard()
    }

    suspend fun pruneOldItems() = withContext(Dispatchers.Main) {
        val numHoursToKeep = context.getSetting(ClipboardHistoryTimeToKeep)
        val numItemsToKeep = context.getSetting(ClipboardHistoryItemsToKeep)
        val minimumTimestamp = System.currentTimeMillis() - (numHoursToKeep * 60L * 60L * 1000L)
        clipboardHistory.removeAll {
            (!it.pinned) && (it.timestamp < minimumTimestamp)
        }

        // Remove duplicates of entries, if any appeared
        // Duplicates will have same timestamp, same text, etc
        val set = clipboardHistory.toSet()
        if (set.size < clipboardHistory.size) {
            clipboardHistory.clear()
            clipboardHistory.addAll(set)
        }

        val maxItems = numItemsToKeep
        val numUnpinnedItems = clipboardHistory.filter { !it.pinned }.size

        val numItemsToRemove = numUnpinnedItems - maxItems
        if (numItemsToRemove > 0) {
            for (i in 0 until numItemsToRemove) {
                val idx = clipboardHistory.indexOfFirst { !it.pinned }
                if (idx == -1) break
                clipboardHistory.removeAt(idx)
            }
        }
    }

    val clipboardIOFailure = mutableStateOf(false)
    var saveClipboardLoadJob: Job? = null
    internal fun saveClipboard(exiting: Boolean = false): Job? {
        if (!context.isDirectBootUnlocked) return null
        if (!clipboardLoaded) {
            if (saveClipboardLoadJob?.isActive == true) return null

            val currentEntries = clipboardHistory.toList()
            saveClipboardLoadJob = coroutineScope.launch {
                loadClipboard()

                if (clipboardLoaded) {
                    clipboardHistory.addAll(currentEntries)
                    saveClipboard(exiting)
                } else {
                    clipboardIOFailure.value = true
                }
            }

            return saveClipboardLoadJob
        }

        return coroutineScope.launch(context = ClipboardIOContext) {
            try {
                if (!exiting) pruneOldItems()

                val list = clipboardHistory.toList()
                val json = Json.encodeToString(list)

                clipboardFileSwap.writeText(json)

                // Validate it can be read
                if (decodeFile(clipboardFileSwap) != list) {
                    throw Exception("Saved file data does not match expected data")
                }

                // Move current to bak
                if (clipboardFile.exists()) {
                    if (!clipboardFile.renameTo(clipboardFileBak)) {
                        throw Exception("Failed to move clipboard file backup")
                    }
                }

                // Move swap to current
                if (!clipboardFileSwap.renameTo(clipboardFile)) {
                    throw Exception("Failed to swap new clipboard file")
                }

                // Finally validate it can be read
                if (decodeFile(clipboardFile) != list) {
                    throw Exception("Saved file data does not match expected data")
                }

                clipboardIOFailure.value = false
            } catch (e: Exception) {
                clipboardIOFailure.value = true
                clipboardIOFailureReason = e.toString()
                reportError("saveClipboard", e)
            }
        }
    }

    fun deleteClipboard() {
        listOf(clipboardFile, clipboardFileSwap, clipboardFileBak).forEach {
            if (it.exists()) it.delete()
        }
    }

    private fun decodeFile(file: File): List<ClipboardEntry> {
        val inputString = file.readText()
        return Json.decodeFromString(inputString)
    }

    private fun reportError(during: String, e: Exception) {
        BugViewerState.pushBug(
            BugInfo(
                "ClipboardHistoryManager",
                """Clipboard IO error during $during

Cause: ${e.message}

Stack trace: ${e.stackTrace.map { it.toString() }}

--- main data start --- snip ---
${if (clipboardFile.exists()) { clipboardFile.readText() } else { "File does not exist" }}
--- main data end --- snip ---


--- bak data start --- snip ---
${if (clipboardFileBak.exists()) { clipboardFileBak.readText() } else { "File does not exist" }}
--- bak data end --- snip ---

--- swap data start --- snip ---
${if (clipboardFileSwap.exists()) { clipboardFileSwap.readText() } else { "File does not exist" }}
--- swap data end --- snip ---"""
            )
        )
    }

    var clipboardIOFailureReason = ""
    private suspend fun loadClipboard() = withContext(ClipboardIOContext) {
        if (!context.isDirectBootUnlocked) {
            clipboardIOFailureReason = "Direct Boot not unlocked"
            clipboardIOFailure.value = true
            return@withContext
        }

        val clipboardSetting = context.getUnlockedSetting(ClipboardHistoryEnabled)
        if (clipboardSetting == null) {
            clipboardIOFailureReason = "Settings not unlocked"
            clipboardIOFailure.value = true
            return@withContext
        }

        try {
            if (clipboardSetting == false) {
                deleteClipboard()
            } else if (clipboardFile.exists()) {
                val data = try {
                    decodeFile(clipboardFile)
                } catch (e: Exception) {
                    reportError("loadClipboard main, trying bak", e)
                    if (clipboardFileBak.exists()) {
                        decodeFile(clipboardFileBak)
                    } else {
                        throw e
                    }
                }

                clipboardHistory.clear()
                clipboardHistory.addAll(data)
                pruneOldItems()
            } else {
                clipboardHistory.add(DefaultClipboardEntry)
            }

            clipboardLoaded = true
            clipboardIOFailureReason = ""
            clipboardIOFailure.value = false
        } catch (e: Exception) {
            e.printStackTrace()
            clipboardIOFailureReason = "Exception: ${e.message}"
            clipboardIOFailure.value = true

            reportError("loadClipboard", e)
        }
    }

    fun onPaste(item: ClipboardEntry) {
        val itemPos = clipboardHistory.indexOf(item)
        clipboardHistory.removeAll { it == item }

        clipboardHistory.add(
            itemPos,
            ClipboardEntry(
                timestamp = System.currentTimeMillis(),
                pinned = item.pinned,
                text = item.text,
                uri = item.uri,
                mimeTypes = item.mimeTypes
            )
        )

        saveClipboard()
    }

    fun onPin(item: ClipboardEntry) {
        clipboardHistory.removeAll { it == item }

        clipboardHistory.add(
            ClipboardEntry(
                timestamp = System.currentTimeMillis(),
                pinned = !item.pinned,
                text = item.text,
                uri = item.uri,
                mimeTypes = item.mimeTypes
            )
        )

        saveClipboard()
    }

    fun onRemove(item: ClipboardEntry) {
        // Clear the clipboard if the item being removed is the current one
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // TODO: URI
            if ((item.text != null) &&
                item.text == clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            ) {
                clipboardManager.clearPrimaryClip()
            }
        }
        clipboardHistory.removeAll { it == item }
        saveClipboard()
    }

    override suspend fun cleanUp() {
        saveClipboard()?.join()
    }

    override fun close() {
        clipboardManager.removePrimaryClipChangedListener(primaryClipChangedListener)
        runBlocking { saveClipboard(true)?.join() }
    }
}
