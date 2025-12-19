package org.futo.inputmethod.latin.uix.actions

import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.START_VOICE_ON_OPEN
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.setSettingBlocking

val VoiceAutoStartAction = Action(
    icon = R.drawable.mic_fill,
    name = R.string.action_voice_input_auto_start_title,
    simplePressImpl = { manager, _ ->
        val context = manager.getContext()
        val isEnabled = context.getSetting(START_VOICE_ON_OPEN)
        context.setSettingBlocking(START_VOICE_ON_OPEN.key, !isEnabled)
        val message = context.getString(
            if (!isEnabled) {
                R.string.action_voice_input_auto_start_enabled
            } else {
                R.string.action_voice_input_auto_start_disabled
            }
        )
        manager.announce(message)
    },
    windowImpl = null,
)
