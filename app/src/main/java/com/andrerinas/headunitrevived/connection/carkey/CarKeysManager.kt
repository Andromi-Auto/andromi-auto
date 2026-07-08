package com.andrerinas.headunitrevived.connection.carkey

import android.content.Context
import com.andrerinas.headunitrevived.utils.AppLog

class CarKeysManager {

    private val receivers: Array<CarKeyReceiver> = CarKeyReceiver.newDefaultReceivers()
    private var isRegistered: Boolean = false

    fun registerReceivers(context: Context) {
        if (isRegistered)
            return

        isRegistered = true

        try {
            receivers.forEach { receiver -> receiver.register(context) }

            AppLog.d("AapService: CarKeyReceiver registered")
        } catch (e: Exception) {
            AppLog.e("AapService: Failed to register CarKeyReceivers", e)
        }
    }

    fun unregisterReceivers() {
        if (!isRegistered)
            return

        try {
            receivers.forEach { receiver -> receiver.unregister() }
        } catch (e: Exception) {
            AppLog.e("AapService: Failed to unregister CarKeyReceiver", e)
        } finally {
            isRegistered = false
        }
    }
}
