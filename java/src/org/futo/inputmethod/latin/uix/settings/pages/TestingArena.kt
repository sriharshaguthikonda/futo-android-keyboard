package org.futo.inputmethod.latin.uix.settings.pages

import android.content.Context
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.Job
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
import org.futo.voiceinput.shared.deepfilternet.DeepFilterNetAssets
import org.futo.voiceinput.shared.groq.GroqWhisperApi
import org.futo.voiceinput.shared.ggml.InvalidModelException
import org.futo.voiceinput.shared.types.InferenceState
import org.futo.voiceinput.shared.types.Language
import org.futo.voiceinput.shared.types.ModelInferenceCallback
import org.futo.voiceinput.shared.types.ModelLoader
import org.futo.voiceinput.shared.util.normalizeTranscription
import org.futo.voiceinput.shared.whisper.DecodingConfiguration
import org.futo.voiceinput.shared.whisper.ModelManager
import org.futo.voiceinput.shared.whisper.MultiModelRunConfiguration
import org.futo.voiceinput.shared.whisper.MultiModelRunner
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

private const val TestingArenaRecordingSeconds = 10
private val TestingArenaRemote = SettingsKey(booleanPreferencesKey("testingArenaRemote"), false)
private val TestingArenaAudioPath = SettingsKey(stringPreferencesKey("testingArenaAudioPath"), "")
private val TestingArenaReferenceTranscript = SettingsKey(stringPreferencesKey("testingArenaReferenceTranscript"), "")
private val TestingArenaSelectedModels = SettingsKey(stringSetPreferencesKey("testingArenaSelectedModels"), setOf())
private val TestingArenaCustomModelPaths = SettingsKey(stringSetPreferencesKey("testingArenaCustomModelPaths"), setOf())

private data class TestingArenaModel(
    val id: String,
    val label: String,
    val loader: ModelLoader,
    val categoryLabel: String,
    val downloadedLabel: String,
)

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

private fun charErrorRate(reference: String, hypothesis: String): Double {
    val refChars = reference.trim().lowercase().toCharArray()
    val hypChars = hypothesis.trim().lowercase().toCharArray()
    if (refChars.isEmpty()) return 0.0

    val dp = Array(refChars.size + 1) { IntArray(hypChars.size + 1) }
    for (i in 0..refChars.size) dp[i][0] = i
    for (j in 0..hypChars.size) dp[0][j] = j

    for (i in 1..refChars.size) {
        for (j in 1..hypChars.size) {
            val cost = if (refChars[i - 1] == hypChars[j - 1]) 0 else 1
            dp[i][j] = minOf(
                dp[i - 1][j] + 1,
                dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + cost
            )
        }
    }

    return dp[refChars.size][hypChars.size].toDouble() / refChars.size.toDouble()
}

private fun loadWavSamples(path: String): FloatArray {
    val file = File(path)
    require(file.exists()) { "File not found" }
    val data = file.readBytes()
    require(data.size >= 12) { "Invalid WAV file" }

    val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
    val riff = ByteArray(4)
    buffer.get(riff)
    require(String(riff, Charsets.US_ASCII) == "RIFF") { "Invalid WAV header" }
    buffer.int
    val wave = ByteArray(4)
    buffer.get(wave)
    require(String(wave, Charsets.US_ASCII) == "WAVE") { "Invalid WAV header" }

    var fmtFound = false
    var audioFormat = 0
    var channels = 0
    var sampleRate = 0
    var bitsPerSample = 0
    var dataOffset = -1
    var dataSize = 0

    while (buffer.remaining() >= 8) {
        val chunkIdBytes = ByteArray(4)
        buffer.get(chunkIdBytes)
        val chunkId = String(chunkIdBytes, Charsets.US_ASCII)
        val chunkSize = buffer.int
        require(chunkSize >= 0 && chunkSize <= buffer.remaining()) { "Invalid WAV chunk" }

        when (chunkId) {
            "fmt " -> {
                require(chunkSize >= 16) { "Invalid WAV fmt chunk" }
                audioFormat = buffer.short.toInt() and 0xFFFF
                channels = buffer.short.toInt() and 0xFFFF
                sampleRate = buffer.int
                buffer.int
                buffer.short
                bitsPerSample = buffer.short.toInt() and 0xFFFF
                val extra = chunkSize - 16
                if (extra > 0) {
                    buffer.position(buffer.position() + extra)
                }
                fmtFound = true
            }
            "data" -> {
                dataOffset = buffer.position()
                dataSize = chunkSize
                buffer.position(buffer.position() + chunkSize)
            }
            else -> {
                buffer.position(buffer.position() + chunkSize)
            }
        }

        if (chunkSize % 2 == 1 && buffer.hasRemaining()) {
            buffer.get()
        }
    }

    require(fmtFound) { "Missing WAV fmt chunk" }
    require(dataOffset >= 0) { "Missing WAV data chunk" }
    require(audioFormat == 1) { "Only PCM WAV is supported" }
    require(channels == 1) { "Only mono WAV is supported" }
    require(sampleRate == 16000) { "Sample rate must be 16000 Hz" }
    require(bitsPerSample == 16) { "Only 16-bit PCM WAV is supported" }
    require(dataOffset + dataSize <= data.size) { "Invalid WAV data" }
    require(dataSize > 0 && dataSize % 2 == 0) { "Invalid PCM data" }

    val samples = FloatArray(dataSize / 2)
    val pcm = ByteBuffer.wrap(data, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN)
    for (i in samples.indices) {
        samples[i] = pcm.short.toFloat() / Short.MAX_VALUE.toFloat()
    }
    return samples
}

private fun copyUriToVoiceModelFile(context: Context, uri: android.net.Uri): File {
    val resolver = context.contentResolver
    val name = resolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1 && cursor.moveToFirst()) {
            cursor.getString(nameIndex)
        } else {
            null
        }
    } ?: "voice_model.bin"
    val targetDir = File(context.filesDir, "voice_models").apply { mkdirs() }
    val targetFile = File(targetDir, name)
    resolver.openInputStream(uri)?.use { input ->
        FileOutputStream(targetFile).use { output ->
            input.copyTo(output)
        }
    }
    return targetFile
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
    val customModelPaths = useDataStore(TestingArenaCustomModelPaths)
    val customModelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                val file = runCatching { copyUriToVoiceModelFile(context, uri) }.getOrNull()
                if (file != null) {
                    val next = customModelPaths.value.toMutableSet()
                    next.add(file.absolutePath)
                    customModelPaths.setValue(next)
                }
            }
        }
    )

    val voiceModels = run {
        val baseModels = (ENGLISH_MODELS + MULTILINGUAL_MODELS).distinctBy { it.key(context) }
        val baseEntries = baseModels.map { model ->
            val categoryLabel = if (ENGLISH_MODELS.contains(model)) {
                context.getString(R.string.testing_arena_voice_model_english)
            } else {
                context.getString(R.string.testing_arena_voice_model_multilingual)
            }
            TestingArenaModel(
                id = model.key(context).toString(),
                label = context.getString(model.name),
                loader = model,
                categoryLabel = categoryLabel,
                downloadedLabel = if (model.exists(context)) {
                    context.getString(R.string.testing_arena_voice_model_downloaded)
                } else {
                    context.getString(R.string.testing_arena_voice_model_missing)
                }
            )
        }
        val customEntries = customModelPaths.value.mapNotNull { path ->
            val file = File(path)
            if (file.exists()) {
                TestingArenaModel(
                    id = path,
                    label = file.name,
                    loader = org.futo.voiceinput.shared.types.ModelFileFile(
                        R.string.testing_arena_voice_model_custom,
                        file
                    ),
                    categoryLabel = context.getString(R.string.testing_arena_voice_model_custom),
                    downloadedLabel = context.getString(R.string.testing_arena_voice_model_custom)
                )
            } else {
                null
            }
        }
        baseEntries + customEntries
    }

    val modelManager = remember { ModelManager(context) }
    val modelRunner = remember { MultiModelRunner(modelManager) }

    var recorder by remember { mutableStateOf<AudioPrebufferRecorder?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var recordedSamples by remember { mutableStateOf<FloatArray?>(null) }
    var recordedDurationSeconds by remember { mutableStateOf(0) }
    var useRecordedAudio by remember { mutableStateOf(false) }
    var runJob by remember { mutableStateOf<Job?>(null) }
    var dfnStatus by remember { mutableStateOf("") }
    var dfnBusy by remember { mutableStateOf(false) }
    var dfnInstalled by remember { mutableStateOf(DeepFilterNetAssets.isInstalled(context)) }
    val dfnBundled = remember { DeepFilterNetAssets.isBundledAvailable(context) }

    DisposableEffect(Unit) {
        onDispose {
            runJob?.cancel()
            recorder?.stop()
            recorder = null
            modelRunner.cancelAll()
        }
    }

    LaunchedEffect(Unit) {
        if (dfnStatus.isBlank()) {
            dfnStatus = if (dfnInstalled) {
                context.getString(R.string.testing_arena_dfn_status_installed)
            } else {
                context.getString(R.string.testing_arena_dfn_status_not_installed)
            }
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
    val customModelsTitle = stringResource(R.string.testing_arena_custom_models_title)
    val customModelsSubtitle = stringResource(R.string.testing_arena_custom_models_subtitle)

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
                val isSelected = selectedModels.value.contains(model.id)
                SettingToggleRaw(
                    title = model.label,
                    subtitle = "${model.categoryLabel} • ${model.downloadedLabel}",
                    enabled = isSelected,
                    setValue = { enabled ->
                        val next = selectedModels.value.toMutableSet()
                        if (enabled) {
                            next.add(model.id)
                        } else {
                            next.remove(model.id)
                        }
                        selectedModels.setValue(next)
                    }
                )
            }
        }

        ScreenTitle(customModelsTitle)
        Text(
            text = customModelsSubtitle,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    customModelPicker.launch(arrayOf("*/*"))
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.testing_arena_custom_models_add))
            }
        }
        customModelPaths.value.forEach { path ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = path, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    val next = customModelPaths.value.toMutableSet()
                    next.remove(path)
                    customModelPaths.setValue(next)
                    val selected = selectedModels.value.toMutableSet()
                    selected.remove(path)
                    selectedModels.setValue(selected)
                }) {
                    Text(stringResource(R.string.testing_arena_custom_models_remove))
                }
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

        ScreenTitle(stringResource(R.string.testing_arena_dfn_title))
        Text(
            text = stringResource(
                R.string.testing_arena_dfn_status_label,
                if (dfnBundled) {
                    stringResource(R.string.testing_arena_dfn_status_bundled_yes)
                } else {
                    stringResource(R.string.testing_arena_dfn_status_bundled_no)
                },
                if (dfnInstalled) {
                    stringResource(R.string.testing_arena_dfn_status_installed)
                } else {
                    stringResource(R.string.testing_arena_dfn_status_not_installed)
                }
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Text(
            text = dfnStatus,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    if (dfnBusy) return@Button
                    dfnBusy = true
                    lifecycleOwner.lifecycleScope.launch {
                        runCatching {
                            DeepFilterNetAssets.installFromBundled(context) { dfnStatus = it }
                        }.onFailure {
                            dfnStatus = it.message ?: "Failed to install bundled model."
                        }
                        dfnInstalled = DeepFilterNetAssets.isInstalled(context)
                        dfnBusy = false
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = dfnBundled && !dfnBusy
            ) {
                Text(stringResource(R.string.testing_arena_dfn_install_bundled))
            }
            Button(
                onClick = {
                    if (dfnBusy) return@Button
                    dfnBusy = true
                    lifecycleOwner.lifecycleScope.launch {
                        runCatching {
                            DeepFilterNetAssets.downloadAndInstall(context) { dfnStatus = it }
                        }.onFailure {
                            dfnStatus = it.message ?: "Download failed."
                        }
                        dfnInstalled = DeepFilterNetAssets.isInstalled(context)
                        dfnBusy = false
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !dfnBusy
            ) {
                Text(stringResource(R.string.testing_arena_dfn_download))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    if (dfnBusy) return@Button
                    dfnBusy = true
                    lifecycleOwner.lifecycleScope.launch {
                        runCatching {
                            DeepFilterNetAssets.clearInstalled(context) { dfnStatus = it }
                        }.onFailure {
                            dfnStatus = it.message ?: "Failed to remove model."
                        }
                        dfnInstalled = DeepFilterNetAssets.isInstalled(context)
                        dfnBusy = false
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = dfnInstalled && !dfnBusy
            ) {
                Text(stringResource(R.string.testing_arena_dfn_clear))
            }
        }

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
                    runJob?.cancel()
                    modelRunner.cancelAll()
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
                        voiceModels.firstOrNull { it.id == key }
                    }
                    if (localModelsToRun.isEmpty() && !remoteEnabled) {
                        results[noticeLabel] = noModelsLabel
                        return@Button
                    }

                    localModelsToRun.forEach { model ->
                        results[model.label] = transcribingLabel
                    }
                    if (remoteEnabled) {
                        results[remoteLabel] = transcribingLabel
                    }

                    runJob = lifecycleOwner.lifecycleScope.launch {
                        localModelsToRun.forEach { model ->
                            val modelName = model.label
                            if (!model.loader.exists(context)) {
                                results[modelName] = modelMissingLabel
                                return@forEach
                            }
                            val languageSet = if (ENGLISH_MODELS.any { it.key(context).toString() == model.id }) {
                                setOf(Language.English)
                            } else {
                                Language.values().toSet()
                            }
                            modelManager.useGpu = useGpuOffload
                            val runConfig = MultiModelRunConfiguration(
                                primaryModel = model.loader,
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
                                    val cer = charErrorRate(referenceTranscript.value, transcript) * 100.0
                                    append(model)
                                    append(": WER ")
                                    append(String.format("%.2f%%", wer))
                                    append(" • CER ")
                                    append(String.format("%.2f%%", cer))
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
