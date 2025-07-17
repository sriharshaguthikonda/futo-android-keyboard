package org.futo.inputmethod.latin.uix

import android.view.inputmethod.InputConnection

/**
 * Holds state related to clipboard search mode.
 */
data class ClipboardSearchState(
    val isActive: Boolean = false,
    val query: String = "",
    val originalCursorPosition: Int? = null,
    val originalInputConnection: InputConnection? = null,
    /** Text before the cursor when search started, used for cursor restoration. */
    val originalTextBeforeCursor: CharSequence? = null
)
