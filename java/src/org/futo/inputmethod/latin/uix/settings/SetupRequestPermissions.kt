package org.futo.inputmethod.latin.uix.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.R

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

            Button(
                onClick = onFinished,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(stringResource(R.string.setup_grant_permissions_button)) // Add this string resource
            }
        }
    }
}
