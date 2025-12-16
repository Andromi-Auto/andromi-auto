package com.andrerinas.headunitrevived.view

import android.view.View
import com.andrerinas.headunitrevived.utils.AppLog

object ProjectionViewScaler {

    fun updateScale(view: View, videoWidth: Int, videoHeight: Int) {
        if (videoWidth == 0 || videoHeight == 0 || view.width == 0 || view.height == 0) {
            return
        }

        // The dimensions of the content area we want to display
        val displayMetrics = view.resources.displayMetrics
        val contentWidth = displayMetrics.widthPixels
        val contentHeight = displayMetrics.heightPixels

        val sourceVideoWidth = videoWidth.toFloat()
        val sourceVideoHeight = videoHeight.toFloat()

        // This is the magic.
        // We scale the View itself. Because the default pivot point is the
        // center, this effectively zooms into the center of the video stream.
        // The scale factor is the ratio of the full video size to the desired cropped content size.
        val finalScaleX = (sourceVideoWidth / contentWidth) * 1.0f
        val finalScaleY = (sourceVideoHeight / contentHeight) * 1.0f

        view.scaleX = finalScaleX
        view.scaleY = finalScaleY
        AppLog.i("ProjectionViewScaler", "Dimensions: Video: ${videoWidth}x$videoHeight, Content: ${contentWidth}x$contentHeight, View: ${view.width}x${view.height}")
        AppLog.i("ProjectionViewScaler", "Scale updated for view ${view.javaClass.simpleName}. scaleX: $finalScaleX, scaleY: $finalScaleY")
    }
}
