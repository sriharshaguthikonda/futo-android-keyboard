package org.futo.inputmethod.latin.uix.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.uix.AUDIO_FOCUS
import org.futo.inputmethod.latin.uix.CAN_EXPAND_SPACE
import org.futo.inputmethod.latin.uix.DISALLOW_SYMBOLS
import org.futo.inputmethod.latin.uix.GROQ_VOICE_API_KEY
import org.futo.inputmethod.latin.uix.GROQ_VOICE_MODEL
import org.futo.inputmethod.latin.uix.GROQ_VOICE_SYSTEM_PROMPT
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.LOCAL_VOICE_BACKEND
import org.futo.inputmethod.latin.uix.LOCAL_VOICE_SYSTEM_PROMPT
import org.futo.inputmethod.latin.uix.PREFER_BLUETOOTH
import org.futo.inputmethod.latin.uix.USE_GPU_OFFLOAD
import org.futo.inputmethod.latin.uix.USE_GROQ_WHISPER
import org.futo.inputmethod.latin.uix.USE_PERSONAL_DICT
import org.futo.inputmethod.latin.uix.USE_VAD_AUTOSTOP
import org.futo.inputmethod.latin.uix.VOICE_INPUT_CHANNEL_MODE
import org.futo.inputmethod.latin.uix.VOICE_INPUT_PREBUFFER_SECONDS
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.uix.actions.VoiceInputPersistentState
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.utils.ModelOutputSanitizer
import org.futo.inputmethod.latin.uix.utils.TextContext
import org.futo.voiceinput.shared.LocalTranscriptionBackend
import org.futo.voiceinput.shared.ModelDoesNotExistException
import org.futo.voiceinput.shared.RecognizerView
import org.futo.voiceinput.shared.RecognizerViewListener
import org.futo.voiceinput.shared.RecognizerViewSettings
import org.futo.voiceinput.shared.RecordingSettings
import org.futo.voiceinput.shared.types.Language
import org.futo.voiceinput.shared.types.ModelLoader
import org.futo.voiceinput.shared.types.RecordingChannelMode
import org.futo.voiceinput.shared.types.getLanguageFromWhisperString
import org.futo.voiceinput.shared.ui.MicrophoneDeviceState
import org.futo.voiceinput.shared.whisper.DecodingConfiguration
import org.futo.voiceinput.shared.whisper.MultiModelRunConfiguration
import java.util.Locale

/**
 * Windowless voice dictation session used when VOICE_SIMULTANEOUS_TYPING is ON.
 *
 * It drives the shared [RecognizerView]/AudioRecognizer on the IME lifecycle scope (no Compose),
 * owns the serial [TextEditCoordinator], and turns recognizer callbacks into [EditIntent]s only.
 * Callbacks never touch the InputConnection directly — all field writes go through [VoiceTailSink]
 * on the IME (main) thread.
 */
class HeadlessVoiceSession(
    private val manager: KeyboardManagerForAction,
    private val state: VoiceInputPersistentState
) : RecognizerViewListener {
    private val context = manager.getContext()
    private val scope = manager.getLifecycleScope()

    private val sink = VoiceTailSink(manager) { delta -> onVoiceEditApplied(delta) }
    private val coordinator = TextEditCoordinator(sink)

    /**
     * Cursor-position deltas produced by our own tail writes, awaiting their matching
     * onUpdateSelection callbacks. Used to tell voice-caused selection changes apart from
     * user-initiated ones (see [isVoiceCausedSelection]). Main-thread only (all writers run
     * through [onMain]/the IME main thread).
     */
    private val pendingVoiceEditDeltas = ArrayDeque<Int>()

    /** The input-session generation currently stamped on every intent. Mirrors the coordinator. */
    private var generation = 0L

    private val listeningState = mutableStateOf(false)
    val listening: State<Boolean> get() = listeningState

    private var recognizerView: RecognizerView? = null

    // --- VAD keep-listening state (main-thread only) ---
    /** True when stop()/cancel() ended the burst; false means finished() came from VAD auto-stop. */
    private var userRequestedStop = false
    /** Generation the current burst was started under; a mismatch means the field changed. */
    private var burstGeneration = 0L
    /** Two consecutive empty finals = silence loop → stop auto-restarting. */
    private var consecutiveEmptyFinals = 0

    /**
     * Advance to a new input-session generation. Submitted with the OLD generation so it passes the
     * coordinator's stale-drop guard, which then adopts [newGeneration] and freezes the tail.
     */
    fun onNewInputSession(newGeneration: Long) {
        coordinator.submit(EditIntent.NewInputSession(newGeneration), generation)
        generation = newGeneration
    }

    /** Build (once) and start the recognizer for a fresh dictation burst. */
    fun start(model: ModelLoader, locales: List<Locale>, newGeneration: Long) {
        onNewInputSession(newGeneration)

        val view = recognizerView ?: try {
            RecognizerView(
                context = context,
                listener = this,
                settings = loadSettings(model, locales),
                lifecycleScope = scope,
                modelManager = state.modelManager
            ).also { recognizerView = it }
        } catch (e: ModelDoesNotExistException) {
            android.util.Log.w("HeadlessVoiceSession", "no voice model installed", e)
            return
        }

        userRequestedStop = false
        consecutiveEmptyFinals = 0
        burstGeneration = generation
        startBurst(view)
    }

    /**
     * Fresh recognizer burst: same sequence for user start and VAD auto-restart. reset() bumps
     * the recognizer's sessionId (dropping any straggler callbacks from the previous burst) and
     * releases the stopped recorder, so it is safe to call right after finished() fired.
     */
    private fun startBurst(view: RecognizerView) {
        val prebufferSnapshot = manager.getVoiceInputPrebufferSnapshot()
        manager.stopVoiceInputPrebuffering()
        view.reset()
        view.setPendingPrebuffer(prebufferSnapshot)
        view.start()
    }

    /** Finalize the current utterance (mic tap / done). */
    fun stop() {
        userRequestedStop = true
        recognizerView?.finish()
    }

    /** Discard the current utterance without committing the unstable tail. */
    fun cancel() {
        userRequestedStop = true
        recognizerView?.cancel()
    }

    private fun onMain(block: () -> Unit) {
        // Main.immediate runs synchronously when already on the main thread (recognizer callbacks
        // are marshalled there), preserving serial ordering into the coordinator.
        scope.launch(Dispatchers.Main.immediate) { block() }
    }

    // --- Touch-coexistence hooks (SAFE policy) ---
    // Called from the IME layer ONLY while this session is listening (callers reach us through
    // UixManager.getListeningVoiceSession(), which returns null otherwise). They go through the
    // same onMain path as the recognizer callbacks so intent ordering stays serial.

    /** Touch started/updated the composing region → freeze the voice tail. */
    fun onKeyboardComposingStarted() {
        onMain { coordinator.submit(EditIntent.KeyboardComposingStarted, generation) }
    }

    /** Touch committed a word (commitText/finishComposingText cleared nonempty composing text). */
    fun onKeyboardWordCommitted() {
        onMain { coordinator.submit(EditIntent.KeyboardWordCommitted, generation) }
    }

    /** Raw selection change from IMEManager.onUpdateSelection (pre-debounce). */
    fun onSelectionChanged(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int) {
        onMain {
            val userInitiated = !isVoiceCausedSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd)
            coordinator.submit(
                EditIntent.SelectionChanged(newSelStart, newSelEnd, userInitiated), generation
            )
        }
    }

    private fun onVoiceEditApplied(delta: Int) {
        pendingVoiceEditDeltas.addLast(delta)
        // Some editors never deliver selection callbacks; cap so the queue cannot grow unbounded.
        while (pendingVoiceEditDeltas.size > MAX_PENDING_DELTAS) pendingVoiceEditDeltas.removeFirst()
    }

    /**
     * Voice writes its tail through the raw InputConnection, so RichInputConnection's
     * expected-selection tracking cannot vouch for those cursor moves. Instead, match the observed
     * collapsed-cursor delta against the queue of deltas our own tail writes produced (editors may
     * coalesce several batch edits into one callback, hence the prefix-sum walk). Everything that
     * does not match is treated as user-initiated: freezing the tail is the safe default — only our
     * OWN writes must not freeze it, because a freeze between two snapshots would re-commit the
     * whole hypothesis and duplicate text in the field.
     */
    private fun isVoiceCausedSelection(
        oldStart: Int, oldEnd: Int, newStart: Int, newEnd: Int
    ): Boolean {
        // No movement at all (some editors re-send the current selection): nothing to freeze.
        if (newStart == oldStart && newEnd == oldEnd) return true
        if (newStart != newEnd || oldStart != oldEnd) {
            // A non-collapsed selection is never produced by our tail writes → user-initiated.
            pendingVoiceEditDeltas.clear()
            return false
        }
        val observed = newEnd - oldEnd
        var sum = 0
        var count = 0
        for (delta in pendingVoiceEditDeltas) {
            sum += delta
            count++
            if (sum == observed) {
                repeat(count) { pendingVoiceEditDeltas.removeFirst() }
                return true
            }
        }
        pendingVoiceEditDeltas.clear()
        return false
    }

    // --- RecognizerViewListener: intents only, never InputConnection ---

    override fun recordingStarted(device: MicrophoneDeviceState) {
        onMain {
            android.util.Log.d("HeadlessVoiceSession", "listening -> true (recordingStarted)")
            listeningState.value = true
        }
    }

    override fun partialResult(result: String) {
        // RAW hypothesis: the coordinator diffs raw token space; the sink transforms on write.
        onMain { coordinator.submit(EditIntent.VoiceSnapshot(result), generation) }
    }

    override fun finished(result: String) {
        onMain {
            coordinator.submit(EditIntent.VoiceFinal(result), generation)

            if (result.isBlank()) consecutiveEmptyFinals++ else consecutiveEmptyFinals = 0

            // VAD auto-stop (not user/cancel/input-finishing): finalize but KEEP LISTENING —
            // restart a fresh burst so dictation continues across pauses. Guards: session still
            // current (generation unchanged), a view to restart, and no silence loop.
            val view = recognizerView
            if (!userRequestedStop && view != null &&
                generation == burstGeneration &&
                consecutiveEmptyFinals < MAX_CONSECUTIVE_EMPTY_FINALS
            ) {
                android.util.Log.d("HeadlessVoiceSession", "VAD finalize -> auto-restart burst")
                startBurst(view) // listeningState stays true: mic glow must not blink off
            } else {
                listeningState.value = false
            }
        }
    }

    override fun cancelled() {
        onMain {
            // Drop the unstable tail (VoiceFinal("") deletes it) and freeze/reset.
            coordinator.submit(EditIntent.VoiceFinal(""), generation)
            listeningState.value = false
        }
    }

    override fun requestPermission(onGranted: () -> Unit, onRejected: () -> Unit): Boolean {
        val hasPermission = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            onGranted()
            return true
        }

        val intent = Intent()
        intent.setClassName(context, "org.futo.inputmethod.latin.MicPermissionActivity")
        intent.setFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        context.startActivity(intent)
        onRejected()
        return true
    }

    override fun openSettings() {
        // Headless mode has no settings affordance; no-op.
    }

    private companion object {
        const val MAX_PENDING_DELTAS = 64
        const val MAX_CONSECUTIVE_EMPTY_FINALS = 2
    }

    private fun loadSettings(model: ModelLoader, locales: List<Locale>): RecognizerViewSettings {
        val disallowSymbols = context.getSetting(DISALLOW_SYMBOLS)
        val useBluetoothAudio = context.getSetting(PREFER_BLUETOOTH)
        val requestAudioFocus = context.getSetting(AUDIO_FOCUS)
        val canExpandSpace = context.getSetting(CAN_EXPAND_SPACE)
        val useVAD = context.getSetting(USE_VAD_AUTOSTOP)
        val prebufferSeconds = context.getSetting(VOICE_INPUT_PREBUFFER_SECONDS)
        val usePersonalDict = context.getSetting(USE_PERSONAL_DICT)
        val useGroq = context.getSetting(USE_GROQ_WHISPER)
        val groqKey = context.getSetting(GROQ_VOICE_API_KEY)
        val groqModel = context.getSetting(GROQ_VOICE_MODEL)
        val useGpu = context.getSetting(USE_GPU_OFFLOAD)
        val groqSystemPrompt = context.getSetting(GROQ_VOICE_SYSTEM_PROMPT)
        val localSystemPrompt = context.getSetting(LOCAL_VOICE_SYSTEM_PROMPT)
        val localBackend = LocalTranscriptionBackend.fromSetting(context.getSetting(LOCAL_VOICE_BACKEND))
        val channelMode = RecordingChannelMode.fromSetting(context.getSetting(VOICE_INPUT_CHANNEL_MODE))

        state.modelManager.useGpu = useGpu

        val languageSpecificModels = mutableMapOf<Language, ModelLoader>()
        val allowedLanguages = locales.mapNotNull { getLanguageFromWhisperString(it.language) }.toSet()
        val glossary = if (usePersonalDict) {
            state.userDictionaryObserver.getWords(locales).filter { it.shortcut.isNullOrEmpty() }.map { it.word }
        } else {
            emptyList()
        }

        return RecognizerViewSettings(
            shouldShowInlinePartialResult = (localBackend == LocalTranscriptionBackend.Moonshine),
            shouldShowVerboseFeedback = false,
            localBackend = localBackend,
            modelRunConfiguration = MultiModelRunConfiguration(
                primaryModel = model,
                languageSpecificModels = languageSpecificModels
            ),
            decodingConfiguration = DecodingConfiguration(
                glossary = glossary,
                languages = allowedLanguages,
                suppressSymbols = disallowSymbols,
                systemPrompt = localSystemPrompt
            ),
            recordingConfiguration = RecordingSettings(
                preferBluetoothMic = useBluetoothAudio,
                requestAudioFocus = requestAudioFocus,
                canExpandSpace = canExpandSpace,
                useVADAutoStop = useVAD,
                channelMode = channelMode,
                prebufferDurationMs = prebufferSeconds.coerceAtLeast(0) * 1000
            ),
            groqApiKey = if (useGroq) groqKey else "",
            groqModel = groqModel,
            groqSystemPrompt = groqSystemPrompt,
            useGpuOffload = useGpu
        )
    }
}

/**
 * [EditSink] that writes voice text into the focused field through the IME's current
 * InputConnection, on the IME (main) thread. Owns ALL text transformation: the coordinator hands
 * over RAW segments; this sink sanitizes them against the field context at the cursor
 * (capitalization, boundary single-space, trailing punctuation) and tracks the WRITTEN length of
 * the revisable tail so it can be deleted and rewritten on the next update.
 *
 * ponytail: the tail is kept at the cursor via a relative deleteSurroundingText + commitText rather
 * than absolute setSelection(tailStart,tailEnd). Functionally identical for the single-writer
 * Step-1 SAFE policy (the coordinator freezes the tail on any keypress/selection change, so voice
 * never holds an offset across a cursor move) and avoids fragile cross-app absolute-offset reads.
 * Absolute range tracking against the InputLogic RichInputConnection is the Step-2 upgrade path.
 */
private class VoiceTailSink(
    private val manager: KeyboardManagerForAction,
    /** Notified with the net cursor delta of every applied write (for selection matching). */
    private val onEditApplied: (delta: Int) -> Unit
) : EditSink {
    // WRITTEN (post-transform) length of the current revisable tail just before the cursor.
    private var writtenTailLen = 0

    // Field context cached at fresh-tail start (reading it mid-tail would see our own tail text).
    // beforeContext grows with every frozen append; the tail itself is never folded in.
    private var beforeContext = ""
    private var afterContext = ""

    private val ic get() = manager.getLatinIMEForDebug().currentInputConnection

    override fun updateVoiceText(frozenAppend: String, tail: String) {
        val connection = ic ?: return
        val oldTailLen = writtenTailLen
        connection.beginBatchEdit()
        if (oldTailLen > 0) connection.deleteSurroundingText(oldTailLen, 0)
        if (oldTailLen == 0) {
            // Starting a fresh tail: the cursor sits on real field content — refresh context.
            beforeContext = connection
                .getTextBeforeCursor(Constants.VOICE_INPUT_CONTEXT_SIZE, 0)?.toString() ?: ""
            afterContext = connection
                .getTextAfterCursor(Constants.VOICE_INPUT_CONTEXT_SIZE, 0)?.toString() ?: ""
        }

        var written = 0
        if (frozenAppend.isNotEmpty()) {
            val text = transform(frozenAppend)
            if (text.isNotEmpty()) {
                connection.commitText(text, 1)
                beforeContext += text // permanent: becomes context for everything after it
                written += text.length
            }
        }
        var newTailLen = 0
        if (tail.isNotEmpty()) {
            val text = transform(tail)
            if (text.isNotEmpty()) {
                connection.commitText(text, 1)
                newTailLen = text.length
                written += text.length
            }
        }
        writtenTailLen = newTailLen
        connection.endBatchEdit()

        val delta = written - oldTailLen
        if (delta != 0) onEditApplied(delta)
    }

    override fun freezeVoiceTail() {
        // Drop tracking; the next update starts a fresh tail at the cursor (context re-read then).
        writtenTailLen = 0
    }

    /**
     * Sanitize a RAW segment against the cached field context: sentence caps/lowercase, boundary
     * single space when the preceding char is non-whitespace (the sanitizer trims the segment
     * first, so no double space can form at the join), punctuation fixes near the after-context.
     */
    private fun transform(segment: String): String = ModelOutputSanitizer.sanitize(
        segment,
        TextContext(beforeCursor = beforeContext, afterCursor = afterContext),
        manager.isCapsLocked()
    )
}
