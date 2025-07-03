package org.futo.voiceinput.shared.groq

import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

object GroqClient {
    data class Config(val apiKey: String)

    fun pcm16ToWav(pcmData: ByteArray): ByteArray {
        val sampleRate = 16000
        val byteRate = 16 * sampleRate / 8
        val dataLength = pcmData.size
        val chunkSize = 36 + dataLength
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (chunkSize and 0xff).toByte(); header[5] = (chunkSize shr 8).toByte(); header[6] = (chunkSize shr 16).toByte(); header[7] = (chunkSize shr 24).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[20] = 1; header[22] = 1; header[24] = (sampleRate and 0xff).toByte(); header[25] = (sampleRate shr 8).toByte(); header[26] = (sampleRate shr 16).toByte(); header[27] = (sampleRate shr 24).toByte()
        header[28] = (byteRate and 0xff).toByte(); header[29] = (byteRate shr 8).toByte(); header[30] = (byteRate shr 16).toByte(); header[31] = (byteRate shr 24).toByte(); header[32] = 2; header[34] = 16
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte();
        header[40] = (dataLength and 0xff).toByte(); header[41] = (dataLength shr 8).toByte(); header[42] = (dataLength shr 16).toByte(); header[43] = (dataLength shr 24).toByte()
        return header + pcmData
    }

    fun floatArrayToPCM(samples: FloatArray): ByteArray {
        val out = ByteArray(samples.size * 2)
        var i = 0
        for (f in samples) {
            val v = (f.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
            out[i] = (v.toInt() and 0xff).toByte(); out[i+1] = ((v.toInt() shr 8) and 0xff).toByte(); i += 2
        }
        return out
    }

    fun transcribe(samples: FloatArray, config: Config): String? {
        val pcm = floatArrayToPCM(samples)
        val wav = pcm16ToWav(pcm)
        val boundary = "----groq${System.currentTimeMillis()}"
        val url = URL("https://api.groq.com/openai/v1/audio/transcriptions")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        conn.doOutput = true
        DataOutputStream(conn.outputStream).use { out ->
            out.writeBytes("--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"model\"\r\n\r\nwhisper-large-v3\r\n")
            out.writeBytes("--$boundary\r\n")
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n")
            out.writeBytes("Content-Type: audio/wav\r\n\r\n")
            out.write(wav)
            out.writeBytes("\r\n--$boundary--\r\n")
        }
        return try {
            val text = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(text)
            json.optString("text", null)
        } catch(e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    fun test(apiKey: String): Boolean {
        return try {
            val url = URL("https://api.groq.com/openai/v1/models")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.inputStream.close()
            conn.responseCode == 200
        } catch(e: Exception) {
            false
        }
    }
}
