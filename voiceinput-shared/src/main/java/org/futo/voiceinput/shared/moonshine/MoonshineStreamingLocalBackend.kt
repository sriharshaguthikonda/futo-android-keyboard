package org.futo.voiceinput.shared.moonshine

import android.content.Context
import android.util.Log
import ai.moonshine.voice.JNI
import ai.moonshine.voice.Transcriber
import ai.moonshine.voice.TranscriptEvent
import java.util.LinkedHashMap
import java.util.function.Consumer
import kotlin.math.min

private const val TAG = "MoonshineStreaming"

class MoonshineStreamingLocalBackend {
    interface StreamingSession {
        fun addAudio(samples: FloatArray, sampleRateHz: Int)
        fun stopAndGetText(): String
        fun close()
    }

    fun preload(context: Context) {
        MoonshineStreamingAssets.ensureInstalled(context)
    }

    fun startStreamingSession(
        context: Context,
        onTranscriptChanged: (String) -> Unit
    ): StreamingSession {
        val modelDir = MoonshineStreamingAssets.ensureInstalled(context)
        val transcriber = Transcriber()
        transcriber.loadFromFiles(
            modelDir.absolutePath,
            JNI.MOONSHINE_MODEL_ARCH_TINY_STREAMING
        )

        val lock = Any()
        val linesById = LinkedHashMap<Long, String>()
        var lastEmitted = ""
        var failure: Throwable? = null
        var closed = false

        lateinit var listener: Consumer<TranscriptEvent>

        val session = object : StreamingSession {
            override fun addAudio(samples: FloatArray, sampleRateHz: Int) {
                if (samples.isEmpty()) return
                check(!closed) { "Moonshine session already closed" }
                val localFailure = synchronized(lock) { failure }
                if (localFailure != null) throw RuntimeException(localFailure)
                transcriber.addAudio(samples, sampleRateHz)
            }

            override fun stopAndGetText(): String {
                check(!closed) { "Moonshine session already closed" }
                val localFailureBeforeStop = synchronized(lock) { failure }
                if (localFailureBeforeStop != null) throw RuntimeException(localFailureBeforeStop)
                transcriber.stop()
                val localFailureAfterStop = synchronized(lock) { failure }
                if (localFailureAfterStop != null) throw RuntimeException(localFailureAfterStop)
                return synchronized(lock) { buildTranscriptText(linesById) }
            }

            override fun close() {
                if (closed) return
                closed = true
                transcriber.removeListener(listener)
            }
        }

        listener = Consumer { event ->
            var textToEmit: String? = null
            synchronized(lock) {
                when (event) {
                    is TranscriptEvent.LineStarted -> {
                        linesById[event.line.id] = event.line.text?.trim().orEmpty()
                        Log.d(TAG, "LineStarted id=${event.line.id} text=[${event.line.text}]")
                    }
                    is TranscriptEvent.LineUpdated -> {
                        linesById[event.line.id] = event.line.text?.trim().orEmpty()
                        Log.d(TAG, "LineUpdated id=${event.line.id} text=[${event.line.text}]")
                    }
                    is TranscriptEvent.LineTextChanged -> {
                        linesById[event.line.id] = event.line.text?.trim().orEmpty()
                        Log.d(TAG, "LineTextChanged id=${event.line.id} text=[${event.line.text}]")
                    }
                    is TranscriptEvent.LineCompleted -> {
                        linesById[event.line.id] = event.line.text?.trim().orEmpty()
                        Log.d(TAG, "LineCompleted id=${event.line.id} text=[${event.line.text}]")
                    }
                    is TranscriptEvent.Error -> {
                        failure = event.cause ?: RuntimeException("Unknown Moonshine error")
                        Log.e(TAG, "Moonshine error", failure)
                    }
                }

                if (failure == null) {
                    val current = buildTranscriptText(linesById)
                    if (current != lastEmitted) {
                        lastEmitted = current
                        textToEmit = current
                    }
                }
            }
            textToEmit?.let {
                Log.d(TAG, "Emitting partial transcript: [$it]")
                onTranscriptChanged(it)
            }
        }

        transcriber.addListener(listener)
        transcriber.start()
        Log.d(TAG, "Streaming session started (model=${modelDir.absolutePath})")
        return session
    }

    fun transcribe(
        context: Context,
        samples: FloatArray,
        sampleRateHz: Int
    ): String {
        val modelDir = MoonshineStreamingAssets.ensureInstalled(context)
        val transcriber = Transcriber()
        transcriber.loadFromFiles(
            modelDir.absolutePath,
            JNI.MOONSHINE_MODEL_ARCH_TINY_STREAMING
        )

        val linesById = LinkedHashMap<Long, String>()
        val listener = Consumer<TranscriptEvent> { event ->
            when (event) {
                is TranscriptEvent.LineStarted -> linesById[event.line.id] = event.line.text?.trim().orEmpty()
                is TranscriptEvent.LineUpdated -> linesById[event.line.id] = event.line.text?.trim().orEmpty()
                is TranscriptEvent.LineTextChanged -> linesById[event.line.id] = event.line.text?.trim().orEmpty()
                is TranscriptEvent.LineCompleted -> linesById[event.line.id] = event.line.text?.trim().orEmpty()
                is TranscriptEvent.Error -> throw RuntimeException(event.cause)
            }
        }

        transcriber.addListener(listener)
        try {
            transcriber.start()

            var cursor = 0
            val chunkSize = 1600
            while (cursor < samples.size) {
                val end = min(cursor + chunkSize, samples.size)
                val chunk = samples.copyOfRange(cursor, end)
                transcriber.addAudio(chunk, sampleRateHz)
                cursor = end
            }

            transcriber.stop()

            val eventText = buildTranscriptText(linesById)
            if (eventText.isNotBlank()) return eventText

            return transcriber
                .transcribeWithoutStreaming(samples, sampleRateHz)
                .text()
                .trim()
        } finally {
            transcriber.removeListener(listener)
        }
    }

    private fun buildTranscriptText(linesById: LinkedHashMap<Long, String>): String {
        return linesById.values
            .filter { it.isNotBlank() }
            .joinToString(separator = " ")
            .trim()
    }
}
