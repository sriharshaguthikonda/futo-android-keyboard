package org.futo.inputmethod.latin.uix.actions

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.view.KeyEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.AUDIO_FOCUS
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionInputTransaction
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.CAN_EXPAND_SPACE
import org.futo.inputmethod.latin.uix.CloseResult
import org.futo.inputmethod.latin.uix.DISALLOW_SYMBOLS
import org.futo.inputmethod.latin.uix.ENABLE_SOUND
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.PREFER_BLUETOOTH
import org.futo.inputmethod.latin.uix.PersistentActionState
import org.futo.inputmethod.latin.uix.ResourceHelper
import org.futo.inputmethod.latin.uix.USE_PERSONAL_DICT
import org.futo.inputmethod.latin.uix.USE_VAD_AUTOSTOP
import org.futo.inputmethod.latin.uix.VERBOSE_PROGRESS
import org.futo.inputmethod.latin.uix.USE_GROQ_WHISPER
import org.futo.inputmethod.latin.uix.GROQ_VOICE_API_KEY
import org.futo.inputmethod.latin.uix.GROQ_VOICE_MODEL
import org.futo.inputmethod.latin.uix.GROQ_VOICE_SYSTEM_PROMPT
import org.futo.inputmethod.latin.uix.LOCAL_VOICE_SYSTEM_PROMPT
import org.futo.inputmethod.latin.uix.LOCAL_VOICE_BACKEND
import org.futo.inputmethod.latin.uix.USE_GPU_OFFLOAD
import org.futo.inputmethod.latin.uix.VOICE_INPUT_BOTTOM_BAR_MODE
import org.futo.inputmethod.latin.uix.VOICE_INPUT_CHANNEL_MODE
import org.futo.inputmethod.latin.uix.VOICE_INPUT_PREBUFFER_SECONDS
import org.futo.inputmethod.latin.uix.LocalKeyboardScheme
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.setSetting
import org.futo.inputmethod.latin.uix.settings.SettingsActivity
import org.futo.inputmethod.latin.uix.utils.ModelOutputSanitizer
import org.futo.inputmethod.latin.xlm.UserDictionaryObserver
import org.futo.inputmethod.updates.openURI
import org.futo.voiceinput.shared.ModelDoesNotExistException
import org.futo.voiceinput.shared.RecognizerView
import org.futo.voiceinput.shared.RecognizerViewListener
import org.futo.voiceinput.shared.RecognizerViewSettings
import org.futo.voiceinput.shared.RecordingSettings
import org.futo.voiceinput.shared.SoundPlayer
import org.futo.voiceinput.shared.LocalTranscriptionBackend
import org.futo.voiceinput.shared.types.Language
import org.futo.voiceinput.shared.types.ModelLoader
import org.futo.voiceinput.shared.types.RecordingChannelMode
import org.futo.voiceinput.shared.types.getLanguageFromWhisperString
import org.futo.voiceinput.shared.ui.MicrophoneDeviceState
import org.futo.voiceinput.shared.whisper.DecodingConfiguration
import org.futo.voiceinput.shared.whisper.ModelManager
import org.futo.voiceinput.shared.whisper.MultiModelRunConfiguration
import java.text.BreakIterator
import java.util.Locale

val SystemVoiceInputAction = Action(
    icon = R.drawable.mic_fill,
    name = R.string.action_system_voice_input_title,
    simplePressImpl = { it, _ ->
        it.triggerSystemVoiceInput()
    },
    persistentState = null,
    windowImpl = null,
    shownInEditor = false
)


@Composable
fun NoModelInstalled(locale: Locale) {
    val context = LocalContext.current
    Box(modifier = Modifier
        .fillMaxSize()
        .clickable(
            enabled = true,
            onClickLabel = null,
            onClick = {
                context.openURI("https://keyboard.futo.org/voice-input-models", true)
            },
            role = null,
            indication = null,
            interactionSource = remember { MutableInteractionSource() })) {
        Text(
            stringResource(
                R.string.action_voice_input_no_model_for_language_x_installed,
                locale.getDisplayName(locale)
            ), modifier = Modifier
                .align(Alignment.Center)
                .padding(8.dp), textAlign = TextAlign.Center)
    }
}

class VoiceInputPersistentState(val manager: KeyboardManagerForAction) : PersistentActionState {
    val modelManager = ModelManager(manager.getContext())
    val soundPlayer = SoundPlayer(manager.getContext())
    val userDictionaryObserver = UserDictionaryObserver(manager.getContext())

    override suspend fun cleanUp() {
        modelManager.cleanUp()
    }

    override fun close() {
        runBlocking { modelManager.cleanUp() }
        userDictionaryObserver.unregister()
    }
}

private class VoiceInputActionWindow(
    val manager: KeyboardManagerForAction, val state: VoiceInputPersistentState,
    val model: ModelLoader, val locales: List<Locale>
) : ActionWindow(), RecognizerViewListener {
    val context = manager.getContext()

    private var shouldPlaySounds: Boolean = false
    private fun loadSettings(): RecognizerViewSettings {
        val enableSound = context.getSetting(ENABLE_SOUND)
        val verboseFeedback = false//context.getSetting(VERBOSE_PROGRESS)
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

        val primaryModel = model
        val languageSpecificModels = mutableMapOf<Language, ModelLoader>()
        val allowedLanguages = locales.mapNotNull { getLanguageFromWhisperString(it.language) }.toSet()
        val glossary = if(usePersonalDict) {
            state.userDictionaryObserver.getWords(locales).filter { it.shortcut.isNullOrEmpty() }.map { it.word }
        } else {
            emptyList()
        }

        shouldPlaySounds = enableSound

        return RecognizerViewSettings(
            shouldShowInlinePartialResult = (localBackend == LocalTranscriptionBackend.Moonshine),
            shouldShowVerboseFeedback = verboseFeedback,
            localBackend = localBackend,
            modelRunConfiguration = MultiModelRunConfiguration(
                primaryModel = primaryModel,
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
            groqApiKey = if(useGroq) groqKey else "",
            groqModel = groqModel,
            groqSystemPrompt = groqSystemPrompt,
            useGpuOffload = useGpu
        )
    }

    private var recognizerView: MutableState<RecognizerView?> = mutableStateOf(null)
    private var modelException: MutableState<ModelDoesNotExistException?> = mutableStateOf(null)

    private val initJob = manager.getLifecycleScope().launch {
        yield()
        val settings = loadSettings()

        yield()
        val recognizerView = try {
            RecognizerView(
                context = manager.getContext(),
                listener = this@VoiceInputActionWindow,
                settings = settings,
                lifecycleScope = manager.getLifecycleScope(),
                modelManager = state.modelManager
            )
        } catch(e: ModelDoesNotExistException) {
            modelException.value = e
            return@launch
        }

        this@VoiceInputActionWindow.recognizerView.value = recognizerView

        val prebufferSnapshot = manager.getVoiceInputPrebufferSnapshot()
        manager.stopVoiceInputPrebuffering()
        recognizerView.reset()
        recognizerView.setPendingPrebuffer(prebufferSnapshot)
        recognizerView.start()
    }

    private var inputTransaction = manager.createInputTransaction()

    @Composable
    private fun ModelDownloader(modelException: ModelDoesNotExistException) {
        NoModelInstalled(locales.firstOrNull() ?: Locale.ROOT)
    }

    @Composable
    override fun windowName(): String {
        return stringResource(R.string.action_voice_input_title)
    }

    @Composable
    override fun WindowContents(keyboardShown: Boolean) {
        Box(modifier = Modifier
            .fillMaxSize()
            .clickable(
                enabled = true,
                onClickLabel = null,
                onClick = { recognizerView.value?.finish() },
                role = null,
                indication = null,
                interactionSource = remember { MutableInteractionSource() })
            .semantics(mergeDescendants = true) {
                traversalIndex = -1.0f
            }) {
            Box(modifier = Modifier.align(Alignment.Center)) {
                when {
                    modelException.value != null -> ModelDownloader(modelException.value!!)
                    recognizerView.value != null -> recognizerView.value!!.Content()
                }
            }
        }
    }

    override fun close(): CloseResult {
        inputTransaction.cancel()
        runBlocking { initJob.cancelAndJoin() }
        recognizerView.value?.cancel()
        state.modelManager.cancelAll()
        manager.startVoiceInputPrebuffering()
        return CloseResult.Default
    }

    private var wasFinished = false
    private var cancelPlayed = false
    override fun cancelled() {
        if (!wasFinished) {
            if (shouldPlaySounds && !cancelPlayed) {
                state.soundPlayer.playCancelSound()
                cancelPlayed = true
            }
            inputTransaction.cancel()
        }
    }

    override fun recordingStarted(device: MicrophoneDeviceState) {
        if (shouldPlaySounds) {
            state.soundPlayer.playStartSound()
        }

        // Only set the setting if bluetooth is available, else it would reset the setting
        // every time it's used without a bluetooth device connected.
        if(device.bluetoothAvailable) {
            manager.getLifecycleScope().launch {
                context.setSetting(PREFER_BLUETOOTH, device.bluetoothActive)
            }
        }
    }

    override fun finished(result: String) {
        wasFinished = true

        manager.getLifecycleScope().launch(Dispatchers.Main) {
            val sanitized = ModelOutputSanitizer.sanitize(result, inputTransaction.textContext, manager.isCapsLocked())
            inputTransaction.commit(sanitized)
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText(context.getString(R.string.action_voice_input_title), sanitized)
            clipboardManager.setPrimaryClip(clipData)
            manager.announce(result)
            manager.closeActionWindow()
        }
    }

    override fun partialResult(result: String) {
        android.util.Log.d("VoiceInputAction", "partialResult(action window) result=[$result]")
        manager.getLifecycleScope().launch(Dispatchers.Main) {
            val sanitized = ModelOutputSanitizer.sanitize(result, inputTransaction.textContext, manager.isCapsLocked())
            android.util.Log.d("VoiceInputAction", "partialResult(action window) sanitized=[$sanitized]")
            inputTransaction.updatePartial(sanitized)
        }
    }

    override fun requestPermission(onGranted: () -> Unit, onRejected: () -> Unit): Boolean {
        // For the full window we delegate to the global MicPermissionActivity flow
        // The AudioRecognizer will call openPermissionSettings() when permission is missing,
        // so we just signal that we did not show an inline dialog here.
        return false
    }

    override fun openSettings() {
        val intent = Intent()
        intent.setClass(context, SettingsActivity::class.java)
        intent.setFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        intent.putExtra("navDest", "languages")
        context.startActivity(intent)
    }
}

private class VoiceInputNoModelWindow(val locale: Locale) : ActionWindow() {
    @Composable
    override fun windowName(): String {
        return stringResource(R.string.action_voice_input_title)
    }

    @Composable
    override fun WindowContents(keyboardShown: Boolean) {
        NoModelInstalled(locale)
    }
}

/**
 * Bottom bar mode for voice input - shows a minimal floating pill at the bottom
 * similar to Gboard's voice typing UI, giving more screen space while dictating.
 */
private class VoiceInputBottomBarWindow(
    val manager: KeyboardManagerForAction, val state: VoiceInputPersistentState,
    val model: ModelLoader, val locales: List<Locale>
) : ActionWindow(), RecognizerViewListener {
    val context = manager.getContext()

    // Hide the keyboard and show only this minimal bar at the bottom
    override val onlyShowAboveKeyboard: Boolean = false
    override val showCloseButton: Boolean = false
    override val fixedWindowHeight: Dp = 88.dp
    override val showHeaderBar: Boolean = false

    private var shouldPlaySounds: Boolean = false
    private val isListening = mutableStateOf(false)
    private val statusText = mutableStateOf("Tap to start")

    private fun loadSettings(): RecognizerViewSettings {
        val enableSound = context.getSetting(ENABLE_SOUND)
        val verboseFeedback = context.getSetting(VERBOSE_PROGRESS)
        val disallowSymbols = context.getSetting(DISALLOW_SYMBOLS)
        val useBluetoothAudio = context.getSetting(PREFER_BLUETOOTH)
        val requestAudioFocus = context.getSetting(AUDIO_FOCUS)
        val canExpandSpace = context.getSetting(CAN_EXPAND_SPACE)
        val useVAD = context.getSetting(USE_VAD_AUTOSTOP)
        val useGroq = context.getSetting(USE_GROQ_WHISPER)
        val groqKey = context.getSetting(GROQ_VOICE_API_KEY)
        val groqModel = context.getSetting(GROQ_VOICE_MODEL)
        val useGpu = context.getSetting(USE_GPU_OFFLOAD)
        val groqSystemPrompt = context.getSetting(GROQ_VOICE_SYSTEM_PROMPT)
        val localSystemPrompt = context.getSetting(LOCAL_VOICE_SYSTEM_PROMPT)
        val localBackend = LocalTranscriptionBackend.fromSetting(context.getSetting(LOCAL_VOICE_BACKEND))
        val channelMode = RecordingChannelMode.fromSetting(context.getSetting(VOICE_INPUT_CHANNEL_MODE))
        val prebufferSeconds = context.getSetting(VOICE_INPUT_PREBUFFER_SECONDS)

        state.modelManager.useGpu = useGpu

        val primaryModel = model
        val languageSpecificModels = mutableMapOf<Language, ModelLoader>()
        val allowedLanguages = locales.map { getLanguageFromWhisperString(it.language) }
            .filterNotNull().toSet()

        shouldPlaySounds = enableSound

        return RecognizerViewSettings(
            shouldShowInlinePartialResult = (localBackend == LocalTranscriptionBackend.Moonshine),
            shouldShowVerboseFeedback = verboseFeedback,
            localBackend = localBackend,
            modelRunConfiguration = MultiModelRunConfiguration(
                primaryModel = primaryModel,
                languageSpecificModels = languageSpecificModels
            ),
            decodingConfiguration = DecodingConfiguration(
                glossary = state.userDictionaryObserver.getWords(locales).map { it.word },
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
            groqApiKey = if(useGroq) groqKey else "",
            groqModel = groqModel,
            groqSystemPrompt = groqSystemPrompt,
            useGpuOffload = useGpu
        )
    }

    private var recognizerView: MutableState<RecognizerView?> = mutableStateOf(null)
    private var modelException: MutableState<ModelDoesNotExistException?> = mutableStateOf(null)

    private val initJob = manager.getLifecycleScope().launch {
        yield()
        val settings = loadSettings()

        yield()
        val recognizerView = try {
            RecognizerView(
                context = manager.getContext(),
                listener = this@VoiceInputBottomBarWindow,
                settings = settings,
                lifecycleScope = manager.getLifecycleScope(),
                modelManager = state.modelManager
            )
        } catch(e: ModelDoesNotExistException) {
            modelException.value = e
            return@launch
        }

        this@VoiceInputBottomBarWindow.recognizerView.value = recognizerView
        val prebufferSnapshot = manager.getVoiceInputPrebufferSnapshot()
        manager.stopVoiceInputPrebuffering()
        recognizerView.reset()
        recognizerView.setPendingPrebuffer(prebufferSnapshot)
        recognizerView.startPrebuffering()
    }

    private var inputTransaction: ActionInputTransaction? = null

    private fun beginNewSession() {
        wasFinished = false
        cancelPlayed = false
        inputTransaction = manager.createInputTransaction()
    }

    private fun deleteWordBeforeCursor() {
        val ic = manager.getLatinIMEForDebug().currentInputConnection
        if (ic == null) {
            manager.sendKeyEvent(KeyEvent.KEYCODE_DEL, 0)
            return
        }

        val selectedText = ic.getSelectedText(0)
        if (!selectedText.isNullOrEmpty()) {
            manager.sendKeyEvent(KeyEvent.KEYCODE_DEL, 0)
            return
        }

        val textBeforeCursor = ic.getTextBeforeCursor(48, 0) ?: run {
            manager.sendKeyEvent(KeyEvent.KEYCODE_DEL, 0)
            return
        }

        if (textBeforeCursor.isEmpty()) {
            manager.sendKeyEvent(KeyEvent.KEYCODE_DEL, 0)
            return
        }

        val breakIterator = BreakIterator.getWordInstance()
        breakIterator.setText(textBeforeCursor.toString())

        val end = breakIterator.last()
        var start = breakIterator.previous()

        if (start == BreakIterator.DONE) {
            manager.sendKeyEvent(KeyEvent.KEYCODE_DEL, 0)
            return
        }

        if (textBeforeCursor.subSequence(start, end).toString() == " ") {
            val prevStart = breakIterator.previous()
            if (prevStart != BreakIterator.DONE) {
                start = prevStart
            }
        }

        val lengthToDelete = end - start
        if (lengthToDelete <= 0) {
            manager.sendKeyEvent(KeyEvent.KEYCODE_DEL, 0)
            return
        }

        ic.deleteSurroundingText(lengthToDelete, 0)
    }

    @Composable
    override fun windowName(): String {
        return stringResource(R.string.action_voice_input_title)
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    override fun WindowContents(keyboardShown: Boolean) {
        val isListeningState by isListening
        
        // Infinite pulsing animation when listening
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(600),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
        
        // Animated sound wave bars
        val wave1 by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(300),
                repeatMode = RepeatMode.Reverse
            ),
            label = "wave1"
        )
        val wave2 by infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(400),
                repeatMode = RepeatMode.Reverse
            ),
            label = "wave2"
        )
        val wave3 by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(350),
                repeatMode = RepeatMode.Reverse
            ),
            label = "wave3"
        )

        val density = LocalDensity.current
        val dragAccumPx = remember { mutableFloatStateOf(0f) }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(28.dp),
            color = LocalKeyboardScheme.current.keyboardContainer,
            contentColor = LocalKeyboardScheme.current.onKeyboardContainer,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Back/close button
                IconButton(
                    onClick = { manager.closeActionWindow() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.arrow_left_26),
                        contentDescription = "Back",
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Undo button (moved to left side)
                IconButton(
                    onClick = {
                        manager.sendKeyEvent(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON)
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.undo),
                        contentDescription = "Undo",
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Show keyboard button
                IconButton(
                    onClick = { manager.closeActionWindow() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.keyboard_regular),
                        contentDescription = "Show keyboard",
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Sound wave indicator + Status text
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .pointerInput(Unit) {
                            val threshold = with(density) { 10.dp.toPx() }
                            detectDragGestures(
                                onDragEnd = { dragAccumPx.floatValue = 0f },
                                onDragCancel = { dragAccumPx.floatValue = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragAccumPx.floatValue += dragAmount.x
                                    while (dragAccumPx.floatValue > threshold) {
                                        manager.sendKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, 0)
                                        dragAccumPx.floatValue -= threshold
                                    }
                                    while (dragAccumPx.floatValue < -threshold) {
                                        manager.sendKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, 0)
                                        dragAccumPx.floatValue += threshold
                                    }
                                }
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (isListeningState) {
                        Row(
                            modifier = Modifier.padding(end = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SoundWaveBar(height = 12.dp * wave1)
                            SoundWaveBar(height = 12.dp * wave2)
                            SoundWaveBar(height = 12.dp * wave3)
                        }
                    }

                    Text(
                        text = statusText.value,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )

                    if (isListeningState) {
                        Row(
                            modifier = Modifier.padding(start = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SoundWaveBar(height = 12.dp * wave3)
                            SoundWaveBar(height = 12.dp * wave1)
                            SoundWaveBar(height = 12.dp * wave2)
                        }
                    }
                }

                // Backspace with continuous word deletion on hold
                val backspaceScope = rememberCoroutineScope()
                var deleteJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val startTime = System.currentTimeMillis()
                                    
                                    // Start a job that will begin word deletion after long press threshold
                                    deleteJob = backspaceScope.launch {
                                        delay(400) // Long press threshold
                                        while (true) {
                                            deleteWordBeforeCursor()
                                            delay(150) // Repeat interval
                                        }
                                    }
                                    
                                    // Wait for release
                                    do {
                                        val event = awaitPointerEvent()
                                    } while (event.changes.any { it.pressed })
                                    
                                    // Cancel the delete job
                                    deleteJob?.cancel()
                                    deleteJob = null
                                    
                                    // If it was a short tap (not long press), delete single char
                                    val pressDuration = System.currentTimeMillis() - startTime
                                    if (pressDuration < 400) {
                                        manager.sendKeyEvent(KeyEvent.KEYCODE_DEL, 0)
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.sym_keyboard_delete_lxx_dark),
                        contentDescription = "Backspace",
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Redo
                IconButton(
                    onClick = {
                        manager.sendKeyEvent(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.redo),
                        contentDescription = "Redo",
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Mic button with pulsing animation when listening
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .scale(if (isListeningState) pulseScale else 1.0f)
                        .background(
                            color = if (isListeningState) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                            shape = CircleShape
                        )
                        .clickable {
                            if (isListeningState) {
                                // Treat mic as stop button when already listening
                                isListening.value = false
                                statusText.value = "Stopping…"
                                recognizerView.value?.finish()
                            } else {
                                recognizerView.value?.start()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.mic_fill),
                        contentDescription = if (isListeningState) "Stop" else "Start",
                        tint = if (isListeningState) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
    
    @Composable
    private fun SoundWaveBar(height: Dp) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(height)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(2.dp)
                )
        )
    }

    override fun close(): CloseResult {
        inputTransaction?.cancel()
        inputTransaction = null
        runBlocking { initJob.cancelAndJoin() }
        recognizerView.value?.cancel()
        state.modelManager.cancelAll()
        manager.startVoiceInputPrebuffering()
        return CloseResult.Default
    }

    private var wasFinished = false
    private var cancelPlayed = false
    override fun cancelled() {
        if (!wasFinished) {
            if (shouldPlaySounds && !cancelPlayed) {
                state.soundPlayer.playCancelSound()
                cancelPlayed = true
            }
            inputTransaction?.cancel()
            inputTransaction = null
        }
        isListening.value = false
        statusText.value = "Cancelled"
        recognizerView.value?.startPrebuffering()
    }

    override fun recordingStarted(device: MicrophoneDeviceState) {
        if (shouldPlaySounds) {
            state.soundPlayer.playStartSound()
        }

        // Reset state for new recognition
        if (!isListening.value) {
            beginNewSession()
        }

        isListening.value = true
        statusText.value = "Listening…"

        if(device.bluetoothAvailable) {
            manager.getLifecycleScope().launch {
                context.setSetting(PREFER_BLUETOOTH, device.bluetoothActive)
            }
        }
    }

    override fun finished(result: String) {
        wasFinished = true
        isListening.value = false
        statusText.value = "Done"

        val transaction = inputTransaction ?: run {
            val t = manager.createInputTransaction()
            inputTransaction = t
            t
        }
        val sanitized = ModelOutputSanitizer.sanitize(result, transaction.textContext, manager.isCapsLocked())
        transaction.commit(sanitized)
        inputTransaction = null
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText(context.getString(R.string.action_voice_input_title), sanitized)
        clipboardManager.setPrimaryClip(clipData)
        manager.announce(result)
        recognizerView.value?.startPrebuffering()
    }

    override fun partialResult(result: String) {
        android.util.Log.d("VoiceInputAction", "partialResult(bottom bar) result=[$result]")
        val transaction = inputTransaction ?: run {
            android.util.Log.w("VoiceInputAction", "partialResult(bottom bar) dropped — no active input transaction")
            return
        }
        val sanitized = ModelOutputSanitizer.sanitize(result, transaction.textContext, manager.isCapsLocked())
        android.util.Log.d("VoiceInputAction", "partialResult(bottom bar) sanitized=[$sanitized]")
        transaction.updatePartial(sanitized)
        // Show abbreviated partial result in status
        statusText.value = if (result.length > 30) "…${result.takeLast(30)}" else result.ifEmpty { "Listening…" }
    }

    override fun requestPermission(onGranted: () -> Unit, onRejected: () -> Unit): Boolean {
        val ctx = manager.getContext()
        val hasPermission = ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            onGranted()
            return true
        }

        val intent = Intent()
        intent.setClassName(ctx, "org.futo.inputmethod.latin.MicPermissionActivity")
        intent.setFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        ctx.startActivity(intent)

        onRejected()
        return true
    }

    override fun openSettings() {
        val intent = Intent()
        intent.setClass(context, SettingsActivity::class.java)
        intent.setFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        intent.putExtra("navDest", "languages")
        context.startActivity(intent)
    }
}

val VoiceInputAction = Action(icon = R.drawable.mic_fill,
    name = R.string.action_voice_input_title,
    simplePressImpl = null,
    keepScreenAwake = true,
    persistentState = { VoiceInputPersistentState(it) },
    windowImpl = { manager, persistentState ->
        val locales = manager.getActiveLocales()
        val useBottomBarMode = manager.getContext().getSetting(VOICE_INPUT_BOTTOM_BAR_MODE)

        val model = ResourceHelper.tryFindingVoiceInputModelForLocale(manager.getContext(), locales.firstOrNull() ?: Locale.ROOT)

        if(model == null) {
            VoiceInputNoModelWindow(locales.firstOrNull() ?: Locale.ROOT)
        } else if(useBottomBarMode) {
            // Use the compact floating bar mode
            VoiceInputBottomBarWindow(
                manager = manager, state = persistentState as VoiceInputPersistentState,
                locales = locales, model = model
            )
        } else {
            // Use the standard full-screen voice input window
            VoiceInputActionWindow(
                manager = manager, state = persistentState as VoiceInputPersistentState,
                locales = locales, model = model
            )
        }
    }
)
