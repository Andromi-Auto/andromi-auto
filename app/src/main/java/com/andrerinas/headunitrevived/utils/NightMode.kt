 package com.andrerinas.headunitrevived.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class NightMode(private val settings: Settings, val hasGPSLocation: Boolean) {
    private val calculator = NightModeCalculator(settings)
    var currentLux: Float = -1f
    var currentBrightness: Int = -1

    var current: Boolean = false
        get()  {
            return when (settings.nightMode){
                Settings.NightMode.AUTO -> calculator.current
                Settings.NightMode.DAY -> false
                Settings.NightMode.NIGHT -> true
                Settings.NightMode.AUTO_WAIT_GPS -> {
                    if (hasGPSLocation) calculator.current else false
                }
                Settings.NightMode.LIGHT_SENSOR -> {
                    if (currentLux >= 0) currentLux < settings.nightModeThresholdLux else false
                }
                Settings.NightMode.SCREEN_BRIGHTNESS -> {
                    if (currentBrightness >= 0) currentBrightness < settings.nightModeThresholdBrightness else false
                }
            }
        }

    override fun toString(): String {
        return when (settings.nightMode){
            Settings.NightMode.AUTO -> "NightMode: ${calculator.current}"
            Settings.NightMode.DAY -> "NightMode: DAY"
            Settings.NightMode.NIGHT -> "NightMode: NIGHT"
            Settings.NightMode.AUTO_WAIT_GPS -> {
                if (hasGPSLocation)"NightMode: ${calculator.current}" else "NightMode: DAY"
            }
            Settings.NightMode.LIGHT_SENSOR -> "NightMode: Sensor ($currentLux < ${settings.nightModeThresholdLux})"
            Settings.NightMode.SCREEN_BRIGHTNESS -> "NightMode: Brightness ($currentBrightness < ${settings.nightModeThresholdBrightness})"
        }
    }

    fun getCalculationInfo(): String {
        return calculator.getCalculationInfo()
    }
}

private class NightModeCalculator(private val settings: Settings) {
    private val twilightCalculator = TwilightCalculator()
    private val format = SimpleDateFormat("HH:mm", Locale.US)

    fun getCalculationInfo(): String {
        val time = Calendar.getInstance().time
        val location = settings.lastKnownLocation
        twilightCalculator.calculateTwilight(time.time, location.latitude, location.longitude)
        
        val sunrise = if (twilightCalculator.mSunrise > 0) format.format(Date(twilightCalculator.mSunrise)) else "--:--"
        val sunset = if (twilightCalculator.mSunset > 0) format.format(Date(twilightCalculator.mSunset)) else "--:--"
        return "$sunrise - $sunset"
    }

    var current: Boolean = false
        get()  {
            val time = Calendar.getInstance().time
            val location = settings.lastKnownLocation
            twilightCalculator.calculateTwilight(time.time, location.latitude, location.longitude)
            return twilightCalculator.mState == TwilightCalculator.NIGHT
        }

    override fun toString(): String {
        val sunrise = if (twilightCalculator.mSunrise > 0) format.format(Date(twilightCalculator.mSunrise)) else "-1"
        val sunset = if (twilightCalculator.mSunset > 0) format.format(Date(twilightCalculator.mSunset)) else "-1"
        val mode = if (twilightCalculator.mState == TwilightCalculator.NIGHT) "NIGHT" else "DAY"
        return String.format(Locale.US, "%s, (%s - %s)", mode, sunrise, sunset)
    }
}
