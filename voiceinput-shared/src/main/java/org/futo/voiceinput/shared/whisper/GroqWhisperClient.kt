package org.futo.voiceinput.shared.whisper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

@Serializable
private data class TranscriptionResponse(val text: String?)

object GroqWhisperClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun transcribeWav(wavData: ByteArray, apiKey: String): String? = withContext(Dispatchers.IO) {
        val tmp = File.createTempFile("voice", ".wav")
        FileOutputStream(tmp).use { it.write(wavData) }

        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "audio.wav", tmp.asRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("model", "whisper-large")
            .build()

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .post(requestBody)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        client.newCall(request).execute().use { resp ->
            if(!resp.isSuccessful) return@use null
            val body = resp.body?.string() ?: return@use null
            return@withContext Json.decodeFromString<TranscriptionResponse>(body).text
        }
    }
}
