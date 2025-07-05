package org.futo.voiceinput.shared.groq

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

object GroqChatApi {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ChatMessage(val role: String, val content: String)
    @Serializable
    private data class ChatRequest(val model: String, val messages: List<ChatMessage>)
    @Serializable
    private data class ChatChoice(val message: ChatMessage)
    @Serializable
    private data class ChatResponse(val choices: List<ChatChoice>)

    fun chat(prompt: String, apiKey: String, model: String, systemPrompt: String): String? {
        if(apiKey.isBlank()) return null
        return try {
            val url = URL("https://api.groq.com/openai/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json")
            val req = ChatRequest(
                model,
                listOf(
                    ChatMessage("system", systemPrompt),
                    ChatMessage("user", prompt)
                )
            )
            val data = json.encodeToString(req).toByteArray(Charsets.UTF_8)
            conn.outputStream.use { it.write(data) }
            if(conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val resp = conn.inputStream.readBytes().toString(Charsets.UTF_8)
            val parsed = json.decodeFromString<ChatResponse>(resp)
            parsed.choices.firstOrNull()?.message?.content
        } catch(e: Exception) {
            null
        }
    }
}
