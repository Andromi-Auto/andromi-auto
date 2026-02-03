package com.andrerinas.headunitrevived.utils

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object SystemUI {

    fun apply(window: Window, root: View, fullscreen: Boolean) {
        // Always keep screen on for Headunit functionality
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val params = window.attributes
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = params
        }

        // Handle Translucent flags for older APIs (19-29)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (fullscreen) {
                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            }
        }

        // Toggle fitsSystemWindows programmatically.
        root.fitsSystemWindows = !fullscreen

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
            // Legacy Flags (KitKat API 19 and above)
            @Suppress("DEPRECATION")
            if (fullscreen) {
                window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
            } else {
                // Reset to visible and clear all layout flags to ensure content stays inside bars
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }

        // Manual Inset Handling
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insetsCompat ->
            if (fullscreen) {
                v.setPadding(0, 0, 0, 0)
                HeadUnitScreenConfig.updateInsets(0, 0, 0, 0)
            } else {
                val bars = insetsCompat.getInsets(WindowInsetsCompat.Type.systemBars())
                // Only apply padding if the system actually provides it
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                HeadUnitScreenConfig.updateInsets(bars.left, bars.top, bars.right, bars.bottom)
            }
            insetsCompat
        }

        ViewCompat.requestApplyInsets(root)
        root.requestLayout()
    }
}
