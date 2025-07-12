package org.futo.inputmethod.latin.uix.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.content.pm.ApplicationInfo

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
        val begin = end - 300_000 // look back up to 5 minutes
        
        // Get usage stats instead of events for more reliable results
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, end)
        
        if (stats != null && stats.isNotEmpty()) {
            // Sort by last time used (most recent first)
            val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
            
            // Find the previous app (skip keyboard and system apps)
            val currentKeyboardPkg = packageName
            var pkgToLaunch: String? = null
            
            // Look for the second most recent non-keyboard, non-system app
            var foundCurrent = false
            for (stat in sortedStats) {
                val pkg = stat.packageName
                if (pkg == currentKeyboardPkg || isSystemApp(pkg)) {
                    continue
                }
                
                if (!foundCurrent) {
                    // This is likely the current app, skip it
                    foundCurrent = true
                    continue
                }
                
                // This should be the previous app
                pkgToLaunch = pkg
                break
            }
            
            // If we couldn't find a previous app, just take the first non-keyboard app
            if (pkgToLaunch == null) {
                pkgToLaunch = sortedStats.find { 
                    it.packageName != currentKeyboardPkg && !isSystemApp(it.packageName) 
                }?.packageName
            }
            
            if (pkgToLaunch != null) {
                val intent = packageManager.getLaunchIntentForPackage(pkgToLaunch)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent)
                    
                   // In switchToPreviousApp(), replace the single postDelayed with:
                    repeat(3) { attempt ->
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            focusOnInputField()
                        }, 300L * (attempt + 1)) // Try at 300ms, 600ms, 900ms
                    }
                    
                    return
                }
            }
        }

        // Fallback to recents screen if no previous app was detected
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }
    
    private fun focusOnInputField() {
        try {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val inputField = findEditableField(rootNode)
                if (inputField != null) {
                    // Try to focus and show keyboard
                    inputField.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS)
                    
                    // Additional attempt to click the field
                    inputField.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                    
                    // Try to set cursor at the end if there's text
                    val text = inputField.text
                    if (text != null && text.isNotEmpty()) {
                        val bundle = android.os.Bundle()
                        bundle.putInt(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, text.length)
                        bundle.putInt(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, text.length)
                        inputField.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_SELECTION, bundle)
                    }
                }
                rootNode.recycle()
            }
        } catch (e: Exception) {
            // If focusing fails, we can optionally retry after a longer delay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                retryFocusOnInputField()
            }, 1000)
        }
    }
    
    private fun retryFocusOnInputField() {
        try {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val inputField = findEditableField(rootNode)
                inputField?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS)
                inputField?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                rootNode.recycle()
            }
        } catch (e: Exception) {
            // Silent fail on retry
        }
    }
    
    private fun findEditableField(node: android.view.accessibility.AccessibilityNodeInfo): android.view.accessibility.AccessibilityNodeInfo? {
        // Check if current node is editable
        if (node.isEditable && node.isEnabled && node.isVisibleToUser) {
            return node
        }
        
        // Check if it's a common input field by class name
        val className = node.className?.toString()
        if (className != null && (
            className.contains("EditText") ||
            className.contains("TextInputEditText") ||
            className.contains("AutoCompleteTextView") ||
            className.contains("MultiAutoCompleteTextView")
        ) && node.isEnabled && node.isVisibleToUser) {
            return node
        }
        
        // Search through child nodes
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = findEditableField(child)
                if (result != null) {
                    child.recycle()
                    return result
                }
                child.recycle()
            }
        }
        
        return null
    }

    private fun isSystemApp(packageName: String): Boolean {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        var instance: QuickSwitchService? = null
    }
}