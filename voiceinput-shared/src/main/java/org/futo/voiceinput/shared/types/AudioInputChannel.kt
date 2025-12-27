package org.futo.voiceinput.shared.types

enum class AudioInputChannel(val preferenceValue: Int) {
    LEFT(0),
    RIGHT(1);

    companion object {
        fun fromPreference(value: Int): AudioInputChannel {
            return entries.firstOrNull { it.preferenceValue == value } ?: LEFT
        }
    }
}
