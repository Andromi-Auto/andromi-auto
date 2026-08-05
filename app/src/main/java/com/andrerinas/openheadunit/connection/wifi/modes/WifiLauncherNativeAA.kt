package com.andrerinas.openheadunit.connection.wifi.modes

import com.andrerinas.openheadunit.App
import com.andrerinas.openheadunit.connection.CommManager
import com.andrerinas.openheadunit.connection.NativeAaHandshakeManager
import com.andrerinas.openheadunit.connection.WifiDirectManager
import com.andrerinas.openheadunit.connection.wifi.WifiLauncher
import com.andrerinas.openheadunit.connection.wifi.WifiLauncherManager
import com.andrerinas.openheadunit.connection.wifi.WifiLauncherMode
import com.andrerinas.openheadunit.utils.AppLog

class WifiLauncherNativeAA(
    manager: WifiLauncherManager
) : WifiLauncher(manager) {

    var handshakeManager: NativeAaHandshakeManager? = null
        private set

    override val mode = WifiLauncherMode.NATIVE_AA

    override fun hasSameStartConfiguration(launcher: WifiLauncher) = launcher is WifiLauncherNativeAA

    override fun hasWifiDirect() = true

    override fun hasWirelessServer() = true

    override fun hasLocalDiscovery() = false

    override fun start(quiet: Boolean) {
        val wifiDirect = manager.sharedServices.wifiDirectManager!!

        handshakeManager = NativeAaHandshakeManager(service, this, service.serviceScope)

        // Start WiFi Direct as a "quiet host" (P2P Group for phone to join)
        // We let WifiDirectManager handle the WiFi state (enabling if needed)
        setupWifiDirect(wifiDirect)
        wifiDirect.startNativeAaQuietHost()

        // Start the official Bluetooth handshake servers
        handshakeManager?.start()
    }

    override fun stop() {
        handshakeManager?.stop()
    }

    private fun setupWifiDirect(wifiDirectManager: WifiDirectManager) {
        val commManager = App.provide(service).commManager

        wifiDirectManager.setCredentialsListener { ssid, psk, ip, bssid ->
            val commManager = App.provide(service).commManager

            AppLog.i("AapService: Received WiFi credentials from manager (SSID=$ssid, IP=$ip). Updating and Triggering Poke.")

            handshakeManager?.updateWifiCredentials(ssid, psk, ip, bssid)

            if (commManager.isConnected ||
                commManager.connectionState.value is CommManager.ConnectionState.Connecting) {
                AppLog.i("AapService: USB/other session already active. Skipping auto-poke to avoid pulling phone into wireless flow.")
            } else if (!service.userExitedAA) {
                handshakeManager?.triggerPoke()
            } else {
                AppLog.i("AapService: userExitedAA is true. Skipping auto-poke.")
            }
        }

        // Settling counts as in-flight here: isHandshakeInFlight() goes false the instant Type 3
        // is written, but the phone still has to associate, do WPS and get a DHCP lease, and
        // recreating the group in that window hands it an SSID it can no longer join.
        wifiDirectManager.setNativeHandshakeStateProvider {
            handshakeManager?.isHandshakeInFlight() == true ||
            handshakeManager?.isHandoffSettling() == true
        }
        wifiDirectManager.setNativeSessionConnectedProvider { commManager.isConnected }
        wifiDirectManager.setNativeGroupInvalidatedListener { handshakeManager?.invalidateCredentials() }
    }

    /**
     * Triggers a refresh of the WiFi Direct "quiet host" state.
     * Called by NativeAaHandshakeManager if it's waiting for credentials that haven't arrived yet.
     */
    fun triggerWifiDirectRefresh() {
        AppLog.i("AapService: WiFi Direct refresh requested.")
        manager.sharedServices.wifiDirectManager?.startNativeAaQuietHost()
    }
}
