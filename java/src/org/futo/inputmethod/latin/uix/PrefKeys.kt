package org.futo.inputmethod.latin.uix

import androidx.datastore.preferences.core.booleanPreferencesKey

val IS_SETUP_COMPLETE = booleanPreferencesKey("is_setup_complete")
val THEME_KEY = booleanPreferencesKey("theme") // Assuming THEME_KEY is also a boolean for now, adjust if different

// Add other preference keys here as needed
val GROQ_VOICE_API_KEY = booleanPreferencesKey("groq_voice_api_key")
val GROQ_VOICE_MODEL = booleanPreferencesKey("groq_voice_model")
val GROQ_REPLY_API_KEY = booleanPreferencesKey("groq_reply_api_key")
val GROQ_REPLY_MODEL = booleanPreferencesKey("groq_reply_model")
val USE_SYSTEM_VOICE_INPUT = booleanPreferencesKey("use_system_voice_input")
