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
import org.futo.inputmethod.latin.uix.actions.VoiceInputPersistentState
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.utils.ModelOutputSanitizer
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

    private val sink = VoiceTailSink(manager)
    private val coordinator = TextEditCoordinator(sink)

    /** The input-session generation currently stamped on every intent. Mirrors the coordinator. */
    private var generation = 0L

    private val listeningState = mutableStateOf(false)
    val listening: State<Boolean> get() = listeningState

    private var recognizerView: RecognizerView? = null

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

        val prebufferSnapshot = manager.getVoiceInputPrebufferSnapshot()
        manager.stopVoiceInputPrebuffering()
        view.reset()
        view.setPendingPrebuffer(prebufferSnapshot)
        view.start()
    }

    /** Finalize the current utterance (mic tap / done). */
    fun stop() {
        recognizerView?.finish()
    }

    /** Discard the current utterance without committing the unstable tail. */
    fun cancel() {
        recognizerView?.cancel()
    }

    private fun onMain(block: () -> Unit) {
        // Main.immediate runs synchronously when already on the main thread (recognizer callbacks
        // are marshalled there), preserving serial ordering into the coordinator.
        scope.launch(Dispatchers.Main.immediate) { block() }
    }

    // --- RecognizerViewListener: intents only, never InputConnection ---

    override fun recordingStarted(device: MicrophoneDeviceState) {
        onMain { listeningState.value = true }
    }

    override fun partialResult(result: String) {
        onMain {
            val sanitized = ModelOutputSanitizer.sanitize(result, null, manager.isCapsLocked())
            coordinator.submit(EditIntent.VoiceSnapshot(sanitized), generation)
        }
    }

    override fun finished(result: String) {
        onMain {
            val sanitized = ModelOutputSanitizer.sanitize(result, null, manager.isCapsLocked())
            coordinator.submit(EditIntent.VoiceFinal(sanitized), generation)
            listeningState.value = false
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
 * [EditSink] that writes the replaceable voice tail into the focused field through the IME's
 * current InputConnection, on the IME (main) thread.
 *
 * ponytail: the tail is kept at the cursor via a relative deleteSurroundingText + commitText rather
 * than absolute setSelection(tailStart,tailEnd). Functionally identical for the single-writer
 * Step-1 SAFE policy (the coordinator freezes the tail on any keypress/selection change, so voice
 * never holds an offset across a cursor move) and avoids fragile cross-app absolute-offset reads.
 * Absolute range tracking against the InputLogic RichInputConnection is the Step-2 upgrade path.
 */
private class VoiceTailSink(private val manager: KeyboardManagerForAction) : EditSink {
    // Length of the current replaceable tail that sits immediately before the cursor.
    private var tailLen = 0

    private val ic get() = manager.getLatinIMEForDebug().currentInputConnection

    override fun replaceVoiceTail(text: String) {
        val connection = ic ?: return
        connection.beginBatchEdit()
        if (tailLen > 0) connection.deleteSurroundingText(tailLen, 0)
        if (text.isNotEmpty()) connection.commitText(text, 1)
        tailLen = text.length
        connection.endBatchEdit()
    }

    override fun freezeVoiceTailPrefix(length: Int) {
        // The newly-stable prefix stays committed; it just leaves the deletable tail region.
        tailLen = (tailLen - length).coerceAtLeast(0)
    }

    override fun freezeVoiceTail() {
        // Drop tracking; the next replace starts a fresh tail at the cursor.
        tailLen = 0
    }
}
