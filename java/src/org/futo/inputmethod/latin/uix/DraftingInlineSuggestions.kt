package org.futo.inputmethod.latin.uix

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InlineSuggestion
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.LatinIME
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.settings.pages.EnableDraftingInlineSuggestions
import org.futo.inputmethod.latin.xlm.ModelPaths
import kotlin.math.min

private const val MAX_COMPLETION_TOKENS = 8
private const val MAX_CONTEXT_CHARS = 160
private const val TAG = "DraftingInline"
private const val DEBUG = BuildConfig.DEBUG

private fun logDebug(message: String) {
    if (DEBUG) {
        Log.d(TAG, message)
    }
}

private fun buildChipView(context: Context, text: String, onClick: () -> Unit): View {
    val chip = TextView(context)
    chip.text = text
    chip.setPadding(28, 12, 28, 12)
    chip.background = AppCompatResources.getDrawable(context, R.drawable.inline_suggestion_chip)
    chip.setTextColor(ContextCompat.getColor(context, R.color.key_text_color_lxx_dark))
    chip.isAllCaps = false
    chip.layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
    chip.setOnClickListener { onClick() }
    chip.maxLines = 1
    return chip
}

class DraftingModelRunner(private val context: Context) {
    private suspend fun loadModelPath(): String? = withContext(Dispatchers.IO) {
        val path = ModelPaths.getDraftingModelFile(context)?.absolutePath.orEmpty()
        logDebug("Resolving drafting model path: ${if (path.isBlank()) "<empty>" else path}")
        if (path.isBlank()) {
            logDebug("Drafting model path is missing or blank")
            return@withContext null
        }
        path
    }

    suspend fun suggestCompletions(prompt: String): List<String> = withContext(Dispatchers.Default) {
        val modelPath = loadModelPath() ?: return@withContext emptyList()
        if (modelPath.isBlank()) {
            logDebug("Model path blank; skipping completions")
            return@withContext emptyList()
        }

        val sanitizedPrompt = prompt.takeLast(MAX_CONTEXT_CHARS).trim()
        if (sanitizedPrompt.isBlank()) {
            logDebug("Sanitized prompt is blank; skipping completions")
            return@withContext emptyList()
        }

        val lastSentence = sanitizedPrompt.substringAfterLast('.')
            .substringAfterLast('?')
            .substringAfterLast('!')
            .trim()

        val tokens = lastSentence.split(" ")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (tokens.isEmpty()) {
            logDebug("No tokens found in prompt; skipping completions")
            return@withContext emptyList()
        }

        val base = tokens.takeLast(3).joinToString(" ")

        // Simple stub completion that prefers concise endings; real inference can be swapped in later.
        val suggestions = listOf(
            "$base…",
            "$base.",
            "$base?",
        )

        val processed = suggestions.map { completion ->
            val words = completion.split(" ")
            val limited = words.take(min(words.size, MAX_COMPLETION_TOKENS)).joinToString(" ")
            limited.take(64)
        }.distinct().filter { it.isNotBlank() }

        logDebug("Generated stub drafting suggestions: $processed")
        processed
    }
}

class DraftingInlineSuggestionController(
    private val latinIME: LatinIME,
    private val lifecycleScope: LifecycleCoroutineScope,
) {
    private val runner = DraftingModelRunner(latinIME)
    private var job: Job? = null

    fun onInputEvent(
        textBlank: Boolean,
        hasExistingInline: Boolean,
        updateInline: (List<MutableState<View?>>) -> Unit,
    ) {
        val inlineEnabled = latinIME.getSetting(EnableDraftingInlineSuggestions)
        val apiOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        logDebug("inlineEnabled=$inlineEnabled apiOk=$apiOk hasExistingInline=$hasExistingInline textBlank=$textBlank")

        if (!inlineEnabled || !apiOk) {
            logDebug("Inline suggestions disabled or API too low; clearing.")
            updateInline(emptyList())
            return
        }

        if (hasExistingInline) {
            logDebug("Existing inline suggestion present; skipping update.")
            return
        }

        if (textBlank) {
            logDebug("Blank text; canceling job and clearing inline suggestions.")
            job?.cancel()
            updateInline(emptyList())
            return
        }

        job?.cancel()
        job = lifecycleScope.launch {
            val contextText = withContext(Dispatchers.Main) {
                latinIME.currentInputConnection?.getTextBeforeCursor(MAX_CONTEXT_CHARS, 0)?.toString() ?: ""
            }
            logDebug("Context before cursor length=${contextText.length}")

            val suggestions = runner.suggestCompletions(contextText)
            logDebug("suggestionCount=${suggestions.size}")

            val views = suggestions.map { suggestion ->
                val state: MutableState<View?> = mutableStateOf(null)
                val view = buildChipView(latinIME, suggestion) {
                    latinIME.currentInputConnection?.commitText(suggestion, 1)
                    logDebug("chipCommitted text='$suggestion'")
                }
                state.value = view
                logDebug("chipCreated text='$suggestion'")
                state
            }

            withContext(Dispatchers.Main) {
                updateInline(views)
            }
        }
    }
}
