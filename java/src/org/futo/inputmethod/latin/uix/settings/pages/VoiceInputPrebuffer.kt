package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.VOICE_INPUT_PREBUFFER_SECONDS
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingSlider
import kotlin.math.roundToInt

@Preview
@Composable
fun VoiceInputPrebufferScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    ScrollableList {
        ScreenTitle(
            stringResource(R.string.voice_input_settings_prebuffer_duration),
            showBack = true,
            navController
        )

        SettingSlider(
            title = stringResource(R.string.voice_input_settings_prebuffer_duration),
            subtitle = stringResource(R.string.voice_input_settings_prebuffer_duration_subtitle),
            setting = VOICE_INPUT_PREBUFFER_SECONDS,
            range = 0.0f..5.0f,
            hardRange = 0.0f..5.0f,
            steps = 4,
            transform = { value ->
                value.roundToInt().coerceIn(0, 5)
            },
            indicator = { value ->
                if (value == 0) {
                    context.getString(R.string.voice_input_settings_prebuffer_duration_off)
                } else {
                    context.getString(R.string.voice_input_settings_prebuffer_duration_seconds, value)
                }
            }
        )
    }
}
