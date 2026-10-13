package com.andrerinas.openheadunit.utils

import com.andrerinas.openheadunit.utils.SystemOptimizer.DisplaySizePreset

/**
 * Android Auto's design guide says a head unit MUST offer at least 748x450 dp; a canvas below it
 * gets a reduced layout. This decides the density that goes on the wire.
 */
object AnnouncedDensityPolicy {

    const val HUIG_MIN_WIDTH_DP = 748
    const val HUIG_MIN_HEIGHT_DP = 450
    private const val FALLBACK_DPI = 160

    enum class Reason { AS_STORED, AUTOMATIC, WIDTH_FLOOR, HEIGHT_FLOOR, PORTRAIT }

    data class Decision(val announced: Int, val requested: Int, val reason: Reason)

    /** The highest density that keeps the visible area at or above the floor. */
    fun huigMaxDpi(w: Int, h: Int): Int = minOf(w * 160 / HUIG_MIN_WIDTH_DP, h * 160 / HUIG_MIN_HEIGHT_DP)

    fun canvasDp(px: Int, dpi: Int): Int = if (dpi <= 0) 0 else px * 160 / dpi

    fun decide(stored: Int, preset: DisplaySizePreset, visibleW: Int, visibleH: Int): Decision {
        if (visibleW <= 0 || visibleH <= 0) {
            return if (stored > 0) Decision(stored, stored, Reason.AS_STORED)
            else Decision(FALLBACK_DPI, 0, Reason.AUTOMATIC)
        }
        if (visibleH > visibleW) {
            val dpi = if (stored > 0) stored
            else OnboardingDisplayPolicy.recommendedDpi(preset, visibleW, visibleH, true)
            return Decision(dpi, stored, Reason.PORTRAIT)
        }
        if (stored <= 0) {
            return Decision(
                OnboardingDisplayPolicy.recommendedDpi(preset, visibleW, visibleH, false), 0, Reason.AUTOMATIC
            )
        }
        val max = huigMaxDpi(visibleW, visibleH)
        if (stored <= max) return Decision(stored, stored, Reason.AS_STORED)
        val heightBound = visibleH * 160 / HUIG_MIN_HEIGHT_DP < visibleW * 160 / HUIG_MIN_WIDTH_DP
        return Decision(max, stored, if (heightBound) Reason.HEIGHT_FLOOR else Reason.WIDTH_FLOOR)
    }
}
