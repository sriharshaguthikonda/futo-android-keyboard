package org.futo.inputmethod.latin.uix.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.CircularProgressIndicator
import org.futo.inputmethod.latin.uix.IS_SETUP_COMPLETE
import org.futo.inputmethod.latin.uix.dataStore
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.KeyboardLayoutPreview
import org.futo.inputmethod.latin.uix.USE_SYSTEM_VOICE_INPUT
import org.futo.inputmethod.latin.uix.theme.Typography
import org.futo.inputmethod.v2keyboard.LayoutManager

@Composable
fun SetupContainer(inner: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.75f)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .align(Alignment.Center),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterVertically)
                    .padding(32.dp)
            ) {
                Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                    inner()
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.25f))
    }
}


@Composable
fun Step(fraction: Float, text: String) {
    Column(modifier = Modifier
        .padding(16.dp)
        .clearAndSetSemantics {
            this.text = AnnotatedString(text)
        }
    ) {
        Text(text, style = Typography.SmallMl)
        LinearProgressIndicator(progress = fraction, modifier = Modifier.fillMaxWidth())
    }
}

// TODO: May wish to have a skip option
@Composable
@Preview
fun SetupEnableIME(onFinished: () -> Unit = { }) {
    val context = LocalContext.current

    val launchImeOptions = {
        // TODO: look into direct boot to get rid of direct boot warning?
        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)

        intent.flags = (Intent.FLAG_ACTIVITY_NEW_TASK
                or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                or Intent.FLAG_ACTIVITY_NO_HISTORY
                or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)

        context.startActivity(intent)
    }

    SetupContainer {
        Column {
            Step(fraction = 1.0f/5.0f, text = stringResource(R.string.setup_step_1))

            Text(
                stringResource(R.string.setup_welcome_text),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    launchImeOptions()
                    onFinished()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(stringResource(R.string.setup_open_input_settings))
            }
        }
    }
}

@Composable
fun SetupNavigation(
    imeEnabled: Boolean,
    imeSelected: Boolean,
    doublePackage: Boolean,
    main: @Composable () -> Unit
) {
    val context = LocalContext.current
    val navController = (LocalContext.current as SettingsActivity).navController
    var isSetupComplete by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        context.dataStore.data.collect { preferences ->
            isSetupComplete = preferences[IS_SETUP_COMPLETE.key] ?: IS_SETUP_COMPLETE.default
        }
    }

    var currentStep by remember { mutableStateOf(0) }

    LaunchedEffect(isSetupComplete, imeEnabled, imeSelected) {
        isSetupComplete?.let {
            currentStep = if (it) {
                6 // Go directly to main settings
            } else if (!imeEnabled) {
                1
            } else if (!imeSelected) {
                2
            } else {
                3
            }
        }
    }


    if (isSetupComplete == null) {
        // Show a loading indicator or a blank screen while checking the flag
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        when (currentStep) {
            1 -> SetupEnableIME { currentStep = 2 }
            2 -> SetupChangeDefaultIME(doublePackage) { currentStep = 3 }
            3 -> SetupRestoreBackup { currentStep = 4 }
            4 -> SetupRequestPermissions { currentStep = 5 }
            5 -> SetupFinish { currentStep = 6 }
            else -> main()
        }
    }
}


@Composable
@Preview
fun SetupChangeDefaultIME(doublePackage: Boolean = true, onFinished: () -> Unit = { }) {
    val context = LocalContext.current

    val launchImeOptions = {
        val inputMethodManager =
            context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        inputMethodManager.showInputMethodPicker()

        (context as SettingsActivity).updateSystemState()
        onFinished()
    }

    SetupContainer {
        Column {
            if(doublePackage) {
                Tip(stringResource(R.string.setup_warning_multiple_versions))
            }

            Step(fraction = 2.0f/5.0f, text = stringResource(R.string.setup_step_2))

            Text(
                stringResource(R.string.setup_active_input_method),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = launchImeOptions,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(stringResource(R.string.setup_switch_input_methods))
            }
        }
    }
}