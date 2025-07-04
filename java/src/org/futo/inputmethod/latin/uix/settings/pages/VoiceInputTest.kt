package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import org.futo.voiceinput.shared.RecognizerView
import org.futo.voiceinput.shared.RecognizerViewListener
import org.futo.voiceinput.shared.RecognizerViewSettings
import org.futo.voiceinput.shared.RecordingSettings
import org.futo.voiceinput.shared.types.Language
import org.futo.voiceinput.shared.whisper.DecodingConfiguration
import org.futo.voiceinput.shared.whisper.ModelManager
import org.futo.voiceinput.shared.whisper.MultiModelRunConfiguration
import java.util.Locale

@Composable
fun VoiceInputTestScreen(navController: NavHostController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleScope = lifecycleOwner.lifecycleScope
    val modelManager = remember { ModelManager(context) }
    val recognizerView = remember { mutableStateOf<RecognizerView?>(null) }

    LaunchedEffect(Unit) {
        val model = org.futo.inputmethod.latin.uix.ResourceHelper.tryFindingVoiceInputModelForLocale(context, Locale.getDefault())
            ?: return@LaunchedEffect
        val settings = RecognizerViewSettings(
            shouldShowVerboseFeedback = true,
            shouldShowInlinePartialResult = true,
            modelRunConfiguration = MultiModelRunConfiguration(model, mapOf()),
            decodingConfiguration = DecodingConfiguration(glossary = emptyList(), languages = setOf(Language.English), suppressSymbols = false),
            recordingConfiguration = RecordingSettings(preferBluetoothMic = false, requestAudioFocus = true, canExpandSpace = false, useVADAutoStop = true)
        )
        recognizerView.value = RecognizerView(
            context = context,
            listener = object : RecognizerViewListener {
                override fun cancelled() { }
                override fun recordingStarted(device: org.futo.voiceinput.shared.ui.MicrophoneDeviceState) { }
                override fun finished(result: String) { }
                override fun partialResult(result: String) { }
                override fun requestPermission(onGranted: () -> Unit, onRejected: () -> Unit): Boolean { return false }
            },
            settings = settings,
            lifecycleScope = lifecycleScope,
            modelManager = modelManager
        )
        recognizerView.value?.reset()
        recognizerView.value?.start()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        recognizerView.value?.Content()
    }
}
