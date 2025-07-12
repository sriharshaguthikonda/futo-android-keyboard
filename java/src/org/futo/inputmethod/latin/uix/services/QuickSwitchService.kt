package org.futo.inputmethod.latin.uix.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents

class QuickSwitchService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun switchToPreviousApp() {
        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val begin = end - 60_000 // look back up to a minute
        val events = usm.queryEvents(begin, end)
        var lastPackage: String? = null
        var prevPackage: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                prevPackage = lastPackage
                lastPackage = event.packageName
            }
        }

        val pkgToLaunch = prevPackage
        if (pkgToLaunch != null && pkgToLaunch != packageName) {
            val intent = packageManager.getLaunchIntentForPackage(pkgToLaunch)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return
            }
        }

        // Fallback to recents screen if no previous app was detected
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    companion object {
        var instance: QuickSwitchService? = null
    }
}
