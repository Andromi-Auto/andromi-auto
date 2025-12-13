package com.andrerinas.headunitrevived.aap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import com.andrerinas.headunitrevived.App
import com.andrerinas.headunitrevived.R
import com.andrerinas.headunitrevived.aap.protocol.messages.TouchEvent
import com.andrerinas.headunitrevived.aap.protocol.messages.VideoFocusEvent
import com.andrerinas.headunitrevived.app.SurfaceActivity
import com.andrerinas.headunitrevived.contract.KeyIntent
import com.andrerinas.headunitrevived.decoder.VideoDecoder
import com.andrerinas.headunitrevived.utils.AppLog
import com.andrerinas.headunitrevived.utils.IntentFilters
import com.andrerinas.headunitrevived.utils.ScreenSpec
import com.andrerinas.headunitrevived.utils.ScreenSpecProvider
import com.andrerinas.headunitrevived.view.IProjectionView
import com.andrerinas.headunitrevived.view.ProjectionView
import com.andrerinas.headunitrevived.view.TextureProjectionView
import com.andrerinas.headunitrevived.utils.Settings

class AapProjectionActivity : SurfaceActivity(), IProjectionView.Callbacks {

    private lateinit var projectionView: IProjectionView
    private val videoDecoder: VideoDecoder by lazy { App.provide(this).videoDecoder }
    private val screenSpec: ScreenSpec by lazy { ScreenSpecProvider.getSpec(this) }
    private val settings: Settings by lazy { Settings(this) }


    private val disconnectReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            finish()
        }
    }

    private val keyCodeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val event: KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(KeyIntent.extraEvent, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(KeyIntent.extraEvent)
            }
            event?.let {
                onKeyEvent(it.keyCode, it.action == KeyEvent.ACTION_DOWN)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_headunit)

        AppLog.i("HeadUnit for Android Auto (tm) - Copyright 2011-2015 Michael A. Reid. All Rights Reserved...")

        projectionView = if (settings.viewMode == Settings.ViewMode.TEXTURE) {
            TextureProjectionView(this)
        } else {
            ProjectionView(this)
        }

        val view = projectionView as android.view.View
        findViewById<android.widget.FrameLayout>(R.id.container).addView(view)

        if (projectionView is TextureProjectionView) {
            view.post {
                val layoutParams = view.layoutParams as android.widget.FrameLayout.LayoutParams
                val margin = (1080 - 720) / 2
                layoutParams.topMargin = -margin
                layoutParams.bottomMargin = -margin
                view.layoutParams = layoutParams
            }
        }

        projectionView.addCallback(this)

        view.setOnTouchListener { _, event ->
            sendTouchEvent(event)
            true
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(disconnectReceiver)
        unregisterReceiver(keyCodeReceiver)
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
            registerReceiver(disconnectReceiver, IntentFilters.disconnect, RECEIVER_NOT_EXPORTED)
            registerReceiver(keyCodeReceiver, IntentFilters.keyEvent, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(disconnectReceiver, IntentFilters.disconnect)
            registerReceiver(keyCodeReceiver, IntentFilters.keyEvent)
        }
    }

    val transport: AapTransport
        get() = App.provide(this).transport

    override fun onSurfaceCreated(surface: android.view.Surface) {
        AppLog.i("[AapProjectionActivity] surfaceCreated. Configuring decoder with negotiated spec: ${screenSpec.width}x${screenSpec.height}")
        videoDecoder.onSurfaceAvailable(surface, screenSpec.width, screenSpec.height)
    }

    override fun onSurfaceChanged(surface: android.view.Surface, width: Int, height: Int) {
        AppLog.i("[AapProjectionActivity] surfaceChanged. Actual surface dimensions: width=$width, height=$height")
        transport.send(VideoFocusEvent(gain = true, unsolicited = false))
    }

    override fun onSurfaceDestroyed(surface: android.view.Surface) {
        transport.send(VideoFocusEvent(gain = false, unsolicited = false))
        videoDecoder.stop("surfaceDestroyed")
    }

    private fun sendTouchEvent(event: MotionEvent) {
        val action = TouchEvent.motionEventToAction(event) ?: return
        val ts = SystemClock.elapsedRealtime()

        val view = projectionView as android.view.View
        // Scale touch events from the actual surface size to the negotiated screen size.
        val scaleX = screenSpec.width.toFloat() / view.width.toFloat()
        val scaleY = screenSpec.height.toFloat() / view.height.toFloat()

        val pointerData = mutableListOf<Triple<Int, Int, Int>>()
        repeat(event.pointerCount) { pointerIndex ->
            val pointerId = event.getPointerId(pointerIndex)
            val x = event.getX(pointerIndex) * scaleX
            val y = event.getY(pointerIndex) * scaleY

            // Boundary check against the negotiated screen size
            if (x < 0 || x >= screenSpec.width || y < 0 || y >= screenSpec.height) {
                AppLog.w("Touch event out of bounds of negotiated screen spec, skipping. x=$x, y=$y, spec=$screenSpec")
                return
            }
            pointerData.add(Triple(pointerId, x.toInt(), y.toInt()))
        }

        transport.send(TouchEvent(ts, action, event.actionIndex, pointerData))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        AppLog.i("KeyCode: %d", keyCode)
        // PRes navigation on the screen
        onKeyEvent(keyCode, true)
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        AppLog.i("onKeyUp: %d", keyCode)
        onKeyEvent(keyCode, false)
        return super.onKeyUp(keyCode, event)
    }


    private fun onKeyEvent(keyCode: Int, isPress: Boolean) {
        transport.send(keyCode, isPress)
    }

    companion object {
        const val EXTRA_FOCUS = "focus"

        fun intent(context: Context): Intent {
            val aapIntent = Intent(context, AapProjectionActivity::class.java)
            aapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return aapIntent
        }
    }
}
