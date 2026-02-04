package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue

private val TestingArenaLocalSmall = SettingsKey(booleanPreferencesKey("testingArenaLocalSmall"), true)
private val TestingArenaLocalLarge = SettingsKey(booleanPreferencesKey("testingArenaLocalLarge"), false)
private val TestingArenaRemote = SettingsKey(booleanPreferencesKey("testingArenaRemote"), false)
private val TestingArenaRemoteEndpoint = SettingsKey(stringPreferencesKey("testingArenaRemoteEndpoint"), "")
private val TestingArenaAudioPath = SettingsKey(stringPreferencesKey("testingArenaAudioPath"), "")
private val TestingArenaReferenceTranscript = SettingsKey(stringPreferencesKey("testingArenaReferenceTranscript"), "")

@Preview(showBackground = true)
@Composable
fun TestingArenaScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val results = remember { mutableStateMapOf<String, String>() }
    var metricsSummary by remember { mutableStateOf(stringResource(R.string.testing_arena_metrics_empty)) }
    val localSmallEnabled = useDataStoreValue(TestingArenaLocalSmall)
    val localLargeEnabled = useDataStoreValue(TestingArenaLocalLarge)
    val remoteEnabled = useDataStoreValue(TestingArenaRemote)
    val localSmallLabel = stringResource(R.string.testing_arena_model_local_small)
    val localLargeLabel = stringResource(R.string.testing_arena_model_local_large)
    val remoteLabel = stringResource(R.string.testing_arena_model_remote)
    val noticeLabel = stringResource(R.string.testing_arena_results_notice)
    val noModelsLabel = stringResource(R.string.testing_arena_results_no_models)
    val missingAudioLabel = stringResource(R.string.testing_arena_results_audio_missing)
    val missingEndpointLabel = stringResource(R.string.testing_arena_results_missing_endpoint)
    val localReadyLabel = stringResource(R.string.testing_arena_results_local_ready)

    ScrollableList {
        ScreenTitle(stringResource(R.string.testing_arena_title), showBack = true, navController)

        ScreenTitle(stringResource(R.string.testing_arena_models_title))
        SettingToggleDataStore(
            title = stringResource(R.string.testing_arena_model_local_small),
            subtitle = stringResource(R.string.testing_arena_model_local_small_subtitle),
            setting = TestingArenaLocalSmall
        )
        SettingToggleDataStore(
            title = stringResource(R.string.testing_arena_model_local_large),
            subtitle = stringResource(R.string.testing_arena_model_local_large_subtitle),
            setting = TestingArenaLocalLarge
        )
        SettingToggleDataStore(
            title = stringResource(R.string.testing_arena_model_remote),
            subtitle = stringResource(R.string.testing_arena_model_remote_subtitle),
            setting = TestingArenaRemote
        )

        val remoteEndpoint = useDataStore(TestingArenaRemoteEndpoint)
        if (remoteEnabled) {
            ScreenTitle(stringResource(R.string.testing_arena_remote_title))
            OutlinedTextField(
                value = remoteEndpoint.value,
                onValueChange = { remoteEndpoint.setValue(it) },
                label = { Text(stringResource(R.string.testing_arena_remote_endpoint_label)) },
                placeholder = { Text(stringResource(R.string.testing_arena_remote_endpoint_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            )
        }

        val audioPath = useDataStore(TestingArenaAudioPath)
        ScreenTitle(stringResource(R.string.testing_arena_audio_title))
        OutlinedTextField(
            value = audioPath.value,
            onValueChange = { audioPath.setValue(it) },
            label = { Text(stringResource(R.string.testing_arena_audio_path_label)) },
            placeholder = { Text(stringResource(R.string.testing_arena_audio_path_placeholder)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        )

        val referenceTranscript = useDataStore(TestingArenaReferenceTranscript)
        ScreenTitle(stringResource(R.string.testing_arena_reference_title))
        OutlinedTextField(
            value = referenceTranscript.value,
            onValueChange = { referenceTranscript.setValue(it) },
            label = { Text(stringResource(R.string.testing_arena_reference_label)) },
            placeholder = { Text(stringResource(R.string.testing_arena_reference_placeholder)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        )

        ScreenTitle(stringResource(R.string.testing_arena_actions_title))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    results.clear()
                    val models = buildList {
                        if (localSmallEnabled) {
                            add(localSmallLabel)
                        }
                        if (localLargeEnabled) {
                            add(localLargeLabel)
                        }
                        if (remoteEnabled) {
                            add(remoteLabel)
                        }
                    }
                    val audioValue = audioPath.value
                    val endpointValue = remoteEndpoint.value
                    if (models.isEmpty()) {
                        results[noticeLabel] = noModelsLabel
                    } else {
                        models.forEach { model ->
                            val status = when (model) {
                                remoteLabel -> if (endpointValue.isBlank()) {
                                    missingEndpointLabel
                                } else {
                                    context.getString(R.string.testing_arena_results_remote_ready, endpointValue)
                                }
                                else -> localReadyLabel
                            }
                            results[model] = context.getString(
                                R.string.testing_arena_results_placeholder,
                                audioValue.ifBlank { missingAudioLabel },
                                status
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.testing_arena_run_button))
            }
            Button(
                onClick = {
                    metricsSummary = if (referenceTranscript.value.isBlank()) {
                        context.getString(R.string.testing_arena_metrics_missing_reference)
                    } else {
                        context.getString(R.string.testing_arena_metrics_placeholder)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.testing_arena_metrics_button))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    metricsSummary = context.getString(R.string.testing_arena_charts_placeholder)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.testing_arena_charts_button))
            }
        }

        ScreenTitle(stringResource(R.string.testing_arena_results_title))
        if (results.isEmpty()) {
            Text(
                text = stringResource(R.string.testing_arena_results_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        } else {
            results.forEach { (model, detail) ->
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(text = model, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = detail, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        ScreenTitle(stringResource(R.string.testing_arena_metrics_title))
        Text(
            text = metricsSummary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}
