package org.futo.voiceinput.shared.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

@Serializable
data class GroqResponse(
    @SerialName("text") val text: String?
)

class GroqApi(private val url: String, private val apiKey: String) {
    private val client = OkHttpClient()

    fun transcribe(tempFile: File, language: String?): String? {
        if (apiKey.isBlank()) return null

        val requestBodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper")
            .addFormDataPart("file", tempFile.name, tempFile.asRequestBody("audio/wav".toMediaType()))
        if (language != null) {
            requestBodyBuilder.addFormDataPart("language", language)
        }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(requestBodyBuilder.build())
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            return try {
                val parsed = Json.decodeFromString<GroqResponse>(body)
                parsed.text
            } catch (_: Exception) {
                null
            }
        }
    }
}
