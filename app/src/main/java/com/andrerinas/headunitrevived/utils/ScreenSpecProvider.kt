package com.andrerinas.headunitrevived.utils

import android.content.Context
import com.andrerinas.headunitrevived.App
import kotlin.math.roundToInt

object ScreenSpecProvider {

    private const val MAX_HEIGHT = 1080

    fun getSpec(context: Context): ScreenSpec {
        val settings = App.provide(context).settings
        val displayMetrics = context.resources.displayMetrics
        val density = displayMetrics.density
        val densityDpi = displayMetrics.densityDpi

        val resolutionSetting = Settings.Resolution.fromId(settings.resolutionId)
            ?: Settings.Resolution.AUTO

        var width: Int
        var height: Int

        if (resolutionSetting == Settings.Resolution.AUTO) {
            // Auto mode: calculate based on screen density
            AppLog.i("[ScreenSpecProvider] Auto resolution selected. Calculating from display metrics.")
            width = (displayMetrics.widthPixels / density).roundToInt()
            height = (displayMetrics.heightPixels / density).roundToInt()
        } else {
            // User has selected a specific resolution
            AppLog.i("[ScreenSpecProvider] User resolution selected: ${resolutionSetting.resName}")
            width = resolutionSetting.width
            height = resolutionSetting.height
        }

        // Apply global constraints, moving logic from VideoDecoder
        if (height > MAX_HEIGHT) {
            AppLog.w("[ScreenSpecProvider] Calculated height ($height) exceeds max ($MAX_HEIGHT). Capping height.")
            height = MAX_HEIGHT
        }

        val finalSpec = ScreenSpec(width, height, densityDpi)
        AppLog.i("[ScreenSpecProvider] Final negotiated spec: $finalSpec")
        return finalSpec
    }
}
