package org.futo.voiceinput.shared.whisper

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.futo.inputmethod.latin.network.BlockingHttpClient
import org.futo.inputmethod.latin.network.HttpUrlConnectionBuilder
import java.io.ByteArrayOutputStream

@Serializable
private data class GroqRequest(
    val model: String,
    val language: String? = null,
    val audio: String
)

@Serializable
private data class GroqResponse(val text: String)

object GroqClient {
    suspend fun transcribe(samples: FloatArray, language: String?, apiKey: String): String = withContext(Dispatchers.IO) {
        val pcm = ShortArray(samples.size) { (samples[it] * Short.MAX_VALUE).toInt().toShort() }
        val bytes = ByteArrayOutputStream(pcm.size * 2)
        pcm.forEach {
            bytes.write(it.toInt() and 0xFF)
            bytes.write((it.toInt() shr 8) and 0xFF)
        }
        val payload = GroqRequest(
            model = "whisper-large",
            language = language,
            audio = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
        )
        val json = Json.encodeToString(payload).toByteArray()

        val conn = HttpUrlConnectionBuilder()
            .setUrl("https://api.groq.com/openai/v1/audio/transcriptions")
            .setMode(HttpUrlConnectionBuilder.MODE_BI_DIRECTIONAL)
            .addHeader(HttpUrlConnectionBuilder.HTTP_HEADER_AUTHORIZATION, "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .setFixedLengthForStreaming(json.size)
            .build()

        val client = BlockingHttpClient(conn)
        client.execute(json) { stream ->
            val responseJson = stream.readBytes().toString(Charsets.UTF_8)
            Json.decodeFromString<GroqResponse>(responseJson).text
        }
    }

    suspend fun testConnection(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val conn = HttpUrlConnectionBuilder()
                .setUrl("https://api.groq.com/openai/v1/models")
                .setMode(HttpUrlConnectionBuilder.MODE_DOWNLOAD_ONLY)
                .addHeader(HttpUrlConnectionBuilder.HTTP_HEADER_AUTHORIZATION, "Bearer $apiKey")
                .build()
            val client = BlockingHttpClient(conn)
            client.execute(null) { true }
        } catch(_: Exception) {
            false
        }
    }
}
