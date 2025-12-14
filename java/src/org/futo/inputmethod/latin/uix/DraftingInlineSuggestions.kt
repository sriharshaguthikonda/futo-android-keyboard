package org.futo.inputmethod.latin.uix

import android.os.Build
import android.util.Log
import android.view.inputmethod.InlineSuggestion
import androidx.annotation.RequiresApi

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
