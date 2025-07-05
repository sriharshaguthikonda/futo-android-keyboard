package org.futo.voiceinput.shared.groq

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.futo.voiceinput.shared.util.DebugLogger
import io.github.vyfor.groqkt.GroqClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    @Serializable
    private data class ModelsResponse(val data: List<Model>)
    @Serializable
    data class Model(val id: String)

    fun chat(systemPrompt: String, userPrompt: String, apiKey: String, model: String): String? {
        if(apiKey.isBlank()) return null
        return try {
            DebugLogger.log("Groq chat start model=$model")
            val req = ChatRequest(model, listOf(ChatMessage("system", systemPrompt), ChatMessage("user", userPrompt)))
            val url = URL("https://api.groq.com/openai/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(json.encodeToString(req).toByteArray()) }
            if(conn.responseCode != HttpURLConnection.HTTP_OK) {
                DebugLogger.log("Groq chat failed code=${conn.responseCode}")
                return null
            }
            val resp = conn.inputStream.readBytes().toString(Charsets.UTF_8)
            val parsed = json.decodeFromString<ChatResponse>(resp)
            parsed.choices.firstOrNull()?.message?.content
        } catch(e: Exception) {
            DebugLogger.log("Groq chat error: ${e.message}")
            null
        }
    }

    suspend fun availableModels(apiKey: String): List<String>? {
        if(apiKey.isBlank()) return null
        return try {
            DebugLogger.log("Groq chat models fetch")
            withContext(Dispatchers.IO) {
                GroqClient(apiKey) { client = HttpClient(CIO) }.use { client ->
                    client.fetchModels().getOrNull()?.map { it.id }
                }
            }
        } catch(e: Exception) {
            DebugLogger.log("Groq chat models error: ${e.message}")
            null
        }
    }
}
