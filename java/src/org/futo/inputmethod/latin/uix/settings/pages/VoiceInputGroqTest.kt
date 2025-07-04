package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.GROQ_API_KEY
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.voiceinput.shared.whisper.GroqWhisperClient
import org.futo.voiceinput.shared.whisper.toWav

@Composable
fun GroqTestDialog(navController: NavHostController) {
    val context = LocalContext.current
    val scope = LocalLifecycleOwner.current
    val result = remember { mutableStateOf("") }
    val running = remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if(!running.value) navController.navigateUp() },
        title = { Text(stringResource(R.string.voice_input_settings_test_groq)) },
        text = { Text(if(result.value.isBlank()) stringResource(R.string.voice_input_settings_test_groq_subtitle) else result.value) },
        confirmButton = {
            TextButton(enabled = !running.value, onClick = {
                running.value = true
                scope.lifecycleScope.launch {
                    val key = context.getSetting(GROQ_API_KEY)
                    val wav = FloatArray(16000) { 0f }.toWav()
                    val r = GroqWhisperClient.transcribeWav(wav, key)
                    result.value = r ?: "Failed"
                    running.value = false
                }
            }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = { navController.navigateUp() }, enabled = !running.value) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
