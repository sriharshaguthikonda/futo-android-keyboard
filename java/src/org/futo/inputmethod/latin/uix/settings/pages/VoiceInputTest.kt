package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import org.futo.inputmethod.latin.R
import org.futo.voiceinput.shared.RecognizerView
import org.futo.voiceinput.shared.RecognizerViewListener
import org.futo.voiceinput.shared.RecognizerViewSettings
import org.futo.voiceinput.shared.RecordingSettings
import org.futo.voiceinput.shared.whisper.DecodingConfiguration
import org.futo.voiceinput.shared.whisper.ModelManager
import org.futo.voiceinput.shared.whisper.MultiModelRunConfiguration
import org.futo.voiceinput.shared.BUILTIN_ENGLISH_MODEL
import org.futo.voiceinput.shared.types.Language

@Composable
fun VoiceInputTestScreen(navController: NavHostController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val manager = remember { ModelManager(context) }

    val settings = RecognizerViewSettings(
        shouldShowVerboseFeedback = true,
        shouldShowInlinePartialResult = false,
        modelRunConfiguration = MultiModelRunConfiguration(
            primaryModel = BUILTIN_ENGLISH_MODEL,
            languageSpecificModels = mapOf()
        ),
        decodingConfiguration = DecodingConfiguration(
            glossary = listOf(),
            languages = setOf(Language.ENGLISH),
            suppressSymbols = false
        ),
        recordingConfiguration = RecordingSettings(false, true, false, false),
        groqApiKey = null
    )

    val recognizer = remember {
        RecognizerView(
            context = context,
            listener = object : RecognizerViewListener {
                override fun cancelled() { navController.navigateUp() }
                override fun recordingStarted(device: org.futo.voiceinput.shared.ui.MicrophoneDeviceState) {}
                override fun finished(result: String) { navController.navigateUp() }
                override fun partialResult(result: String) {}
                override fun requestPermission(onGranted: () -> Unit, onRejected: () -> Unit): Boolean { return false }
            },
            settings = settings,
            lifecycleScope = lifecycleOwner.lifecycleScope,
            modelManager = manager
        )
    }

    Box(Modifier.fillMaxSize()) {
        recognizer.Content()
    }
}
