package com.andrerinas.headunitrevived.decoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.andrerinas.headunitrevived.utils.AppLog
import com.andrerinas.headunitrevived.utils.Settings
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

// Listener to notify about video dimension changes
interface VideoDimensionsListener {
    fun onVideoDimensionsChanged(width: Int, height: Int)
}

class VideoDecoder(private val settings: Settings) {
    private var mCodec: MediaCodec? = null
    private var mCodecBufferInfo: MediaCodec.BufferInfo? = null

    private var mHeight: Int = 0
    private var mWidth: Int = 0
    private var mSurface: Surface? = null
    private var mCodecConfigured: Boolean = false

    // For asynchronous decoding
    private val freeInputBuffers: BlockingQueue<Int> = ArrayBlockingQueue(1024)
    private var callbackThread: HandlerThread? = null

    var dimensionsListener: VideoDimensionsListener? = null

    val videoWidth: Int
        get() = mWidth

    val videoHeight: Int
        get() = mHeight

    private val mCallback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            freeInputBuffers.offer(index)
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            try {
                codec.releaseOutputBuffer(index, true)
            } catch (e: Exception) {
                AppLog.e("Error releasing output buffer: ${e.message}")
            }
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            AppLog.e("MediaCodec error: ${e.message}")
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            AppLog.i("--- DECODER OUTPUT FORMAT CHANGED ---")
            AppLog.i("New video format: $format")
            val newWidth = try { format.getInteger(MediaFormat.KEY_WIDTH) } catch (e: Exception) { mWidth }
            val newHeight = try { format.getInteger(MediaFormat.KEY_HEIGHT) } catch (e: Exception) { mHeight }
            if (mWidth != newWidth || mHeight != newHeight) {
                AppLog.i("Video dimensions changed via format. New: ${newWidth}x$newHeight")
                mWidth = newWidth
                mHeight = newHeight
                dimensionsListener?.onVideoDimensionsChanged(mWidth, mHeight)
            }
            codec.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT)
        }
    }

    fun decode(buffer: ByteArray, offset: Int, size: Int, forceSoftware: Boolean, codecName: String) {
        synchronized(sLock) {
            if (mCodec == null) {
                val detectedType = Companion.detectCodecType(buffer, offset, size)
                val finalCodecName = if (detectedType != null) {
                    AppLog.i("VideoDecoder: Detected $detectedType in stream, using it instead of preference $codecName")
                    detectedType.name
                } else {
                    codecName
                }

                AppLog.i("Codec is not initialized, attempting to init with codec: $finalCodecName, forceSoftware: $forceSoftware")
                val mime = if (finalCodecName == "H265" || finalCodecName == "H.265") "video/hevc" else "video/avc"
                codec_init(mime, forceSoftware)
            }

            if (mCodec == null) {
                AppLog.e("Codec could not be initialized.")
                return
            }

            if (!mCodecConfigured) {
                try {
                    val mime = if (codecName == "H.265" || codecName == "H.265") "video/hevc" else "video/avc"
                    configureDecoder(mime)
                    mCodecConfigured = true
                    AppLog.i("VideoDecoder: Initial configuration complete, proceeding to feed first buffer.")
                } catch (e: Exception) {
                    AppLog.e("Failed to configure decoder", e)
                    codec_stop("Configuration failed")
                    return
                }
            }
            
            val presentationTimeUs = System.nanoTime() / 1000
            val content = ByteBuffer.wrap(buffer, offset, size)
            while (content.hasRemaining()) {
                if (!codec_input_provide(content, presentationTimeUs)) {
                    return
                }
            }
        }
    }

    private fun codec_init(mime: String, forceSoftware: Boolean) {
        synchronized(sLock) {
            try {
                val codecNameToUse = findBestCodec(mime, !forceSoftware)
                if (codecNameToUse == null) {
                    AppLog.e("No suitable decoder found for mime type $mime, forceSoftware: $forceSoftware")
                    return
                }
                AppLog.i("Selected decoder: $codecNameToUse for $mime (forceSoftware: $forceSoftware)")
                mCodec = MediaCodec.createByCodecName(codecNameToUse)
            } catch (t: Throwable) {
                AppLog.e("Throwable creating decoder for $mime: $t")
            }
        }
    }

    @Throws(IOException::class)
    private fun configureDecoder(mime: String) {
        val width = if (mWidth > 0) mWidth else 1920
        val height = if (mHeight > 0) mHeight else 1080
        
        val format = MediaFormat.createVideoFormat(mime, width, height)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 10485760)
        format.setInteger(MediaFormat.KEY_PRIORITY, 0)
        format.setFloat(MediaFormat.KEY_OPERATING_RATE, 120.0f)
        
        AppLog.i("VideoDecoder: configureDecoder with mime=$mime, target dimensions=${width}x${height}")
        try {
            if (callbackThread == null || !callbackThread!!.isAlive) {
                callbackThread = HandlerThread("VideoDecoderCallbackThread")
                callbackThread!!.start()
            }
            val handler = Handler(callbackThread!!.looper)
            
            mCodec!!.setCallback(mCallback, handler)
            mCodec!!.configure(format, mSurface, null, 0)
            mCodec!!.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            mCodec!!.start()
            mCodecBufferInfo = MediaCodec.BufferInfo()
            AppLog.i("Codec configured and started. Selected codec: ${mCodec?.name}")
        } catch (e: Exception) {
            AppLog.e("Codec configuration failed", e)
            throw e
        }
    }

    private fun codec_stop(reason: String) {
        synchronized(sLock) {
            if (mCodec != null) {
                try {
                    mCodec!!.stop()
                    mCodec!!.release()
                } catch (e: Exception) {
                    AppLog.e("Error during codec release: ${e.message}")
                }
            }
            mCodec = null
            mCodecBufferInfo = null
            mCodecConfigured = false
            frameStartTimes.clear()
            freeInputBuffers.clear()
            
            callbackThread?.quitSafely()
            callbackThread = null
            
            AppLog.i("Reason: $reason")
        }
    }

    private fun codec_input_provide(content: ByteBuffer, presentationTimeUs: Long): Boolean {
        try {
            // Wait up to 100ms for an input buffer. 
            // If the decoder is healthy, this should be almost instant.
            val inputBufIndex = freeInputBuffers.poll(100, TimeUnit.MILLISECONDS)
            
            if (inputBufIndex != null && inputBufIndex >= 0) {
                val buffer = mCodec!!.getInputBuffer(inputBufIndex)
                if (buffer == null) {
                    AppLog.e("Input buffer is null for index $inputBufIndex")
                    return false
                }

                buffer.clear()
                buffer.put(content)
                mCodec!!.queueInputBuffer(inputBufIndex, 0, buffer.limit(), presentationTimeUs, 0)
                return true
            } else {
                AppLog.e("dequeueInputBuffer timed out (queue empty). Frame will be dropped.")
                return false
            }
        } catch (t: Throwable) {
            AppLog.e("Error providing codec input", t)
            return false
        }
    }

    fun onSurfaceAvailable(surface: Surface) {
        synchronized(sLock) {
            if (mCodec != null) {
                AppLog.i("Codec is running, stopping for new surface")
                codec_stop("New surface")
            }
        }
        mSurface = surface
    }

    fun stop(reason: String) {
        codec_stop(reason)
    }

    enum class CodecType(val mimeType: String, val displayName: String) {
        H264("video/avc", "H.264/AVC"),
        H265("video/hevc", "H.265/HEVC");

        companion object {
            fun fromName(name: String): CodecType {
                return when (name) {
                    "H.265" -> H265
                    else -> H264
                }
            }
        }
    }

    companion object {
        private val sLock = Object()

        fun detectCodecType(buffer: ByteArray, offset: Int, size: Int): CodecType? {
            var i = offset
            val limit = offset + size - 5
            while (i < limit) {
                if (buffer[i] == 0.toByte() && buffer[i+1] == 0.toByte() && buffer[i+2] == 0.toByte() && buffer[i+3] == 1.toByte()) {
                    val header = buffer[i+4]
                    if ((header.toInt() and 0x1F) == 7) return CodecType.H264
                    if (((header.toInt() shr 1) and 0x3F) == 33) return CodecType.H265
                }
                i++
            }
            return null
        }

        private fun findBestCodec(mimeType: String, preferHardware: Boolean): String? {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            var hardwareCodec: String? = null
            var softwareCodec: String? = null

            for (codecInfo in codecList.codecInfos) {
                if (codecInfo.isEncoder) continue
                if (codecInfo.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }) {
                    if (isHardwareAccelerated(codecInfo)) {
                        if (hardwareCodec == null) hardwareCodec = codecInfo.name
                    } else {
                        if (softwareCodec == null) softwareCodec = codecInfo.name
                    }
                }
            }

            if (preferHardware && hardwareCodec != null) {
                AppLog.i("Selected hardware decoder: $hardwareCodec for $mimeType")
                return hardwareCodec
            }
            if (softwareCodec != null) {
                AppLog.i("Selected software decoder: $softwareCodec for $mimeType")
                return softwareCodec
            }
            if (hardwareCodec != null) {
                AppLog.i("Selected hardware decoder as fallback: $hardwareCodec for $mimeType")
                return hardwareCodec
            }
            return null
        }

        private fun isHardwareAccelerated(codecInfo: MediaCodecInfo): Boolean {
            val name = codecInfo.name.lowercase(Locale.ROOT)
            return !name.startsWith("omx.google.") &&
                    !name.startsWith("c2.android.") &&
                    !name.contains(".sw.")
        }
    }
}
