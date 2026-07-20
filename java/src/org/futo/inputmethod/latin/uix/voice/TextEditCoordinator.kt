package org.futo.inputmethod.latin.uix.voice

interface EditSink {
    /**
     * Replace the current revisable tail with [frozenAppend] + [tail].
     * [frozenAppend] becomes permanent immediately (never rewritten); [tail] is the new
     * revisable region. Either may be empty ("" + "" just clears the tail).
     * The sink transforms outgoing text (sanitize w/ field context, boundary space,
     * sentence caps) and tracks the WRITTEN length of the tail itself.
     */
    fun updateVoiceText(frozenAppend: String, tail: String)

    /** Current tail becomes permanent field content; tracking drops. */
    fun freezeVoiceTail()
}

sealed interface EditIntent {
    /** RAW recognizer hypothesis (untransformed). The sink owns all text transformation. */
    data class VoiceSnapshot(val fullHypothesis: String) : EditIntent
    /** RAW final recognizer result. */
    data class VoiceFinal(val text: String) : EditIntent
    object KeyboardComposingStarted : EditIntent
    object KeyboardWordCommitted : EditIntent
    data class SelectionChanged(val start: Int, val end: Int, val userInitiated: Boolean) : EditIntent
    data class NewInputSession(val generation: Long) : EditIntent
}

/**
 * Append-only frontier state machine over RAW whitespace-tokenized recognizer snapshots
 * (per docs/Research/state_machine.txt). Tokens before [frontier] have been emitted as
 * permanent text and are never rewritten; if the recognizer revises anything before the
 * frontier (token merge/split/rewrite), the burst FAILS CLOSED: the revisable tail is
 * cleared and nothing more is emitted until VoiceFinal resets the burst. This trades a
 * few lost words in a rare case for never re-pasting or corrupting committed text.
 */
class TextEditCoordinator(private val sink: EditSink) {
    private var currentGeneration = 0L

    // --- per-burst state (reset on VoiceFinal / NewInputSession) ---
    private var prevRawTokens: List<String> = emptyList()
    /** Count of tokens emitted as permanent text. High-water mark, validated per snapshot. */
    private var frontier = 0
    private var revisionCrossedFrontier = false
    private var composingActive = false
    /** Token count of the tail last handed to the sink (frontier advances by this on freeze). */
    private var lastWrittenTailTokens = 0

    fun submit(intent: EditIntent, generation: Long) {
        if (generation != currentGeneration) return

        when (intent) {
            is EditIntent.VoiceSnapshot -> applySnapshot(intent.fullHypothesis)
            is EditIntent.VoiceFinal -> applyFinal(intent.text)
            EditIntent.KeyboardComposingStarted -> {
                freezeTail()
                composingActive = true
            }
            EditIntent.KeyboardWordCommitted -> composingActive = false
            is EditIntent.SelectionChanged -> {
                // Deliberate collapsed cursor placement: freeze and continue at the new cursor.
                if (intent.userInitiated && intent.start == intent.end) freezeTail()
            }
            is EditIntent.NewInputSession -> {
                currentGeneration = intent.generation
                sink.freezeVoiceTail()
                resetBurst()
            }
        }
    }

    private fun applySnapshot(rawFull: String) {
        val tokens = tokenize(rawFull)
        if (tokens.isEmpty()) {
            sink.updateVoiceText("", "")
            lastWrittenTailTokens = 0
            return
        }

        val common = commonPrefixLength(prevRawTokens, tokens)
        prevRawTokens = tokens

        if (common < frontier) {
            // Recognizer revised already-permanent text: fail closed for the rest of the burst.
            revisionCrossedFrontier = true
            sink.updateVoiceText("", "")
            lastWrittenTailTokens = 0
            return
        }
        if (revisionCrossedFrontier) return
        if (composingActive) return // queue: bookkeeping only while a word is being typed

        // One-word holdback: only words stable across consecutive snapshots become permanent.
        val safeEnd = maxOf(frontier, common - HOLDBACK)
        sink.updateVoiceText(
            frozenAppend = tokens.subList(frontier, safeEnd).joinToString(" "),
            tail = tokens.subList(safeEnd, tokens.size).joinToString(" ")
        )
        lastWrittenTailTokens = tokens.size - safeEnd
        frontier = safeEnd
    }

    private fun applyFinal(rawFinal: String) {
        if (revisionCrossedFrontier) {
            // Uncertain tail was already cleared; commit nothing more.
            sink.freezeVoiceTail()
        } else {
            val tokens = tokenize(rawFinal)
            val start = minOf(frontier, tokens.size)
            sink.updateVoiceText(tokens.subList(start, tokens.size).joinToString(" "), "")
            sink.freezeVoiceTail()
        }
        resetBurst()
    }

    /**
     * Touch interrupted dictation: whatever tail is in the field becomes permanent, so the
     * frontier advances past the tokens it contained. The recognizer keeps streaming the same
     * transcript; later snapshots emit only tokens beyond the new frontier.
     */
    private fun freezeTail() {
        sink.freezeVoiceTail()
        frontier += lastWrittenTailTokens
        lastWrittenTailTokens = 0
    }

    private fun resetBurst() {
        prevRawTokens = emptyList()
        frontier = 0
        revisionCrossedFrontier = false
        composingActive = false
        lastWrittenTailTokens = 0
    }

    private fun tokenize(raw: String): List<String> {
        val trimmed = raw.trim()
        return if (trimmed.isEmpty()) emptyList() else trimmed.split(WHITESPACE)
    }

    private fun commonPrefixLength(a: List<String>, b: List<String>): Int {
        val max = minOf(a.size, b.size)
        var i = 0
        while (i < max && a[i] == b[i]) i++
        return i
    }

    private companion object {
        const val HOLDBACK = 1
        val WHITESPACE = Regex("\\s+")
    }
}
