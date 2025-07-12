package org.futo.inputmethod.latin.uix

import androidx.datastore.preferences.core.booleanPreferencesKey

/**
 * Key used to track whether the initial setup flow has been completed.
 */
val IS_SETUP_COMPLETE = booleanPreferencesKey("is_setup_complete")

/**
 * Key used to track whether the restore backup prompt has already been shown.
 */
val BACKUP_RESTORE_PROMPT_SHOWN = booleanPreferencesKey("backup_restore_prompt_shown")
