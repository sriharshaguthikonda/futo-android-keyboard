package org.futo.voiceinput.shared.deepfilternet

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

object DeepFilterNetAssets {
    const val MODEL_NAME = "DeepFilterNet2_onnx"
    const val ASSET_PATH = "deepfilternet/DeepFilterNet2_onnx.tar.gz"
    const val DOWNLOAD_URL =
        "https://raw.githubusercontent.com/Rikorose/DeepFilterNet/1e96ef05e1ef75b3702f8c55ca065368deae637d/models/DeepFilterNet2_onnx.tar.gz"

    private const val INSTALL_ROOT_DIR = "deepfilternet"
    private const val MARKER_FILE = ".installed"

    fun getInstallDir(context: Context): File {
        return File(context.filesDir, "$INSTALL_ROOT_DIR/$MODEL_NAME")
    }

    fun isBundledAvailable(context: Context): Boolean {
        return runCatching {
            context.assets.open(ASSET_PATH).close()
        }.isSuccess
    }

    fun isInstalled(context: Context): Boolean {
        return File(getInstallDir(context), MARKER_FILE).exists()
    }

    suspend fun installFromBundled(context: Context, onStatus: (String) -> Unit) {
        updateStatus(onStatus, "Installing bundled DeepFilterNet model…")
        withContext(Dispatchers.IO) {
            context.assets.open(ASSET_PATH).use { stream ->
                installFromStream(context, stream)
            }
        }
        updateStatus(onStatus, "DeepFilterNet model installed.")
    }

    suspend fun downloadAndInstall(context: Context, onStatus: (String) -> Unit) {
        updateStatus(onStatus, "Downloading DeepFilterNet model…")
        withContext(Dispatchers.IO) {
            val connection = URL(DOWNLOAD_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.connect()
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("HTTP ${connection.responseCode}")
                }
                val tmpFile = File(context.cacheDir, "deepfilternet_model.tar.gz")
                connection.inputStream.use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } >= 0) {
                            output.write(buffer, 0, read)
                        }
                    }
                }
                tmpFile.inputStream().use { stream ->
                    installFromStream(context, stream)
                }
                tmpFile.delete()
            } finally {
                connection.disconnect()
            }
        }
        updateStatus(onStatus, "DeepFilterNet model installed.")
    }

    suspend fun clearInstalled(context: Context, onStatus: (String) -> Unit) {
        updateStatus(onStatus, "Removing DeepFilterNet model…")
        withContext(Dispatchers.IO) {
            getInstallDir(context).deleteRecursively()
        }
        updateStatus(onStatus, "DeepFilterNet model removed.")
    }

    private suspend fun updateStatus(onStatus: (String) -> Unit, message: String) {
        withContext(Dispatchers.Main) {
            onStatus(message)
        }
    }

    private fun installFromStream(context: Context, inputStream: InputStream) {
        val installDir = getInstallDir(context)
        val tmpRoot = File(context.filesDir, "$INSTALL_ROOT_DIR/tmp-${System.currentTimeMillis()}")
        tmpRoot.mkdirs()

        BufferedInputStream(GZIPInputStream(inputStream)).use { gz ->
            extractTar(gz, tmpRoot)
        }

        val candidateDirs = tmpRoot.listFiles()?.filter { it.isDirectory } ?: emptyList()
        val finalDir = if (candidateDirs.size == 1 && candidateDirs[0].isDirectory) {
            candidateDirs[0]
        } else {
            tmpRoot
        }

        installDir.deleteRecursively()
        installDir.parentFile?.mkdirs()
        if (finalDir != installDir) {
            if (!finalDir.renameTo(installDir)) {
                copyDirectory(finalDir, installDir)
            }
        }
        if (tmpRoot.exists() && tmpRoot != installDir) {
            tmpRoot.deleteRecursively()
        }

        File(installDir, MARKER_FILE).writeText("ok")
    }

    private fun extractTar(input: InputStream, outputDir: File) {
        val header = ByteArray(512)
        val rootPath = outputDir.canonicalFile

        while (true) {
            val read = readFully(input, header)
            if (read <= 0) return
            if (read < header.size) return
            if (header.all { it == 0.toByte() }) return

            val name = header.decodeString(0, 100).trimEnd('\u0000')
            if (name.isBlank()) return

            val size = header.decodeString(124, 12).trim { it <= ' ' || it == '\u0000' }
            val fileSize = if (size.isBlank()) 0L else size.toLong(8)
            val typeFlag = header[156].toInt().toChar()
            val target = safeResolve(rootPath, name) ?: run {
                skipFully(input, fileSize)
                skipPadding(input, fileSize)
                continue
            }

            if (typeFlag == '5' || name.endsWith("/")) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                FileOutputStream(target).use { output ->
                    copyBytes(input, output, fileSize)
                }
            }

            skipPadding(input, fileSize)
        }
    }

    private fun ByteArray.decodeString(offset: Int, length: Int): String {
        return String(this, offset, length, Charsets.US_ASCII)
    }

    private fun safeResolve(root: File, name: String): File? {
        if (name.startsWith("/") || name.startsWith("\\") || name.contains(":")) return null
        val candidate = File(root, name)
        val canonical = candidate.canonicalFile
        if (!canonical.path.startsWith(root.path + File.separator)) return null
        return canonical
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val read = input.read(buffer, offset, buffer.size - offset)
            if (read < 0) break
            offset += read
        }
        return offset
    }

    private fun copyBytes(input: InputStream, output: FileOutputStream, size: Long) {
        val buffer = ByteArray(32 * 1024)
        var remaining = size
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) break
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun skipFully(input: InputStream, size: Long) {
        var remaining = size
        val buffer = ByteArray(32 * 1024)
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) break
            remaining -= read
        }
    }

    private fun skipPadding(input: InputStream, size: Long) {
        val padding = (512 - (size % 512)).takeIf { it != 512L } ?: 0L
        if (padding > 0) {
            skipFully(input, padding)
        }
    }

    private fun copyDirectory(src: File, dest: File) {
        if (src.isDirectory) {
            dest.mkdirs()
            src.listFiles()?.forEach { child ->
                copyDirectory(child, File(dest, child.name))
            }
        } else {
            dest.parentFile?.mkdirs()
            src.inputStream().use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}
