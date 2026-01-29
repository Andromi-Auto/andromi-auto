package com.andrerinas.headunitrevived.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings as SystemSettings
import com.andrerinas.headunitrevived.contract.LocationUpdateIntent

class NightModeManager(
    private val context: Context,
    private val settings: Settings,
    private val onUpdate: (Boolean) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private var nightModeCalculator = NightMode(settings, false)
    
    // State
    private var lastEmittedValue: Boolean? = null
    private var currentLux: Float = -1f
    private var currentBrightness: Int = -1
    private var isFirstSensorReading = true
    
    private val handler = Handler(Looper.getMainLooper())

    // Debouncing
    private var pendingValue: Boolean? = null
    private val debounceRunnable = Runnable {
        pendingValue?.let { newValue ->
            if (lastEmittedValue != newValue) {
                AppLog.i("NightModeManager: Debounce finished. Switching to $newValue")
                lastEmittedValue = newValue
                onUpdate(newValue)
            }
        }
    }

    private val brightnessObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            update()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == LocationUpdateIntent.action) {
                // Keep the sensor values when recreating the calculator
                val oldLux = currentLux
                val oldBright = currentBrightness
                nightModeCalculator = NightMode(settings, true)
                nightModeCalculator.currentLux = oldLux
                nightModeCalculator.currentBrightness = oldBright
            }
            update()
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(LocationUpdateIntent.action)
        }
        context.registerReceiver(receiver, filter)

        AppLog.i("NightModeManager: Starting with mode ${settings.nightMode}")

        if (settings.nightMode == Settings.NightMode.LIGHT_SENSOR) {
            if (lightSensor != null) {
                AppLog.i("NightModeManager: Registering light sensor listener")
                sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
            } else {
                AppLog.e("NightModeManager: Light sensor not available on this device!")
            }
        }

        if (settings.nightMode == Settings.NightMode.SCREEN_BRIGHTNESS) {
            AppLog.i("NightModeManager: Registering brightness observer")
            context.contentResolver.registerContentObserver(
                SystemSettings.System.getUriFor(SystemSettings.System.SCREEN_BRIGHTNESS),
                false,
                brightnessObserver
            )
        }

        // Initial update immediately
        update(debounce = false)
    }

    fun stop() {
        try { context.unregisterReceiver(receiver) } catch (e: Exception) {}
        sensorManager.unregisterListener(this)
        context.contentResolver.unregisterContentObserver(brightnessObserver)
        handler.removeCallbacks(debounceRunnable)
    }

    fun resendCurrentState() {
        val isNight = nightModeCalculator.current
        AppLog.i("NightModeManager: Resending current state: $isNight")
        onUpdate(isNight)
    }

    private fun update(debounce: Boolean = true) {
        var isNight = false

        when (settings.nightMode) {
            Settings.NightMode.LIGHT_SENSOR -> {
                val threshold = settings.nightModeThresholdLux
                if (currentLux >= 0) {
                    // Hysteresis Logic
                    val hyst = 5.0f // 5 Lux buffer
                    val currentIsNight = lastEmittedValue ?: false
                    
                    isNight = if (currentIsNight) {
                        currentLux < (threshold + hyst) // Stay night until significantly brighter
                    } else {
                        currentLux < threshold // Become night when darker than threshold
                    }
                }
            }
            Settings.NightMode.SCREEN_BRIGHTNESS -> {
                val threshold = settings.nightModeThresholdBrightness
                try {
                    currentBrightness = SystemSettings.System.getInt(
                        context.contentResolver, 
                        SystemSettings.System.SCREEN_BRIGHTNESS
                    )
                    // Hysteresis Logic for Brightness
                    val hyst = 10 // 10 steps buffer
                    val currentIsNight = lastEmittedValue ?: false
                    
                    isNight = if (currentIsNight) {
                        currentBrightness < (threshold + hyst)
                    } else {
                        currentBrightness < threshold
                    }
                } catch (e: Exception) {
                    AppLog.e("NightModeManager: Failed to read brightness", e)
                }
            }
            // Delegate to standard calculator for other modes
            else -> {
                isNight = nightModeCalculator.current
            }
        }

        // Apply
        if (debounce) {
            if (pendingValue != isNight) {
                AppLog.i("NightModeManager: State change detected (pending: $isNight). Starting 2s debounce...")
                pendingValue = isNight
                handler.removeCallbacks(debounceRunnable)
                handler.postDelayed(debounceRunnable, 2000)
            }
        } else {
            // Immediate update (for start or manual modes if we handled them here)
            if (lastEmittedValue != isNight) {
                AppLog.i("NightModeManager: Immediate update to $isNight")
                lastEmittedValue = isNight
                onUpdate(isNight)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_LIGHT) {
            val newLux = event.values[0]
            
            // Only update if value changed significantly or it's the first reading
            if (kotlin.math.abs(newLux - currentLux) >= 1.0f || isFirstSensorReading) {
                currentLux = newLux
                nightModeCalculator.currentLux = currentLux
                
                if (isFirstSensorReading) {
                    isFirstSensorReading = false
                    update(debounce = false)
                } else {
                    update()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
