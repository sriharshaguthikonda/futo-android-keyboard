package org.futo.voiceinput.shared

enum class LocalTranscriptionBackend(val settingValue: Int) {
    Whisper(0),
    Moonshine(1);

    companion object {
        fun fromSetting(value: Int): LocalTranscriptionBackend {
            return entries.firstOrNull { it.settingValue == value } ?: Whisper
        }
    }
}
