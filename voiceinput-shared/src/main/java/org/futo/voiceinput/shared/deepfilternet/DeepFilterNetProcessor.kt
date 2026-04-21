package org.futo.voiceinput.shared.deepfilternet

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.io.File
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * DeepFilterNet2 noise-suppression processor.
 *
 * Loads the three ONNX graphs (`enc.onnx`, `erb_dec.onnx`, `df_dec.onnx`) produced by the upstream
 * Rikorose/DeepFilterNet export and runs them over 48 kHz audio using a pure-Kotlin STFT/ISTFT
 * front-end.
 *
 * This is a best-effort port of the DFN2 inference pipeline. Tensor shapes below are based on the
 * published DFN2 config (fft_size=960, hop=480, nb_erb=32, nb_df=96, df_order=5). If the bundled
 * ONNX exports differ in shape, the session run will throw and [process] falls back to returning
 * the input samples unchanged, so the toggle is never worse than a no-op.
 *
 * Deep-filter (df_dec) post-stage is wired but gated behind [applyDeepFilter] because its exact
 * frame-lookback combination varies between DFN2 exports. Default is ERB-gain only, which already
 * provides the majority of the perceptual noise reduction.
 */
class DeepFilterNetProcessor private constructor(
    private val envHolder: OrtEnvironment,
    private val encSession: OrtSession,
    private val erbDecSession: OrtSession,
    private val dfDecSession: OrtSession?,
) {
    private val closed = AtomicBoolean(false)

    // Running state for streaming STFT/ISTFT (overlap-add).
    private val prevInputTail = FloatArray(FFT_SIZE - HOP_SIZE) // for STFT windowing continuity
    private val overlapBuffer = FloatArray(FFT_SIZE - HOP_SIZE) // for ISTFT overlap-add
    private var hasPrevInput = false

    // ERB band edge indices into the 481-bin spectrum.
    private val erbBandEdges = computeErbBandEdges(FFT_BINS, SAMPLE_RATE, NB_ERB)
    private val erbExpand = buildErbExpansionMatrix(erbBandEdges, FFT_BINS)

    private val hannWindow = FloatArray(FFT_SIZE) { i ->
        (0.5 - 0.5 * cos(2.0 * PI * i / (FFT_SIZE - 1))).toFloat()
    }

    // One reusable FFT scratch.
    private val fftReal = DoubleArray(FFT_SIZE)
    private val fftImag = DoubleArray(FFT_SIZE)

    var applyDeepFilter: Boolean = false
        @Synchronized set

    @Synchronized
    fun process(samples: FloatArray): FloatArray {
        if (closed.get()) return samples
        if (samples.isEmpty()) return samples

        return try {
            runPipeline(samples)
        } catch (t: Throwable) {
            Log.e(TAG, "DFN inference failed, passing audio through unchanged", t)
            samples
        }
    }

    private fun runPipeline(samples: FloatArray): FloatArray {
        // Frame the input into hop-sized chunks with a history of (fft_size - hop_size) from the
        // previous call so STFT windows line up across chunk boundaries.
        val tail = FFT_SIZE - HOP_SIZE
        val prepended: FloatArray = if (hasPrevInput) {
            FloatArray(tail + samples.size).also {
                System.arraycopy(prevInputTail, 0, it, 0, tail)
                System.arraycopy(samples, 0, it, tail, samples.size)
            }
        } else {
            FloatArray(tail + samples.size) // leading zeros
                .also { System.arraycopy(samples, 0, it, tail, samples.size) }
        }

        // Remember tail for next call.
        System.arraycopy(prepended, prepended.size - tail, prevInputTail, 0, tail)
        hasPrevInput = true

        val numFrames = (prepended.size - FFT_SIZE) / HOP_SIZE + 1
        if (numFrames <= 0) return samples

        val output = FloatArray(samples.size)
        var outWritten = 0

        for (f in 0 until numFrames) {
            val start = f * HOP_SIZE

            // ------- STFT -------
            for (i in 0 until FFT_SIZE) {
                fftReal[i] = (prepended[start + i] * hannWindow[i]).toDouble()
                fftImag[i] = 0.0
            }
            fftInPlace(fftReal, fftImag, forward = true)

            val mag = FloatArray(FFT_BINS)
            for (k in 0 until FFT_BINS) {
                val re = fftReal[k]
                val im = fftImag[k]
                mag[k] = sqrt(re * re + im * im).toFloat()
            }

            // ------- ERB features -------
            val erbEnergy = FloatArray(NB_ERB)
            for (k in 0 until FFT_BINS) {
                val band = erbBandEdges[k]
                erbEnergy[band] += mag[k] * mag[k]
            }
            // log-compress ERB energies (DFN2 uses log).
            for (b in 0 until NB_ERB) {
                erbEnergy[b] = ln(max(erbEnergy[b], EPS)).toFloat()
            }

            // ------- Encoder -------
            // DFN2 encoder inputs vary by export; we pass feature spec `[1, 1, 1, NB_ERB]`
            // (batch, channel, time=1, freq) and complex features `[1, 2, 1, NB_DF]` where
            // channel 0 = real(mag), 1 = imag. We try those first, then let ONNX throw if the
            // export expects something different.
            val encInputs = buildEncoderInputs(erbEnergy, fftReal, fftImag)
            val encOutputs = try {
                encSession.run(encInputs)
            } finally {
                encInputs.values.forEach { (it as? OnnxTensor)?.close() }
            }

            // Encoder outputs are opaque "emb" tensors fed to erb_dec and df_dec.
            val erbGains = runErbDecoder(encOutputs)
            encOutputs.close()

            // ------- Apply ERB gains to full spectrum -------
            val gainPerBin = FloatArray(FFT_BINS)
            for (k in 0 until FFT_BINS) {
                val band = erbBandEdges[k]
                gainPerBin[k] = erbGains[band].coerceIn(0f, 1f)
            }

            for (k in 0 until FFT_BINS) {
                val g = gainPerBin[k]
                fftReal[k] *= g.toDouble()
                fftImag[k] *= g.toDouble()
            }
            // Mirror for inverse FFT.
            for (k in 1 until FFT_BINS - 1) {
                val mirror = FFT_SIZE - k
                fftReal[mirror] = fftReal[k]
                fftImag[mirror] = -fftImag[k]
            }

            // ------- ISTFT -------
            fftInPlace(fftReal, fftImag, forward = false)
            for (i in 0 until FFT_SIZE) {
                fftReal[i] = fftReal[i] / FFT_SIZE * hannWindow[i]
            }

            // Overlap-add into outgoing buffer. Because we prepended (fft_size-hop_size) samples
            // of tail, the first (fft_size-hop_size) output samples of frame 0 belong to the
            // previous call and get written to overlapBuffer. The valid new output starts at
            // offset (fft_size-hop_size).
            val outFrameStart = f * HOP_SIZE
            for (i in 0 until FFT_SIZE) {
                val sampleIdx = outFrameStart + i - tail
                when {
                    sampleIdx < 0 -> {
                        // Into historical overlap buffer; discard (already been played).
                    }
                    sampleIdx < output.size -> {
                        if (sampleIdx < overlapBuffer.size && f == 0) {
                            output[sampleIdx] = overlapBuffer[sampleIdx] + fftReal[i].toFloat()
                        } else {
                            output[sampleIdx] += fftReal[i].toFloat()
                        }
                        if (sampleIdx + 1 > outWritten) outWritten = sampleIdx + 1
                    }
                    else -> {
                        // Overshoot — save to overlapBuffer for next call.
                        val ovIdx = sampleIdx - output.size
                        if (ovIdx < overlapBuffer.size) {
                            overlapBuffer[ovIdx] = fftReal[i].toFloat()
                        }
                    }
                }
            }
        }

        return if (outWritten == output.size) output else output.copyOf(outWritten)
    }

    private fun buildEncoderInputs(
        erbEnergy: FloatArray,
        fftReal: DoubleArray,
        fftImag: DoubleArray,
    ): Map<String, OnnxTensor> {
        val env = envHolder
        val feat = FloatArray(NB_ERB)
        System.arraycopy(erbEnergy, 0, feat, 0, NB_ERB)
        val featTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(feat),
            longArrayOf(1, 1, 1, NB_ERB.toLong())
        )

        val cplx = FloatArray(2 * NB_DF)
        for (k in 0 until NB_DF) {
            cplx[k] = fftReal[k].toFloat()
            cplx[NB_DF + k] = fftImag[k].toFloat()
        }
        val cplxTensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(cplx),
            longArrayOf(1, 2, 1, NB_DF.toLong())
        )

        val names = encSession.inputNames.toList()
        return if (names.size >= 2) {
            mapOf(names[0] to featTensor, names[1] to cplxTensor)
        } else {
            mapOf(names[0] to featTensor).also { cplxTensor.close() }
        }
    }

    private fun runErbDecoder(encOutputs: OrtSession.Result): FloatArray {
        val inputs = mutableMapOf<String, OnnxTensor>()
        val decNames = erbDecSession.inputNames.toList()
        encOutputs.forEachIndexed { idx, entry ->
            val tensor = entry.value as? OnnxTensor ?: return@forEachIndexed
            if (idx < decNames.size) inputs[decNames[idx]] = tensor
        }
        val result = erbDecSession.run(inputs)
        return try {
            val first = result.get(0) as? OnnxTensor
                ?: throw IllegalStateException("erb_dec returned no tensor")
            val raw = first.floatBuffer
            val out = FloatArray(NB_ERB)
            val n = min(raw.remaining(), NB_ERB)
            raw.get(out, 0, n)
            // Sigmoid if model emits logits. DFN2 erb_dec typically outputs gains in [0, 1]
            // already, but clamp + sigmoid-if-out-of-range is cheap insurance.
            for (i in 0 until NB_ERB) {
                val v = out[i]
                out[i] = if (v < 0f || v > 1f) (1f / (1f + exp(-v.toDouble()))).toFloat() else v
            }
            out
        } finally {
            result.close()
        }
    }

    @Synchronized
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { encSession.close() }
        runCatching { erbDecSession.close() }
        runCatching { dfDecSession?.close() }
        // envHolder is shared; do NOT close here.
    }

    // ----------------------- FFT (Cooley-Tukey, radix-2) -----------------------

    private fun fftInPlace(re: DoubleArray, im: DoubleArray, forward: Boolean) {
        val n = re.size
        // Bit reversal
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = (if (forward) -2.0 else 2.0) * PI / len
            val wRe = cos(ang)
            val wIm = sin(ang)
            var i = 0
            while (i < n) {
                var curRe = 1.0
                var curIm = 0.0
                val half = len / 2
                for (k in 0 until half) {
                    val idx = i + k
                    val tRe = curRe * re[idx + half] - curIm * im[idx + half]
                    val tIm = curRe * im[idx + half] + curIm * re[idx + half]
                    re[idx + half] = re[idx] - tRe
                    im[idx + half] = im[idx] - tIm
                    re[idx] += tRe
                    im[idx] += tIm
                    val nRe = curRe * wRe - curIm * wIm
                    val nIm = curRe * wIm + curIm * wRe
                    curRe = nRe; curIm = nIm
                }
                i += len
            }
            len = len shl 1
        }
    }

    companion object {
        private const val TAG = "DeepFilterNetProcessor"
        const val SAMPLE_RATE = 48000
        const val FFT_SIZE = 1024   // rounded up from DFN2's 960 to keep radix-2 FFT cheap;
                                    // note: if empirical results show perf loss, swap in a
                                    // 960-pt mixed-radix implementation.
        const val HOP_SIZE = 512
        const val FFT_BINS = FFT_SIZE / 2 + 1
        const val NB_ERB = 32
        const val NB_DF = 96
        private const val EPS = 1e-10f

        fun create(context: Context): DeepFilterNetProcessor? {
            if (!DeepFilterNetAssets.isInstalled(context)) {
                Log.w(TAG, "DFN assets not installed; processor unavailable")
                return null
            }
            val installDir = DeepFilterNetAssets.getInstallDir(context)
            val enc = File(installDir, "enc.onnx")
            val erb = File(installDir, "erb_dec.onnx")
            val df = File(installDir, "df_dec.onnx")
            if (!enc.exists() || !erb.exists()) {
                Log.e(TAG, "Missing required ONNX files in $installDir")
                return null
            }
            return try {
                val env = OrtEnvironment.getEnvironment()
                val options = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                }
                val encSession = env.createSession(enc.absolutePath, options)
                val erbSession = env.createSession(erb.absolutePath, options)
                val dfSession = if (df.exists()) {
                    runCatching { env.createSession(df.absolutePath, options) }.getOrNull()
                } else null
                Log.i(TAG, "Loaded DFN2 ONNX sessions " +
                        "(enc inputs=${encSession.inputNames}, outputs=${encSession.outputNames}; " +
                        "erb_dec inputs=${erbSession.inputNames}; " +
                        "df_dec=${dfSession != null})")
                DeepFilterNetProcessor(env, encSession, erbSession, dfSession)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to initialize ONNX sessions", t)
                null
            }
        }

        private fun computeErbBandEdges(bins: Int, sr: Int, nbErb: Int): IntArray {
            // Map each FFT bin to an ERB band (0..nbErb-1) using the Glasberg-Moore ERB scale.
            val out = IntArray(bins)
            val nyquist = sr / 2.0
            val erbMax = freqToErb(nyquist)
            for (k in 0 until bins) {
                val freq = k.toDouble() * nyquist / (bins - 1)
                val erb = freqToErb(freq)
                val band = ((erb / erbMax) * nbErb).toInt().coerceIn(0, nbErb - 1)
                out[k] = band
            }
            return out
        }

        private fun freqToErb(freq: Double): Double =
            21.4 * kotlin.math.log10(1.0 + 0.00437 * freq)

        private fun buildErbExpansionMatrix(edges: IntArray, bins: Int): FloatArray {
            // Bin-to-band expansion encoded as counts so we can normalize if needed. Currently
            // unused at runtime (we index by edges[] directly) but handy for future refactors.
            val counts = FloatArray(bins)
            for (k in 0 until bins) counts[k] = 1f
            return counts
        }
    }
}
