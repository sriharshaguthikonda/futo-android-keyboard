package org.futo.inputmethod.latin.uix.actions

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.futo.inputmethod.latin.uix.SettingsKey
import java.io.File

val ClipboardHistoryEnabled = SettingsKey(
    booleanPreferencesKey("enableClipboardHistory"),
    false
)

val ClipboardHistoryItemsToKeep = SettingsKey(
    intPreferencesKey("clipboard_history_items_to_keep"),
    25
)

val ClipboardHistoryTimeToKeep = SettingsKey(
    intPreferencesKey("clipboard_history_time_to_keep"),
    3 * 24
)

val ClipboardHistorySaveSensitive = SettingsKey(
    booleanPreferencesKey("clipboard_history_save_sensitive"),
    false
)

object UriSerializer : KSerializer<Uri> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Uri", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Uri) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Uri {
        return Uri.parse(decoder.decodeString())
    }
}

@Serializable
data class ClipboardEntry(
    val timestamp: Long,
    val pinned: Boolean,
    val text: String?,
    @Serializable(with = UriSerializer::class)
    val uri: Uri?,
    val mimeTypes: List<String>
)

const val ClipboardFileName = "clipboard.json"
val Context.clipboardFile get() = File(filesDir, ClipboardFileName)

val DefaultClipboardEntry = ClipboardEntry(
    timestamp = 0L,
    pinned = true,
    text = "Clipboard entries will appear here",
    uri = null,
    mimeTypes = listOf()
)
