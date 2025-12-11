package org.futo.inputmethod.latin.uix.actions

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
// Removed mutableStateListOf, mutableStateOf, remember as they were primarily for ClipboardHistoryManager state
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.PersistentStateInitialization
import org.futo.inputmethod.latin.uix.SettingsExporter
import org.futo.inputmethod.latin.uix.settings.NavigationItem
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.UserSetting
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.SettingSlider
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import org.futo.inputmethod.latin.uix.settings.userSettingToggleDataStore
import org.futo.inputmethod.latin.uix.theme.Typography
// Explicit imports for ClipboardHistoryData symbols
import org.futo.inputmethod.latin.uix.actions.ClipboardHistoryEnabled
import org.futo.inputmethod.latin.uix.actions.ClipboardHistoryItemsToKeep
import org.futo.inputmethod.latin.uix.actions.ClipboardHistoryTimeToKeep
import org.futo.inputmethod.latin.uix.actions.ClipboardHistorySaveSensitive
import org.futo.inputmethod.latin.uix.actions.ClipboardShowPinnedOnTop
import org.futo.inputmethod.latin.uix.actions.ClipboardSingleColumn
import org.futo.inputmethod.latin.uix.actions.ClipboardQuickClipsEnabled
// Explicit import for ClipboardHistoryManager
import org.futo.inputmethod.latin.uix.actions.ClipboardHistoryManager
// Explicit imports for ClipboardHistoryComposables symbols
import org.futo.inputmethod.latin.uix.actions.ClipboardHistoryWindowContent
import org.futo.inputmethod.latin.uix.actions.ClipboardHistoryWindowTitleBar
// Required for persistentState lambda
import org.futo.inputmethod.latin.uix.UixManager
import kotlin.math.roundToInt

// Data classes, settings keys, and ClipboardHistoryManager are in separate files:
// - ClipboardHistoryData.kt
// - ClipboardHistoryManager.kt
// - ClipboardHistoryComposables.kt

fun String.toFNV1aHash(): Long {
    val fnvPrime: Long = 1099511628211L
    var hash: Long = -3750763034362895579L

    for (byte in this.toByteArray()) {
        hash = hash xor byte.toLong()
        hash *= fnvPrime
    }

    return hash
}

val ClipboardHistoryAction = Action(
    icon = R.drawable.clipboard_manager,
    name = R.string.action_clipboard_manager_title,
    simplePressImpl = null,
    canShowKeyboard = true,
    persistentState = { manager ->
        ClipboardHistoryManager(manager.getContext(), manager.getLifecycleScope())
    },
    altPressImpl = PasteAction.simplePressImpl,
    persistentStateInitialization = PersistentStateInitialization.OnKeyboardLoad,
    windowImpl = { manager, persistent ->
        val unlocked = !manager.isDeviceLocked()
        val clipboardHistoryManager = persistent as ClipboardHistoryManager

        // This launch is fine here as it's part of the action's window logic
        manager.getLifecycleScope().launch { clipboardHistoryManager.pruneOldItems() }
        object : ActionWindow() {
            @Composable
            override fun windowName(): String {
                return stringResource(R.string.action_clipboard_manager_title)
            }

            @Composable
            override fun WindowTitleBar(rowScope: RowScope) {
                super.WindowTitleBar(rowScope)
                ClipboardHistoryWindowTitleBar(rowScope, manager, clipboardHistoryManager, unlocked)
            }

            @Composable
            override fun WindowContents(keyboardShown: Boolean) {
                ClipboardHistoryWindowContent(manager, clipboardHistoryManager, unlocked, keyboardShown)
            }
        }
    },

    settingsMenu = UserSettingsMenu(
        title = R.string.action_clipboard_manager_settings_title,
        navPath = "actions/clipboard_history",
        registerNavPath = true,
        settings = listOf(
            userSettingToggleDataStore(
                title = R.string.action_clipboard_manager_settings_show_quick_clips,
                setting = ClipboardQuickClipsEnabled
            ),

            userSettingToggleDataStore(
                title = R.string.typing_settings_enable_clipboard_history,
                setting = ClipboardHistoryEnabled
            ).copy(searchTags = R.string.typing_settings_enable_clipboard_history_tags),

            UserSetting(
                name = R.string.action_clipboard_manager_settings_maximum_clips,
                component = {
                    SettingSlider(
                        stringResource(R.string.action_clipboard_manager_settings_maximum_clips),
                        ClipboardHistoryItemsToKeep, // From ClipboardHistoryData.kt
                        range = 0.0f..100.0f,
                        hardRange = 0.0f..Float.POSITIVE_INFINITY,
                        transform = { it.toInt() },
                    )
                },
                visibilityCheck = { useDataStoreValue(ClipboardHistoryEnabled) } // From ClipboardHistoryData.kt
            ),

            UserSetting(
                name = R.string.action_clipboard_manager_settings_hours_to_keep_clips,
                component = {
                    SettingSlider(
                        stringResource(R.string.action_clipboard_manager_settings_hours_to_keep_clips),
                        ClipboardHistoryTimeToKeep, // From ClipboardHistoryData.kt
                        range = 1.0f..336.0f,
                        hardRange = 0.0f..Float.POSITIVE_INFINITY,
                        transform = { it.toInt() },
                    )
                },
                visibilityCheck = { useDataStoreValue(ClipboardHistoryEnabled) } // From ClipboardHistoryData.kt
            ),

            UserSetting(
                name = R.string.action_clipboard_manager_settings_save_sensitive_clips,
                subtitle = R.string.action_clipboard_manager_settings_save_sensitive_clips_subtitle,
                component = {
                    SettingToggleDataStore(
                        title = stringResource(R.string.action_clipboard_manager_settings_save_sensitive_clips),
                        subtitle = stringResource(R.string.action_clipboard_manager_settings_save_sensitive_clips_subtitle),
                        setting = ClipboardHistorySaveSensitive // From ClipboardHistoryData.kt
                    )
                },
                visibilityCheck = { useDataStoreValue(ClipboardHistoryEnabled) } // From ClipboardHistoryData.kt
            ),

            userSettingToggleDataStore(
                title = R.string.action_clipboard_manager_settings_show_pinned_above_others,
                setting = ClipboardShowPinnedOnTop
            ).copy(visibilityCheck = { useDataStoreValue(ClipboardHistoryEnabled) }),

            userSettingToggleDataStore(
                title = R.string.action_clipboard_manager_settings_list_layout,
                setting = ClipboardSingleColumn
            ).copy(visibilityCheck = { useDataStoreValue(ClipboardHistoryEnabled) }),

            UserSetting(
                name = R.string.action_clipboard_manager_settings_export_pinned,
                component = {
                    val context = LocalContext.current
                    NavigationItem(
                        title = stringResource(R.string.action_clipboard_manager_settings_export_pinned),
                        style = NavigationItemStyle.MiscNoArrow,
                        navigate = {
                            SettingsExporter.triggerExportPinnedClipboard(context)
                        }
                    )
                },
                visibilityCheck = { useDataStoreValue(ClipboardHistoryEnabled) }
            ),

            UserSetting(
                name = R.string.action_clipboard_manager_settings_import_pinned,
                component = {
                    val context = LocalContext.current
                    NavigationItem(
                        title = stringResource(R.string.action_clipboard_manager_settings_import_pinned),
                        style = NavigationItemStyle.MiscNoArrow,
                        navigate = {
                            SettingsExporter.triggerImportPinnedClipboard(context)
                        }
                    )
                },
                visibilityCheck = { useDataStoreValue(ClipboardHistoryEnabled) }
            ),
        )
    )
)