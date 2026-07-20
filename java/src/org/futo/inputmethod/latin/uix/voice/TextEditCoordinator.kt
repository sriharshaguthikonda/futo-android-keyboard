package org.futo.inputmethod.latin.uix.voice

interface EditSink {
    fun replaceVoiceTail(text: String)
    fun freezeVoiceTailPrefix(length: Int)
    fun freezeVoiceTail()
}

sealed interface EditIntent {
    data class VoiceSnapshot(val fullHypothesis: String) : EditIntent
    data class VoiceFinal(val text: String) : EditIntent
    object KeyboardComposingStarted : EditIntent
    object KeyboardWordCommitted : EditIntent
    data class SelectionChanged(val start: Int, val end: Int, val userInitiated: Boolean) : EditIntent
    data class NewInputSession(val generation: Long) : EditIntent
}

class TextEditCoordinator(private val sink: EditSink) {
    private var currentGeneration = 0L
    private var stablePrefix = ""
    private var mutableTail = ""
    private val recentSnapshots = ArrayDeque<String>(SNAPSHOT_COUNT)

    fun submit(intent: EditIntent, generation: Long) {
        if (generation != currentGeneration) return

        when (intent) {
            is EditIntent.VoiceSnapshot -> applySnapshot(intent.fullHypothesis)
            is EditIntent.VoiceFinal -> {
                sink.replaceVoiceTail(intent.text.removePrefix(stablePrefix))
                sink.freezeVoiceTail()
                resetTracking()
            }
            EditIntent.KeyboardComposingStarted -> freezeTail()
            EditIntent.KeyboardWordCommitted -> Unit
            is EditIntent.SelectionChanged -> {
                if (intent.userInitiated && intent.start == intent.end) freezeTail()
            }
            is EditIntent.NewInputSession -> {
                currentGeneration = intent.generation
                sink.freezeVoiceTail()
                resetTracking()
            }
        }
    }

    private fun applySnapshot(fullHypothesis: String) {
        mutableTail = fullHypothesis.removePrefix(stablePrefix)
        sink.replaceVoiceTail(mutableTail)

        recentSnapshots.addLast(fullHypothesis)
        if (recentSnapshots.size > SNAPSHOT_COUNT) recentSnapshots.removeFirst()
        if (recentSnapshots.size < SNAPSHOT_COUNT) return

        val commonPrefix = recentSnapshots.reduce { prefix, snapshot ->
            prefix.commonPrefixWith(snapshot)
        }
        if (!commonPrefix.startsWith(stablePrefix)) return

        // Freeze whole words only: advance to the last space in the shared prefix, so the
        // trailing in-progress word stays in the mutable tail until a space (or VoiceFinal)
        // arrives. Never freeze a half word the recognizer may still revise.
        // ponytail: revision of an already-frozen word (removePrefix mismatch) is a known
        // Step-2 limitation — handled there by the range ledger + context fingerprints.
        val boundary = commonPrefix.lastIndexOf(' ') + 1
        if (boundary <= stablePrefix.length) return

        val newStable = commonPrefix.substring(0, boundary)
        sink.freezeVoiceTailPrefix(newStable.length - stablePrefix.length)
        stablePrefix = newStable
        mutableTail = fullHypothesis.removePrefix(stablePrefix)
    }

    /**
     * Touch interrupted dictation: the current tail becomes permanent field content, but the
     * recognizer keeps streaming the SAME full transcript. Preserve everything already written
     * (stablePrefix + mutableTail) as the prefix to strip from later snapshots — resetting it
     * would re-commit the entire hypothesis and duplicate text in the field.
     */
    private fun freezeTail() {
        sink.freezeVoiceTail()
        stablePrefix += mutableTail
        mutableTail = ""
        recentSnapshots.clear()
    }

    private fun resetTracking() {
        stablePrefix = ""
        mutableTail = ""
        recentSnapshots.clear()
    }

    private companion object {
        const val SNAPSHOT_COUNT = 3
    }
}
