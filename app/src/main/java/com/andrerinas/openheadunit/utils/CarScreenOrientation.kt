package com.andrerinas.openheadunit.utils

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.Point
import android.os.Build

/**
 * Andromi Auto: portrait or landscape from the size of the screen area the app is shown in, not
 * from the phone's rotation. On the Xiaomi car screen the phone never rotates; only the area
 * changes: size 1 is tall (portrait), sizes 2 and 3 are wide (landscape). Open Headunit already
 * adapts Android Auto to the screen once the orientation is right, so this only picks it.
 */
object CarScreenOrientation {

    private var lastLogged: String? = null

    /**
     * Width and height of the screen area this activity is shown in. The screen (display) itself
     * when the app has it to itself, as on the car's projection screen; the window's largest size
     * when it shares the screen (split screen / free windows). Never the activity's own current
     * bounds: once portrait is requested, a wide screen shows the app in a tall box, and reading
     * that box would keep it portrait forever.
     */
    fun areaSize(activity: Activity): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= 24 && activity.isInMultiWindowMode && Build.VERSION.SDK_INT >= 30) {
            val b = activity.windowManager.maximumWindowMetrics.bounds
            if (b.width() > 0 && b.height() > 0) return b.width() to b.height()
        }
        val display = if (Build.VERSION.SDK_INT >= 30) try { activity.display } catch (e: Exception) { null } else null
        @Suppress("DEPRECATION")
        val d = display ?: activity.windowManager.defaultDisplay
        val p = Point()
        @Suppress("DEPRECATION")
        d.getRealSize(p)
        if (p.x > 0 && p.y > 0) return p.x to p.y
        val c = activity.resources.configuration
        return c.screenWidthDp to c.screenHeightDp
    }

    fun isLandscape(activity: Activity): Boolean {
        val (w, h) = areaSize(activity)
        return w > h
    }

    /** The orientation to request: portrait for a tall area (size 1), landscape for a wide one. */
    fun requested(activity: Activity): Int {
        val (w, h) = areaSize(activity)
        val landscape = w > h
        val line = "${w}x$h -> ${if (landscape) "landscape" else "portrait"}"
        if (line != lastLogged) {
            lastLogged = line
            AppLog.i("CarScreenOrientation: screen area $line")
        }
        return if (landscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}
