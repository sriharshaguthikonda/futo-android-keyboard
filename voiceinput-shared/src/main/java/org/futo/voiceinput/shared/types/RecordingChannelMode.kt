package org.futo.voiceinput.shared.types

enum class RecordingChannelMode {
    MONO,
    CHANNEL_1,
    CHANNEL_2,
    TEST_CHANNELS;

    fun requiresStereo(): Boolean {
        return this != MONO
    }

    fun isTestMode(): Boolean {
        return this == TEST_CHANNELS
    }

    companion object {
        fun fromSetting(value: Int): RecordingChannelMode {
            return entries.getOrElse(value) { MONO }
        }
    }
}
