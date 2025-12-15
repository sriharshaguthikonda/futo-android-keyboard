package org.futo.inputmethod.latin.uix

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.inputmethod.InlineSuggestion
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.xlm.ModelPaths

class DraftingModelRunner(private val context: Context) {
    companion object {
        private const val DEBUG = BuildConfig.DEBUG
    }

    private fun logDebug(message: String) {
        if (DEBUG) {
            Log.d(TAG, message)
        }
    }

    suspend fun loadModelPath(): String? = withContext(Dispatchers.IO) {
        val path = ModelPaths.getDraftingModelFile(context)?.absolutePath.orEmpty()
        logDebug("Resolving drafting model path: ${if (path.isBlank()) "<empty>" else path}")
        if (path.isBlank()) {
            logDebug("Drafting model path is missing or blank")
            return@withContext null
        }

        return@withContext path
    }

    suspend fun suggestCompletions(inputContext: String?): List<String> = withContext(Dispatchers.Default) {
        val normalizedContext = inputContext?.trim().orEmpty()
        if (normalizedContext.isEmpty()) {
            logDebug("Input context is empty after trimming; skipping completions")
            return@withContext emptyList()
        }

        val suggestions = listOf(
            "$normalizedContext …",
            "$normalizedContext (draft continuation)",
            "Continue: $normalizedContext"
        )
        logDebug("Generated stub drafting suggestions: $suggestions")
        return@withContext suggestions
    }
}

private const val TAG = "DraftingInline"

class DraftingInlineSuggestionController {
    @RequiresApi(Build.VERSION_CODES.R)
    fun onInputEvent(
        inlineToggleEnabled: Boolean,
        hasExistingInlineSuggestion: Boolean,
        textIsBlank: Boolean,
        cursorContext: CharSequence?,
        suggestions: List<InlineSuggestion>,
        onChipCreated: (InlineSuggestion) -> Unit = {},
        onChipCommitted: (InlineSuggestion) -> Unit = {}
    ) {
        Log.d(TAG, "toggle=$inlineToggleEnabled apiOk=${Build.VERSION.SDK_INT >= Build.VERSION_CODES.R}")

        if (!inlineToggleEnabled) {
            Log.d(TAG, "inline disabled")
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.d(TAG, "api too low")
            return
        }

        if (hasExistingInlineSuggestion) {
            Log.d(TAG, "existing inline suggestion")
            return
        }

        if (textIsBlank) {
            Log.d(TAG, "blank text")
            return
        }

        Log.d(TAG, "cursorContextLen=${cursorContext?.length ?: 0}")
        Log.d(TAG, "suggestionCount=${suggestions.size}")

        val suggestion = suggestions.firstOrNull() ?: return

        Log.d(TAG, "chipCreated")
        onChipCreated(suggestion)

        Log.d(TAG, "chipCommitted")
        onChipCommitted(suggestion)
    }
}
