package org.futo.voiceinput.shared.util

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

fun floatArrayToWav(samples: FloatArray, sampleRate: Int = 16000): ByteArray {
    val byteBuffer = ByteBuffer.allocate(44 + samples.size * 2)
    byteBuffer.order(ByteOrder.LITTLE_ENDIAN)

    fun writeString(value: String) {
        byteBuffer.put(value.toByteArray())
    }

    fun writeInt(value: Int) {
        byteBuffer.putInt(value)
    }

    fun writeShort(value: Short) {
        byteBuffer.putShort(value)
    }

    writeString("RIFF")
    writeInt(36 + samples.size * 2)
    writeString("WAVE")
    writeString("fmt ")
    writeInt(16)
    writeShort(1) // PCM
    writeShort(1) // Mono
    writeInt(sampleRate)
    writeInt(sampleRate * 2)
    writeShort(2)
    writeShort(16)
    writeString("data")
    writeInt(samples.size * 2)

    for(sample in samples) {
        val s = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        writeShort(s)
    }

    return byteBuffer.array()
}
