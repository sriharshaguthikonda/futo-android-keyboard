package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.voiceinput.shared.whisper.GroqRemote

@Composable
fun VoiceInputTestScreen(navController: NavHostController) {
    val scope = rememberCoroutineScope()
    val result = remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ScreenTitle(stringResource(R.string.voice_input_settings_test), showBack = true, navController)
        Button(onClick = {
            scope.launch {
                result.value = stringResource(R.string.processing)
                val r = GroqRemote.transcribe(FloatArray(16000))
                result.value = r ?: stringResource(R.string.voice_input_settings_test_subtitle)
            }
        }) {
            Text(stringResource(R.string.voice_input_settings_test))
        }
        result.value?.let {
            Text(it, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

val VoiceInputTestMenu = UserSettingsMenu(
    title = R.string.voice_input_settings_test,
    navPath = "voiceInputTest", registerNavPath = false,
    settings = emptyList()
)

