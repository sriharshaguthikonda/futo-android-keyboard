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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.DISALLOW_SYMBOLS
import org.futo.inputmethod.latin.uix.GROQ_VOICE_API_KEY
import org.futo.inputmethod.latin.uix.GROQ_VOICE_MODEL
import org.futo.inputmethod.latin.uix.LOCAL_VOICE_SYSTEM_PROMPT
import org.futo.inputmethod.latin.uix.PREFER_BLUETOOTH
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.USE_GPU_OFFLOAD
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.SettingToggleRaw
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import org.futo.voiceinput.shared.ENGLISH_MODELS
import org.futo.voiceinput.shared.MULTILINGUAL_MODELS
import org.futo.voiceinput.shared.AudioPrebufferRecorder
import org.futo.voiceinput.shared.groq.GroqWhisperApi
import org.futo.voiceinput.shared.ggml.InvalidModelException
import org.futo.voiceinput.shared.types.InferenceState
import org.futo.voiceinput.shared.types.Language
import org.futo.voiceinput.shared.types.ModelInferenceCallback
import org.futo.voiceinput.shared.util.normalizeTranscription
import org.futo.voiceinput.shared.whisper.DecodingConfiguration
import org.futo.voiceinput.shared.whisper.ModelManager
import org.futo.voiceinput.shared.whisper.MultiModelRunConfiguration
import org.futo.voiceinput.shared.whisper.MultiModelRunner
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

private const val TestingArenaRecordingSeconds = 10
private val TestingArenaRemote = SettingsKey(booleanPreferencesKey("testingArenaRemote"), false)
private val TestingArenaAudioPath = SettingsKey(stringPreferencesKey("testingArenaAudioPath"), "")
private val TestingArenaReferenceTranscript = SettingsKey(stringPreferencesKey("testingArenaReferenceTranscript"), "")
private val TestingArenaSelectedModels = SettingsKey(stringSetPreferencesKey("testingArenaSelectedModels"), setOf())

private fun wordErrorRate(reference: String, hypothesis: String): Double {
    val refTokens = reference.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    val hypTokens = hypothesis.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (refTokens.isEmpty()) return 0.0

    val dp = Array(refTokens.size + 1) { IntArray(hypTokens.size + 1) }
    for (i in 0..refTokens.size) dp[i][0] = i
    for (j in 0..hypTokens.size) dp[0][j] = j

    for (i in 1..refTokens.size) {
        for (j in 1..hypTokens.size) {
            val cost = if (refTokens[i - 1] == hypTokens[j - 1]) 0 else 1
            dp[i][j] = minOf(
                dp[i - 1][j] + 1,
                dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + cost
            )
        }
    }

    return dp[refTokens.size][hypTokens.size].toDouble() / refTokens.size.toDouble()
}

private fun loadWavSamples(path: String): FloatArray {
    val file = File(path)
    require(file.exists()) { "File not found" }
    val data = file.readBytes()
    require(data.size >= 44) { "Invalid WAV file" }
    val header = ByteBuffer.wrap(data, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
    val riff = ByteArray(4)
    header.get(riff)
    require(String(riff) == "RIFF") { "Invalid WAV header" }
    header.position(8)
    val wave = ByteArray(4)
    header.get(wave)
    require(String(wave) == "WAVE") { "Invalid WAV header" }
    header.position(22)
    val channels = header.short
    require(channels.toInt() == 1) { "Only mono WAV is supported" }
    header.position(24)
    val sampleRate = header.int
    require(sampleRate == 16000) { "Sample rate must be 16000 Hz" }
    header.position(34)
    val bitsPerSample = header.short
    require(bitsPerSample.toInt() == 16) { "Only 16-bit PCM WAV is supported" }
    val pcmOffset = 44
    val pcmSize = data.size - pcmOffset
    require(pcmSize > 0 && pcmSize % 2 == 0) { "Invalid PCM data" }
    val samples = FloatArray(pcmSize / 2)
    val pcm = ByteBuffer.wrap(data, pcmOffset, pcmSize).order(ByteOrder.LITTLE_ENDIAN)
    for (i in samples.indices) {
        samples[i] = pcm.short.toFloat() / Short.MAX_VALUE.toFloat()
    }
    return samples
}

@Preview(showBackground = true)
@Composable
fun TestingArenaScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val results = remember { mutableStateMapOf<String, String>() }
    val transcripts = remember { mutableStateMapOf<String, String>() }
    var metricsSummary by remember { mutableStateOf(context.getString(R.string.testing_arena_metrics_empty)) }
    val remoteEnabled = useDataStoreValue(TestingArenaRemote)
    val selectedModels = useDataStore(TestingArenaSelectedModels)
    val audioPath = useDataStore(TestingArenaAudioPath)
    val referenceTranscript = useDataStore(TestingArenaReferenceTranscript)
    val groqApiKey = useDataStore(GROQ_VOICE_API_KEY)
    val groqModel = useDataStore(GROQ_VOICE_MODEL)
    val preferBluetooth = useDataStoreValue(PREFER_BLUETOOTH)
    val suppressSymbols = useDataStoreValue(DISALLOW_SYMBOLS)
    val useGpuOffload = useDataStoreValue(USE_GPU_OFFLOAD)
    val localSystemPrompt = useDataStore(LOCAL_VOICE_SYSTEM_PROMPT)

    val voiceModels = remember {
        (ENGLISH_MODELS + MULTILINGUAL_MODELS).distinctBy { it.key(context) }
    }

    val modelManager = remember { ModelManager(context) }
    val modelRunner = remember { MultiModelRunner(modelManager) }

    var recorder by remember { mutableStateOf<AudioPrebufferRecorder?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recordedSamples by remember { mutableStateOf<FloatArray?>(null) }
    var recordedDurationSeconds by remember { mutableStateOf(0) }
    var useRecordedAudio by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            recorder?.stop()
            recorder = null
            modelRunner.cancelAll()
        }
    }

    val localReadyLabel = stringResource(R.string.testing_arena_results_local_ready)
    val missingAudioLabel = stringResource(R.string.testing_arena_results_audio_missing)
    val missingGroqKeyLabel = stringResource(R.string.testing_arena_results_missing_groq_key)
    val noticeLabel = stringResource(R.string.testing_arena_results_notice)
    val noModelsLabel = stringResource(R.string.testing_arena_results_no_models)
    val remoteLabel = stringResource(R.string.testing_arena_model_remote)
    val noAudioLabel = stringResource(R.string.testing_arena_results_no_audio)
    val transcribingLabel = stringResource(R.string.testing_arena_results_transcribing)
    val modelMissingLabel = stringResource(R.string.testing_arena_results_model_missing)
    val transcriptionFailedLabel = stringResource(R.string.testing_arena_results_transcription_failed)
    val invalidAudioLabel = stringResource(R.string.testing_arena_results_invalid_audio)
    val unsupportedModelLabel = stringResource(R.string.testing_arena_results_unsupported_model)

    ScrollableList {
        ScreenTitle(stringResource(R.string.testing_arena_title), showBack = true, navController)

        ScreenTitle(stringResource(R.string.testing_arena_models_title))
        if (voiceModels.isEmpty()) {
            Text(
                text = stringResource(R.string.testing_arena_no_voice_models),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        } else {
            voiceModels.forEach { model ->
                val label = context.getString(model.name)
                val categoryLabel = when {
                    ENGLISH_MODELS.contains(model) -> stringResource(R.string.testing_arena_voice_model_english)
                    MULTILINGUAL_MODELS.contains(model) -> stringResource(R.string.testing_arena_voice_model_multilingual)
                    else -> stringResource(R.string.testing_arena_voice_model_unknown)
                }
                val downloadedLabel = if (model.exists(context)) {
                    stringResource(R.string.testing_arena_voice_model_downloaded)
                } else {
                    stringResource(R.string.testing_arena_voice_model_missing)
                }
                val modelKey = model.key(context).toString()
                val isSelected = selectedModels.value.contains(modelKey)
                SettingToggleRaw(
                    title = label,
                    subtitle = "$categoryLabel • $downloadedLabel",
                    enabled = isSelected,
                    setValue = { enabled ->
                        val next = selectedModels.value.toMutableSet()
                        if (enabled) {
                            next.add(modelKey)
                        } else {
                            next.remove(modelKey)
                        }
                        selectedModels.setValue(next)
                    }
                )
            }
        }

        NavigationItem(
            title = stringResource(R.string.testing_arena_manage_voice_models_title),
            subtitle = stringResource(R.string.testing_arena_manage_voice_models_subtitle),
            style = NavigationItemStyle.Misc,
            navigate = { navController.navigate("voiceInput") }
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
                    transcripts.clear()
                    val audioSamples = when {
                        useRecordedAudio -> recordedSamples
                        audioPath.value.isNotBlank() -> {
                            runCatching { loadWavSamples(audioPath.value) }
                                .getOrElse {
                                    results[noticeLabel] = "$invalidAudioLabel ${it.message ?: ""}".trim()
                                    null
                                }
                        }
                        else -> null
                    }
                    if (audioSamples == null) {
                        if (results.isEmpty()) {
                            results[noticeLabel] = noAudioLabel
                        }
                        return@Button
                    }
                    val localModelsToRun = selectedModels.value.mapNotNull { key ->
                        voiceModels.firstOrNull { it.key(context).toString() == key }
                    }
                    if (localModelsToRun.isEmpty() && !remoteEnabled) {
                        results[noticeLabel] = noModelsLabel
                        return@Button
                    }

                    localModelsToRun.forEach { model ->
                        results[context.getString(model.name)] = transcribingLabel
                    }
                    if (remoteEnabled) {
                        results[remoteLabel] = transcribingLabel
                    }

                    lifecycleOwner.lifecycleScope.launch {
                        localModelsToRun.forEach { model ->
                            val modelName = context.getString(model.name)
                            if (!model.exists(context)) {
                                results[modelName] = modelMissingLabel
                                return@forEach
                            }
                            val languageSet = if (ENGLISH_MODELS.contains(model)) {
                                setOf(Language.English)
                            } else {
                                Language.values().toSet()
                            }
                            modelManager.useGpu = useGpuOffload
                            val runConfig = MultiModelRunConfiguration(
                                primaryModel = model,
                                languageSpecificModels = emptyMap()
                            )
                            val decodingConfig = DecodingConfiguration(
                                glossary = emptyList(),
                                languages = languageSet,
                                suppressSymbols = suppressSymbols,
                                systemPrompt = localSystemPrompt.value
                            )
                            val result = runCatching {
                                withContext(Dispatchers.Default) {
                                    modelRunner.run(
                                        samples = audioSamples,
                                        runConfiguration = runConfig,
                                        decodingConfiguration = decodingConfig,
                                        callback = object : ModelInferenceCallback {
                                            override fun updateStatus(state: InferenceState) {}
                                            override fun languageDetected(language: Language) {}
                                            override fun partialResult(string: String) {}
                                        }
                                    )
                                }
                            }.map { normalizeTranscription(it) }
                                .getOrElse { error ->
                                    val message = error.message.orEmpty()
                                    results[modelName] = if (error is InvalidModelException ||
                                        message.contains("tflite", ignoreCase = true)
                                    ) {
                                        unsupportedModelLabel
                                    } else {
                                        "$transcriptionFailedLabel ${message}".trim()
                                    }
                                ""
                            }
                            if (result.isNotBlank()) {
                                results[modelName] = result
                                transcripts[modelName] = result
                            }
                        }

                        if (remoteEnabled) {
                            val remoteResult = runCatching {
                                if (groqApiKey.value.isBlank()) {
                                    missingGroqKeyLabel
                                } else {
                                    withContext(Dispatchers.IO) {
                                        GroqWhisperApi.transcribe(
                                            samples = audioSamples,
                                            apiKey = groqApiKey.value,
                                            model = groqModel.value
                                        )
                                    }?.let { normalizeTranscription(it) } ?: transcriptionFailedLabel
                                }
                            }.getOrElse { error ->
                                "$transcriptionFailedLabel ${error.message ?: ""}".trim()
                            }
                            results[remoteLabel] = remoteResult
                            if (remoteResult != missingGroqKeyLabel && remoteResult.isNotBlank()) {
                                transcripts[remoteLabel] = remoteResult
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.testing_arena_run_button))
            }
            Button(
                onClick = {
                    metricsSummary = when {
                        referenceTranscript.value.isBlank() ->
                            context.getString(R.string.testing_arena_metrics_missing_reference)
                        transcripts.isEmpty() ->
                            context.getString(R.string.testing_arena_metrics_no_results)
                        else -> {
                            buildString {
                                transcripts.forEach { (model, transcript) ->
                                    val wer = wordErrorRate(referenceTranscript.value, transcript) * 100.0
                                    append(model)
                                    append(": ")
                                    append(String.format("%.2f%%", wer))
                                    append("\n")
                                }
                            }.trimEnd()
                        }
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
