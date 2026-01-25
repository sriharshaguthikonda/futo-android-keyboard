package org.futo.inline

import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Produces an inline suggestion continuation based on local context. */
interface InlineSuggestionProvider {
    /**
     * @param beforeCursor Full-ish context immediately before cursor (truncate as needed)
     * @param editorInfo EditorInfo for inputType heuristics
     * @return A suggestion that should be inserted AT cursor, without leading spaces (usually).
     */
    suspend fun suggest(beforeCursor: String, editorInfo: EditorInfo): String?
}

/**
 * Proof-of-life provider (replace with your real local model).
 *
 * Examples:
 *   "thank you" -> " for your help"
 *   ends with "?" -> " I’m not sure—can you clarify?"
 */
class RuleBasedProvider : InlineSuggestionProvider {
    override suspend fun suggest(beforeCursor: String, editorInfo: EditorInfo): String? {
        val t = beforeCursor.trimEnd()
        if (t.endsWith("thank you", ignoreCase = true)) return " for your help"
        if (t.endsWith("?")) return " I’m not sure—can you clarify?"
        if (t.endsWith("Regards", ignoreCase = true)) return ",\nHarsha"
        return null
    }
}

// ============================================================================
// MULTI-WORD / SENTENCE-LIKE CONTINUATIONS (the part you asked for)
// ============================================================================

/** A next-word candidate from a model with an associated score (log-prob). */
data class NextWordCandidate(
    val word: String,
    val logProb: Float,
)

/**
 * If you can access the FUTO transformer LM (llama.cpp) next-word API, expose it like this.
 *
 * IMPORTANT: FUTO’s LM is intentionally optimized for **word-level** inference (see their docs about
 * whitespace-as-suffix tokenization used to know when a word ends). The normal pipeline stops after 1 word.
 * For “sentences”, you repeatedly call next-word prediction in a loop.
 */
interface NextWordProvider {
    /**
     * Return top-K next-word candidates for the given context.
     * Words should NOT include leading spaces; treat spacing separately.
     */
    suspend fun topKNextWords(context: String, editorInfo: EditorInfo, k: Int): List<NextWordCandidate>
}

/**
 * Generates a multi-word continuation by repeatedly calling a NextWordProvider.
 *
 * This is the core trick: the model was built to give **next word** efficiently.
 * So we generate 3–8 words by chaining next-word calls.
 */
class MultiWordContinuationProvider(
    private val nextWord: NextWordProvider,
    private val maxWords: Int = 6,
    private val beamWidth: Int = 3,
    private val topKPerStep: Int = 6,
    private val minAvgLogProb: Float = -4.5f,
    private val stopOnPunctuation: Boolean = true,
    private val stopChars: Set<Char> = setOf('.', '!', '?'),
    private val repetitionPenalty: Float = 0.8f,
) : InlineSuggestionProvider {

    override suspend fun suggest(beforeCursor: String, editorInfo: EditorInfo): String? {
        val context = beforeCursor.takeLast(240) // keep it cheap

        // Don’t try to be clever if context is empty.
        if (context.isBlank()) return null

        // Beam search over sequences of words.
        data class Beam(val words: List<String>, val score: Float)

        fun applyRepetitionPenalty(words: List<String>, candidate: String, baseScore: Float): Float {
            if (words.isEmpty()) return baseScore
            val lower = candidate.lowercase()
            val repeats = words.count { it.lowercase() == lower }
            return if (repeats == 0) baseScore else baseScore + (repetitionPenalty * repeats)
        }

        var beams = listOf(Beam(emptyList(), 0f))

        for (step in 0 until maxWords) {
            val expanded = mutableListOf<Beam>()

            for (beam in beams) {
                val partial = buildString {
                    append(context)
                    if (beam.words.isNotEmpty()) {
                        // IMPORTANT: we insert spaces between words.
                        // If your LM expects different spacing, adjust here.
                        append(" ")
                        append(beam.words.joinToString(" "))
                    }
                }

                val cands = nextWord.topKNextWords(partial, editorInfo, topKPerStep)
                for (c in cands) {
                    val w = c.word.trim()
                    if (w.isEmpty()) continue

                    // Avoid pathological loops (e.g. "the the the").
                    if (beam.words.isNotEmpty() && beam.words.last().equals(w, ignoreCase = true)) continue

                    val newScore = applyRepetitionPenalty(beam.words, w, beam.score + c.logProb)
                    expanded += Beam(beam.words + w, newScore)
                }
            }

            if (expanded.isEmpty()) break

            // Keep best beams.
            beams = expanded.sortedByDescending { it.score }.take(beamWidth)

            // Early stop: if best beam ends in punctuation, consider it complete.
            val bestText = beams.first().words.joinToString(" ")
            if (stopOnPunctuation && bestText.isNotEmpty() && bestText.last() in stopChars) break
        }

        val best = beams.maxByOrNull { it.score } ?: return null
        if (best.words.isEmpty()) return null

        val text = best.words.joinToString(" ")

        // Quality gate: reject low-confidence junk.
        val avg = best.score / best.words.size
        if (avg < minAvgLogProb) return null

        // Optional: If you want “sentence-like” output, prefer beams that contain punctuation
        // by adding a small bonus when a candidate ends with .,!,?. (Not shown here.)

        // Return with a leading space if context doesn’t already end with whitespace.
        val needsSpace = context.isNotEmpty() && !context.last().isWhitespace()
        return (if (needsSpace) " " else "") + text
    }
}

/** Configuration knobs you will actually tweak. */
data class InlineSuggestionConfig(
    val enabled: Boolean = true,
    val debounceMs: Long = 300,
    val maxContextChars: Int = 200,
    val maxSuggestionChars: Int = 120,
    val requireWordBoundaryBeforeSuggest: Boolean = true,
    val allowInPasswordFields: Boolean = false,
    val allowWhenSelectionNotCollapsed: Boolean = false,
    val styleAsGhostIfPossible: Boolean = true,
)

/**
 * Controller that owns the state machine + safe editor mutations.
 *
 * You must call:
 * - onStartInput(editorInfo)
 * - onFinishInput()
 * - onUpdateSelection(...)
 * - onUserKeyPress(...)
 * - acceptInlineSuggestion() for your chosen accept gesture
 */
class InlineSuggestionController(
    private val provider: InlineSuggestionProvider,
    private val scope: CoroutineScope,
    private val config: InlineSuggestionConfig = InlineSuggestionConfig(),
    /** Provide current InputConnection from your IME (never store a stale one). */
    private val inputConnectionProvider: () -> InputConnection?,
    /** Provide current EditorInfo from your IME. */
    private val editorInfoProvider: () -> EditorInfo?
) {
    // --- Internal state ---

    private enum class State { IDLE, WAITING, REQUESTING, SHOWING }

    private var state: State = State.IDLE
    private var job: Job? = null

    // last known selection + composing span boundaries (absolute indices)
    private var selStart: Int = -1
    private var selEnd: Int = -1
    private var candidatesStart: Int = -1
    private var candidatesEnd: Int = -1

    // ghost suggestion region
    private var ghostText: String = ""
    private var ghostStart: Int = -1
    private var ghostEnd: Int = -1

    // guards
    private var selfEditDepth: Int = 0
    private var lastUserActivityUptime: Long = 0L

    /** Call from InputMethodService.onStartInput/onStartInputView */
    fun onStartInput() {
        resetHard()
    }

    /** Call from InputMethodService.onFinishInput/onFinishInputView */
    fun onFinishInput() {
        clearInlineSuggestion(reason = "finish input")
        resetHard()
    }

    /**
     * Call from InputMethodService.onUpdateSelection(...)
     * candidatesStart/candidatesEnd track composing region bounds.
     */
    fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        newCandidatesStart: Int,
        newCandidatesEnd: Int,
    ) {
        selStart = newSelStart
        selEnd = newSelEnd
        candidatesStart = newCandidatesStart
        candidatesEnd = newCandidatesEnd

        // If the user moved the cursor/selection while showing, attempt safe cleanup.
        if (state == State.SHOWING && selfEditDepth == 0) {
            val cursorMoved = (newSelStart != oldSelStart) || (newSelEnd != oldSelEnd)
            val selectionExpanded = newSelStart != newSelEnd
            if (cursorMoved || selectionExpanded) {
                // Best-effort removal, but do NOT risk deleting user text if we can't verify.
                clearInlineSuggestion(reason = "cursor/selection moved", verifyBeforeDelete = true)
            }
        }

        // Optional: if editor created a composing region for normal typing, don't offer inline.
        // We use this in shouldOfferNow().
    }

    /**
     * Call this on any user activity (typing, swipe, gesture).
     *
     * For normal characters: cancel inline suggestion first.
     * For accept key: you’ll call acceptInlineSuggestion() instead.
     */
    fun onUserTypedNonAcceptKey() {
        lastUserActivityUptime = SystemClock.uptimeMillis()
        if (state == State.SHOWING) {
            clearInlineSuggestion(reason = "user typed")
        } else {
            cancelPendingRequest()
        }
    }

    /** Call when backspace pressed (before your normal backspace handling). */
    fun onBackspace(): Boolean {
        lastUserActivityUptime = SystemClock.uptimeMillis()
        return if (state == State.SHOWING) {
            clearInlineSuggestion(reason = "backspace")
            // We consumed the action; caller should NOT do normal backspace.
            true
        } else {
            cancelPendingRequest()
            false
        }
    }

    /**
     * Call when the user triggers ACCEPT (Tab, swipe-right, dedicated key, etc.)
     * @return true if accepted/consumed.
     */
    fun acceptInlineSuggestion(addTrailingSpace: Boolean = false): Boolean {
        if (state != State.SHOWING) return false
        val ic = inputConnectionProvider() ?: return false

        selfEditDepth++
        try {
            ic.beginBatchEdit()

            // Move caret to end of ghost and finish composing.
            if (ghostEnd >= 0) {
                ic.setSelection(ghostEnd, ghostEnd)
            }
            ic.finishComposingText()

            if (addTrailingSpace) {
                ic.commitText(" ", 1)
            }

            ic.endBatchEdit()
        } finally {
            selfEditDepth--
        }

        resetGhost()
        state = State.IDLE
        return true
    }

    /**
     * Call this after “word finished” events (space/punct/enter), or after pause.
     * This is your main trigger.
     */
    fun scheduleInlineSuggestion() {
        if (!config.enabled) return

        lastUserActivityUptime = SystemClock.uptimeMillis()

        // If already showing, don't schedule.
        if (state == State.SHOWING) return

        // Cancel any pending request and debounce.
        cancelPendingRequest()

        if (!shouldOfferNow()) {
            state = State.IDLE
            return
        }

        state = State.WAITING
        job = scope.launch(Dispatchers.Main.immediate) {
            delay(config.debounceMs)

            // If user continued typing during debounce, abort.
            if (SystemClock.uptimeMillis() - lastUserActivityUptime < config.debounceMs) {
                state = State.IDLE
                return@launch
            }

            if (!shouldOfferNow()) {
                state = State.IDLE
                return@launch
            }

            state = State.REQUESTING

            val ic = inputConnectionProvider() ?: run {
                state = State.IDLE
                return@launch
            }
            val ei = editorInfoProvider() ?: run {
                state = State.IDLE
                return@launch
            }

            val before = safeGetBeforeCursor(ic, config.maxContextChars)
            val suggestion = withContext(Dispatchers.Default) {
                provider.suggest(before, ei)
            }?.take(config.maxSuggestionChars)

            if (suggestion.isNullOrBlank()) {
                state = State.IDLE
                return@launch
            }

            // Still eligible? (cursor may have moved)
            if (!shouldOfferNow()) {
                state = State.IDLE
                return@launch
            }

            showSuggestion(ic, suggestion, ei)
        }
    }

    // ------------------------------
    // Internal helpers
    // ------------------------------

    private fun shouldOfferNow(): Boolean {
        val ei = editorInfoProvider() ?: return false
        if (!config.allowInPasswordFields && isPasswordField(ei)) return false

        // Don’t show if there is an active composing region from normal typing.
        // candidatesStart/candidatesEnd are set via onUpdateSelection.
        val hasNormalComposing = candidatesStart >= 0 && candidatesEnd >= 0
        if (hasNormalComposing) return false

        // Don’t show if user has selection (unless explicitly allowed).
        val selectionCollapsed = selStart >= 0 && selStart == selEnd
        if (!selectionCollapsed && !config.allowWhenSelectionNotCollapsed) return false

        val ic = inputConnectionProvider() ?: return false

        // Heuristic: require word boundary before suggestion.
        if (config.requireWordBoundaryBeforeSuggest) {
            val before = safeGetBeforeCursor(ic, 2)
            if (before.isEmpty()) return false
            val last = before.last()
            val ok = last.isWhitespace() || last in charArrayOf('.', ',', '!', '?', ';', ':', ')', '"', '\'', '\n')
            if (!ok) return false
        }

        // Also respect editors that ask for no personalized learning.
        val noPersonalized = (ei.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        if (noPersonalized) return false

        return true
    }

    private fun showSuggestion(ic: InputConnection, suggestion: String, ei: EditorInfo) {
        // Cancel any existing ghost just in case.
        clearInlineSuggestion(reason = "replace")

        val styled: CharSequence = if (config.styleAsGhostIfPossible) {
            // Many editors ignore this span and underline anyway; that's fine.
            SpannableString(suggestion).apply {
                // NOTE: You’ll pick an actual colour from your theme system.
                // Here we use a placeholder span range. Remove if undesired.
                setSpan(ForegroundColorSpan(0xFF888888.toInt()), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        } else {
            suggestion
        }

        selfEditDepth++
        try {
            ic.beginBatchEdit()

            // Insert as composing text at cursor.
            // newCursorPosition=1 places cursor AFTER inserted text, so we move it back.
            ic.setComposingText(styled, 1)

            // Keep cursor where the user was typing (start of suggestion).
            if (selStart >= 0) {
                ic.setSelection(selStart, selStart)
            }

            ic.endBatchEdit()
        } finally {
            selfEditDepth--
        }

        ghostText = suggestion

        // We don't know ghostStart/ghostEnd yet in a robust way until the editor reports it.
        // It should arrive via onUpdateSelection as candidatesStart/candidatesEnd.
        // As a fallback, we estimate via extracted text if available.
        estimateGhostBoundsFallback(ic)

        state = State.SHOWING
    }

    private fun estimateGhostBoundsFallback(ic: InputConnection) {
        // Best effort only.
        // Many editors will update candidatesStart/candidatesEnd promptly.
        if (ghostText.isEmpty()) return

        if (candidatesStart >= 0 && candidatesEnd >= 0) {
            ghostStart = candidatesStart
            ghostEnd = candidatesEnd
            return
        }

        // Try ExtractedText (may return null or truncated).
        val et = ic.getExtractedText(ExtractedTextRequest(), 0)
        if (et != null) {
            val caret = et.selectionStart
            if (caret >= 0) {
                // We inserted suggestion at caret. Cursor was moved back to caret.
                ghostStart = caret
                ghostEnd = caret + ghostText.length
            }
        }
    }

    private fun clearInlineSuggestion(
        reason: String,
        verifyBeforeDelete: Boolean = false
    ) {
        cancelPendingRequest()

        if (state != State.SHOWING) {
            resetGhost()
            state = State.IDLE
            return
        }

        val ic = inputConnectionProvider() ?: run {
            resetGhost()
            state = State.IDLE
            return
        }

        // If candidatesStart/candidatesEnd is available, prefer it.
        if (candidatesStart >= 0 && candidatesEnd >= 0) {
            ghostStart = candidatesStart
            ghostEnd = candidatesEnd
        }

        // If we can't confidently identify the region, just drop state.
        if (ghostStart < 0 || ghostEnd < 0 || ghostEnd <= ghostStart) {
            resetGhost()
            state = State.IDLE
            return
        }

        if (verifyBeforeDelete && !regionMatches(ic, ghostStart, ghostEnd, ghostText)) {
            // Don’t risk deleting user text.
            resetGhost()
            state = State.IDLE
            return
        }

        selfEditDepth++
        try {
            ic.beginBatchEdit()

            // Replace the ghost region with empty.
            ic.setSelection(ghostStart, ghostEnd)
            ic.commitText("", 1)
            ic.finishComposingText()

            // Put caret back at start position (where user was typing).
            ic.setSelection(ghostStart, ghostStart)

            ic.endBatchEdit()
        } finally {
            selfEditDepth--
        }

        resetGhost()
        state = State.IDLE
    }

    private fun regionMatches(ic: InputConnection, start: Int, end: Int, expected: String): Boolean {
        // Most robust check uses ExtractedText. Some fields return null.
        val et: ExtractedText? = ic.getExtractedText(ExtractedTextRequest(), 0)
        val text = et?.text ?: return false
        if (start < 0 || end > text.length || end <= start) return false
        val sub = text.subSequence(start, end).toString()
        return sub == expected
    }

    private fun safeGetBeforeCursor(ic: InputConnection, max: Int): String {
        return (ic.getTextBeforeCursor(max, 0) ?: "").toString()
    }

    private fun isPasswordField(ei: EditorInfo): Boolean {
        val variation = ei.inputType and EditorInfo.TYPE_MASK_VARIATION
        val klass = ei.inputType and EditorInfo.TYPE_MASK_CLASS

        // Covers common password variations.
        return (klass == EditorInfo.TYPE_CLASS_TEXT && (
            variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD
            )) ||
            (klass == EditorInfo.TYPE_CLASS_NUMBER && variation == EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD)
    }

    private fun cancelPendingRequest() {
        job?.cancel()
        job = null
        if (state == State.WAITING || state == State.REQUESTING) {
            state = State.IDLE
        }
    }

    private fun resetHard() {
        cancelPendingRequest()
        resetGhost()
        state = State.IDLE
        selStart = -1
        selEnd = -1
        candidatesStart = -1
        candidatesEnd = -1
        selfEditDepth = 0
        lastUserActivityUptime = 0L
    }

    private fun resetGhost() {
        ghostText = ""
        ghostStart = -1
        ghostEnd = -1
    }
}
