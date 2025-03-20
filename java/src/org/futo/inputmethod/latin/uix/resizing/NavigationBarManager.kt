package org.futo.inputmethod.latin // Update the package name if it's different

import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class NavigationBarManager(private val window: Window) {

    private var isNavigationBarVisible: Boolean = true
    private val windowInsetsController: WindowInsetsControllerCompat by lazy {
        WindowCompat.getInsetsController(window, window.decorView)
    }

    /**
     * Updates the visibility of the navigation bar.
     *
     * @param visible If true, shows the navigation bar; if false, hides it. If null, toggles its visibility.
     */
    fun updateNavigationBarVisibility(visible: Boolean? = null) {
        // Determine the new visibility state
        val newVisibility = visible ?: !isNavigationBarVisible

        //apply new visibility
        isNavigationBarVisible = newVisibility

        // Apply the visibility changes to the navigation bar
        if (isNavigationBarVisible) {
            showSystemUI()
        } else {
            hideSystemUI()
        }
    }

    private fun showSystemUI() {
        windowInsetsController.show(WindowInsetsCompat.Type.navigationBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_TOUCH
    }

    private fun hideSystemUI() {
        // Hides the navigation bar and makes it immersive-sticky
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}