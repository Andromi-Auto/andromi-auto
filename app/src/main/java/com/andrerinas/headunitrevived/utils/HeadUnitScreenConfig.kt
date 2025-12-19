package com.andrerinas.headunitrevived.utils

import android.util.DisplayMetrics
import android.util.Log
import com.andrerinas.headunitrevived.aap.protocol.proto.Control
import kotlin.math.roundToInt

object HeadUnitScreenConfig {

    private var screenWidthPx: Int = 0
    private var screenHeightPx: Int = 0
    private var density: Float = 1.0f
    private var scaleFactor: Float = 1.0f
    private var isSmallScreen: Boolean = true
    private var isPortraitScaled: Boolean = false
    var negotiatedResolutionType: Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType? = null

    fun init(displayMetrics: DisplayMetrics) {
        screenWidthPx = displayMetrics.widthPixels
        screenHeightPx = displayMetrics.heightPixels
        density = displayMetrics.density

        if (screenWidthPx == 0 || screenHeightPx == 0) {
            return
        }
        AppLog.i("CarScreen: width: $screenWidthPx height: $screenHeightPx")

        // Save to SharedPreferences - this part is not strictly needed for calculation but was in original
        /*val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit { putInt("screenWidth", widthPx) }
        prefs.edit { putInt("screenHeight", heightPx) }*/

        // Determine negotiatedResolutionType (f7513f) based on physical pixels
        if (screenHeightPx > screenWidthPx) { // Portrait mode
            if (screenWidthPx > 720 || screenHeightPx > 1280) {
                if (screenWidthPx > 1080 || screenHeightPx > 1920) {
                    isSmallScreen = false
                }
                negotiatedResolutionType = Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._1080x1920
            } else {
                negotiatedResolutionType = Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._720x1280
            }
        } else { // Landscape mode
            if (screenWidthPx <= 800 && screenHeightPx <= 480) {
                negotiatedResolutionType = Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._800x480
            } else if (screenWidthPx > 1280 || screenHeightPx > 720) {
                if (screenWidthPx > 1920 || screenHeightPx > 1080) {
                    isSmallScreen = false
                }
                negotiatedResolutionType = Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._1920x1080
            } else {
                negotiatedResolutionType = Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._1280x720
            }
        }

        if (!isSmallScreen) {
            val f6 = screenWidthPx.toFloat()
            val f7 = screenHeightPx.toFloat()
            if (f6 / f7 < getAspectRatio()) {
                isPortraitScaled = true
                scaleFactor = (f7 * 1.0f) / getNegotiatedHeight().toFloat()
            } else {
                isPortraitScaled = false
                scaleFactor = (f6 * 1.0f) / getNegotiatedWidth().toFloat()
            }
        }
        AppLog.i("CarScreen using: $negotiatedResolutionType, number: ${negotiatedResolutionType?.number}, scales: scaleX: ${getScaleX()}, scaleY: ${getScaleY()}")
    }

    // Corresponds to b()
    fun getAdjustedHeight(): Int {
        return (getNegotiatedHeight() * scaleFactor).roundToInt()
    }

    // Corresponds to c()
    fun getAdjustedWidth(): Int {
        return (getNegotiatedWidth() * scaleFactor).roundToInt()
    }

    // Corresponds to d()
    private fun getAspectRatio(): Float {
        return getNegotiatedWidth().toFloat() / getNegotiatedHeight().toFloat()
    }

    // Corresponds to e()
    fun getNegotiatedHeight(): Int {
        val resString = negotiatedResolutionType.toString().replace("_", "")
        return resString.split("x")[1].toInt()
    }

    // Corresponds to f()
    fun getNegotiatedWidth(): Int {
        val resString = negotiatedResolutionType.toString().replace("_", "")
        return resString.split("x")[0].toInt()
    }

    // Corresponds to g()
    fun getHeightMargin(): Int {
        AppLog.i("CarScreen: Zoom is: $scaleFactor, adjusted height: ${getAdjustedHeight()}")
        val margin = ((getAdjustedHeight() - screenHeightPx) / scaleFactor).roundToInt()
        return margin.coerceAtLeast(0)
    }

    // Corresponds to j()
    fun getWidthMargin(): Int {
        val margin = ((getAdjustedWidth() - screenWidthPx) / scaleFactor).roundToInt()
        AppLog.i("CarScreen: Zoom is: $scaleFactor, adjusted width: ${getAdjustedWidth()}")
        return margin.coerceAtLeast(0)
    }

    // Corresponds to k()
    fun getHorizontalCorrection(): Float {
        AppLog.i("CarScreen: Horizontal correction: 0, width ${getNegotiatedWidth()}, marg: ${getWidthMargin()}, width: $screenWidthPx")
        return (getNegotiatedWidth() - getWidthMargin()).toFloat() / screenWidthPx.toFloat()
    }

    // Corresponds to o()
    fun getVerticalCorrection(): Float {
        val fIntValue = (getNegotiatedHeight() - getHeightMargin()).toFloat() / screenHeightPx.toFloat()
        AppLog.i("CarScreen: Vertical correction: $fIntValue, height ${getNegotiatedHeight()}, marg: ${getHeightMargin()}, height: $screenHeightPx")
        return fIntValue
    }

    // Helper for l()
    private fun divideOrOne(numerator: Float, denominator: Float): Float {
        return if (denominator == 0.0f) 1.0f else numerator / denominator
    }

    // Corresponds to m()
    fun getScaleX(): Float {
        if (getNegotiatedWidth() > screenWidthPx) {
            return divideOrOne(getNegotiatedWidth().toFloat(), screenWidthPx.toFloat())
        }
        if (isPortraitScaled) {
            return divideOrOne(getAspectRatio(), (screenWidthPx.toFloat() / screenHeightPx.toFloat()))
        }
        return 1.0f
    }

    // Corresponds to n()
    fun getScaleY(): Float {
        if (getNegotiatedHeight() > screenHeightPx) {
            return divideOrOne(getNegotiatedHeight().toFloat(), screenHeightPx.toFloat())
        }
        if (isPortraitScaled) {
            return 1.0f
        }
        return divideOrOne((screenWidthPx.toFloat() / screenHeightPx.toFloat()), getAspectRatio())
    }

    fun getDensityWidth(): Int {
        return (screenWidthPx / density).roundToInt()
    }

    fun getDensityHeight(): Int {
        return (screenHeightPx / density).roundToInt()
    }

    fun getDensityDpi(): Int {
        return density.roundToInt()
    }
}
