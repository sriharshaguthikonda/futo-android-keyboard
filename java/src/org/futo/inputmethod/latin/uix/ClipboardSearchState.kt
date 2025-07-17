package org.futo.inputmethod.latin.uix

import android.view.inputmethod.InputConnection

data class ClipboardSearchState(
    val isActive: Boolean = false,
    val query: String = "",
    val originalCursorPosition: Int? = null,
    val originalInputConnection: InputConnection? = null
)
