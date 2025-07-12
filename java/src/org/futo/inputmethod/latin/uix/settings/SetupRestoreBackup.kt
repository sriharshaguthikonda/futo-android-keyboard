package org.futo.inputmethod.latin.uix.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.IMPORT_SETTINGS_REQUEST
import org.futo.inputmethod.latin.uix.SettingsExporter
import org.futo.inputmethod.latin.uix.HAS_SEEN_RESTORE_BACKUP_PROMPT
import org.futo.inputmethod.latin.uix.dataStore

@Composable
@Preview
fun SetupRestoreBackup(onFinished: () -> Unit = { }) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    val markSeen: () -> Unit = {
        scope.launch {
            context.dataStore.updateData { prefs ->
                prefs.toMutablePreferences().apply {
                    this[HAS_SEEN_RESTORE_BACKUP_PROMPT] = true
                }
            }
        }
    }

    val importSettings = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                Log.d("SetupRestoreBackup", "Selected backup uri: $uri")
                isLoading = true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                Log.d("SetupRestoreBackup", "Loading settings...")
                                SettingsExporter.loadSettings(context, inputStream, true)
                            }
                        }
                        Log.d("SetupRestoreBackup", "Settings loaded")
                    } catch (e: Exception) {
                        Log.e("SetupRestoreBackup", "Error loading settings", e)
                    }
                    isLoading = false
                    markSeen()
                    onFinished()
                }
            } ?: run {
                markSeen()
                onFinished()
            }
        } else {
            markSeen()
            onFinished()
        }
    }

    SetupContainer {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Step(fraction = 3.0f / 5.0f, text = stringResource(R.string.setup_step_restore_backup))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                Text(
                    stringResource(R.string.setup_restoring_backup_loading_text), // Add this string resource
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    stringResource(R.string.setup_restore_backup_text),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "application/octet-stream"
                        }
                        importSettings.launch(intent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(stringResource(R.string.setup_restore_backup_button))
                }

                Button(
                    onClick = {
                        markSeen()
                        onFinished()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(stringResource(R.string.setup_skip_button))
                }
            }
        }
    }
}
