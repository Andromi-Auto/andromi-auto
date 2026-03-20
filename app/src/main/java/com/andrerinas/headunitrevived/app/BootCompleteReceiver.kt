package com.andrerinas.headunitrevived.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.andrerinas.headunitrevived.aap.AapService
import com.andrerinas.headunitrevived.main.MainActivity
import com.andrerinas.headunitrevived.utils.AppLog
import com.andrerinas.headunitrevived.utils.Settings

class BootCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val settings = Settings(context)
        if (!settings.autoStartOnBoot) {
            AppLog.i("Boot auto-start: disabled, skipping")
            return
        }

        AppLog.i("Boot auto-start: starting AapService")
        val serviceIntent = Intent(context, AapService::class.java).apply {
            putExtra(EXTRA_BOOT_START, true)
        }
        ContextCompat.startForegroundService(context, serviceIntent)

        // Launch the UI so the app is visible (not just a background service)
        if (Build.VERSION.SDK_INT < 23 || android.provider.Settings.canDrawOverlays(context)) {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(MainActivity.EXTRA_LAUNCH_SOURCE, "Boot auto-start")
            }
            try {
                context.startActivity(launchIntent)
            } catch (e: Exception) {
                AppLog.w("Could not start UI from boot auto-start: ${e.message}")
            }
        } else {
            AppLog.w("Boot auto-start: overlay permission not granted, cannot launch UI")
        }
    }

    companion object {
        const val EXTRA_BOOT_START = "com.andrerinas.headunitrevived.EXTRA_BOOT_START"
    }
}
