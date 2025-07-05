package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.GROQ_API_KEY
import org.futo.inputmethod.latin.uix.GROQ_CHAT_MODEL
import org.futo.inputmethod.latin.uix.GROQ_CHAT_SYSTEM_PROMPT
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.DropDownPickerSettingItem
import org.futo.inputmethod.latin.uix.settings.SettingTextField
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.voiceinput.shared.groq.GroqChatApi

@Composable
fun AiReplyConfigScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val apiKeyItem = useDataStore(GROQ_API_KEY)
    val modelItem = useDataStore(GROQ_CHAT_MODEL)
    val promptItem = useDataStore(GROQ_CHAT_SYSTEM_PROMPT)
    val modelOptions = remember { mutableStateOf(listOf("llama3-8b-8192")) }

    LaunchedEffect(apiKeyItem.value) {
        if(apiKeyItem.value.isNotBlank()) {
            val models = GroqChatApi.availableModels(apiKeyItem.value)
            if(!models.isNullOrEmpty()) {
                modelOptions.value = models
            }
        }
    }

    ScrollableList {
        ScreenTitle(stringResource(R.string.ai_reply_settings_title), showBack = true, navController)

        SettingTextField(
            title = stringResource(R.string.groq_settings_api_key),
            placeholder = "sk-...",
            field = GROQ_API_KEY
        )

        DropDownPickerSettingItem(
            label = stringResource(R.string.ai_reply_settings_model),
            options = modelOptions.value,
            selection = modelItem.value,
            onSet = { modelItem.setValue(it) },
            getDisplayName = { it }
        )

        SettingTextField(
            title = stringResource(R.string.ai_reply_settings_system_prompt),
            placeholder = stringResource(R.string.ai_reply_settings_system_prompt_placeholder),
            field = GROQ_CHAT_SYSTEM_PROMPT
        )
    }
}
