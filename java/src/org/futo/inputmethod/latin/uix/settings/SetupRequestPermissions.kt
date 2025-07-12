package org.futo.inputmethod.latin.uix.settings

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.services.QuickSwitchService

@Composable
@Preview
fun SetupRequestPermissions(onFinished: () -> Unit = { }) {
    SetupContainer {
        Column {
            Step(fraction = 4.0f / 5.0f, text = stringResource(R.string.setup_step_3)) // Assuming step 3 is permissions

            Text(
                stringResource(R.string.setup_permissions_text), // Add this string resource
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            val context = LocalContext.current
            Button(
                onClick = {
                    val component = ComponentName(context, QuickSwitchService::class.java)
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Intent(Settings.ACTION_ACCESSIBILITY_DETAILS_SETTINGS).apply {
                            putExtra(Intent.EXTRA_COMPONENT_NAME, component.flattenToString())
                        }
                    } else {
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            putExtra(Intent.EXTRA_COMPONENT_NAME, component.flattenToString())
                        }
                    }
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    onFinished()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(stringResource(R.string.setup_grant_permissions_button))
            }
        }
    }
}
