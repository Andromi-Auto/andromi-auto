package com.andrerinas.openheadunit.main

import android.content.Context
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.BluetoothHelper

/**
 * Andromi Auto's "Automatic start": when it is on, Android Auto (Self Mode) starts by itself while
 * the car's chosen Bluetooth device is connected, and stops when that device goes away.
 */
object AndromiAutoStart {

    /** How long the car may be gone before Android Auto stops, so a Bluetooth blip does not end it. */
    const val CAR_GONE_GRACE_MS = 5_000L

    /**
     * Whether opening the app should start Self Mode at once: automatic start is on and the car
     * is already connected. Without the car the app opens normally and the car's Bluetooth
     * connecting later is what starts Android Auto.
     */
    fun startsOnOpen(context: Context): Boolean {
        val app = App.provide(context)
        val settings = app.settings
        return settings.autoStartSelfMode &&
            settings.hasAcceptedDisclaimer &&
            settings.onboardingVersion >= OnboardingActivity.CURRENT_ONBOARDING_VERSION &&
            !app.commManager.isConnected &&
            carConnected(context)
    }

    /**
     * Whether one of the chosen car Bluetooth devices is connected to this phone right now.
     * An answer that cannot be read (Bluetooth off, no permission) counts as not connected.
     */
    fun carConnected(context: Context): Boolean {
        val macs = App.provide(context).settings.autoStartBluetoothDeviceMacs
        if (macs.isEmpty()) return false
        val adapter = BluetoothHelper.getBluetoothAdapter(context) ?: return false
        val connected = try {
            adapter.isEnabled && macs.any { mac ->
                BluetoothHelper.deviceConnectionState(adapter.getRemoteDevice(mac)) == true
            }
        } catch (e: Exception) {
            AppLog.w("AndromiAutoStart: could not read the car's Bluetooth state: ${e.message}")
            false
        }
        AppLog.i("AndromiAutoStart: car Bluetooth connected=$connected")
        return connected
    }

    /**
     * Whether a Bluetooth link event from [mac] is the car leaving while automatic start is on,
     * which stops Android Auto and its head unit server.
     */
    fun isCarLeaving(autoStartOn: Boolean, carMacs: Set<String>, mac: String?, connected: Boolean): Boolean =
        autoStartOn && !connected && mac != null && mac in carMacs

    /** Whether a Bluetooth link event from [mac] is the car coming back, which cancels a pending stop. */
    fun isCarBack(carMacs: Set<String>, mac: String?, connected: Boolean): Boolean =
        connected && mac != null && mac in carMacs
}
