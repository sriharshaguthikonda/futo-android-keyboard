package org.futo.voiceinput.shared

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.MicrophoneDirection
import android.os.Build
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

class AudioPrebufferRecorder(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val preferBluetoothMic: Boolean,
    prebufferDurationMs: Int
) {
    private val sampleRateHz = 16000
    private val readBufferSize = 1600
    private val prebufferSampleCount = (sampleRateHz * prebufferDurationMs / 1000).coerceAtLeast(0)

    private var buffer: FloatArray = FloatArray(prebufferSampleCount)
    private var writeIndex = 0
    private var filled = false
    private var recorder: AudioRecord? = null
    private var job: Job? = null
    private var isRunning = false

    private fun setCommunicationDevice(): Boolean {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val devices = audioManager.availableCommunicationDevices
                val targetDevice =
                    devices.firstOrNull { preferBluetoothMic && it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                        ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
                        ?: devices.firstOrNull { it.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                        ?: devices.firstOrNull()
                if (targetDevice != null) {
                    return audioManager.setCommunicationDevice(targetDevice)
                }
            }
        } catch (_: Exception) {
        }
        return false
    }

    private fun clearCommunicationDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.clearCommunicationDevice()
        }
    }

    private fun createAudioRecorder(): AudioRecord {
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            sampleRateHz * 2 * 5
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            recorder.setPreferredMicrophoneDirection(MicrophoneDirection.MIC_DIRECTION_TOWARDS_USER)
        }

        return recorder
    }

    fun start() {
        if (prebufferSampleCount <= 0 || isRunning) return

        setCommunicationDevice()
        val newRecorder = createAudioRecorder()
        newRecorder.startRecording()
        recorder = newRecorder
        isRunning = true

        job = lifecycleScope.launch {
            withContext(Dispatchers.Default) {
                runLoop(newRecorder)
            }
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        job?.cancel()
        job = null
        recorder?.stop()
        recorder?.release()
        recorder = null
        clearCommunicationDevice()
    }

    fun snapshotAndReset(): FloatArray {
        if (prebufferSampleCount <= 0) return FloatArray(0)
        val snapshot = snapshot()
        writeIndex = 0
        filled = false
        return snapshot
    }

    private suspend fun runLoop(recorder: AudioRecord) {
        val samples = ShortArray(readBufferSize)
        while (isRunning) {
            yield()
            val nRead = recorder.read(samples, 0, readBufferSize, AudioRecord.READ_BLOCKING)
            if (nRead <= 0) break
            store(samples, nRead)
        }
    }

    private fun store(samples: ShortArray, nRead: Int) {
        if (prebufferSampleCount <= 0) return
        var idx = 0
        while (idx < nRead) {
            buffer[writeIndex] = samples[idx].toFloat() / Short.MAX_VALUE.toFloat()
            writeIndex += 1
            if (writeIndex >= prebufferSampleCount) {
                writeIndex = 0
                filled = true
            }
            idx += 1
        }
    }

    private fun snapshot(): FloatArray {
        if (prebufferSampleCount <= 0) return FloatArray(0)
        if (!filled) {
            return buffer.copyOfRange(0, writeIndex)
        }
        val result = FloatArray(prebufferSampleCount)
        val tailLength = prebufferSampleCount - writeIndex
        System.arraycopy(buffer, writeIndex, result, 0, tailLength)
        System.arraycopy(buffer, 0, result, tailLength, writeIndex)
        return result
    }
}
