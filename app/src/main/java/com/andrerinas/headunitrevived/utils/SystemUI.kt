package com.andrerinas.headunitrevived.utils

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager

object SystemUI {

    fun apply(window: Window, root: View, fullscreen: Boolean) {
        // Always keep screen on for Headunit functionality
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val params = window.attributes
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = params
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController
            if (controller != null) {
                if (fullscreen) {
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    controller.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                }
            }
        } else {
            // Legacy Flags
            @Suppress("DEPRECATION")
            if (fullscreen) {
                window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
            } else {
                // Clear flags to show bars. VISIBLE is 0.
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }

        // Manual Inset Handling for Edge-to-Edge (Android 15+)
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (fullscreen) {
                v.setPadding(0, 0, 0, 0)
            } else {
                val left: Int
                val top: Int
                val right: Int
                val bottom: Int

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    left = bars.left
                    top = bars.top
                    right = bars.right
                    bottom = bars.bottom
                } else {
                    left = insets.systemWindowInsetLeft
                    top = insets.systemWindowInsetTop
                    right = insets.systemWindowInsetRight
                    bottom = insets.systemWindowInsetBottom
                }
                v.setPadding(left, top, right, bottom)
            }
            insets
        }
        
        root.requestApplyInsets()
    }
    
    // Compatibility method for SurfaceActivity (if we keep it calling hide)
    // But we plan to remove the call in SurfaceActivity.
    fun hide(view: View) {
        // No-op or delegate?
        // We will remove usages in SurfaceActivity.
    }
}
