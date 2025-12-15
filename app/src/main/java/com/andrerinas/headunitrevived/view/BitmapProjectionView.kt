package com.andrerinas.headunitrevived.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView
import com.andrerinas.headunitrevived.utils.AppLog

class BitmapProjectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr),
    TextureView.SurfaceTextureListener,
    IProjectionView {

    private var bitmap: Bitmap? = null
    private var surface: Surface? = null

    private var surfaceReady = false
    private var viewReady = false

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    // ----------------------------------------------------------------
    // Public API
    // ----------------------------------------------------------------

    fun setBitmap(bmp: Bitmap) {
        bitmap = bmp
        drawBitmapIfPossible()
    }

    // ----------------------------------------------------------------
    // Lifecycle
    // ----------------------------------------------------------------

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
        AppLog.i("BitmapProjectionView", "Surface available ${w}x$h")
        surface = Surface(st)
        surfaceReady = true
        drawBitmapIfPossible()
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
        AppLog.i("BitmapProjectionView", "Surface size changed ${w}x$h")
        drawBitmapIfPossible()
    }

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        AppLog.i("BitmapProjectionView", "Surface destroyed")
        surfaceReady = false
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewReady = w > 0 && h > 0
        drawBitmapIfPossible()
    }

    // ----------------------------------------------------------------
    // Core rendering (DAS IST DER WICHTIGE TEIL)
    // ----------------------------------------------------------------

    private fun drawBitmapIfPossible() {
        if (!surfaceReady || !viewReady) return
        val bmp = bitmap ?: return
        val sfc = surface ?: return

        var canvas: Canvas? = null
        try {
            canvas = sfc.lockCanvas(null)
            canvas ?: return

            // Clear
            canvas.drawColor(Color.BLACK)

            // --- SOURCE: zentriertes 1848x720 aus 1920x1080 ---
            val srcLeft = (bmp.width - 1848) / 2      // 36
            val srcTop = (bmp.height - 720) / 2       // 180
            val srcRect = Rect(
                srcLeft,
                srcTop,
                srcLeft + 1848,
                srcTop + 720
            )

            // --- DEST: komplette View ---
            val dstRect = Rect(0, 0, width, height)

            canvas.drawBitmap(bmp, srcRect, dstRect, null)

        } catch (e: Exception) {
            AppLog.e("BitmapProjectionView", "Draw error: ${e.message}")
        } finally {
            canvas?.let { sfc.unlockCanvasAndPost(it) }
        }
    }

    // ----------------------------------------------------------------
    // IProjectionView (unverändert)
    // ----------------------------------------------------------------

    override fun addCallback(callback: IProjectionView.Callbacks) {}
    override fun removeCallback(callback: IProjectionView.Callbacks) {}
}
