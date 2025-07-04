package org.futo.voiceinput.shared.whisper

import java.nio.ByteBuffer
import java.nio.ByteOrder

fun FloatArray.toWav(sampleRate: Int = 16000): ByteArray {
    val buffer = ByteBuffer.allocate(44 + size * 2)
    buffer.order(ByteOrder.LITTLE_ENDIAN)
    buffer.put("RIFF".toByteArray())
    buffer.putInt(36 + size * 2)
    buffer.put("WAVE".toByteArray())
    buffer.put("fmt ".toByteArray())
    buffer.putInt(16)
    buffer.putShort(1)
    buffer.putShort(1)
    buffer.putInt(sampleRate)
    buffer.putInt(sampleRate * 2)
    buffer.putShort(2)
    buffer.putShort(16)
    buffer.put("data".toByteArray())
    buffer.putInt(size * 2)
    for (f in this) {
        val s = (f.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        buffer.putShort(s)
    }
    return buffer.array()
}
