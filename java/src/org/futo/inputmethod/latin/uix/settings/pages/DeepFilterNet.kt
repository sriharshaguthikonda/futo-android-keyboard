package org.futo.inputmethod.latin.uix.settings.pages

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.USE_DEEP_FILTER_NET
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.SettingItem
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStore
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

private enum class RecordingTarget {
    Raw,
    Filtered
}

@Composable
fun DeepFilterNetScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recordingTarget = remember { mutableStateOf<RecordingTarget?>(null) }
    val recordJob = remember { mutableStateOf<Job?>(null) }
    val stopRecording = remember { AtomicBoolean(false) }
    val isPlaying = remember { mutableStateOf(false) }
    val rawSamples = remember { mutableStateOf<ShortArray?>(null) }
    val filteredSamples = remember { mutableStateOf<ShortArray?>(null) }
    val statusText = remember { mutableStateOf("") }
    val noiseSuppressorAvailable = remember { NoiseSuppressor.isAvailable() }

    val idleText = stringResource(R.string.deep_filter_net_status_idle)
    val recordingText = stringResource(R.string.deep_filter_net_status_recording)
    val noSampleText = stringResource(R.string.deep_filter_net_status_no_sample)

    LaunchedEffect(idleText) {
        if (statusText.value.isBlank()) {
            statusText.value = idleText
        }
    }

    fun updateStatus(text: String) {
        statusText.value = text
    }

    fun requestMicPermission() {
        val intent = Intent().apply {
            setClassName(context, "org.futo.inputmethod.latin.MicPermissionActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }

    fun startRecording(target: RecordingTarget, useNoiseSuppression: Boolean) {
        if (recordJob.value != null) return
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateStatus(stringResource(R.string.deep_filter_net_status_permission_needed))
            requestMicPermission()
            return
        }

        stopRecording.set(false)
        recordingTarget.value = target
        updateStatus(recordingText)

        recordJob.value = scope.launch(Dispatchers.IO) {
            val result = recordSample(
                stopRecording = stopRecording,
                useNoiseSuppression = useNoiseSuppression
            )

            withContext(Dispatchers.Main) {
                if (result == null) {
                    updateStatus(stringResource(R.string.deep_filter_net_status_recording_failed))
                } else {
                    when (target) {
                        RecordingTarget.Raw -> rawSamples.value = result
                        RecordingTarget.Filtered -> filteredSamples.value = result
                    }
                    updateStatus(stringResource(R.string.deep_filter_net_status_recorded))
                }
                recordingTarget.value = null
                recordJob.value = null
            }
        }
    }

    fun stopActiveRecording() {
        if (recordJob.value == null) return
        stopRecording.set(true)
    }

    fun playSample(samples: ShortArray?, statusState: MutableState<String>) {
        if (isPlaying.value) return
        if (samples == null || samples.isEmpty()) {
            statusState.value = noSampleText
            return
        }
        isPlaying.value = true
        statusState.value = stringResource(R.string.deep_filter_net_status_playing)
        scope.launch(Dispatchers.IO) {
            playSamples(samples)
            withContext(Dispatchers.Main) {
                isPlaying.value = false
                statusState.value = idleText
            }
        }
    }

    ScrollableList {
        ScreenTitle(stringResource(R.string.deep_filter_net_settings_title), showBack = true, navController)

        SettingToggleDataStore(
            title = stringResource(R.string.deep_filter_net_enable),
            subtitle = stringResource(R.string.deep_filter_net_enable_subtitle),
            setting = USE_DEEP_FILTER_NET
        )

        SettingItem(
            title = stringResource(R.string.deep_filter_net_status_title),
            subtitle = if (noiseSuppressorAvailable) {
                stringResource(R.string.deep_filter_net_status_available)
            } else {
                stringResource(R.string.deep_filter_net_status_unavailable)
            },
            onClick = { }
        ) { }

        SettingItem(
            title = stringResource(R.string.deep_filter_net_record_sample),
            subtitle = stringResource(R.string.deep_filter_net_record_sample_subtitle),
            onClick = { startRecording(RecordingTarget.Raw, useNoiseSuppression = false) }
        ) { }

        SettingItem(
            title = stringResource(R.string.deep_filter_net_record_filtered_sample),
            subtitle = stringResource(R.string.deep_filter_net_record_filtered_sample_subtitle),
            onClick = { startRecording(RecordingTarget.Filtered, useNoiseSuppression = true) }
        ) { }

        if (recordingTarget.value != null) {
            SettingItem(
                title = stringResource(R.string.deep_filter_net_stop_recording),
                subtitle = stringResource(R.string.deep_filter_net_stop_recording_subtitle),
                onClick = { stopActiveRecording() }
            ) { }
        }

        SettingItem(
            title = stringResource(R.string.deep_filter_net_play_sample),
            subtitle = stringResource(R.string.deep_filter_net_play_sample_subtitle),
            onClick = { playSample(rawSamples.value, statusText) }
        ) { }

        SettingItem(
            title = stringResource(R.string.deep_filter_net_play_filtered_sample),
            subtitle = stringResource(R.string.deep_filter_net_play_filtered_sample_subtitle),
            onClick = { playSample(filteredSamples.value, statusText) }
        ) { }

        SettingItem(
            title = stringResource(R.string.deep_filter_net_clear_samples),
            subtitle = stringResource(R.string.deep_filter_net_clear_samples_subtitle),
            onClick = {
                rawSamples.value = null
                filteredSamples.value = null
                updateStatus(idleText)
            }
        ) { }

        SettingItem(
            title = stringResource(R.string.deep_filter_net_status_label),
            subtitle = statusText.value,
            onClick = { }
        ) { }
    }
}

private fun recordSample(
    stopRecording: AtomicBoolean,
    useNoiseSuppression: Boolean,
    sampleRate: Int = 16000,
    maxSeconds: Int = 8
): ShortArray? {
    val minBuffer = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    val bufferSize = max(minBuffer, sampleRate)
    val recorder = AudioRecord(
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSize
    )
    val noiseSuppressor = if (useNoiseSuppression && NoiseSuppressor.isAvailable()) {
        NoiseSuppressor.create(recorder.audioSessionId)?.apply { enabled = true }
    } else {
        null
    }

    val maxSamples = sampleRate * maxSeconds
    val buffer = ShortArray(1024)
    val chunks = mutableListOf<ShortArray>()
    var totalSamples = 0

    return try {
        recorder.startRecording()
        while (!stopRecording.get() && totalSamples < maxSamples) {
            val nRead = recorder.read(buffer, 0, buffer.size)
            if (nRead <= 0) break
            totalSamples += nRead
            chunks.add(buffer.copyOf(nRead))
        }
        if (totalSamples == 0) {
            null
        } else {
            val combined = ShortArray(totalSamples)
            var offset = 0
            for (chunk in chunks) {
                System.arraycopy(chunk, 0, combined, offset, chunk.size)
                offset += chunk.size
            }
            combined
        }
    } finally {
        try {
            recorder.stop()
        } catch (_: IllegalStateException) {
        }
        recorder.release()
        noiseSuppressor?.release()
    }
}

private fun playSamples(
    samples: ShortArray,
    sampleRate: Int = 16000
) {
    val minBuffer = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    val bufferSize = max(minBuffer, samples.size * 2)
    val audioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setBufferSizeInBytes(bufferSize)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    try {
        audioTrack.play()
        audioTrack.write(samples, 0, samples.size)
        audioTrack.stop()
    } finally {
        audioTrack.release()
    }
}
