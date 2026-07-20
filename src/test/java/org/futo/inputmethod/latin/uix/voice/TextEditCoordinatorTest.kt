package org.futo.inputmethod.latin.uix.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class TextEditCoordinatorTest {
    @Test
    fun stablePrefixAdvancesOnWordBoundaryOnly() {
        val sink = RecordingEditSink()
        val coordinator = TextEditCoordinator(sink)

        // Three agreeing snapshots whose shared prefix "hello" has no trailing space:
        // nothing may be frozen yet (a half word could still be revised).
        coordinator.submit(EditIntent.VoiceSnapshot("hello"), generation = 0)
        coordinator.submit(EditIntent.VoiceSnapshot("hello world"), generation = 0)
        coordinator.submit(EditIntent.VoiceSnapshot("hello world"), generation = 0)
        assertEquals(emptyList<Int>(), sink.frozenPrefixLengths)

        // Now the shared prefix reaches "hello world" — the complete word "hello " (6 chars,
        // incl. trailing space) becomes stable; "world" stays in the mutable tail.
        coordinator.submit(EditIntent.VoiceSnapshot("hello world today"), generation = 0)
        assertEquals(listOf(6), sink.frozenPrefixLengths)

        // Subsequent snapshots are expressed relative to the frozen "hello ".
        coordinator.submit(EditIntent.VoiceSnapshot("hello world tonight"), generation = 0)
        assertEquals("world tonight", sink.replacements.last())
    }

    @Test
    fun freezeDoesNotDuplicateContinuingHypothesis() {
        val sink = RecordingEditSink()
        val coordinator = TextEditCoordinator(sink)

        // Mid-dictation the user types a key; the recognizer keeps streaming the SAME transcript.
        coordinator.submit(EditIntent.VoiceSnapshot("hello wor"), generation = 0)
        coordinator.submit(EditIntent.KeyboardComposingStarted, generation = 0)
        coordinator.submit(EditIntent.VoiceSnapshot("hello world today"), generation = 0)

        // Only the NEW suffix may be committed — never the whole hypothesis again.
        assertEquals(listOf("hello wor", "ld today"), sink.replacements)
        assertEquals(1, sink.freezeCount)
    }

    @Test
    fun midWordFragmentIsNeverFrozen() {
        val sink = RecordingEditSink()
        val coordinator = TextEditCoordinator(sink)

        // Shared prefix "wash" has no space — must never be frozen as a half word.
        coordinator.submit(EditIntent.VoiceSnapshot("wash"), generation = 0)
        coordinator.submit(EditIntent.VoiceSnapshot("wash"), generation = 0)
        coordinator.submit(EditIntent.VoiceSnapshot("wash"), generation = 0)

        assertEquals(emptyList<Int>(), sink.frozenPrefixLengths)
    }

    @Test
    fun freezesCompletedWordKeepsTrailingFragmentMutable() {
        val sink = RecordingEditSink()
        val coordinator = TextEditCoordinator(sink)

        // Shared prefix "washing mac": freeze the whole word "washing " (8), keep "mac" mutable.
        coordinator.submit(EditIntent.VoiceSnapshot("washing mac"), generation = 0)
        coordinator.submit(EditIntent.VoiceSnapshot("washing mac"), generation = 0)
        coordinator.submit(EditIntent.VoiceSnapshot("washing mac"), generation = 0)
        assertEquals(listOf(8), sink.frozenPrefixLengths)

        coordinator.submit(EditIntent.VoiceSnapshot("washing machine"), generation = 0)
        assertEquals("machine", sink.replacements.last())
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

        // Freeze the whole word "washing " first, leaving "mac" mutable.
        coordinator.submit(EditIntent.VoiceSnapshot("washing mac"), generation = 0)
        coordinator.submit(EditIntent.VoiceSnapshot("washing mac"), generation = 0)
        coordinator.submit(EditIntent.VoiceSnapshot("washing mac"), generation = 0)
        sink.clear()

        // Final result is expressed relative to the frozen "washing " prefix.
        coordinator.submit(EditIntent.VoiceFinal("washing machine"), generation = 0)
        assertEquals(listOf("replace:machine", "freeze"), sink.events)
        coordinator.submit(EditIntent.VoiceSnapshot("new note"), generation = 0)

        assertEquals(listOf("machine", "new note"), sink.replacements)
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
