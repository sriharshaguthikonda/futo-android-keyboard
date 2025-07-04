package org.futo.voiceinput.shared.whisper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class GroqWhisperClient(private val apiKey: String) {
    @Serializable
    private data class Response(@SerialName("text") val text: String)

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()

    fun transcribe(wavData: ByteArray, language: String?, prompt: String?): String? {
        val tempFile = File.createTempFile("audio", ".wav")
        FileOutputStream(tempFile).use { it.write(wavData) }

        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart(
                name = "file",
                filename = "audio.wav",
                body = tempFile.asRequestBody("audio/wav".toMediaType())
            )
            .addFormDataPart("model", "whisper-large-v3")
        if (language != null) bodyBuilder.addFormDataPart("language", language)
        if (prompt != null) bodyBuilder.addFormDataPart("prompt", prompt)

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .post(bodyBuilder.build())
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            return json.decodeFromString<Response>(body).text
        }
    }
}

fun encodeWav(samples: FloatArray, sampleRate: Int = 16000): ByteArray {
    val pcm = ByteArrayOutputStream()
    for (f in samples) {
        val v = (f.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt()
        pcm.write(v and 0xFF)
        pcm.write((v shr 8) and 0xFF)
    }
    val audioData = pcm.toByteArray()
    val totalDataLen = audioData.size + 36
    val byteRate = sampleRate * 2
    val header = ByteArrayOutputStream()
    header.write("RIFF".toByteArray())
    header.write(intToLE(totalDataLen))
    header.write("WAVE".toByteArray())
    header.write("fmt ".toByteArray())
    header.write(intToLE(16))
    header.write(shortToLE(1))
    header.write(shortToLE(1))
    header.write(intToLE(sampleRate))
    header.write(intToLE(byteRate))
    header.write(shortToLE(2))
    header.write(shortToLE(16))
    header.write("data".toByteArray())
    header.write(intToLE(audioData.size))
    return header.toByteArray() + audioData
}

private fun intToLE(v: Int) = byteArrayOf(
    (v and 0xFF).toByte(),
    ((v shr 8) and 0xFF).toByte(),
    ((v shr 16) and 0xFF).toByte(),
    ((v shr 24) and 0xFF).toByte()
)

private fun shortToLE(v: Int) = byteArrayOf(
    (v and 0xFF).toByte(),
    ((v shr 8) and 0xFF).toByte()
)
