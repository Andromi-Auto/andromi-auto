package com.andrerinas.headunitrevived.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView
import com.andrerinas.headunitrevived.utils.AppLog

class BitmapProjectionView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr), IProjectionView, TextureView.SurfaceTextureListener {

    private val callbacks = mutableListOf<IProjectionView.Callbacks>()
    private var bitmap: Bitmap? = null
    private var surface: Surface? = null

    init {
        surfaceTextureListener = this
        isOpaque = false // Make TextureView non-opaque
    }

    fun setBitmap(bmp: Bitmap) {
        this.bitmap = bmp
        drawBitmap() // Call drawBitmap directly when bitmap is set
    }

    private fun drawBitmap() {
        AppLog.i("BitmapProjectionView", "drawBitmap() called.")
        val currentBitmap = bitmap ?: return
        val currentSurface = surface ?: return

        var canvas: Canvas? = null
        try {
            AppLog.i("BitmapProjectionView", "Attempting to lock canvas...")
            canvas = currentSurface.lockCanvas(null)
            AppLog.i("BitmapProjectionView", "Canvas after lockCanvas: $canvas") // Log canvas object itself

            if (canvas == null) {
                AppLog.e("BitmapProjectionView", "Canvas is null from lockCanvas! Cannot draw.")
                return // Exit if canvas is null
            }

            AppLog.i("BitmapProjectionView", "Canvas dimensions: ${canvas.width}x${canvas.height}")
            // Clear the canvas before drawing
            canvas.drawColor(android.graphics.Color.TRANSPARENT) // Clear with transparent color
            // Draw the bitmap scaled to the TextureView's current dimensions
            val destRect = android.graphics.Rect(0, 0, width, height) // Use TextureView's width and height
            canvas.drawBitmap(currentBitmap, null, destRect, null)
        } catch (e: Exception) {
            AppLog.e("BitmapProjectionView", "Error drawing bitmap: ${e.message}")
        } finally {
            canvas?.let {
                currentSurface.unlockCanvasAndPost(it)
            }
        }
    }

    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        AppLog.i("BitmapProjectionView: surfaceTexture available: width=$width, height=$height")
        surface = Surface(surfaceTexture)
        drawBitmap() // Draw immediately when surface is available
    }

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        AppLog.i("BitmapProjectionView: surfaceTexture size changed: width=$width, height=$height")
        drawBitmap() // Redraw on size change
    }

    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        AppLog.i("BitmapProjectionView: surfaceTexture destroyed")
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
        // Not used
    }

    // IProjectionView implementations
    override fun addCallback(callback: IProjectionView.Callbacks) {
        callbacks.add(callback)
    }

    override fun removeCallback(callback: IProjectionView.Callbacks) {
        callbacks.remove(callback)
    }
}
