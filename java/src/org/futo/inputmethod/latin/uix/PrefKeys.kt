package org.futo.inputmethod.latin.uix

import androidx.datastore.preferences.core.booleanPreferencesKey

/**
 * Key used to track whether the initial setup flow has been completed.
 */
val IS_SETUP_COMPLETE = booleanPreferencesKey("is_setup_complete")
/**
 * Flag used to record that the user has already been offered to restore a backup.
 * This prevents repeatedly prompting on every app launch.
 */
val RESTORE_BACKUP_PROMPT_SHOWN = booleanPreferencesKey("restore_backup_prompt_shown")
