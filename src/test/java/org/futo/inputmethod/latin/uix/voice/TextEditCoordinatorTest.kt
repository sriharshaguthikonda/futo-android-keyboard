package org.futo.inputmethod.latin.uix.voice

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure coordinator tests over RAW token space: the fake sink records (frozenAppend, tail) pairs;
 * no sanitizer/transformation is involved at this layer.
 */
class TextEditCoordinatorTest {
    // (a) growing snapshots: frontier advances only past the validated common prefix with a
    // one-word holdback; frozen text is never re-emitted.
    @Test
    fun frontierAdvancesWithHoldbackAndNeverReEmits() {
        val sink = RecordingEditSink()
        val coordinator = TextEditCoordinator(sink)

        coordinator.submit(EditIntent.VoiceSnapshot("hello"), generation = 0)
        // First snapshot: nothing validated yet, everything stays in the revisable tail.
        assertEquals("" to "hello", sink.updates.last())

        coordinator.submit(EditIntent.VoiceSnapshot("hello world"), generation = 0)
        // common=1, holdback 1 → still nothing frozen.
        assertEquals("" to "hello world", sink.updates.last())

        coordinator.submit(EditIntent.VoiceSnapshot("hello world today"), generation = 0)
        // common=2 → freeze "hello", hold back "world" in the tail.
        assertEquals("hello" to "world today", sink.updates.last())

        coordinator.submit(EditIntent.VoiceSnapshot("hello world today now"), generation = 0)
        // common=3 → freeze "world" only; "hello" is never re-emitted.
        assertEquals("world" to "today now", sink.updates.last())

        assertEquals(
            listOf("", "", "hello", "world"),
            sink.updates.map { it.first }
        )
    }

    // (b) REGRESSION for the on-device re-paste bug: a revision of an already-frozen word must
    // fail closed — tail cleared, NO re-emission of old text, nothing more until VoiceFinal.
    @Test
    fun revisionBeforeFrontierFailsClosed() {
        val sink = RecordingEditSink()
        val coordinator = TextEditCoordinator(sink)

        coordinator.submit(EditIntent.VoiceSnapshot("hello world today"), generation = 0)
        coordinator.submit(EditIntent.VoiceSnapshot("hello world today"), generation = 0)
        assertEquals("hello world" to "today", sink.updates.last())

        // Recognizer rewrites the frozen "hello" → common=0 < frontier=2.
        coordinator.submit(EditIntent.VoiceSnapshot("goodbye world today"), generation = 0)
        assertEquals("" to "", sink.updates.last()) // tail cleared, nothing re-emitted

        // Burst stays closed: later snapshots produce no writes at all.
        sink.clear()
        coordinator.submit(EditIntent.VoiceSnapshot("goodbye world today friend"), generation = 0)
        assertEquals(emptyList<Pair<String, String>>(), sink.updates)

        // VoiceFinal while closed: freeze only, never a late commit of uncertain text.
        coordinator.submit(EditIntent.VoiceFinal("goodbye world today friend"), generation = 0)
        assertEquals(listOf("freeze"), sink.events)

        // Burst state fully reset: the next utterance starts clean.
        sink.clear()
        coordinator.submit(EditIntent.VoiceSnapshot("fresh start"), generation = 0)
        assertEquals(listOf("" to "fresh start"), sink.updates)
    }

    // (c) token merge "gon na"→"gonna home": positions drift, common prefix drops below the
    // frontier → fail closed. Never silent word loss or wrong emission.
    @Test
    fun tokenMergeFailsClosed() {
        val sink = RecordingEditSink()
        val coordinator = TextEditCoordinator(sink)

        coordinator.submit(EditIntent.VoiceSnapshot("gon na"), generation = 0)
        coordinator.submit(EditIntent.VoiceSnapshot("gon na"), generation = 0)
        assertEquals("gon" to "na", sink.updates.last())

        coordinator.submit(EditIntent.VoiceSnapshot("gonna home"), generation = 0)
        assertEquals("" to "", sink.updates.last())

        sink.clear()
        coordinator.submit(EditIntent.VoiceSnapshot("gonna home now"), generation = 0)
        assertEquals(emptyList<Pair<String, String>>(), sink.updates)
    }

    // (d) composingActive queues: no field writes while a word is being typed; backlog beyond the
    // frontier flushes after KeyboardWordCommitted.
    @Test
    fun composingQueuesAndFlushesAfterWordCommitted() {
        val sink = RecordingEditSink()
        val coordinator = TextEditCoordinator(sink)

        coordinator.submit(EditIntent.VoiceSnapshot("one"), generation = 0)
        assertEquals("" to "one", sink.updates.last())

        coordinator.submit(EditIntent.KeyboardComposingStarted, generation = 0)
        assertEquals(1, sink.freezeCount) // written tail "one" became permanent

        sink.clear()
        coordinator.submit(EditIntent.VoiceSnapshot("one two"), generation = 0)
        coordinator.submit(EditIntent.VoiceSnapshot("one two three"), generation = 0)
        assertEquals(emptyList<Pair<String, String>>(), sink.updates) // queued, no writes

        coordinator.submit(EditIntent.KeyboardWordCommitted, generation = 0)
        coordinator.submit(EditIntent.VoiceSnapshot("one two three four"), generation = 0)
        // Frontier was advanced past "one" by the freeze; flush emits only what came after it.
        assertEquals(listOf("two" to "three four"), sink.updates)
    }

    // (e) VoiceFinal flushes tokens beyond the frontier, freezes, and resets for the next burst.
    @Test
    fun finalFlushesBeyondFrontierAndResets() {
        val sink = RecordingEditSink()
        val coordinator = TextEditCoordinator(sink)

        coordinator.submit(EditIntent.VoiceSnapshot("alpha beta"), generation = 0)
        coordinator.submit(EditIntent.VoiceSnapshot("alpha beta"), generation = 0)
        assertEquals("alpha" to "beta", sink.updates.last())

        sink.clear()
        coordinator.submit(EditIntent.VoiceFinal("alpha beta gamma"), generation = 0)
        assertEquals(listOf("update:beta gamma|", "freeze"), sink.events)

        // Next burst starts from a clean frontier.
        sink.clear()
        coordinator.submit(EditIntent.VoiceSnapshot("new note"), generation = 0)
        assertEquals(listOf("" to "new note"), sink.updates)
    }

    // (f) intents stamped with a stale generation are dropped.
    @Test
    fun staleGenerationIsDropped() {
        val sink = RecordingEditSink()
        val coordinator = TextEditCoordinator(sink)

        coordinator.submit(EditIntent.NewInputSession(1), generation = 0)
        sink.clear()

        coordinator.submit(EditIntent.VoiceSnapshot("stale"), generation = 0)
        assertEquals(emptyList<String>(), sink.events)

        coordinator.submit(EditIntent.VoiceSnapshot("current"), generation = 1)
        assertEquals(listOf("" to "current"), sink.updates)
    }

    // User taps to a collapsed cursor position mid-dictation: freeze, then dictation continues at
    // the new spot — the frozen tokens are never re-emitted.
    @Test
    fun userCursorMoveFreezesTail() {
        val sink = RecordingEditSink()
        val coordinator = TextEditCoordinator(sink)

        coordinator.submit(EditIntent.VoiceSnapshot("hello"), generation = 0)
        coordinator.submit(
            EditIntent.SelectionChanged(3, 3, userInitiated = true), generation = 0
        )
        assertEquals(1, sink.freezeCount)

        sink.clear()
        coordinator.submit(EditIntent.VoiceSnapshot("hello world"), generation = 0)
        // Not composing → continues immediately, emitting only tokens beyond the frozen "hello".
        assertEquals(listOf("" to "world"), sink.updates)
    }

    private class RecordingEditSink : EditSink {
        val events = mutableListOf<String>()
        val updates = mutableListOf<Pair<String, String>>()
        var freezeCount = 0

        override fun updateVoiceText(frozenAppend: String, tail: String) {
            events += "update:$frozenAppend|$tail"
            updates += frozenAppend to tail
        }

        override fun freezeVoiceTail() {
            events += "freeze"
            freezeCount++
        }

        fun clear() {
            events.clear()
            updates.clear()
            freezeCount = 0
        }
    }
}
