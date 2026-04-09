package com.andrerinas.headunitrevived.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.lifecycle.lifecycleScope
import com.andrerinas.headunitrevived.R
import com.andrerinas.headunitrevived.app.BaseActivity
import com.andrerinas.headunitrevived.utils.Settings
import com.andrerinas.headunitrevived.utils.SystemUI
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        
        applyFullscreen()

        lifecycleScope.launch {
            delay(1000)
            // If a connection was established during the splash (e.g. USB auto-connect),
            // go directly to the projection activity to avoid a singleTask conflict.
            if (com.andrerinas.headunitrevived.App.provide(this@SplashActivity).commManager.isConnected) {
                startActivity(com.andrerinas.headunitrevived.aap.AapProjectionActivity.intent(this@SplashActivity))
            } else {
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            }
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    private fun applyFullscreen() {
        val root = findViewById<View>(android.R.id.content)
        SystemUI.apply(window, root, Settings.FullscreenMode.STATUS_ONLY)
    }
}
