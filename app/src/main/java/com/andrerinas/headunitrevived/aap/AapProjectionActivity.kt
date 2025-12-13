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
    private lateinit var screenSpec: ScreenSpec
    private val videoDecoder: VideoDecoder by lazy { App.provide(this).videoDecoder }
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

        val container = findViewById<android.widget.FrameLayout>(R.id.container)
        if (settings.viewMode == Settings.ViewMode.TEXTURE) {
            AppLog.i("Using TextureView")

            val displayMetrics = resources.displayMetrics
            val textureViewSpec = ScreenSpecProvider.getSpecForTextureView(displayMetrics.widthPixels, displayMetrics.heightPixels, displayMetrics.densityDpi)
            screenSpec = textureViewSpec.screenSpec

            val textureView = TextureProjectionView(this)
            textureView.layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
            projectionView = textureView
            container.setBackgroundColor(android.graphics.Color.BLACK)
        } else {
            AppLog.i("Using SurfaceView")
            screenSpec = ScreenSpecProvider.getSpec(this)
            projectionView = ProjectionView(this)
        }

        val view = projectionView as android.view.View
        container.addView(view)

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
        AppLog.i("[AapProjectionActivity] onSurfaceCreated")
        // Decoder configuration is now in onSurfaceChanged
    }

    override fun onSurfaceChanged(surface: android.view.Surface, width: Int, height: Int) {
        AppLog.i("[AapProjectionActivity] onSurfaceChanged. Actual surface dimensions: width=$width, height=$height")
        AppLog.i("[AapProjectionActivity] Configuring decoder with negotiated spec: ${screenSpec.width}x${screenSpec.height}")
        videoDecoder.onSurfaceAvailable(surface, screenSpec.width, screenSpec.height)
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
        val displayMetrics = resources.displayMetrics
        val textureViewSpec = ScreenSpecProvider.getSpecForTextureView(displayMetrics.widthPixels, displayMetrics.heightPixels, displayMetrics.densityDpi)

        // Scale touch events from the actual view size to the negotiated screen size.
        // The negotiated screen size is the resolution the phone is sending (e.g., 1920x1080).
        // The view.width/height is the actual size of the TextureView (e.g., 1920x720).
        val scaleX = textureViewSpec.screenSpec.width.toFloat() / view.width.toFloat()
        val scaleY = textureViewSpec.screenSpec.height.toFloat() / view.height.toFloat()

        val pointerData = mutableListOf<Triple<Int, Int, Int>>()
        repeat(event.pointerCount) { pointerIndex ->
            val pointerId = event.getPointerId(pointerIndex)
            // Adjust touch coordinates by the margins that the phone is applying
            val x = (event.getX(pointerIndex) + textureViewSpec.leftMargin) * scaleX
            val y = (event.getY(pointerIndex) + textureViewSpec.topMargin) * scaleY

            // Boundary check against the negotiated screen size
            if (x < 0 || x >= textureViewSpec.screenSpec.width || y < 0 || y >= textureViewSpec.screenSpec.height) {
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
