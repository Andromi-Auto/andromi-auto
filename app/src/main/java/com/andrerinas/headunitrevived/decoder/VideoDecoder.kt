package com.andrerinas.headunitrevived.decoder

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import com.andrerinas.headunitrevived.utils.AppLog
import java.io.IOException
import java.nio.ByteBuffer

class VideoDecoder {
    private var mCodec: MediaCodec? = null
    private var mCodecBufferInfo: MediaCodec.BufferInfo? = null
    private var mInputBuffers: Array<ByteBuffer>? = null

    private var mHeight: Int = 0
    private var mWidth: Int = 0
    private var mSurface: Surface? = null
    private var mCodecConfigured: Boolean = false
    private var sps: ByteArray? = null
    private var pps: ByteArray? = null

    fun decode(buffer: ByteArray, offset: Int, size: Int) {
        synchronized(sLock) {
            if (mCodec == null) {
                AppLog.v("Codec is not initialized")
                return
            }

            if (!mCodecConfigured) {
                var currentOffset = offset
                while (currentOffset < offset + size) {
                    val nalUnitSize = findNalUnitSize(buffer, currentOffset, offset + size)
                    if (nalUnitSize == -1) {
                        break // No more NAL units found
                    }

                    val nalUnitType = getNalType(buffer, currentOffset)
                    if (nalUnitType == 7) { // SPS
                        sps = buffer.copyOfRange(currentOffset, currentOffset + nalUnitSize)
                        AppLog.i("Got SPS sequence...")
                    } else if (nalUnitType == 8) { // PPS
                        pps = buffer.copyOfRange(currentOffset, currentOffset + nalUnitSize)
                        AppLog.i("Got PPS sequence...")
                    }
                    currentOffset += nalUnitSize
                }


                if (sps != null && pps != null) {
                    try {
                        configureDecoder(sps!!, pps!!)
                        mCodecConfigured = true
                    } catch (e: Exception) {
                        AppLog.e("Failed to configure decoder", e)
                    }
                }
                return
            }

            val content = ByteBuffer.wrap(buffer, offset, size)
            while (content.hasRemaining()) {
                if (!codec_input_provide(content)) {
                    AppLog.e("Dropping content because there are no available buffers.")
                    return
                }
                codecOutputConsume()
            }
        }
    }

    private fun codec_init() {
        synchronized(sLock) {
            try {
                mCodec = MediaCodec.createDecoderByType("video/avc")
            } catch (t: Throwable) {
                AppLog.e("Throwable creating video/avc decoder: $t")
            }
        }
    }

    @Throws(IOException::class)
    private fun configureDecoder(sps: ByteArray, pps: ByteArray) {
        val format = MediaFormat.createVideoFormat("video/avc", mWidth, mHeight)
        format.setByteBuffer("csd-0", ByteBuffer.wrap(sps))
        format.setByteBuffer("csd-1", ByteBuffer.wrap(pps))
        AppLog.i("VideoDecoder: configureDecoder with width=$mWidth, height=$mHeight")
        try {
            mCodec!!.configure(format, mSurface, null, 0)
            mCodec!!.start()
            mInputBuffers = mCodec!!.inputBuffers
            mCodecBufferInfo = MediaCodec.BufferInfo()
            AppLog.i("Codec configured and started")
        } catch (e: Exception) {
            AppLog.e("Codec configuration failed", e)
            throw e
        }
    }

    private fun codec_stop(reason: String) {
        synchronized(sLock) {
            if (mCodec != null) {
                mCodec!!.stop()
            }
            mCodec = null
            mInputBuffers = null
            mCodecBufferInfo = null
            mCodecConfigured = false
            sps = null
            pps = null
            AppLog.i("Reason: $reason")
        }
    }

    private fun codec_input_provide(content: ByteBuffer): Boolean {
        try {
            val inputBufIndex = mCodec!!.dequeueInputBuffer(10000)
            if (inputBufIndex < 0) {
                AppLog.e("dequeueInputBuffer: $inputBufIndex")
                return false
            }

            val buffer = mInputBuffers!![inputBufIndex]
            buffer.clear()
            buffer.put(content)
            mCodec!!.queueInputBuffer(inputBufIndex, 0, buffer.limit(), 0, 0)
            return true
        } catch (t: Throwable) {
            AppLog.e(t)
        }
        return false
    }

    private fun codecOutputConsume() {
        var index: Int
        while (true) {
            index = mCodec!!.dequeueOutputBuffer(mCodecBufferInfo!!, 0)
            if (index >= 0) {
                mCodec!!.releaseOutputBuffer(index, true)
            } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                AppLog.i("INFO_OUTPUT_BUFFERS_CHANGED")
                mInputBuffers = mCodec!!.inputBuffers
            } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val outputFormat = mCodec!!.outputFormat
                AppLog.i("--- DECODER OUTPUT FORMAT CHANGED ---")
                AppLog.i("New video format: $outputFormat")
            } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break
            } else {
                break
            }
        }
    }

    fun onSurfaceAvailable(surface: Surface, width: Int, height: Int) {
        synchronized(sLock) {
            if (mCodec != null) {
                AppLog.i("Codec is running")
                return
            }
        }

        mSurface = surface
        mWidth = width
        mHeight = height
        codec_init()
    }

    fun stop(reason: String) {
        codec_stop(reason)
    }

    companion object {
        private val sLock = Object()

        private fun findNalUnitSize(buffer: ByteArray, offset: Int, limit: Int): Int {
            var i = offset + 4 // Start after the 0x00 00 00 01 start code
            while (i < limit - 3) {
                if (buffer[i].toInt() == 0 && buffer[i + 1].toInt() == 0 && buffer[i + 2].toInt() == 0 && buffer[i + 3].toInt() == 1) {
                    return i - offset
                }
                i++
            }
            return limit - offset // Last NAL unit
        }

        private fun isSps(ba: ByteArray, offset: Int): Boolean {
            return getNalType(ba, offset) == 7
        }

        private fun isPps(ba: ByteArray, offset: Int): Boolean {
            return getNalType(ba, offset) == 8
        }

        private fun getNalType(ba: ByteArray, offset: Int): Int {
            // NAL unit type is in the byte after the start code (0x00 00 00 01)
            // The NAL unit type is the last 5 bits of that byte
            return ba[offset + 4].toInt() and 0x1f
        }
    }
}
