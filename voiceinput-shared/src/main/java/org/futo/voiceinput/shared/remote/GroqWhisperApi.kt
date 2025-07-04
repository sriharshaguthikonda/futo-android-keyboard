package org.futo.voiceinput.shared.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

@Serializable
private data class TranscriptionRequest(val model: String, val audio: String)

@Serializable
private data class TranscriptionResponse(val text: String)

object GroqWhisperApi {
    suspend fun transcribe(samples: FloatArray, apiKey: String): String? = withContext(Dispatchers.IO) {
        if(apiKey.isBlank()) return@withContext null

        val url = URL("https://api.groq.com/openai/v1/audio/transcriptions")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true

        val pcm = ByteArray(samples.size * 2)
        for(i in samples.indices){
            val v = (samples[i].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt()
            pcm[i*2] = (v and 0xFF).toByte()
            pcm[i*2+1] = ((v shr 8) and 0xFF).toByte()
        }

        val body = Json.encodeToString(TranscriptionRequest("whisper-large-v3", Base64.getEncoder().encodeToString(pcm)))
        conn.outputStream.use { it.write(body.toByteArray()) }

        return@withContext if(conn.responseCode == 200){
            val text = conn.inputStream.bufferedReader().readText()
            try {
                Json.decodeFromString<TranscriptionResponse>(text).text
            } catch(_: Exception){
                null
            }
        } else {
            null
        }
    }

    suspend fun ping(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        if(apiKey.isBlank()) return@withContext false
        val url = URL("https://api.groq.com/openai/v1/models")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $apiKey")
        return@withContext try { conn.responseCode == 200 } catch(_: Exception){ false }
    }
}
