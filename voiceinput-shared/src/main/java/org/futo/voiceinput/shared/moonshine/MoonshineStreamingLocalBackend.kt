package org.futo.voiceinput.shared.moonshine

import ai.moonshine.voice.JNI
import ai.moonshine.voice.Transcriber
import ai.moonshine.voice.TranscriptEvent
import java.util.LinkedHashMap
import java.util.function.Consumer
import kotlin.math.min

class MoonshineStreamingLocalBackend {
    fun preload(context: android.content.Context) {
        MoonshineStreamingAssets.ensureInstalled(context)
    }

    fun transcribe(
        context: android.content.Context,
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

            val eventText = linesById.values
                .filter { it.isNotBlank() }
                .joinToString(separator = " ")
                .trim()
            if (eventText.isNotBlank()) return eventText

            return transcriber
                .transcribeWithoutStreaming(samples, sampleRateHz)
                .text()
                .trim()
        } finally {
            transcriber.removeListener(listener)
        }
    }
}
