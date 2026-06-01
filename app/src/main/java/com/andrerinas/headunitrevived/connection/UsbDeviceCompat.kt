package com.andrerinas.headunitrevived.connection

import android.hardware.usb.UsbDevice
import com.andrerinas.headunitrevived.aap.Utils
import java.util.Locale

class UsbDeviceCompat(val wrappedDevice: UsbDevice) {

    val deviceName: String
        get() = wrappedDevice.deviceName

    val uniqueName: String
        get() = getUniqueName(wrappedDevice)

    override fun toString(): String {
        return String.format(Locale.US, "%s - %s", uniqueName, wrappedDevice.toString())
    }

    val isInAccessoryMode: Boolean
        get() = isInAccessoryMode(wrappedDevice)

    companion object {
        private const val USB_VID_GOO = 0x18D1   // 6353   Nexus or ACC mode, see PID to distinguish
        private const val USB_VID_HTC = 0x0bb4   // 2996
        private const val USB_VID_SAM = 0x04e8   // 1256
        private const val USB_VID_O1A = 0xfff6   // 65526    Samsung ?
        private const val USB_VID_SON = 0x0fce   // 4046
        private const val USB_VID_LGE = 0x1004   // 65525
        private const val USB_VID_MOT = 0x22b8   // 8888
        private const val USB_VID_ACE = 0x0502
        private const val USB_VID_HUA = 0x12d1
        private const val USB_VID_ZTE = 0x19d2
        private const val USB_VID_XIA = 0x2717
        private const val USB_VID_ASU = 0x0b05
        private const val USB_VID_MEI = 0x2a45
        private const val USB_VID_WIL = 0x4ee7

        private const val USB_PID_ACC = 0x2D00      // Accessory                  100
        private const val USB_PID_ACC_ADB = 0x2D01      // Accessory + ADB            110

        private val VENDOR_NAMES = mapOf(
            USB_VID_GOO to "Google",
            USB_VID_HTC to "HTC",
            USB_VID_SAM to "Samsung",
            USB_VID_SON to "Sony",
            USB_VID_MOT to "Motorola",
            USB_VID_LGE to "LG",
            USB_VID_O1A to "O1A",
            USB_VID_HUA to "Huawei",
            USB_VID_ACE to "Acer",
            USB_VID_ZTE to "ZTE",
            USB_VID_XIA to "Xiaomi",
            USB_VID_ASU to "Asus",
            USB_VID_MEI to "Meizu",
            USB_VID_WIL to "Wileyfox"
        )

        fun getUniqueName(device: UsbDevice): String {
            val vendorId = device.vendorId  // mVendorId=2996               HTC
            val productId = device.productId  // mProductId=1562              OneM8

//            if (App.IS_LOLLIPOP) {                                 // Android 5.0+ only
//                try {
//                    dev_man = usb_man_get(device).toUpperCase(Locale.getDefault())                             // mManufacturerName=HTC
//                    dev_prod = usb_pro_get(device).toUpperCase(Locale.getDefault())                                // mProductName=Android Phone
//                    dev_ser = usb_ser_get(device).toUpperCase(Locale.getDefault())                              // mSerialNumber=FA46RWM22264
//                } catch (e: Throwable) {
//                    AppLog.e(e)
//                }
//            }

            var usb_dev_name = ""
            usb_dev_name += VENDOR_NAMES[vendorId] ?: "$vendorId"
            usb_dev_name += " "
            usb_dev_name += Utils.hex_get(vendorId.toShort())
            usb_dev_name += ":"
            usb_dev_name += Utils.hex_get(productId.toShort())

            return usb_dev_name
        }

        fun isInAccessoryMode(device: UsbDevice): Boolean {
            val dev_vend_id = device.vendorId
            val dev_prod_id = device.productId
            return dev_vend_id == UsbDeviceCompat.USB_VID_GOO &&
                    (dev_prod_id == UsbDeviceCompat.USB_PID_ACC || dev_prod_id == UsbDeviceCompat.USB_PID_ACC_ADB)
        }

        private val ANDROID_VENDORS = setOf(
            USB_VID_GOO, // Google (0x18D1)
            USB_VID_HTC, // HTC (0x0BB4)
            USB_VID_SAM, // Samsung (0x04E8)
            USB_VID_O1A, // O1A (0xFFF6)
            USB_VID_SON, // Sony Ericsson (0x0FCE)
            USB_VID_LGE, // LG (0x1004)
            USB_VID_MOT, // Motorola (0x22B8)
            USB_VID_ACE, // Acer (0x0502)
            USB_VID_ZTE, // ZTE (0x19D2)
            USB_VID_XIA, // Xiaomi (0x2717)
            USB_VID_ASU, // Asus (0x0B05)
            USB_VID_MEI, // Meizu (0x2A45)
            USB_VID_WIL, // Wileyfox (0x4EE7)
            0x22D9,      // Oppo/OnePlus/Realme
            0x2D95,      // Vivo
            0x17EF,      // Lenovo
            0x1EBF,      // OnePlus (alternate)
            0x1782,      // Spreadtrum / Multilaser
            0x0E8D,      // MediaTek
            0x2E04,      // HMD Global (Nokia)
            0x05C6,      // Qualcomm/LG reference designs
            0x0FCA,      // Blackberry
            0x2207,      // Fuzhou Rockchip
            0x2E17,      // Essential
            // Added from 51-android.rules
            0x413C,      // Dell
            0x0489,      // Foxconn
            0x04C5,      // Fujitsu
            0x091E,      // Garmin-Asus
            0x201E,      // Haier
            0x109B,      // Hisense
            0x24E3,      // K-Touch
            0x2116,      // KT Tech
            0x0482,      // Kyocera
            0x0409,      // NEC
            0x2080,      // Nook
            0x0955,      // Nvidia
            0x2257,      // OTGV
            0x10A9,      // Pantech
            0x1D4D,      // Pegatron
            0x0471,      // Philips
            0x04DA,      // PMC-Sierra
            0x1F53,      // SK Telesys
            0x04DD,      // Sharp
            0x054C,      // Sony (main)
            0x2340,      // Teleepoch
            0x0930,      // Toshiba
            0x0414,      // Gigabyte
            0x1949       // Amazon
        )

        fun isAndroidDevice(device: UsbDevice): Boolean {
            if (isInAccessoryMode(device)) return true
            // Explicitly ignore Apple devices (0x05AC) to prevent CarPlay interference
            if (device.vendorId == 0x05AC) return false
            return ANDROID_VENDORS.contains(device.vendorId)
        }
    }
}
