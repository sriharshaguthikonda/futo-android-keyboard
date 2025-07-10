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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import org.futo.inputmethod.latin.uix.UixManager
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.pages.ParagraphText
import org.futo.inputmethod.latin.uix.settings.pages.PaymentSurface
import org.futo.inputmethod.latin.uix.settings.pages.PaymentSurfaceHeading
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.theme.Typography
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipboardEntryView(modifier: Modifier, clipboardEntry: ClipboardEntry, onPaste: (ClipboardEntry) -> Unit, onRemove: (ClipboardEntry) -> Unit, onPin: (ClipboardEntry) -> Unit) {
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

            val text = (clipboardEntry.text ?: "").let {
                if(it.length > 256) {
                    it.substring(0, 256) + "..."
                } else {
                    it
                }
            }

            Text(text, modifier = Modifier.padding(8.dp, 2.dp), style = Typography.SmallMl)

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ClipboardEntryViewPreview() {
    val sampleText = listOf("This is an entry", "Copying text a lot", "hunter2", "https://www.example.com/forum/viewpost/1234573193.html?parameter=1234")
    LazyVerticalStaggeredGrid(
        modifier = Modifier.fillMaxWidth(),
        columns = StaggeredGridCells.Adaptive(160.dp),
        verticalItemSpacing = 4.dp,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(sampleText.size) {
            ClipboardEntryView(modifier = Modifier, clipboardEntry = ClipboardEntry(0L, it % 2 == 0, sampleText[it], null, listOf()), onPin = {}, onPaste = {}, onRemove = {})
        }
    }
}

@Composable
fun ClipboardHistoryWindowTitleBar(
    rowScope: RowScope,
    manager: UixManager,
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
    manager: UixManager,
    clipboardHistoryManager: ClipboardHistoryManager,
    unlocked: Boolean,
    keyboardShown: Boolean // keyboardShown might not be used here, but good to keep if original logic depended on it
) {
    val view = LocalView.current
    val context = LocalContext.current
    val clipboardHistoryEnabledState = useDataStore(ClipboardHistoryEnabled, blocking = true)

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
        LazyVerticalStaggeredGrid(
            modifier = Modifier.fillMaxWidth(),
            columns = StaggeredGridCells.Adaptive(140.dp),
            verticalItemSpacing = 4.dp,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(
                items = clipboardHistoryManager.clipboardHistory.reversed(),
                key = { entry ->
                entry.text?.let {
                    if(it.length > 512) {
                        // Compose really doesn't like extremely long keys, so
                        // to avoid crashing we just provide a hash
                        it.toFNV1aHash()
                    } else {
                        it
                    }
                } ?: entry.timestamp // Fallback key if text is null
            }) { entry ->
                ClipboardEntryView(
                    modifier = Modifier.animateItemPlacement(),
                    clipboardEntry = entry,
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
