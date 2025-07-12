package org.futo.inputmethod.latin.uix.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.IS_SETUP_COMPLETE
import org.futo.inputmethod.latin.uix.dataStore

@Composable
@Preview
fun SetupFinish(onFinished: () -> Unit = { }) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    SetupContainer {
        Column {
            Step(fraction = 5.0f / 5.0f, text = stringResource(R.string.setup_step_4)) // Assuming step 4 is finish

            Text(
                stringResource(R.string.setup_congrats),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    scope.launch {
                        try {
                            // First update the setup completion flag
                            context.dataStore.updateData { preferences ->
                                preferences.toMutablePreferences().apply {
                                    this[IS_SETUP_COMPLETE] = true
                                }
                            }
                            // Only call onFinished after the flag is successfully updated
                            onFinished()
                        } catch (e: Exception) {
                            // If there's an error, still proceed to avoid blocking the user
                            onFinished()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(stringResource(R.string.setup_button_finish))
            }
        }
    }
}
