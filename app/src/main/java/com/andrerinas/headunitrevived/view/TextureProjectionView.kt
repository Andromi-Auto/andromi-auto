package com.andrerinas.headunitrevived.view

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView
import com.andrerinas.headunitrevived.App
import com.andrerinas.headunitrevived.decoder.VideoDecoder
import com.andrerinas.headunitrevived.utils.AppLog

class TextureProjectionView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr), IProjectionView, TextureView.SurfaceTextureListener {

    private val callbacks = mutableListOf<IProjectionView.Callbacks>()
    private var videoDecoder: VideoDecoder? = null
    private var surface: Surface? = null
    private var viewWidth = 0
    private var viewHeight = 0
    private var isDecoderConfigured = false

    // Add properties to store desired content size
    private var desiredContentWidth: Int = 0
    private var desiredContentHeight: Int = 0

    init {
        videoDecoder = App.provide(context).videoDecoder
        surfaceTextureListener = this
    }

    // Add a setter for desired content size
    fun setDesiredContentSize(width: Int, height: Int) {
        desiredContentWidth = width
        desiredContentHeight = height
        // If surface is already available, apply buffer size immediately
        if (surface != null && surfaceTexture != null) {
            surfaceTexture?.setDefaultBufferSize(desiredContentWidth, desiredContentHeight)
            AppLog.i("TextureProjectionView: setDefaultBufferSize called from setDesiredContentSize with: ${desiredContentWidth}x${desiredContentHeight}")
        }
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        AppLog.i("TextureProjectionView: surfaceTexture available: width=$width, height=$height") // Added logging
        surface = Surface(surfaceTexture)
        if (desiredContentWidth > 0 && desiredContentHeight > 0) {
            surfaceTexture.setDefaultBufferSize(desiredContentWidth, desiredContentHeight)
            AppLog.i("TextureProjectionView: setDefaultBufferSize called from onSurfaceTextureAvailable with: ${desiredContentWidth}x${desiredContentHeight}")
        }
        tryConfigureDecoder()
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        AppLog.i("TextureProjectionView: surfaceTexture size changed: width=$width, height=$height") // Added logging
        viewWidth = width
        viewHeight = height
        if (desiredContentWidth > 0 && desiredContentHeight > 0) {
            surfaceTexture.setDefaultBufferSize(desiredContentWidth, desiredContentHeight)
            AppLog.i("TextureProjectionView: setDefaultBufferSize called from onSurfaceTextureSizeChanged with: ${desiredContentWidth}x${desiredContentHeight}")
        }
        tryConfigureDecoder()
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        AppLog.i("TextureProjectionView: surfaceTexture destroyed")
        surface?.let {
            callbacks.forEach { cb -> cb.onSurfaceDestroyed(it) }
        }
        videoDecoder?.stop("surfaceDestroyed")
        surface?.release()
        surface = null
        isDecoderConfigured = false
        return false // As per decompiled code analysis
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
        // Not used
    }

    private fun tryConfigureDecoder() {
        if (surface != null && viewWidth > 0 && viewHeight > 0 && !isDecoderConfigured) {
            AppLog.i("TextureProjectionView: Configuring decoder now")
            surface?.let {
                // We call onSurfaceChanged from AapProjectionActivity, which will configure the decoder
                callbacks.forEach { cb -> cb.onSurfaceChanged(it, viewWidth, viewHeight) }
                isDecoderConfigured = true
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        AppLog.i("TextureProjectionView: onMeasure: width=${MeasureSpec.toString(widthMeasureSpec)}, height=${MeasureSpec.toString(heightMeasureSpec)}")
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        AppLog.i("TextureProjectionView: onLayout: changed=$changed, left=$left, top=$top, right=$right, bottom=$bottom")
        if (changed) {
            viewWidth = right - left
            viewHeight = bottom - top
            tryConfigureDecoder()
        }
    }

    override fun addCallback(callback: IProjectionView.Callbacks) {
        callbacks.add(callback)
    }

    override fun removeCallback(callback: IProjectionView.Callbacks) {
        callbacks.remove(callback)
    }
}
