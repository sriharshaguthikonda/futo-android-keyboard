package org.futo.voiceinput.shared.groq

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.json.JSONObject
import org.futo.voiceinput.shared.util.DebugLogger
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charsets

object GroqChatApi {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Returns a model that definitely exists on Groq.
     * If [preferredId] is available it is used, otherwise a recommended
     * fallback is selected.
     */
    fun pickGroqModel(apiKey: String, preferredId: String = "llama3-70b-8192"): String {
        return try {
            val url = URL("https://api.groq.com/openai/v1/models")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val resp = conn.inputStream.readBytes().toString(Charsets.UTF_8)
            if(conn.responseCode != HttpURLConnection.HTTP_OK) {
                return "llama-3.1-8b-instant"
            }
            val data = JSONObject(resp).getJSONArray("data")
            val ids = mutableSetOf<String>()
            for(i in 0 until data.length()) {
                ids.add(data.getJSONObject(i).getString("id"))
            }
            when {
                preferredId in ids -> preferredId
                "llama-3.3-70b-versatile" in ids -> "llama-3.3-70b-versatile"
                "llama-3.1-8b-instant" in ids -> "llama-3.1-8b-instant"
                else -> ids.first()
            }
        } catch(e: Exception) {
            DebugLogger.log("Groq pick model error: ${e.message}")
            "llama-3.1-8b-instant"
        }
    }

    @Serializable
    private data class ChatMessage(val role: String, val content: String)
    @Serializable
    private data class ChatRequest(val model: String, val messages: List<ChatMessage>)
    @Serializable
    private data class ChatChoice(val message: ChatMessage)
    @Serializable
    private data class ChatResponse(val choices: List<ChatChoice>)

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

    /**
     * Streams a reply token by token. This will automatically pick a valid model
     * if [preferredModel] is unavailable.
     */
    fun stream(prompt: String, apiKey: String, preferredModel: String = "llama3-70b-8192", onToken: (String) -> Unit) {
        if(apiKey.isBlank()) return
        val model = pickGroqModel(apiKey, preferredModel)
        val reqBody = """
          {
            "model":"$model",
            "stream":true,
            "messages":[{"role":"user","content":"$prompt"}]
          }
        """.trimIndent()

        try {
            val url = URL("https://api.groq.com/openai/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "text/event-stream")
            conn.outputStream.use { it.write(reqBody.toByteArray()) }
            conn.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if(!line.startsWith("data:")) return@forEach
                    val payload = line.removePrefix("data:").trim()
                    if(payload == "[DONE]") return
                    val token = JSONObject(payload)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("delta")
                        .optString("content")
                    if(token.isNotEmpty()) onToken(token)
                }
            }
        } catch(e: Exception) {
            DebugLogger.log("Groq chat stream error: ${e.message}")
        }
    }

    fun test(apiKey: String, model: String): Boolean {
        if(apiKey.isBlank()) return false
        return try {
            DebugLogger.log("Groq chat test start model=$model")
            val req = ChatRequest(model, listOf(ChatMessage("user", "Hello")))
            val url = URL("https://api.groq.com/openai/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.outputStream.use { it.write(json.encodeToString(req).toByteArray()) }
            val ok = conn.responseCode == HttpURLConnection.HTTP_OK
            DebugLogger.log("Groq chat test result code=${conn.responseCode}")
            ok
        } catch(e: Exception) {
            DebugLogger.log("Groq chat test error: ${e.message}")
            false
        }
    }
}
