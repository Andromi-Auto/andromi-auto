package com.andrerinas.headunitrevived.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.andrerinas.headunitrevived.App
import com.andrerinas.headunitrevived.aap.AapService
import com.andrerinas.headunitrevived.main.MainActivity
import com.andrerinas.headunitrevived.utils.AppLog
import com.andrerinas.headunitrevived.utils.Settings

class WifiAutoStartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WifiManager.NETWORK_STATE_CHANGED_ACTION) return

        val networkInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO)
        }

        if (networkInfo != null && networkInfo.isConnected) {
            checkAndStart(context)
        }
    }

    companion object {
        fun checkAndStart(context: Context) {
            // Read settings from device-protected storage for maximum reliability
            if (!Settings.isAutoStartOnWifiEnabled(context)) return
            val targetSsid = Settings.getAutoStartWifiSsid(context).removeSurrounding("\"")
            if (targetSsid.isEmpty()) return

            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo: WifiInfo? = wifiManager.connectionInfo
            
            val currentSsid = wifiInfo?.ssid?.removeSurrounding("\"")
            
            if (currentSsid == null || currentSsid == "<unknown ssid>") {
                AppLog.d("WifiAutoStartReceiver: No valid SSID connected yet.")
                return
            }

            AppLog.d("WifiAutoStartReceiver: Checking WiFi: $currentSsid (Target: $targetSsid)")

            if (currentSsid.equals(targetSsid, ignoreCase = true)) {
                AppLog.i("WifiAutoStartReceiver: MATCH! Starting AapService via WiFi Auto-start...")

                // Don't trigger if already connected
                if (App.provide(context).commManager.isConnected) {
                    AppLog.d("WifiAutoStartReceiver: Already connected to Android Auto. Ignoring event.")
                    return
                }

                // Start the service
                val serviceIntent = Intent(context, AapService::class.java)
                try {
                    ContextCompat.startForegroundService(context, serviceIntent)
                } catch (e: Exception) {
                    AppLog.e("Failed to start AapService from background: ${e.message}")
                }

                // Attempt to start the UI
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(MainActivity.EXTRA_LAUNCH_SOURCE, "WiFi auto-start")
                }
                try {
                    context.startActivity(launchIntent)
                } catch (e: Exception) {
                    AppLog.w("Could not start UI from background: ${e.message}")
                }
            }
        }
    }
}
