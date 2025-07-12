package org.futo.inputmethod.latin.uix

import androidx.datastore.preferences.core.booleanPreferencesKey

/**
 * Key used to track whether the initial setup flow has been completed.
 */
val IS_SETUP_COMPLETE = booleanPreferencesKey("is_setup_complete")

/**
 * Indicates whether the user has already been prompted to restore a backup
 * during the initial setup flow. This prevents repeatedly showing the restore
 * prompt on every launch if the user dismissed it once.
 */
val HAS_SEEN_RESTORE_BACKUP = booleanPreferencesKey("has_seen_restore_backup")
