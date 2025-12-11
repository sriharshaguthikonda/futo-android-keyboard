package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.ENABLE_AI_REPLY
import org.futo.inputmethod.latin.uix.AI_REPLY_PROMPT
import org.futo.inputmethod.latin.uix.AI_REPLY_SYSTEM_PROMPTS
import org.futo.inputmethod.latin.uix.AI_REPLY_ACTIVE_PROMPT_NAME
import org.futo.inputmethod.latin.uix.SystemPrompt
import org.futo.inputmethod.latin.uix.SystemPromptManager
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.userSettingNavigationItem
import org.futo.inputmethod.latin.uix.settings.userSettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.userSettingDecorationOnly
import org.futo.inputmethod.latin.uix.settings.SettingTextField
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.theme.Typography

@Composable
fun SystemPromptsManager() {
    val systemPromptsItem = useDataStore(AI_REPLY_SYSTEM_PROMPTS)
    val activePromptNameItem = useDataStore(AI_REPLY_ACTIVE_PROMPT_NAME)
    val prompts = remember(systemPromptsItem.value) { 
        SystemPromptManager.parsePrompts(systemPromptsItem.value).toMutableList()
    }
    
    val showAddDialog = remember { mutableStateOf(false) }
    val showEditDialog = remember { mutableStateOf(false) }
    val editingPromptIndex = remember { mutableStateOf(-1) }
    val dialogName = remember { mutableStateOf("") }
    val dialogPrompt = remember { mutableStateOf("") }
    
    fun savePrompts(newPrompts: List<SystemPrompt>) {
        systemPromptsItem.setValue(SystemPromptManager.toJson(newPrompts))
    }
    
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("System Prompts", style = Typography.Heading.RegularMl)
            IconButton(onClick = {
                dialogName.value = ""
                dialogPrompt.value = ""
                showAddDialog.value = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add prompt")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        prompts.forEachIndexed { index, prompt ->
            val isActive = prompt.name == activePromptNameItem.value
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isActive) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .border(
                        width = if (isActive) 2.dp else 1.dp,
                        color = if (isActive) MaterialTheme.colorScheme.primary 
                               else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable { activePromptNameItem.setValue(prompt.name) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = prompt.name,
                        style = Typography.Heading.RegularMl,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = prompt.prompt,
                        style = Typography.SmallMl,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 2
                    )
                }
                
                IconButton(onClick = {
                    editingPromptIndex.value = index
                    dialogName.value = prompt.name
                    dialogPrompt.value = prompt.prompt
                    showEditDialog.value = true
                }) {
                    Icon(
                        Icons.Default.Edit, 
                        contentDescription = "Edit",
                        tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                              else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (prompts.size > 1) {
                    IconButton(onClick = {
                        val newPrompts = prompts.toMutableList()
                        newPrompts.removeAt(index)
                        if (prompt.name == activePromptNameItem.value) {
                            activePromptNameItem.setValue(newPrompts.first().name)
                        }
                        savePrompts(newPrompts)
                    }) {
                        Icon(
                            Icons.Default.Delete, 
                            contentDescription = "Delete",
                            tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                                  else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    
    if (showAddDialog.value) {
        AlertDialog(
            onDismissRequest = { showAddDialog.value = false },
            title = { Text("Add System Prompt") },
            text = {
                Column {
                    TextField(
                        value = dialogName.value,
                        onValueChange = { dialogName.value = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = dialogPrompt.value,
                        onValueChange = { dialogPrompt.value = it },
                        label = { Text("System Prompt") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (dialogName.value.isNotBlank() && dialogPrompt.value.isNotBlank()) {
                            val newPrompts = prompts.toMutableList()
                            newPrompts.add(SystemPrompt(dialogName.value, dialogPrompt.value))
                            savePrompts(newPrompts)
                            showAddDialog.value = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog.value = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    if (showEditDialog.value && editingPromptIndex.value >= 0) {
        AlertDialog(
            onDismissRequest = { showEditDialog.value = false },
            title = { Text("Edit System Prompt") },
            text = {
                Column {
                    TextField(
                        value = dialogName.value,
                        onValueChange = { dialogName.value = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = dialogPrompt.value,
                        onValueChange = { dialogPrompt.value = it },
                        label = { Text("System Prompt") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (dialogName.value.isNotBlank() && dialogPrompt.value.isNotBlank()) {
                            val oldName = prompts[editingPromptIndex.value].name
                            val newPrompts = prompts.toMutableList()
                            newPrompts[editingPromptIndex.value] = SystemPrompt(dialogName.value, dialogPrompt.value)
                            savePrompts(newPrompts)
                            if (oldName == activePromptNameItem.value) {
                                activePromptNameItem.setValue(dialogName.value)
                            }
                            showEditDialog.value = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog.value = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

val AiReplyMenu = UserSettingsMenu(
    title = R.string.ai_reply_settings_title,
    navPath = "aiReply", registerNavPath = true,
    settings = listOf(
        userSettingToggleDataStore(
            title = R.string.ai_reply_enable,
            setting = ENABLE_AI_REPLY
        ),
        userSettingDecorationOnly {
            SystemPromptsManager()
        },
        userSettingDecorationOnly {
            SettingTextField(
                title = stringResource(R.string.ai_reply_prompt_title),
                placeholder = stringResource(R.string.ai_reply_prompt_placeholder),
                field = AI_REPLY_PROMPT
            )
        },
        userSettingNavigationItem(
            title = R.string.ai_reply_groq_config,
            subtitle = R.string.ai_reply_groq_config_subtitle,
            style = NavigationItemStyle.Misc,
            navigateTo = "groqChat"
        )
    )
)
