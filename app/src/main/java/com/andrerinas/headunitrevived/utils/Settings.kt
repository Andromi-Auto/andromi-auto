package com.andrerinas.headunitrevived.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import com.andrerinas.headunitrevived.aap.protocol.proto.Control
import com.andrerinas.headunitrevived.connection.UsbDeviceCompat

/**
 * @author algavris
 * *
 * @date 21/05/2016.
 */

class Settings(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun isConnectingDevice(deviceCompat: UsbDeviceCompat): Boolean {
        val allowDevices = prefs.getStringSet("allow-devices", null) ?: return false
        return allowDevices.contains(deviceCompat.uniqueName)
    }

    var allowedDevices: Set<String>
        get() = prefs.getStringSet("allow-devices", HashSet<String>())!!
        set(devices) {
            prefs.edit().putStringSet("allow-devices", devices).apply()
        }

    var networkAddresses: Set<String>
        get() = prefs.getStringSet("network-addresses", HashSet<String>())!!
        set(addrs) {
            prefs.edit().putStringSet("network-addresses", addrs).apply()
        }

    var bluetoothAddress: String
        get() = prefs.getString("bt-address", "40:EF:4C:A3:CB:A5")!!
        set(value) = prefs.edit().putString("bt-address", value).apply()

    var lastKnownLocation: Location
        get() {
            val latitude = prefs.getLong("last-loc-latitude", (32.0864169).toLong())
            val longitude = prefs.getLong("last-loc-longitude", (34.7557871).toLong())

            val location = Location("")
            location.latitude = latitude.toDouble()
            location.longitude = longitude.toDouble()
            return location
        }
        set(location) {
            prefs.edit()
                .putLong("last-loc-latitude", location.latitude.toLong())
                .putLong("last-loc-longitude", location.longitude.toLong())
                .apply()
        }

    var resolutionId: Int
        get() = prefs.getInt("resolutionId", 0)
        set(value) = prefs.edit().putInt("resolutionId", value).apply()

    var micSampleRate: Int
        get() = prefs.getInt("mic-sample-rate", 8000)
        set(sampleRate) {
            prefs.edit().putInt("mic-sample-rate", sampleRate).apply()
        }

    var useGpsForNavigation: Boolean
        get() = prefs.getBoolean("gps-navigation", true)
        set(value) {
            prefs.edit().putBoolean("gps-navigation", value).apply()
        }

    var nightMode: NightMode
        get() {
            val value = prefs.getInt("night-mode", 0)
            val mode = NightMode.fromInt(value)
            return mode!!
        }
        set(nightMode) {
            prefs.edit().putInt("night-mode", nightMode.value).apply()
        }

    var keyCodes: MutableMap<Int, Int>
        get() {
            val set = prefs.getStringSet("key-codes", mutableSetOf())!!
            val map = mutableMapOf<Int, Int>()
            set.forEach {
                val codes = it.split("-")
                map[codes[0].toInt()] = codes[1].toInt()
            }
            return map
        }
        set(codesMap) {
            val list: List<String> = codesMap.map { "${it.key}-${it.value}" }
            prefs.edit().putStringSet("key-codes", list.toSet()).apply()
        }

    var debugMode: Boolean
        get() = prefs.getBoolean("debug-mode", false)
        set(value) {
            prefs.edit().putBoolean("debug-mode", value).apply()
        }

    var viewMode: ViewMode
        get() {
            val value = prefs.getInt("view-mode", 0)
            return ViewMode.fromInt(value)!!
        }
        set(viewMode) {
            prefs.edit().putInt("view-mode", viewMode.value).apply()
        }

    @SuppressLint("ApplySharedPref")
    fun commit() {
        prefs.edit().commit()
    }

    enum class Resolution(val id: Int, val resName: String, val width: Int, val height: Int, val codec: Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType?) {
        AUTO(0, "Auto",0, 0, null),
        _800x480(1, "800x480", 800, 480, Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._800x480),
        _1280x720(2, "1280x720", 1280, 720, Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._1280x720),
        _1920x1080(3, "1920x1080", 1920, 1080, Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._1920x1080);

        companion object {
            private val map = values().associateBy(Resolution::id)
            fun fromId(id: Int) = map[id]
            val allRes: Array<String>
                get() = values().map { it.resName }.toTypedArray()
            val allResolutions: Array<Resolution>
                get() = values()
        }
    }

    enum class NightMode(val value: Int) {
        AUTO(0),
        DAY(1),
        NIGHT(2),
        AUTO_WAIT_GPS(3),
        NONE(4);

        companion object {
            private val map = NightMode.values().associateBy(NightMode::value)
            fun fromInt(value: Int) = map[value]
        }
    }

    companion object {
        val MicSampleRates = hashMapOf(
            8000 to 16000,
            16000 to 8000
        )

        val NightModes = hashMapOf(
            0 to 1,
            1 to 2,
            2 to 3,
            3 to 4,
            4 to 0
        )
    }

    enum class ViewMode(val value: Int) {
        SURFACE(0),
        TEXTURE(1);

        companion object {
            private val map = values().associateBy(ViewMode::value)
            fun fromInt(value: Int) = map[value]
        }
    }

}
