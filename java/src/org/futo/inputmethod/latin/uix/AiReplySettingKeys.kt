package org.futo.inputmethod.latin.uix

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import org.json.JSONArray
import org.json.JSONObject

data class SystemPrompt(
    val name: String,
    val prompt: String
)

object SystemPromptManager {
    private val defaultPrompts = listOf(
        SystemPrompt("Short Reply", "Write a short helpful reply"),
        SystemPrompt("Formal", "Write a formal and professional reply"),
        SystemPrompt("Friendly", "Write a friendly and casual reply"),
        SystemPrompt("Detailed", "Write a detailed and comprehensive reply")
    )

    fun parsePrompts(json: String): List<SystemPrompt> {
        if (json.isBlank()) return defaultPrompts
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                SystemPrompt(
                    name = obj.getString("name"),
                    prompt = obj.getString("prompt")
                )
            }
        } catch (e: Exception) {
            defaultPrompts
        }
    }

    fun toJson(prompts: List<SystemPrompt>): String {
        val array = JSONArray()
        prompts.forEach { prompt ->
            val obj = JSONObject()
            obj.put("name", prompt.name)
            obj.put("prompt", prompt.prompt)
            array.put(obj)
        }
        return array.toString()
    }

    fun getDefaultPromptsJson(): String = toJson(defaultPrompts)
}

val ENABLE_AI_REPLY = SettingsKey(
    key = booleanPreferencesKey("enable_ai_reply"),
    default = true
)

val AI_REPLY_PROMPT = SettingsKey(
    key = stringPreferencesKey("ai_reply_prompt"),
    default = "Write a short helpful reply"
)

val AI_REPLY_SYSTEM_PROMPTS = SettingsKey(
    key = stringPreferencesKey("ai_reply_system_prompts"),
    default = SystemPromptManager.getDefaultPromptsJson()
)

val AI_REPLY_ACTIVE_PROMPT_NAME = SettingsKey(
    key = stringPreferencesKey("ai_reply_active_prompt_name"),
    default = "Short Reply"
)

val GROQ_REPLY_API_KEY = SettingsKey(
    key = stringPreferencesKey("groq_reply_api_key"),
    default = ""
)

val GROQ_REPLY_MODEL = SettingsKey(
    key = stringPreferencesKey("groq_reply_model"),
    default = "llama3-70b-8192"
)

val ENABLE_SWITCH_APPS = SettingsKey(
    key = booleanPreferencesKey("enable_switch_apps"),
    default = true
)
