package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.GROQ_API_KEY
import org.futo.inputmethod.latin.uix.GROQ_MODEL
import org.futo.inputmethod.latin.uix.settings.UserSetting
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.SettingItem
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.voiceinput.shared.groq.GroqChatApi

val AiReplySettingsMenu = UserSettingsMenu(
    title = R.string.ai_reply_settings_title,
    navPath = "actions/ai_reply",
    registerNavPath = true,
    settings = listOf(
        UserSetting(name = R.string.ai_reply_settings_test) {
            val lifecycleOwner = LocalLifecycleOwner.current
            val apiKeyItem = useDataStore(GROQ_API_KEY)
            val modelItem = useDataStore(GROQ_MODEL)
            val testStatus = remember { mutableStateOf("") }
            val testing = stringResource(R.string.groq_settings_testing)
            val successText = stringResource(R.string.groq_settings_success)
            val failureText = stringResource(R.string.groq_settings_failure)

            SettingItem(
                title = stringResource(R.string.ai_reply_settings_test),
                subtitle = testStatus.value,
                onClick = {
                    lifecycleOwner.lifecycleScope.launch {
                        testStatus.value = testing
                        val success = withContext(Dispatchers.IO) {
                            GroqChatApi.chat(
                                "You are a helpful assistant that writes concise replies.",
                                "Hello",
                                apiKeyItem.value,
                                modelItem.value
                            ) != null
                        }
                        testStatus.value = if (success) successText else failureText
                    }
                }
            ) { }
        }
    )
)
