package org.futo.inputmethod.latin.uix.actions

import android.widget.Toast
import android.app.AppOpsManager
import android.content.Context
import android.provider.Settings
import android.content.Intent
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.showToastAboveKeyboard
import org.futo.inputmethod.latin.uix.services.QuickSwitchService
import org.futo.inputmethod.latin.uix.ENABLE_SWITCH_APPS
import org.futo.inputmethod.latin.uix.getSettingBlocking

val SwitchAppsAction = Action(
    icon = R.drawable.move,
    name = R.string.action_switch_apps_title,
    simplePressImpl = { manager: KeyboardManagerForAction, _ ->
        if(!manager.getContext().getSettingBlocking(ENABLE_SWITCH_APPS)) return@Action

        val context = manager.getContext()
        val service = QuickSwitchService.instance

        fun usageGranted(): Boolean {
            try {
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                val mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
                return mode == AppOpsManager.MODE_ALLOWED
            } catch (e: Exception) {
                // Fallback: try to actually use the UsageStatsManager
                return try {
                    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
                    val stats = usageStatsManager.queryUsageStats(
                        android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                        System.currentTimeMillis() - 1000 * 60,
                        System.currentTimeMillis()
                    )
                    stats != null && stats.isNotEmpty()
                } catch (ex: Exception) {
                    false
                }
            }
        }

        if (service != null) {
            if (usageGranted()) {
                service.switchToPreviousApp()
            } else {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } else {
            context.showToastAboveKeyboard(
                context.getString(R.string.action_switch_apps_enable_service),
                Toast.LENGTH_SHORT
            )
        }
    },
    windowImpl = null,
)
