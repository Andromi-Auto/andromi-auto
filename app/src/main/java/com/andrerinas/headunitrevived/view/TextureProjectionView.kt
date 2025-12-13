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

    init {
        videoDecoder = App.provide(context).videoDecoder
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        AppLog.i("surfaceTexture available")
        surface = Surface(surfaceTexture)
        surface?.let {
            callbacks.forEach { cb -> cb.onSurfaceCreated(it) }
            videoDecoder?.onSurfaceAvailable(it, width, height)
        }
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        AppLog.i("surfaceTexture size changed")
        surface?.let {
            callbacks.forEach { cb -> cb.onSurfaceChanged(it, width, height) }
        }
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        AppLog.i("surfaceTexture destroyed")
        surface?.let {
            callbacks.forEach { cb -> cb.onSurfaceDestroyed(it) }
        }
        videoDecoder?.stop("surfaceDestroyed")
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
        // Not used
    }

    override fun addCallback(callback: IProjectionView.Callbacks) {
        callbacks.add(callback)
    }

    override fun removeCallback(callback: IProjectionView.Callbacks) {
        callbacks.remove(callback)
    }
}
