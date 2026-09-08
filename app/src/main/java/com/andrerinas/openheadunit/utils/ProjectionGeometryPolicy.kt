package com.andrerinas.openheadunit.utils

import kotlin.math.roundToInt

/**
 * The panel geometry decisions: how far the negotiated video is scaled, what margin that leaves,
 * and what pixel shape the phone should author for. Pure so the ultra-wide cases can be asserted
 * without a device; HeadUnitScreenConfig keeps the Context and DisplayMetrics reading.
 */
object ProjectionGeometryPolicy {

    /** isPortraitScaled is null on a small screen, where recalculate() leaves the previous value standing. */
    data class Fit(
        val scaleFactor: Float,
        val isSmallScreen: Boolean,
        val isPortraitScaled: Boolean?
    )

    fun divideOrOne(numerator: Float, denominator: Float): Float {
        return if (denominator == 0.0f) 1.0f else numerator / denominator
    }

    fun isSmallScreen(panelW: Int, panelH: Int): Boolean {
        return if (panelH > panelW) {
            panelW <= 1080 && panelH <= 1920
        } else {
            panelW <= 1920 && panelH <= 1080
        }
    }

    fun fit(panelW: Int, panelH: Int, videoW: Int, videoH: Int): Fit {
        val small = isSmallScreen(panelW, panelH)
        if (small || videoW <= 0 || videoH <= 0) return Fit(1.0f, small, null)
        val sW = panelW.toFloat()
        val sH = panelH.toFloat()
        val aspect = videoW.toFloat() / videoH.toFloat()
        return if (sW / sH < aspect) {
            Fit(sH / videoH.toFloat(), small, true)
        } else {
            Fit(sW / videoW.toFloat(), small, false)
        }
    }

    fun adjustedWidth(videoW: Int, scaleFactor: Float): Int = (videoW * scaleFactor).roundToInt()

    fun adjustedHeight(videoH: Int, scaleFactor: Float): Int = (videoH * scaleFactor).roundToInt()

    fun widthMargin(videoW: Int, panelW: Int, scaleFactor: Float): Int {
        if (scaleFactor == 0.0f) return 0
        return ((adjustedWidth(videoW, scaleFactor) - panelW) / scaleFactor).roundToInt().coerceAtLeast(0)
    }

    fun heightMargin(videoH: Int, panelH: Int, scaleFactor: Float): Int {
        if (scaleFactor == 0.0f) return 0
        return ((adjustedHeight(videoH, scaleFactor) - panelH) / scaleFactor).roundToInt().coerceAtLeast(0)
    }

    /** The largest uniform scale that fits the canvas entirely inside the panel: the letterbox. */
    fun containScaleFactor(panelW: Int, panelH: Int, videoW: Int, videoH: Int): Float {
        return minOf(
            divideOrOne(panelW.toFloat(), videoW.toFloat()),
            divideOrOne(panelH.toFloat(), videoH.toFloat())
        )
    }

    /**
     * The smallest uniform scale that still covers the panel, cropping the overshoot. Floored at
     * 1.0f so a video that already has the pixels is cropped at native size rather than downscaled.
     */
    fun coverScaleFactor(panelW: Int, panelH: Int, videoW: Int, videoH: Int): Float {
        return maxOf(
            1.0f,
            maxOf(
                divideOrOne(panelW.toFloat(), videoW.toFloat()),
                divideOrOne(panelH.toFloat(), videoH.toFloat())
            )
        )
    }

    // These two size the view on the legacy forcedScale SurfaceView path, which lays the whole
    // buffer out rather than scaling it, so they measure the buffer and not the margin-reduced canvas.
    fun coverWidth(panelW: Int, panelH: Int, videoW: Int, videoH: Int): Int =
        (videoW * coverScaleFactor(panelW, panelH, videoW, videoH)).roundToInt()

    fun coverHeight(panelW: Int, panelH: Int, videoW: Int, videoH: Int): Int =
        (videoH * coverScaleFactor(panelW, panelH, videoW, videoH)).roundToInt()

    /**
     * The visible canvas: the negotiated buffer minus the margin the phone was told to keep clear.
     * Every scale below is expressed against it, because it is what the panel actually shows.
     */
    fun uiWidth(videoW: Int, marginW: Int): Int = videoW - marginW

    fun uiHeight(videoH: Int, marginH: Int): Int = videoH - marginH

    private fun fitFactor(mode: Settings.VideoFitMode, panelW: Int, panelH: Int, uiW: Int, uiH: Int): Float =
        when (mode) {
            Settings.VideoFitMode.COVER -> coverScaleFactor(panelW, panelH, uiW, uiH)
            else -> containScaleFactor(panelW, panelH, uiW, uiH)
        }

    /**
     * FILL asks for the canvas to reach the panel edges, which means scaling the buffer by however
     * much of it the margin hides. Returning 1.0f unconditionally stretched a 720p picture 1.5x on
     * a 1920x720 panel; returning panel aspect over video aspect showed margin rows we had promised
     * the phone were invisible. One expression covers both, and reproduces what the removed
     * videoW > panelW and isPortraitScaled arms computed.
     */
    fun scaleX(
        mode: Settings.VideoFitMode,
        forcedScale: Boolean,
        panelW: Int,
        panelH: Int,
        videoW: Int,
        videoH: Int,
        marginW: Int,
        marginH: Int
    ): Float {
        if (forcedScale) return 1.0f
        val uiW = uiWidth(videoW, marginW)
        val uiH = uiHeight(videoH, marginH)
        if (mode == Settings.VideoFitMode.FILL) return divideOrOne(videoW.toFloat(), uiW.toFloat())
        return fitFactor(mode, panelW, panelH, uiW, uiH) * divideOrOne(videoW.toFloat(), panelW.toFloat())
    }

    fun scaleY(
        mode: Settings.VideoFitMode,
        forcedScale: Boolean,
        panelW: Int,
        panelH: Int,
        videoW: Int,
        videoH: Int,
        marginW: Int,
        marginH: Int
    ): Float {
        if (forcedScale) return 1.0f
        val uiW = uiWidth(videoW, marginW)
        val uiH = uiHeight(videoH, marginH)
        if (mode == Settings.VideoFitMode.FILL) return divideOrOne(videoH.toFloat(), uiH.toFloat())
        return fitFactor(mode, panelW, panelH, uiW, uiH) * divideOrOne(videoH.toFloat(), panelH.toFloat())
    }
}
