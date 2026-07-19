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
            EditIntent.KeyboardComposingStarted -> freezeAndReset()
            EditIntent.KeyboardWordCommitted -> Unit
            is EditIntent.SelectionChanged -> {
                if (intent.userInitiated && intent.start == intent.end) freezeAndReset()
            }
            is EditIntent.NewInputSession -> {
                currentGeneration = intent.generation
                freezeAndReset()
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
        if (!commonPrefix.startsWith(stablePrefix) || commonPrefix.length == stablePrefix.length) return

        val newlyStableLength = commonPrefix.length - stablePrefix.length
        sink.freezeVoiceTailPrefix(newlyStableLength)
        stablePrefix = commonPrefix
        mutableTail = mutableTail.drop(newlyStableLength)
    }

    private fun freezeAndReset() {
        sink.freezeVoiceTail()
        resetTracking()
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
