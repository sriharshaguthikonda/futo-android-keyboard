package org.futo.voiceinput.shared.groq

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.futo.voiceinput.shared.util.DebugLogger
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

object GroqChatApi {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient(OkHttp)

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

    suspend fun chat(systemPrompt: String, userPrompt: String, apiKey: String, model: String): String? {
        if (apiKey.isBlank()) return null
        return try {
            DebugLogger.log("Groq chat start model=$model")
            val req = ChatRequest(model, listOf(ChatMessage("system", systemPrompt), ChatMessage("user", userPrompt)))
            val response = http.post("https://api.groq.com/openai/v1/chat/completions") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(req))
            }
            if (response.status != HttpStatusCode.OK) {
                DebugLogger.log("Groq chat failed code=${response.status.value}")
                return null
            }
            val resp = response.bodyAsText()
            val parsed = json.decodeFromString<ChatResponse>(resp)
            parsed.choices.firstOrNull()?.message?.content
        } catch (e: Exception) {
            DebugLogger.log("Groq chat error: ${e.message}")
            null
        }
    }

    suspend fun availableModels(apiKey: String): List<String>? {
        if (apiKey.isBlank()) return null
        return try {
            DebugLogger.log("Groq chat models fetch")
            val response = http.get("https://api.groq.com/openai/v1/models") {
                header("Authorization", "Bearer $apiKey")
            }
            if (response.status != HttpStatusCode.OK) {
                DebugLogger.log("Groq chat models failed code=${response.status.value}")
                return null
            }
            val resp = response.bodyAsText()
            val parsed = json.decodeFromString<ModelsResponse>(resp)
            parsed.data.map { it.id }.filter { it.contains("llama") || it.contains("mixtral") || it.contains("gemma") }
        } catch (e: Exception) {
            DebugLogger.log("Groq chat models error: ${e.message}")
            null
        }
    }
}
