package com.andrerinas.headunitrevived.connection.carkey

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import com.andrerinas.headunitrevived.connection.CommManager
import com.andrerinas.headunitrevived.connection.carkey.fyt.CarFYTReceiver
import com.andrerinas.headunitrevived.contract.KeyIntent
import com.andrerinas.headunitrevived.utils.AppLog

interface CarKeyReceiver {

    companion object {
        @JvmStatic
        fun newDefaultReceivers(): Array<CarKeyReceiver> {
            return arrayOf(
                CarKeyBroadcastReceiver(),
                CarFYTReceiver(),
            )
        }
    }

    @Throws(Exception::class)
    fun register(context: Context)

    @Throws(Exception::class)
    fun unregister()


    /** Single key press or release — broadcasts for learning and projection handling. */
    fun handleKey(context: Context, commManager: CommManager, keyCode: Int, isDown: Boolean) {
        AppLog.d("CarKeyReceiver: Broadcasting key event: code=$keyCode, isDown=$isDown")
        context.sendBroadcast(
            Intent(KeyIntent.action).apply {
                setPackage(context.packageName)
                putExtra(
                    KeyIntent.extraEvent,
                    KeyEvent(
                        if (isDown) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, keyCode,
                    ),
                )
            },
        )
        commManager.sendKey(keyCode, isDown)
    }

    /** Full click (DOWN + UP) — broadcasts both events for learning AND sends to AA. */
    fun handleClick(context: Context, commManager: CommManager, keyCode: Int) {
        handleKey(context, commManager, keyCode, true)
        handleKey(context, commManager, keyCode, false)
    }

}
