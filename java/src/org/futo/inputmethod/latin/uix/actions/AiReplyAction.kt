package org.futo.inputmethod.latin.uix.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.GROQ_API_KEY
import org.futo.inputmethod.latin.uix.GROQ_MODEL
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.UserSetting
import org.futo.inputmethod.latin.uix.settings.SettingItem
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.voiceinput.shared.groq.GroqChatApi
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant that writes concise replies."

private class AiReplyWindow(
    val manager: KeyboardManagerForAction,
    val text: String
) : ActionWindow() {
    @Composable
    override fun windowName(): String = stringResource(R.string.action_ai_reply_title)

    @Composable
    override fun WindowContents(keyboardShown: Boolean) {
        val context = LocalContext.current
        val reply = remember { mutableStateOf<String?>(null) }
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text)
            reply.value?.let { Text(it) }
            Button(onClick = {
                val apiKey = context.getSetting(GROQ_API_KEY)
                val model = context.getSetting(GROQ_MODEL)
                reply.value = GroqChatApi.chat(DEFAULT_SYSTEM_PROMPT, text, apiKey, model)
            }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ai_reply_generate))
            }
            reply.value?.let { r ->
                Button(onClick = { manager.typeText(r); manager.closeActionWindow() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ai_reply_insert))
                }
            }
        }
    }
}

object AiReplyActionHolder { var pendingText: String = "" }

val AiReplyAction = Action(
    icon = R.drawable.text_prediction,
    name = R.string.action_ai_reply_title,
    simplePressImpl = null,
    windowImpl = { manager, _ ->
        val text = AiReplyActionHolder.pendingText
        AiReplyActionHolder.pendingText = ""
        AiReplyWindow(manager, text)
    },
    settingsMenu = UserSettingsMenu(
        title = R.string.ai_reply_settings_title,
        navPath = "actions/ai_reply",
        registerNavPath = true,
        settings = listOf(
            UserSetting(name = R.string.ai_reply_settings_test) {
                val lifecycleOwner = LocalLifecycleOwner.current
                val apiKeyItem = useDataStore(GROQ_API_KEY)
                val modelItem = useDataStore(GROQ_MODEL)
                val status = remember { mutableStateOf("") }

                val testing = stringResource(R.string.ai_reply_settings_testing)
                val successText = stringResource(R.string.ai_reply_settings_success)
                val failureText = stringResource(R.string.ai_reply_settings_failure)

                SettingItem(
                    title = stringResource(R.string.ai_reply_settings_test),
                    subtitle = status.value,
                    onClick = {
                        lifecycleOwner.lifecycleScope.launch {
                            status.value = testing
                            val success = withContext(Dispatchers.IO) {
                                GroqChatApi.chat(DEFAULT_SYSTEM_PROMPT, "Rewrite this text.", apiKeyItem.value, modelItem.value) != null
                            }
                            status.value = if (success) successText else failureText
                        }
                    }
                ) { }
            }
        )
    )
)
