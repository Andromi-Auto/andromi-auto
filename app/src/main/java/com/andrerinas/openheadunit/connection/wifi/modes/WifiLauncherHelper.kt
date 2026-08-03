package com.andrerinas.openheadunit.connection.wifi.modes

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.widget.Toast
import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.R
import com.andrerinas.openheadunit.connection.NearbyManager
import com.andrerinas.openheadunit.connection.wifi.WifiLauncher
import com.andrerinas.openheadunit.connection.wifi.WifiLauncherManager
import com.andrerinas.openheadunit.connection.wifi.WifiLauncherMode
import com.andrerinas.openheadunit.connection.wifi.WifiLauncherSharedServices
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.HotspotManager
import com.andrerinas.openheadunit.utils.Settings
import com.andrerinas.openheadunit.utils.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WifiLauncherHelper : WifiLauncher {

    private val strategy: Strategy

    var nearbyManager: NearbyManager? = null
        private set

    constructor(manager: WifiLauncherManager) : super(manager) {
        // copy settings early in construction to align with #hasSameStartConfiguration
        this.strategy = settings.helperConnectionStrategy
    }

    constructor(manager: WifiLauncherManager, strategy: Strategy) : super(manager) {
        this.strategy = strategy
    }


    override val mode = WifiLauncherMode.HELPER

    override fun hasSameStartConfiguration(launcher: WifiLauncher): Boolean {
        return launcher is WifiLauncherHelper && launcher.strategy == strategy
    }

    override fun hasWifiDirect() = strategy == Strategy.WIFI_DIRECT

    override fun hasWirelessServer() = true

    override fun hasLocalDiscovery(): Boolean {
        return strategy == Strategy.COMMON_WIFI || strategy == Strategy.PHONE_HOTSPOT || strategy == Strategy.HEADUNIT_HOTSPOT
    }


    override fun start(quiet: Boolean) {
        when (strategy) {
            Strategy.COMMON_WIFI -> { /* #startDiscovery(oneShot = false) handled by SharedServices */ }
            Strategy.WIFI_DIRECT -> {
                val wifiManager = service.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val wifiDirect = manager.sharedServices.wifiDirectManager!!

                if (wifiManager.isWifiEnabled) {
                    wifiDirect.makeVisible()
                } else if (!quiet) {
                    ToastUtils.showToast(service, service.getString(R.string.wifi_disabled_info), Toast.LENGTH_SHORT)
                }
            }
            Strategy.NEARBY_DEVICES -> { // Google Nearby
                initNearbyManager()
                nearbyManager?.start()
            }
            Strategy.PHONE_HOTSPOT, Strategy.HEADUNIT_HOTSPOT -> { /* Host/Passive - just wait for connection on WirelessServer port */ }
        }

        // Hotspot logic for Helper mode if enabled (only for Strategy 4: Headunit Hotspot)
        if (settings.autoEnableHotspot && strategy == Strategy.HEADUNIT_HOTSPOT) {
            Thread {
                AppLog.i("AapService: Auto-enabling hotspot for Helper mode...")
                HotspotManager.setHotspotEnabled(service, true)
            }.start()
        }
    }

    override fun restartDiscovery() {
        when (strategy) {
            Strategy.NEARBY_DEVICES -> nearbyManager?.start()
            Strategy.WIFI_DIRECT -> {
                val wifiManager = service.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val wifiDirect = manager.sharedServices.wifiDirectManager

                if (wifiManager.isWifiEnabled) {
                    wifiDirect?.makeVisible()
                }
            }
            else -> super.restartDiscovery()
        }
    }

    override fun stop() {
        nearbyManager?.stop()
    }

    private fun initNearbyManager() {
        if (nearbyManager != null)
            return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                nearbyManager = NearbyManager(service, service.serviceScope) { socket ->
                    val appSettings = App.provide(service).settings
                    appSettings.saveLastConnection(Settings.CONNECTION_TYPE_NEARBY)
                    service.serviceScope.launch(Dispatchers.IO) {
                        App.provide(service).commManager.connect(socket)
                    }
                }
            } catch (e: Exception) {
                AppLog.e("AapService: Failed to init NearbyManager: ${e.message}")
            }
        }
    }



    enum class Strategy(val id: Int) {

        COMMON_WIFI(0), // NSD
        WIFI_DIRECT(1), // P2P
        NEARBY_DEVICES(2), // Google Nearby
        PHONE_HOTSPOT(3),
        HEADUNIT_HOTSPOT(4);

        companion object {

            val DEFAULT: Strategy = NEARBY_DEVICES


            fun byIdOrDefault(id: Int): Strategy {
                for (mode in Strategy.entries) {
                    if (mode.id == id)
                        return mode
                }

                return DEFAULT
            }
        }
    }
}
