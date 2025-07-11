package org.futo.inputmethod.latin.uix

import androidx.datastore.preferences.core.booleanPreferencesKey

/**
 * Preference keys used for the initial setup flow.
 *
 * Only the `IS_SETUP_COMPLETE` flag is defined here to avoid
 * name clashes with the existing preference keys declared in
 * `Settings.kt` and other files.
 */
val IS_SETUP_COMPLETE = booleanPreferencesKey("is_setup_complete")
