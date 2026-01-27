package com.andrerinas.headunitrevived.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.andrerinas.headunitrevived.App
import com.andrerinas.headunitrevived.aap.AapService

import com.andrerinas.headunitrevived.location.GpsLocationService

class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {

        val h = Handler(Looper.getMainLooper())
        h.postDelayed({
            val serviceIntent = Intent(context, AapService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }, 10000)
    }
}
