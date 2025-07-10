package org.futo.inputmethod.latin.uix.actions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.uix.DialogRequestItem
// Replaced UixManager with KeyboardManagerForAction as per Action.kt definition
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.pages.ParagraphText
import org.futo.inputmethod.latin.uix.settings.pages.PaymentSurface
import org.futo.inputmethod.latin.uix.settings.pages.PaymentSurfaceHeading
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.theme.Typography
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipboardEntryView(modifier: Modifier, clipboardEntry: ClipboardEntry, searchQuery: String, onPaste: (ClipboardEntry) -> Unit, onRemove: (ClipboardEntry) -> Unit, onPin: (ClipboardEntry) -> Unit) {
    val textToDisplay = clipboardEntry.text ?: ""
    val annotatedText = buildAnnotatedString {
        if (searchQuery.isNotEmpty() && textToDisplay.contains(searchQuery, ignoreCase = true)) {
            var startIndex = 0
            while (startIndex < textToDisplay.length) {
                val indexOfMatch = textToDisplay.indexOf(searchQuery, startIndex, ignoreCase = true)
                if (indexOfMatch == -1) {
                    append(textToDisplay.substring(startIndex))
                    break
                }
                append(textToDisplay.substring(startIndex, indexOfMatch))
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, backgroundColor = Color.Yellow.copy(alpha = 0.5f))) {
                    append(textToDisplay.substring(indexOfMatch, indexOfMatch + searchQuery.length))
                }
                startIndex = indexOfMatch + searchQuery.length
            }
        } else {
            append(textToDisplay)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .padding(2.dp)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(),
                enabled = true,
                onClick = { onPaste(clipboardEntry) },
                onLongClick = { onPin(clipboardEntry) }
            ),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column {
            Row(modifier = Modifier.padding(0.dp)) {
                IconButton(onClick = {
                    onPin(clipboardEntry)
                }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        painterResource(id = R.drawable.push_pin),
                        contentDescription = if(clipboardEntry.pinned) {
                            stringResource(R.string.action_clipboard_manager_unpin_item)
                        } else {
                            stringResource(R.string.action_clipboard_manager_pin_item)
                        },
                        tint = if(clipboardEntry.pinned) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1.0f))

                IconButton(onClick = {
                    onRemove(clipboardEntry)
                }, modifier = Modifier.size(32.dp), enabled = !clipboardEntry.pinned) {
                    Icon(
                        painterResource(id = R.drawable.close),
                        contentDescription = stringResource(R.string.action_clipboard_manager_remove_item),
                        tint = if(clipboardEntry.pinned) {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            val displayedText = if (annotatedText.text.length > 256) {
                buildAnnotatedString {
                    append(annotatedText.subSequence(0, 256))
                    append("...")
                }
            } else {
                annotatedText
            }

            Text(displayedText, modifier = Modifier.padding(8.dp, 2.dp), style = Typography.SmallMl)

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ClipboardEntryViewPreview() {
    val sampleText = listOf("This is an entry with searchterm", "Copying text a lot", "searchterm here too", "https://www.example.com/forum/viewpost/1234573193.html?parameter=1234")
    val searchQuery = "searchterm"
    LazyVerticalStaggeredGrid(
        modifier = Modifier.fillMaxWidth(),
        columns = StaggeredGridCells.Adaptive(160.dp),
        verticalItemSpacing = 4.dp,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(sampleText.size) {
            ClipboardEntryView(
                modifier = Modifier,
                clipboardEntry = ClipboardEntry(0L, it % 2 == 0, sampleText[it], null, listOf()),
                searchQuery = searchQuery,
                onPin = {},
                onPaste = {},
                onRemove = {}
            )
        }
    }
}

@Composable
fun ClipboardHistoryWindowTitleBar(
    rowScope: RowScope,
    manager: KeyboardManagerForAction, // Changed UixManager to KeyboardManagerForAction
    clipboardHistoryManager: ClipboardHistoryManager,
    unlocked: Boolean
) {
    val context = LocalContext.current
    val clipboardHistoryEnabledState = useDataStore(ClipboardHistoryEnabled, blocking = true)

    if (!clipboardHistoryEnabledState.value) return

    if (unlocked && !clipboardHistoryManager.clipboardIOFailure.value) {
        IconButton(onClick = {
            val numUnpinnedItems = clipboardHistoryManager.clipboardHistory.count { !it.pinned }
            if (clipboardHistoryManager.clipboardHistory.isEmpty()) {
                manager.requestDialog(
                    context.getString(R.string.action_clipboard_manager_disable_text),
                    listOf(
                        DialogRequestItem(context.getString(R.string.action_clipboard_manager_cancel_action_button)) {},
                        DialogRequestItem(context.getString(R.string.action_clipboard_manager_disable_button)) {
                            clipboardHistoryEnabledState.setValue(false)
                        },
                    ),
                    {}
                )
            } else if (numUnpinnedItems == 0) {
                manager.requestDialog(
                    context.getString(R.string.action_clipboard_manager_unpin_all_items_text),
                    listOf(
                        DialogRequestItem(context.getString(R.string.action_clipboard_manager_cancel_action_button)) {},
                        DialogRequestItem(context.getString(R.string.action_clipboard_manager_unpin_all_items_button)) {
                            clipboardHistoryManager.clipboardHistory.toList().forEach {
                                if (it.pinned) {
                                    clipboardHistoryManager.onPin(it)
                                }
                            }
                        },
                    ),
                    {}
                )
            } else {
                manager.requestDialog(
                    context.getString(R.string.action_clipboard_manager_clear_unpinned_items_text),
                    listOf(
                        DialogRequestItem(context.getString(R.string.action_clipboard_manager_cancel_action_button)) {},
                        DialogRequestItem(context.getString(R.string.action_clipboard_manager_clear_unpinned_items_button)) {
                            clipboardHistoryManager.clipboardHistory.toList().forEach {
                                if (!it.pinned) {
                                    clipboardHistoryManager.onRemove(it)
                                }
                            }
                        },
                    ),
                    {}
                )
            }
        }) {
            Icon(
                painterResource(id = R.drawable.close),
                contentDescription = stringResource(R.string.action_clipboard_manager_clear_clipboard)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipboardHistoryWindowContent(
    manager: KeyboardManagerForAction, // Changed UixManager to KeyboardManagerForAction
    clipboardHistoryManager: ClipboardHistoryManager,
    unlocked: Boolean,
    keyboardShown: Boolean
) {
    val view = LocalView.current
    val context = LocalContext.current
    val clipboardHistoryEnabledState = useDataStore(ClipboardHistoryEnabled, blocking = true)
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text(stringResource(R.string.search_clipboard_history_label)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        )

        if (!unlocked) {
            ScrollableList {
                PaymentSurface(isPrimary = true) {
                PaymentSurfaceHeading(title = stringResource(R.string.action_clipboard_manager_error_device_locked_title))
                ParagraphText(stringResource(R.string.action_clipboard_manager_error_device_locked_text))
            }
        }
    } else if (clipboardHistoryManager.clipboardIOFailure.value) {
        ScrollableList {
            PaymentSurface(isPrimary = true) {
                PaymentSurfaceHeading(title = stringResource(R.string.action_clipboard_manager_error_general_title))
                ParagraphText(
                    stringResource(
                        R.string.action_clipboard_manager_error_general_text,
                        clipboardHistoryManager.clipboardIOFailureReason
                    )
                )
                Button(onClick = {
                    manager.activateAction(BugViewerAction)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_clipboard_manager_inspect_error_via_bugs_action))
                }
                Button(onClick = {
                    clipboardHistoryManager.saveClipboard()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_clipboard_manager_retry_saving_loading))
                }
                Button(onClick = {
                    manager.requestDialog(
                        context.getString(R.string.action_clipboard_manager_delete_corrupted_clipboard_text),
                        listOf(
                            DialogRequestItem(context.getString(R.string.action_clipboard_manager_cancel_action_button)) {},
                            DialogRequestItem(context.getString(R.string.action_clipboard_manager_delete_corrupted_clipboard_button)) {
                                clipboardHistoryManager.clipboardIOFailure.value = false
                                clipboardHistoryEnabledState.setValue(false)
                                clipboardHistoryManager.deleteClipboard()
                            },
                        ),
                        {}
                    )
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(context.getString(R.string.action_clipboard_manager_delete_corrupted_clipboard_button))
                }
            }
        }
    } else if (!clipboardHistoryEnabledState.value) {
        ScrollableList {
            PaymentSurface(isPrimary = true) {
                PaymentSurfaceHeading(title = stringResource(R.string.action_clipboard_manager_error_clipboard_history_disabled_title))
                ParagraphText(
                    stringResource(
                        R.string.action_clipboard_manager_error_clipboard_history_disabled_text_v2,
                        context.getSetting(ClipboardHistoryItemsToKeep),
                        (context.getSetting(ClipboardHistoryTimeToKeep) / 24.0f).roundToInt()
                    )
                )
                Button(onClick = {
                    clipboardHistoryEnabledState.setValue(true)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_clipboard_manager_enable_clipboard_history_button))
                }
            }
        }
    } else {
        val filteredList = if (searchQuery.isBlank()) {
            clipboardHistoryManager.clipboardHistory.toList() // Use a copy for stability during recomposition
        } else {
            clipboardHistoryManager.clipboardHistory.filter {
                it.text?.contains(searchQuery, ignoreCase = true) == true
            }
        }

        LazyVerticalStaggeredGrid(
            modifier = Modifier.fillMaxWidth(),
            columns = StaggeredGridCells.Adaptive(140.dp),
            verticalItemSpacing = 4.dp,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(
                items = filteredList.reversed(), // Show newest first
                key = { entry ->
                    // Ensure unique keys, especially if text can be null or identical
                    (entry.text ?: "") + entry.timestamp.toString()
                }
            ) { entry ->
                ClipboardEntryView(
                    modifier = Modifier.animateItemPlacement(),
                    clipboardEntry = entry,
                    searchQuery = searchQuery, // Pass the search query for highlighting
                    onPaste = {
                        if (it.uri != null) {
                            manager.typeUri(it.uri, it.mimeTypes)
                        } else if (it.text != null) {
                            manager.typeText(it.text)
                        }
                        clipboardHistoryManager.onPaste(it)
                        manager.performHapticAndAudioFeedback(Constants.CODE_OUTPUT_TEXT, view)
                    },
                    onRemove = {
                        clipboardHistoryManager.onRemove(it)
                        manager.performHapticAndAudioFeedback(Constants.CODE_TAB, view)
                    },
                    onPin = {
                        clipboardHistoryManager.onPin(it)
                        manager.performHapticAndAudioFeedback(Constants.CODE_TAB, view)
                    }
                )
            }
        }
    }
}
