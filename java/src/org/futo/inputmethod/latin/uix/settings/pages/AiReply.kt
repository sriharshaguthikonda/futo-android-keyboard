package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.GROQ_API_KEY
import org.futo.inputmethod.latin.uix.GROQ_CHAT_MODEL
import org.futo.inputmethod.latin.uix.GROQ_CHAT_SYSTEM_PROMPT
import org.futo.inputmethod.latin.uix.AI_REPLY_PROVIDER
import org.futo.inputmethod.latin.uix.LOCAL_CHAT_MODEL_PATH
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.DropDownPickerSettingItem
import org.futo.inputmethod.latin.uix.settings.SettingTextField
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.voiceinput.shared.groq.GroqChatApi

@Composable
fun AiReplyConfigScreen(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val providerItem = useDataStore(AI_REPLY_PROVIDER)
    val apiKeyItem = useDataStore(GROQ_API_KEY)
    val modelItem = useDataStore(GROQ_CHAT_MODEL)
    val promptItem = useDataStore(GROQ_CHAT_SYSTEM_PROMPT)
    val localModelItem = useDataStore(LOCAL_CHAT_MODEL_PATH)
    val modelOptions = remember { mutableStateOf(listOf("llama3-8b-8192")) }
    val providerOptions = listOf("groq", "local")

    LaunchedEffect(apiKeyItem.value, providerItem.value) {
        if(providerItem.value == "groq" && apiKeyItem.value.isNotBlank()) {
            val models = withContext(Dispatchers.IO) {
                GroqChatApi.availableModels(apiKeyItem.value)
            }
            if(!models.isNullOrEmpty()) {
                modelOptions.value = models
            }
        }
    }

    ScrollableList {
        ScreenTitle(stringResource(R.string.ai_reply_settings_title), showBack = true, navController)

        DropDownPickerSettingItem(
            label = stringResource(R.string.ai_reply_settings_provider),
            options = providerOptions,
            selection = providerItem.value,
            onSet = { providerItem.setValue(it) },
            getDisplayName = {
                if(it == "local") stringResource(R.string.ai_reply_provider_local) else stringResource(R.string.ai_reply_provider_groq)
            }
        )

        if(providerItem.value == "groq") {
            SettingTextField(
                title = stringResource(R.string.groq_settings_api_key),
                placeholder = "sk-...",
                field = GROQ_API_KEY
            )

        val providerDisplayNames = mapOf(
            "local" to stringResource(R.string.ai_reply_provider_local),
            "groq" to stringResource(R.string.ai_reply_provider_groq)
        )
        
        DropDownPickerSettingItem(
            label = stringResource(R.string.ai_reply_settings_provider),
            options = providerOptions,
            selection = providerItem.value,
            onSet = { providerItem.setValue(it) },
            getDisplayName = { providerDisplayNames[it] ?: it }
        )
        } else {
            SettingTextField(
                title = stringResource(R.string.ai_reply_settings_local_model),
                placeholder = "/sdcard/model.gguf",
                field = LOCAL_CHAT_MODEL_PATH
            )
        }

        SettingTextField(
            title = stringResource(R.string.ai_reply_settings_system_prompt),
            placeholder = stringResource(R.string.ai_reply_settings_system_prompt_placeholder),
            field = GROQ_CHAT_SYSTEM_PROMPT
        )
    }
}
