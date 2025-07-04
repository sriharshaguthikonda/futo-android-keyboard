package org.futo.voiceinput.shared.whisper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object GroqRemote {
    private val client = OkHttpClient()
    private val mediaType = "application/json".toMediaType()

    suspend fun transcribe(samples: FloatArray): String? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject()
            json.put("audio", samples.joinToString(separator = ","))
            val body = json.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(BuildConfig.GROQ_API_URL)
                .header("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()?.let { bodyStr ->
                        JSONObject(bodyStr).optString("text", null)
                    }
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
