package org.futo.inputmethod.latin.uix

import androidx.datastore.preferences.core.booleanPreferencesKey

/**
 * Key used to track whether the initial setup flow has been completed.
 */
val IS_SETUP_COMPLETE = booleanPreferencesKey("is_setup_complete")

/**
 * Key used to ensure the restore backup prompt is only shown once after
 * installation.
 */
val HAS_SEEN_RESTORE_BACKUP_PROMPT = booleanPreferencesKey("seen_restore_backup_prompt")
