package org.futo.voiceinput.shared.moonshine

import android.content.Context
import java.io.File
import java.io.FileOutputStream

object MoonshineStreamingAssets {
    private const val AssetRoot = "moonshine/tiny_streaming_en"
    private const val InstalledRoot = "moonshine/tiny_streaming_en"
    private const val VersionMarkerName = ".version"
    private const val ModelVersion = "moonshine-voice-0.0.59-tiny-streaming-en"

    private val RequiredFiles = listOf(
        "adapter.ort",
        "cross_kv.ort",
        "decoder_kv.ort",
        "decoder_kv_with_attention.ort",
        "encoder.ort",
        "frontend.ort",
        "streaming_config.json",
        "tokenizer.bin",
    )

    fun isBundledAvailable(context: Context): Boolean {
        return RequiredFiles.all { fileName ->
            val assetPath = "$AssetRoot/$fileName"
            runCatching {
                context.assets.open(assetPath).use { }
            }.isSuccess
        }
    }

    fun isInstalled(context: Context): Boolean {
        val installDir = getInstallDir(context)
        val marker = File(installDir, VersionMarkerName)
        if (!marker.exists()) return false
        if (marker.readText().trim() != ModelVersion) return false
        return RequiredFiles.all { fileName -> File(installDir, fileName).exists() }
    }

    fun ensureInstalled(context: Context): File {
        val installDir = getInstallDir(context)
        if (isInstalled(context)) return installDir

        installDir.mkdirs()
        RequiredFiles.forEach { fileName ->
            copyAssetToFile(
                context = context,
                assetPath = "$AssetRoot/$fileName",
                outFile = File(installDir, fileName)
            )
        }
        File(installDir, VersionMarkerName).writeText(ModelVersion)
        return installDir
    }

    private fun getInstallDir(context: Context): File {
        return File(context.filesDir, InstalledRoot)
    }

    private fun copyAssetToFile(context: Context, assetPath: String, outFile: File) {
        outFile.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        }
    }
}
