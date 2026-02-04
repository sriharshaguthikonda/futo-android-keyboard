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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.GROQ_VOICE_API_KEY
import org.futo.inputmethod.latin.uix.GROQ_VOICE_MODEL
import org.futo.inputmethod.latin.uix.PREFER_BLUETOOTH
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.SettingToggleRaw
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import org.futo.inputmethod.latin.xlm.ModelPaths
import org.futo.voiceinput.shared.AudioPrebufferRecorder
import kotlin.math.roundToInt

private const val TestingArenaRecordingSeconds = 10
private val TestingArenaRemote = SettingsKey(booleanPreferencesKey("testingArenaRemote"), false)
private val TestingArenaAudioPath = SettingsKey(stringPreferencesKey("testingArenaAudioPath"), "")
private val TestingArenaReferenceTranscript = SettingsKey(stringPreferencesKey("testingArenaReferenceTranscript"), "")
private val TestingArenaSelectedModels = SettingsKey(stringSetPreferencesKey("testingArenaSelectedModels"), setOf())

@Preview(showBackground = true)
@Composable
fun TestingArenaScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val results = remember { mutableStateMapOf<String, String>() }
    var metricsSummary by remember { mutableStateOf(context.getString(R.string.testing_arena_metrics_empty)) }
    val remoteEnabled = useDataStoreValue(TestingArenaRemote)
    val selectedModels = useDataStore(TestingArenaSelectedModels)
    val audioPath = useDataStore(TestingArenaAudioPath)
    val referenceTranscript = useDataStore(TestingArenaReferenceTranscript)
    val groqApiKey = useDataStore(GROQ_VOICE_API_KEY)
    val groqModel = useDataStore(GROQ_VOICE_MODEL)
    val preferBluetooth = useDataStoreValue(PREFER_BLUETOOTH)

    val localModels = remember {
        ModelPaths.getModels(context).mapNotNull { it.loadDetails() }
    }

    var recorder by remember { mutableStateOf<AudioPrebufferRecorder?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recordedSamples by remember { mutableStateOf<FloatArray?>(null) }
    var recordedDurationSeconds by remember { mutableStateOf(0) }
    var useRecordedAudio by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            recorder?.stop()
            recorder = null
        }
    }

    val localReadyLabel = stringResource(R.string.testing_arena_results_local_ready)
    val missingAudioLabel = stringResource(R.string.testing_arena_results_audio_missing)
    val missingGroqKeyLabel = stringResource(R.string.testing_arena_results_missing_groq_key)

    ScrollableList {
        ScreenTitle(stringResource(R.string.testing_arena_title), showBack = true, navController)

        ScreenTitle(stringResource(R.string.testing_arena_models_title))
        if (localModels.isEmpty()) {
            Text(
                text = stringResource(R.string.testing_arena_no_local_models),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        } else {
            localModels.forEach { model ->
                val label = model.name.trim().ifBlank { model.path }
                val subtitle = model.languages.joinToString(", ").ifBlank {
                    stringResource(R.string.testing_arena_model_language_unknown)
                }
                val isSelected = selectedModels.value.contains(model.path)
                SettingToggleRaw(
                    title = label,
                    subtitle = subtitle,
                    enabled = isSelected,
                    setValue = { enabled ->
                        val next = selectedModels.value.toMutableSet()
                        if (enabled) {
                            next.add(model.path)
                        } else {
                            next.remove(model.path)
                        }
                        selectedModels.setValue(next)
                    }
                )
            }
        }

        NavigationItem(
            title = stringResource(R.string.testing_arena_manage_models_title),
            subtitle = stringResource(R.string.testing_arena_manage_models_subtitle),
            style = NavigationItemStyle.Misc,
            navigate = { navController.navigate("models") }
        )

        SettingToggleDataStore(
            title = stringResource(R.string.testing_arena_model_remote),
            subtitle = stringResource(R.string.testing_arena_model_remote_subtitle),
            setting = TestingArenaRemote
        )

        if (remoteEnabled) {
            ScreenTitle(stringResource(R.string.testing_arena_remote_title))
            val groqStatus = if (groqApiKey.value.isBlank()) {
                stringResource(R.string.testing_arena_remote_missing_key)
            } else {
                stringResource(R.string.testing_arena_remote_ready, groqModel.value)
            }
            Text(
                text = groqStatus,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            NavigationItem(
                title = stringResource(R.string.testing_arena_remote_open_settings),
                subtitle = stringResource(R.string.testing_arena_remote_open_settings_subtitle),
                style = NavigationItemStyle.Misc,
                navigate = { navController.navigate("groqWhisper") }
            )
        }

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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    if (isRecording) {
                        val snapshot = recorder?.snapshotAndReset() ?: FloatArray(0)
                        recorder?.stop()
                        recorder = null
                        isRecording = false
                        if (snapshot.isNotEmpty()) {
                            recordedSamples = snapshot
                            recordedDurationSeconds = (snapshot.size / 16000.0f).roundToInt()
                            useRecordedAudio = true
                        }
                    } else {
                        recordedSamples = null
                        recordedDurationSeconds = 0
                        val newRecorder = AudioPrebufferRecorder(
                            context = context,
                            lifecycleScope = lifecycleOwner.lifecycleScope,
                            preferBluetoothMic = preferBluetooth,
                            prebufferDurationMs = TestingArenaRecordingSeconds * 1000
                        )
                        newRecorder.start()
                        recorder = newRecorder
                        isRecording = true
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    if (isRecording) {
                        stringResource(R.string.testing_arena_record_stop)
                    } else {
                        stringResource(R.string.testing_arena_record_start)
                    }
                )
            }
            Button(
                onClick = {
                    recorder?.stop()
                    recorder = null
                    isRecording = false
                    recordedSamples = null
                    recordedDurationSeconds = 0
                    useRecordedAudio = false
                },
                modifier = Modifier.weight(1f),
                enabled = recordedSamples != null || isRecording
            ) {
                Text(stringResource(R.string.testing_arena_record_clear))
            }
        }

        if (recordedSamples != null) {
            Text(
                text = stringResource(R.string.testing_arena_record_ready, recordedDurationSeconds),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            SettingToggleRaw(
                title = stringResource(R.string.testing_arena_use_recorded_audio),
                enabled = useRecordedAudio,
                setValue = { useRecordedAudio = it },
                subtitle = stringResource(R.string.testing_arena_use_recorded_audio_subtitle)
            )
        }

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
                    val audioValue = if (useRecordedAudio && recordedSamples != null) {
                        stringResource(R.string.testing_arena_audio_recorded, recordedDurationSeconds)
                    } else {
                        audioPath.value
                    }
                    val localModelLabels = localModels.filter { selectedModels.value.contains(it.path) }
                        .map { it.name.trim().ifBlank { it.path } }
                    if (localModelLabels.isEmpty() && !remoteEnabled) {
                        results[stringResource(R.string.testing_arena_results_notice)] =
                            stringResource(R.string.testing_arena_results_no_models)
                        return@Button
                    }

                    localModelLabels.forEach { model ->
                        results[model] = context.getString(
                            R.string.testing_arena_results_placeholder,
                            audioValue.ifBlank { missingAudioLabel },
                            localReadyLabel
                        )
                    }

                    if (remoteEnabled) {
                        val status = if (groqApiKey.value.isBlank()) {
                            missingGroqKeyLabel
                        } else {
                            context.getString(
                                R.string.testing_arena_results_remote_ready,
                                groqModel.value
                            )
                        }
                        results[stringResource(R.string.testing_arena_model_remote)] = context.getString(
                            R.string.testing_arena_results_placeholder,
                            audioValue.ifBlank { missingAudioLabel },
                            status
                        )
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
