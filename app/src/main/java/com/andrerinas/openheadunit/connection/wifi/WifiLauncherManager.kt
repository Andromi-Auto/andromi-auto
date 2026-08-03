package com.andrerinas.openheadunit.connection.wifi

import android.content.Context
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.aap.AapService
import com.andrerinas.openheadunit.connection.wifi.modes.WifiLauncherHelper
import com.andrerinas.openheadunit.utils.AppLog

class WifiLauncherManager(val service: AapService) {

    val sharedServices: WifiLauncherSharedServices = WifiLauncherSharedServices(service)
    var active: WifiLauncher? = null
        private set


    fun isActive(): Boolean = active != null

    fun getActiveMode(): WifiLauncherMode? = active?.mode

    fun setActiveFromSettings(force: Boolean = false, quiet: Boolean = true) {
        val settings = App.provide(service).settings

        setActive(settings.wifiConnectionMode, force, quiet)
    }

    fun setActive(mode: WifiLauncherMode, force: Boolean = false, quiet: Boolean = true) {
        setActive(mode.factory(this), force, quiet)
    }

    fun setActive(newLauncher: WifiLauncher, force: Boolean = false, quiet: Boolean = true) {
        if (newLauncher.manager != this)
            throw IllegalArgumentException("newLauncher.manager is different instance")
        if (active == newLauncher)
            throw IllegalArgumentException("newLauncher is already active")

        if (!force && (active?.hasSameStartConfiguration(newLauncher) ?: false)) {
            AppLog.d("AapService: WiFi Mode ${newLauncher.mode}.mode with same start-configuration is already initialized.")
            return
        }

        AppLog.i("AapService: Initializing WiFi Mode: ${newLauncher.mode}")

        // stop old launcher
        active?.stop()

        // replace it with new one
        active = newLauncher
        sharedServices.update(newLauncher)
        active?.start(quiet)
    }

    fun stop() {
        if (active == null)
            return

        sharedServices.stopAll()
        active?.stop()
        active = null
    }

    fun forceStartDiscoveryScan() {
        val discovery = sharedServices.localDiscovery

        if (discovery != null) {
            discovery.stop()
            discovery.startScan()
        }
    }

    fun startDiscovery(oneShot: Boolean = false) {
        // Allow discovery for Strategy 0 (NSD), 3 (Phone Hotspot) and 4 (Headunit Hotspot)
        if (active == null || active?.hasLocalDiscovery() == false)
            return

        sharedServices.startLocalDiscovery(oneShot)
    }

    fun restartDiscovery() {
        active?.restartDiscovery()
    }
}
