package com.andrerinas.openheadunit.connection.self.launchers

import android.content.Intent
import android.os.Build
import com.andrerinas.openheadunit.connection.self.SelfLauncher
import com.andrerinas.openheadunit.connection.self.SelfLauncherManager
import com.andrerinas.openheadunit.connection.self.SelfLauncherServices
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.utils.RootSetup

/**
 * Andromi Auto: Self Mode through Android Auto's WirelessStartupReceiver, which the root setup
 * ([RootSetup]) re-enabled. Starts our wireless server on 5288 and asks Android Auto to connect to
 * it, so no developer "headunit server" has to be started by hand. Works on Android Auto 17.4+.
 */
class SelfLauncherRoot(
    manager: SelfLauncherManager,
    services: SelfLauncherServices) : SelfLauncher(manager, services) {

    override val name = "Root: WirelessStartupReceiver"

    override suspend fun run(): Boolean {
        if (!RootSetup.isReady(services.aap)) return false
        SelfLauncherLegacy(manager, services).runWifiLauncher()
        val activeNetwork = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            services.connectivityManager.activeNetwork else null
        val networkToUse = activeNetwork ?: services.fakeNetwork
        val intent = Intent().apply {
            setClassName(RootSetup.AA_PACKAGE, RootSetup.RECEIVER)
            action = "com.google.android.apps.auto.wireless.setup.receiver.wirelessstartup.START"
            putExtra("ip_address", "127.0.0.1")
            putExtra("projection_port", 5288)
            networkToUse?.let { putExtra("PARAM_SERVICE_WIFI_NETWORK", it) }
            services.fakeWifiInfo?.let { putExtra("wifi_info", it) }
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        services.ensureLaunchAllowed()
        services.aap.sendBroadcast(intent)
        AppLog.i("SelfMode: root route, WirelessStartupReceiver asked to connect to 127.0.0.1:5288")
        return true
    }
}
