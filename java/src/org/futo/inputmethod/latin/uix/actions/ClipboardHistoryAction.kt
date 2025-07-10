package org.futo.inputmethod.latin.uix.actions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.animateItemPlacement
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.DialogRequestItem
import org.futo.inputmethod.latin.uix.PersistentStateInitialization
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingSlider
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.UserSetting
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.pages.ParagraphText
import org.futo.inputmethod.latin.uix.settings.pages.PaymentSurface
import org.futo.inputmethod.latin.uix.settings.pages.PaymentSurfaceHeading
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
val ClipboardHistoryAction = Action(
    icon = R.drawable.clipboard_manager,
    name = R.string.action_clipboard_manager_title,
    simplePressImpl = null,
    canShowKeyboard = true,
    persistentState = { manager ->
        ClipboardHistoryManager(manager.getContext(), manager.getLifecycleScope())
    },
    persistentStateInitialization = PersistentStateInitialization.OnKeyboardLoad,
    windowImpl = { manager, persistent ->
        val unlocked = !manager.isDeviceLocked()
        val clipboardHistoryManager = persistent as ClipboardHistoryManager

        manager.getLifecycleScope().launch { clipboardHistoryManager.pruneOldItems() }
        object : ActionWindow() {
            @Composable
            override fun windowName(): String =
                stringResource(R.string.action_clipboard_manager_title)

            @Composable
            override fun WindowTitleBar(rowScope: RowScope) {
                super.WindowTitleBar(rowScope)
                val context = LocalContext.current
                val clipboardHistory = useDataStore(ClipboardHistoryEnabled, blocking = true)
                if (!clipboardHistory.value) return

                if (unlocked && !clipboardHistoryManager.clipboardIOFailure.value) {
                    IconButton(onClick = {
                        val numUnpinnedItems =
                            clipboardHistoryManager.clipboardHistory.count { !it.pinned }
                        if (clipboardHistoryManager.clipboardHistory.isEmpty()) {
                            manager.requestDialog(
                                context.getString(R.string.action_clipboard_manager_disable_text),
                                listOf(
                                    DialogRequestItem(context.getString(R.string.action_clipboard_manager_cancel_action_button)) {},
                                    DialogRequestItem(context.getString(R.string.action_clipboard_manager_disable_button)) {
                                        clipboardHistory.setValue(false)
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

            @Composable
            override fun WindowContents(keyboardShown: Boolean) {
                val view = LocalView.current
                val context = LocalContext.current
                val clipboardHistory = useDataStore(ClipboardHistoryEnabled, blocking = true)
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
                            Button(onClick = { manager.activateAction(BugViewerAction) }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.action_clipboard_manager_inspect_error_via_bugs_action))
                            }
                            Button(onClick = { clipboardHistoryManager.saveClipboard() }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.action_clipboard_manager_retry_saving_loading))
                            }
                            Button(
                                onClick = {
                                    manager.requestDialog(
                                        context.getString(R.string.action_clipboard_manager_delete_corrupted_clipboard_text),
                                        listOf(
                                            DialogRequestItem(context.getString(R.string.action_clipboard_manager_cancel_action_button)) {},
                                            DialogRequestItem(context.getString(R.string.action_clipboard_manager_delete_corrupted_clipboard_button)) {
                                                clipboardHistoryManager.clipboardIOFailure.value = false
                                                clipboardHistory.setValue(false)
                                                clipboardHistoryManager.deleteClipboard()
                                            },
                                        ),
                                        {}
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(context.getString(R.string.action_clipboard_manager_delete_corrupted_clipboard_button))
                            }
                        }
                    }
                } else if (!clipboardHistory.value) {
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
                            Button(
                                onClick = { clipboardHistory.setValue(true) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.action_clipboard_manager_enable_clipboard_history_button))
                            }
                        }
                    }
                } else {
                    LazyVerticalStaggeredGrid(
                        modifier = Modifier.fillMaxWidth(),
                        columns = StaggeredGridCells.Adaptive(140.dp),
                        verticalItemSpacing = 4.dp,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                    ) {
                        items(clipboardHistoryManager.clipboardHistory.size, key = { r_i ->
                            val i = clipboardHistoryManager.clipboardHistory.size - r_i - 1
                            val entry = clipboardHistoryManager.clipboardHistory[i]
                            entry.text?.let {
                                if (it.length > 512) {
                                    it.toFNV1aHash()
                                } else {
                                    it
                                }
                            } ?: i
                            i
                        }) { r_i ->
                            val i = clipboardHistoryManager.clipboardHistory.size - r_i - 1
                            val entry = clipboardHistoryManager.clipboardHistory[i]
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
        }
    },
    settingsMenu = UserSettingsMenu(
        title = R.string.action_clipboard_manager_settings_title,
        navPath = "actions/clipboard_history",
        registerNavPath = true,
        settings = listOf(
            UserSetting(
                name = R.string.typing_settings_enable_clipboard_history,
                component = {
                    SettingToggleDataStore(
                        title = stringResource(R.string.typing_settings_enable_clipboard_history),
                        setting = ClipboardHistoryEnabled
                    )
                }
            ).copy(searchTags = R.string.typing_settings_enable_clipboard_history_tags),
            UserSetting(
                name = R.string.action_clipboard_manager_settings_maximum_clips,
                component = {
                    SettingSlider(
                        stringResource(R.string.action_clipboard_manager_settings_maximum_clips),
                        ClipboardHistoryItemsToKeep,
                        range = 0.0f..100.0f,
                        hardRange = 0.0f..Float.POSITIVE_INFINITY,
                        transform = { it.toInt() },
                    )
                },
                visibilityCheck = { useDataStoreValue(ClipboardHistoryEnabled) }
            ),
            UserSetting(
                name = R.string.action_clipboard_manager_settings_hours_to_keep_clips,
                component = {
                    SettingSlider(
                        stringResource(R.string.action_clipboard_manager_settings_hours_to_keep_clips),
                        ClipboardHistoryTimeToKeep,
                        range = 1.0f..336.0f,
                        hardRange = 0.0f..Float.POSITIVE_INFINITY,
                        transform = { it.toInt() },
                    )
                },
                visibilityCheck = { useDataStoreValue(ClipboardHistoryEnabled) }
            ),
            UserSetting(
                name = R.string.action_clipboard_manager_settings_save_sensitive_clips,
                subtitle = R.string.action_clipboard_manager_settings_save_sensitive_clips_subtitle,
                component = {
                    SettingToggleDataStore(
                        title = stringResource(R.string.action_clipboard_manager_settings_save_sensitive_clips),
                        subtitle = stringResource(R.string.action_clipboard_manager_settings_save_sensitive_clips_subtitle),
                        setting = ClipboardHistorySaveSensitive
                    )
                },
                visibilityCheck = { useDataStoreValue(ClipboardHistoryEnabled) }
            ),
        )
    )
)
