package org.futo.inputmethod.latin.uix.actions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast
import android.view.KeyEvent
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.GROQ_REPLY_API_KEY
import org.futo.inputmethod.latin.uix.AI_REPLY_PROMPT
import org.futo.inputmethod.latin.uix.AI_REPLY_SYSTEM_PROMPTS
import org.futo.inputmethod.latin.uix.AI_REPLY_ACTIVE_PROMPT_NAME
import org.futo.inputmethod.latin.uix.SystemPromptManager
import org.futo.inputmethod.latin.uix.GROQ_REPLY_MODEL
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.showToastAboveKeyboard
import androidx.compose.material3.TextField
import org.futo.voiceinput.shared.groq.GroqChatApi
import android.util.Log
import org.futo.inputmethod.latin.uix.utils.latestClipboardText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.futo.inputmethod.latin.uix.theme.LocalKeyboardScheme

private const val DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant that writes concise replies."

private class AiReplyWindow(
    val manager: KeyboardManagerForAction,
    val text: String
) : ActionWindow() {
    override val fixedWindowHeight: Dp = 350.dp
    
    @Composable
    override fun windowName(): String = stringResource(R.string.action_ai_reply_title)

    @Composable
    override fun WindowContents(keyboardShown: Boolean) {
        val context = LocalContext.current
        val reply = remember { mutableStateOf<String?>(null) }
        val promptItem = useDataStore(AI_REPLY_PROMPT)
        val promptText = remember { mutableStateOf(promptItem.value) }
        val coroutineScope = rememberCoroutineScope()
        val isLoading = remember { mutableStateOf(false) }
        val scrollState = rememberScrollState()
        
        val systemPromptsItem = useDataStore(AI_REPLY_SYSTEM_PROMPTS)
        val activePromptNameItem = useDataStore(AI_REPLY_ACTIVE_PROMPT_NAME)
        val systemPrompts = remember(systemPromptsItem.value) { 
            SystemPromptManager.parsePrompts(systemPromptsItem.value) 
        }
        val selectedPrompt = remember(activePromptNameItem.value, systemPrompts) {
            systemPrompts.find { it.name == activePromptNameItem.value } ?: systemPrompts.firstOrNull()
        }
        
        LaunchedEffect(promptText.value) { promptItem.setValue(promptText.value) }

        fun sendNav(keyCode: Int) {
            manager.sendKeyEvent(keyCode, 0)
        }
        
        // Calculate max height based on keyboard state
        val maxHeight = if (keyboardShown) 0.5f else 0.7f
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Text content with max height and scrolling
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(text)
                Spacer(modifier = Modifier.height(8.dp))
                reply.value?.let { 
                    Text(it) 
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            
            // System prompt selector (compact horizontal chips)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.ai_reply_prompt_title),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    systemPrompts.forEach { prompt ->
                        val isSelected = prompt.name == activePromptNameItem.value
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = if (isSelected) 1.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(999.dp)
                                )
                                .clickable { activePromptNameItem.setValue(prompt.name) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = prompt.name,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Navigation keys row (cursor movement) - full width, evenly spaced large buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val navModifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)

                IconButton(
                    onClick = { sendNav(KeyEvent.KEYCODE_DPAD_LEFT) },
                    modifier = navModifier
                ) {
                    Icon(
                        painter = painterResource(R.drawable.chevron_left),
                        contentDescription = "Left",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { sendNav(KeyEvent.KEYCODE_DPAD_UP) },
                    modifier = navModifier
                ) {
                    Icon(
                        painter = painterResource(R.drawable.chevron_up),
                        contentDescription = "Up",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { sendNav(KeyEvent.KEYCODE_DPAD_DOWN) },
                    modifier = navModifier
                ) {
                    Icon(
                        painter = painterResource(R.drawable.chevron_down),
                        contentDescription = "Down",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { sendNav(KeyEvent.KEYCODE_DPAD_RIGHT) },
                    modifier = navModifier
                ) {
                    Icon(
                        painter = painterResource(R.drawable.chevron_right),
                        contentDescription = "Right",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Editing controls: undo on the left, then select all, copy, paste, backspace, redo
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val editModifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)

                IconButton(
                    onClick = { manager.sendKeyEvent(KeyEvent.KEYCODE_Z, KeyEvent.META_CTRL_ON) },
                    modifier = editModifier
                ) {
                    Icon(
                        painter = painterResource(R.drawable.undo),
                        contentDescription = "Undo",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { manager.sendKeyEvent(KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON) },
                    modifier = editModifier
                ) {
                    Icon(
                        painter = painterResource(R.drawable.maximize),
                        contentDescription = "Select All",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { manager.copyToClipboard(cut = false) },
                    modifier = editModifier
                ) {
                    Icon(
                        painter = painterResource(R.drawable.copy),
                        contentDescription = "Copy",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { manager.pasteFromClipboard() },
                    modifier = editModifier
                ) {
                    Icon(
                        painter = painterResource(R.drawable.clipboard),
                        contentDescription = "Paste",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { manager.backspace(1) },
                    modifier = editModifier
                ) {
                    Icon(
                        painter = painterResource(R.drawable.delete),
                        contentDescription = "Backspace",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { manager.sendKeyEvent(KeyEvent.KEYCODE_Y, KeyEvent.META_CTRL_ON) },
                    modifier = editModifier
                ) {
                    Icon(
                        painter = painterResource(R.drawable.redo),
                        contentDescription = "Redo",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Fixed height prompt input
            TextField(
                value = promptText.value,
                onValueChange = { promptText.value = it },
                placeholder = { Text(stringResource(R.string.ai_reply_prompt_placeholder)) },
                modifier = Modifier.fillMaxWidth()
            )
            
            // Button row for Generate and Insert actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Generate button
                Button(
                    onClick = {
                        val apiKey = context.getSetting(GROQ_REPLY_API_KEY)
                        val model = context.getSetting(GROQ_REPLY_MODEL)
                        
                        if (apiKey.isBlank()) {
                            context.showToastAboveKeyboard(
                                context.getString(R.string.groq_api_key_required),
                                Toast.LENGTH_LONG
                            )
                            return@Button
                        }
                        
                        isLoading.value = true
                        reply.value = null
                        
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                withContext(Dispatchers.Main) { reply.value = "" }
                                val systemPrompt = selectedPrompt?.prompt ?: DEFAULT_SYSTEM_PROMPT
                                val userPrompt = buildString {
                                    if (promptText.value.isNotBlank()) append(promptText.value).append('\n')
                                    append(text)
                                }
                                
                                Log.d("AiReplyAction", "Starting chat completion with model: $model")
                                val response = GroqChatApi.chat(
                                    systemPrompt = systemPrompt,
                                    userPrompt = userPrompt,
                                    apiKey = apiKey,
                                    model = model
                                )
                                
                                response?.let { generatedText ->
                                    withContext(Dispatchers.Main) {
                                        reply.value = generatedText
                                        Log.d("AiReplyAction", "Successfully generated reply")
                                    }
                                } ?: run {
                                    throw Exception("No response received from Groq API")
                                }
                            } catch (t: Throwable) {
                                Log.e("AiReplyAction", "Error generating reply", t)
                                withContext(Dispatchers.Main) {
                                    val errorMsg = context.getString(R.string.ai_reply_error, t.message ?: context.getString(R.string.unknown_error))
                                    context.showToastAboveKeyboard(errorMsg, Toast.LENGTH_LONG)
                                }
                            } finally {
                                withContext(Dispatchers.Main) {
                                    isLoading.value = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading.value
                ) {
                    if (isLoading.value) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Generating")
                    } else {
                        Text("Generate")
                    }
                }
                
                // Insert button (only shown when there's a reply)
                reply.value?.let { r ->
                    Button(
                        onClick = { 
                            manager.typeText(r)
                            manager.closeActionWindow() 
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Insert")
                    }
                } ?: Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun buildAngles(count: Int): List<Float> {
    if (count <= 1) return listOf(225f)

    val startAngle = 45f
    val sweep = 270f

    return List(count) { index -> startAngle + sweep * index.toFloat() / (count - 1) }
}

@Composable
private fun RadialMenuItem(
    label: String,
    offset: IntOffset,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalKeyboardScheme.current

    Box(
        modifier = Modifier
            .offset { offset }
            .size(42.dp)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(colors.keyboardContainer, colors.keyboardSurfaceDim)
                    ),
                    radius = size.minDimension / 2
                )
            }
            .clip(CircleShape)
            .background(Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = colors.onKeyboardContainer,
            maxLines = 2
        )
    }
}

@Composable
private fun RadialMenu(
    prompts: List<SystemPrompt>,
    isGenerating: Boolean,
    onSelect: (SystemPrompt) -> Unit
) {
    val density = LocalDensity.current
    val radiusPx = with(density) { 110.dp.toPx() }

    val angles = remember(prompts) { buildAngles(prompts.size) }

    Box(
        modifier = Modifier
            .size(220.dp)
            .semantics { testTag = "AiReplyRadialMenu" },
        contentAlignment = Alignment.Center
    ) {
        prompts.forEachIndexed { index, prompt ->
            val angleRad = angles[index] * PI.toFloat() / 180f
            val x = cos(angleRad) * radiusPx
            val y = -sin(angleRad) * radiusPx
            RadialMenuItem(
                label = prompt.name,
                offset = IntOffset(x.roundToInt(), y.roundToInt()),
                enabled = !isGenerating
            ) {
                onSelect(prompt)
            }
        }

        val colors = LocalKeyboardScheme.current
        Box(
            modifier = Modifier
                .size(84.dp)
                .drawBehind {
                    drawCircle(color = colors.keyboardContainer)
                }
                .clip(CircleShape)
                .background(Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.text_prediction),
                contentDescription = stringResource(id = R.string.action_ai_reply_title),
                tint = colors.onKeyboardContainer
            )
        }
    }
}

private class AiReplyRadialWindow(
    private val manager: KeyboardManagerForAction
) : ActionWindow() {
    override val showHeaderBar: Boolean = false
    override val onlyShowAboveKeyboard: Boolean = true

    @Composable
    override fun windowName(): String = stringResource(R.string.action_ai_reply_title)

    @Composable
    override fun WindowContents(keyboardShown: Boolean) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }
        val promptItem = useDataStore(AI_REPLY_PROMPT)
        val systemPromptsItem = useDataStore(AI_REPLY_SYSTEM_PROMPTS)
        val prompts = remember(systemPromptsItem.value) {
            SystemPromptManager.parsePrompts(systemPromptsItem.value)
        }
        val isGenerating = remember { mutableStateOf(false) }
        val seedText = remember {
            mutableStateOf(
                AiReplyActionHolder.pendingText.takeIf { it.isNotBlank() }
                    ?: latestClipboardText(context)
                    ?: ""
            )
        }

        LaunchedEffect(Unit) {
            AiReplyActionHolder.pendingText = ""
        }

        fun generate(prompt: SystemPrompt) {
            if (isGenerating.value) return

            val apiKey = context.getSetting(GROQ_REPLY_API_KEY)
            val model = context.getSetting(GROQ_REPLY_MODEL)
            val baseText = seedText.value

            if (apiKey.isBlank()) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.groq_api_key_required))
                }
                return
            }

            if (baseText.isBlank()) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.ai_reply_prompt_placeholder))
                }
                return
            }

            isGenerating.value = true

            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val userPrompt = buildString {
                        if (promptItem.value.isNotBlank()) append(promptItem.value).append('\n')
                        append(baseText)
                    }

                    val response = GroqChatApi.chat(
                        systemPrompt = prompt.prompt,
                        userPrompt = userPrompt,
                        apiKey = apiKey,
                        model = model
                    )

                    if (response != null) {
                        withContext(Dispatchers.Main) {
                            manager.typeText(response)
                            manager.closeActionWindow()
                        }
                    } else {
                        throw Exception("No response received from Groq API")
                    }
                } catch (t: Throwable) {
                    withContext(Dispatchers.Main) {
                        val errorMsg = context.getString(
                            R.string.ai_reply_error,
                            t.message ?: context.getString(R.string.unknown_error)
                        )
                        snackbarHostState.showSnackbar(errorMsg)
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isGenerating.value = false
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { manager.closeActionWindow() })
                }
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .pointerInput(Unit) { detectTapGestures {} },
                contentAlignment = Alignment.Center
            ) {
                RadialMenu(
                    prompts = prompts,
                    isGenerating = isGenerating.value,
                    onSelect = { generate(it) }
                )

                if (isGenerating.value) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            )
        }
    }
}

object AiReplyActionHolder { var pendingText: String = "" }

val AiReplyRadialAction = Action(
    icon = R.drawable.text_prediction,
    name = R.string.action_ai_reply_title,
    simplePressImpl = null,
    windowImpl = { manager, _ ->
        AiReplyRadialWindow(manager)
    },
    shownInEditor = false
)

val AiReplyAction = Action(
    icon = R.drawable.text_prediction,
    name = R.string.action_ai_reply_title,
    simplePressImpl = null,
    windowImpl = { manager, _ ->
        var text = AiReplyActionHolder.pendingText
        if (text.isBlank()) {
            text = latestClipboardText(manager.getContext()) ?: ""
        }
        if (text.isBlank()) {
            text = ""
        }
        AiReplyActionHolder.pendingText = ""
        AiReplyWindow(manager, text)
    },
    altPressImpl = { manager, _ ->
        if (AiReplyActionHolder.pendingText.isBlank()) {
            AiReplyActionHolder.pendingText = latestClipboardText(manager.getContext()) ?: ""
        }
        manager.activateAction(AiReplyRadialAction)
    }
)
