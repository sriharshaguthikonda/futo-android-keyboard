package org.futo.inputmethod.latin.uix

import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.LatinIME
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.settings.pages.EnableDraftingInlineSuggestions
import org.futo.inputmethod.latin.xlm.ModelPaths
import kotlin.math.min

private const val MAX_COMPLETION_TOKENS = 8
private const val MAX_CONTEXT_CHARS = 160

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
        ModelPaths.getDraftingModelFile(context)?.absolutePath
    }

    suspend fun suggestCompletions(prompt: String): List<String> = withContext(Dispatchers.Default) {
        val modelPath = loadModelPath() ?: return@withContext emptyList()
        if (modelPath.isBlank()) return@withContext emptyList()

        val sanitizedPrompt = prompt.takeLast(MAX_CONTEXT_CHARS).trim()
        if (sanitizedPrompt.isBlank()) return@withContext emptyList()

        val lastSentence = sanitizedPrompt.substringAfterLast('.')
            .substringAfterLast('?')
            .substringAfterLast('!')
            .trim()

        val tokens = lastSentence.split(" ")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (tokens.isEmpty()) return@withContext emptyList()

        val base = tokens.takeLast(3).joinToString(" ")

        // Simple stub completion that prefers concise endings; real inference can be swapped in later.
        val suggestions = listOf(
            "$base…",
            "$base.",
            "$base?",
        )

        suggestions.map { completion ->
            val words = completion.split(" ")
            val limited = words.take(min(words.size, MAX_COMPLETION_TOKENS)).joinToString(" ")
            limited.take(64)
        }.distinct().filter { it.isNotBlank() }
    }
}

class DraftingInlineSuggestionController(
    private val latinIME: LatinIME,
    private val lifecycleScope: LifecycleCoroutineScope,
) {
    private val runner = DraftingModelRunner(latinIME)
    private var job: Job? = null

    fun onInputEvent(textBlank: Boolean, hasExistingInline: Boolean, updateInline: (List<MutableState<View?>>) -> Unit) {
        if (!latinIME.getSetting(EnableDraftingInlineSuggestions) || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            updateInline(emptyList())
            return
        }

        if (hasExistingInline) return

        if (textBlank) {
            job?.cancel()
            updateInline(emptyList())
            return
        }

        job?.cancel()
        job = lifecycleScope.launch {
            val contextText = withContext(Dispatchers.Main) {
                latinIME.currentInputConnection?.getTextBeforeCursor(MAX_CONTEXT_CHARS, 0)?.toString() ?: ""
            }

            val suggestions = runner.suggestCompletions(contextText)
            val views = suggestions.map { suggestion ->
                val state: MutableState<View?> = mutableStateOf(null)
                val view = buildChipView(latinIME, suggestion) {
                    latinIME.currentInputConnection?.commitText(suggestion, 1)
                }
                state.value = view
                state
            }

            withContext(Dispatchers.Main) {
                updateInline(views)
            }
        }
    }
}
