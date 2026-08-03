package com.andrerinas.openheadunit.connection.wifi

import android.content.Context
import com.andrerinas.openheadunit.connection.wifi.modes.WifiLauncherAuto
import com.andrerinas.openheadunit.connection.wifi.modes.WifiLauncherHelper
import com.andrerinas.openheadunit.connection.wifi.modes.WifiLauncherManual
import com.andrerinas.openheadunit.connection.wifi.modes.WifiLauncherNativeAA
import com.andrerinas.openheadunit.utils.Settings

enum class WifiLauncherMode(
    val id: Int,
    val factory: (WifiLauncherManager) -> WifiLauncher) {

    MANUAL(0, ::WifiLauncherManual),
    AUTO(1, ::WifiLauncherAuto),
    HELPER(2, ::WifiLauncherHelper),
    NATIVE_AA(3, ::WifiLauncherNativeAA);

    companion object {

        val DEFAULT: WifiLauncherMode = HELPER


        fun byIdOrDefault(id: Int): WifiLauncherMode {
            for (mode in WifiLauncherMode.entries) {
                if (mode.id == id)
                    return mode
            }

            return DEFAULT
        }
    }
}
