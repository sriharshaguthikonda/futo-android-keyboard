package org.futo.inputmethod.latin.uix

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.futo.voiceinput.shared.groq.GroqChatApi

suspend fun Context.generateAiReply(text: String, systemPrompt: String = "Reply to the user"): String? {
    val apiKey = getSetting(GROQ_API_KEY)
    val model = getSetting(GROQ_MODEL)
    return withContext(Dispatchers.IO) {
        GroqChatApi.chat(text, apiKey, model, systemPrompt)
    }
}
