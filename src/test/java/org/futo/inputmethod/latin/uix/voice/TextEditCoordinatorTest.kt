package org.futo.inputmethod.latin.uix.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class TextEditCoordinatorTest {
    @Test
    fun stablePrefixAdvancesAfterThreeSnapshots() {
        val sink = RecordingEditSink()
        val coordinator = TextEditCoordinator(sink)

        coordinator.submit(EditIntent.VoiceSnapshot("hello"), generation = 0)
        coordinator.submit(EditIntent.VoiceSnapshot("hello world"), generation = 0)
        coordinator.submit(EditIntent.VoiceSnapshot("hello world!"), generation = 0)

        assertEquals(listOf("hello", "hello world", "hello world!"), sink.replacements)
        assertEquals(listOf(5), sink.frozenPrefixLengths)
        assertEquals(
            listOf("replace:hello", "replace:hello world", "replace:hello world!", "freezePrefix:5"),
            sink.events
        )

        coordinator.submit(EditIntent.VoiceSnapshot("hello there"), generation = 0)

        assertEquals(" there", sink.replacements.last())
    }

    @Test
    fun composingFreezesTailAndNextSnapshotStartsFresh() {
        val sink = RecordingEditSink()
        val coordinator = TextEditCoordinator(sink)

        coordinator.submit(EditIntent.VoiceSnapshot("draft"), generation = 0)
        coordinator.submit(EditIntent.KeyboardComposingStarted, generation = 0)
        coordinator.submit(EditIntent.VoiceSnapshot("fresh"), generation = 0)

        assertEquals(listOf("draft", "fresh"), sink.replacements)
        assertEquals(1, sink.freezeCount)
    }

    @Test
    fun staleGenerationIsDropped() {
        val sink = RecordingEditSink()
        val coordinator = TextEditCoordinator(sink)

        coordinator.submit(EditIntent.NewInputSession(1), generation = 0)
        sink.clear()
        coordinator.submit(EditIntent.VoiceSnapshot("stale"), generation = 0)
        coordinator.submit(EditIntent.VoiceSnapshot("current"), generation = 1)

        assertEquals(listOf("current"), sink.replacements)
    }

    @Test
    fun finalFreezesAndResetsTracking() {
        val sink = RecordingEditSink()
        val coordinator = TextEditCoordinator(sink)

        coordinator.submit(EditIntent.VoiceSnapshot("hello"), generation = 0)
        coordinator.submit(EditIntent.VoiceSnapshot("hello world"), generation = 0)
        coordinator.submit(EditIntent.VoiceSnapshot("hello world!"), generation = 0)
        sink.clear()

        coordinator.submit(EditIntent.VoiceFinal("hello world"), generation = 0)
        assertEquals(listOf("replace: world", "freeze"), sink.events)
        coordinator.submit(EditIntent.VoiceSnapshot("hello again"), generation = 0)

        assertEquals(listOf(" world", "hello again"), sink.replacements)
        assertEquals(1, sink.freezeCount)
    }

    private class RecordingEditSink : EditSink {
        val events = mutableListOf<String>()
        val replacements = mutableListOf<String>()
        val frozenPrefixLengths = mutableListOf<Int>()
        var freezeCount = 0

        override fun replaceVoiceTail(text: String) {
            events += "replace:$text"
            replacements += text
        }

        override fun freezeVoiceTailPrefix(length: Int) {
            events += "freezePrefix:$length"
            frozenPrefixLengths += length
        }

        override fun freezeVoiceTail() {
            events += "freeze"
            freezeCount++
        }

        fun clear() {
            events.clear()
            replacements.clear()
            frozenPrefixLengths.clear()
            freezeCount = 0
        }
    }
}
