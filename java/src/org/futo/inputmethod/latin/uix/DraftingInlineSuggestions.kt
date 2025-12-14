package org.futo.inputmethod.latin.uix

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.xlm.ModelPaths

class DraftingModelRunner(private val context: Context) {
    companion object {
        private const val TAG = "DraftingInline"
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
