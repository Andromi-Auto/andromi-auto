package com.andrerinas.headunitrevived.decoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.view.Surface
import com.andrerinas.headunitrevived.utils.AppLog
import com.andrerinas.headunitrevived.utils.Settings
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.pow

// Listener to notify about video dimension changes
interface VideoDimensionsListener {
    fun onVideoDimensionsChanged(width: Int, height: Int)
}

class VideoDecoder(private val settings: Settings) {
    private var mCodec: MediaCodec? = null
    private var mCodecBufferInfo: MediaCodec.BufferInfo? = null
    private var mInputBuffers: Array<ByteBuffer>? = null

    private var mHeight: Int = 0
    private var mWidth: Int = 0
    private var mSurface: Surface? = null
    private var mCodecConfigured: Boolean = false
    private var sps: ByteArray? = null
    private var pps: ByteArray? = null

    // For performance logging
    private var lastFrameTimestamp: Long = 0L
    private var totalFramesDecoded = 0
    private var totalDecodeTime = 0L
    private val frameStartTimes = mutableMapOf<Long, Long>() // Map presentation time to start time

    var dimensionsListener: VideoDimensionsListener? = null

    val videoWidth: Int
        get() = mWidth

    val videoHeight: Int
        get() = mHeight

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
                // Fix: Check for "H265" (enum name) OR "H.265" (settings value)
                val mime = if (finalCodecName == "H265" || finalCodecName == "H.265") "video/hevc" else "video/avc"
                codec_init(mime, forceSoftware)
            }

            if (mCodec == null) {
                AppLog.e("Codec could not be initialized.")
                return
            }

            if (!mCodecConfigured) {
                try {
                    // Fix: Don't access mCodec.outputFormat here as codec might not be configured yet.
                    // Rely on the detected/preferred codec name.
                    val mime = if (codecName == "H.265" || codecName == "H.265") "video/hevc" else "video/avc"
                    configureDecoder(mime)
                    mCodecConfigured = true
                    AppLog.i("VideoDecoder: Initial configuration complete, proceeding to feed first buffer.")
                } catch (e: Exception) {
                    AppLog.e("Failed to configure decoder", e)
                    codec_stop("Configuration failed") // Ensure clean state for retry
                    return
                }
                // Note: No return here! We proceed to feed this first buffer (containing VPS/SPS/PPS) into the codec.
            }
            
            val presentationTimeUs = System.nanoTime() / 1000
            frameStartTimes[presentationTimeUs] = System.currentTimeMillis()

            val content = ByteBuffer.wrap(buffer, offset, size)
            while (content.hasRemaining()) {
                if (!codec_input_provide(content, presentationTimeUs)) {
                    AppLog.e("Dropping content because there are no available buffers.")
                    return
                }
                codecOutputConsume()
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
        // Use standard dimensions if not known yet; MediaCodec will update on format change
        val width = if (mWidth > 0) mWidth else 1920
        val height = if (mHeight > 0) mHeight else 1080
        
        val format = MediaFormat.createVideoFormat(mime, width, height)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 10485760) // Set max input size to ~10MB
        format.setInteger(MediaFormat.KEY_PRIORITY, 0) // Real-time priority
        format.setFloat(MediaFormat.KEY_OPERATING_RATE, 120.0f) // Request high operating rate (120fps)
        
        AppLog.i("VideoDecoder: configureDecoder with mime=$mime, target dimensions=${width}x${height}")
        try {
            mCodec!!.configure(format, mSurface, null, 0)
            mCodec!!.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            mCodec!!.start()
            mInputBuffers = mCodec!!.inputBuffers
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
            mInputBuffers = null
            mCodecBufferInfo = null
            mCodecConfigured = false
            sps = null
            pps = null
            frameStartTimes.clear()
            AppLog.i("Reason: $reason")
        }
    }

    private fun codec_input_provide(content: ByteBuffer, presentationTimeUs: Long): Boolean {
        var retryCount = 0
        val maxRetries = 20 // Be more patient
        
        while (retryCount < maxRetries) {
            try {
                val inputBufIndex = mCodec!!.dequeueInputBuffer(10000)
                if (inputBufIndex >= 0) {
                    val buffer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        mCodec!!.getInputBuffer(inputBufIndex)
                    } else {
                        mInputBuffers!![inputBufIndex]
                    }
                    if (buffer == null) {
                        AppLog.e("Input buffer is null for index $inputBufIndex")
                        return false
                    }

                    buffer.clear()
                    buffer.put(content)
                    mCodec!!.queueInputBuffer(inputBufIndex, 0, buffer.limit(), presentationTimeUs, 0)
                    return true
                } else if (inputBufIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // Decoder is busy. Try to free up output buffers and then retry.
                    codecOutputConsume()
                    retryCount++
                    Thread.sleep(5) // Wait a bit for the hardware to catch up
                } else {
                    AppLog.e("Unexpected dequeueInputBuffer result: $inputBufIndex")
                    return false
                }
            } catch (t: Throwable) {
                AppLog.e("Error providing codec input", t)
                return false
            }
        }
        
        AppLog.e("dequeueInputBuffer timed out after $maxRetries retries. Frame will be dropped.")
        return false
    }

    private fun codecOutputConsume() {
        var index: Int
        while (true) {
            index = try {
                mCodec!!.dequeueOutputBuffer(mCodecBufferInfo!!, 0)
            } catch (e: Exception) {
                AppLog.e("dequeueOutputBuffer failed: $e")
                -1
            }

            if (index >= 0) {
                val presentationTimeUs = mCodecBufferInfo!!.presentationTimeUs
                val startTime = frameStartTimes.remove(presentationTimeUs)
                if (startTime != null) {
                    val decodeTime = System.currentTimeMillis() - startTime
                    totalFramesDecoded++
                    totalDecodeTime += decodeTime
                    if (totalFramesDecoded % 60 == 0) {
                        AppLog.i("Decoder Performance: Avg decode time: ${totalDecodeTime / totalFramesDecoded}ms, Last frame: ${decodeTime}ms")
                    }
                }

                mCodec!!.releaseOutputBuffer(index, true)
            } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                AppLog.i("INFO_OUTPUT_BUFFERS_CHANGED")
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
                    mInputBuffers = mCodec!!.inputBuffers
                }
            } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val outputFormat = mCodec!!.outputFormat
                AppLog.i("--- DECODER OUTPUT FORMAT CHANGED ---")
                AppLog.i("New video format: $outputFormat")
                // The format change might contain the definitive dimensions
                val newWidth = try { outputFormat.getInteger(MediaFormat.KEY_WIDTH) } catch (e: Exception) { mWidth }
                val newHeight = try { outputFormat.getInteger(MediaFormat.KEY_HEIGHT) } catch (e: Exception) { mHeight }
                if (mWidth != newWidth || mHeight != newHeight) {
                    AppLog.i("Video dimensions changed via format. New: ${newWidth}x$newHeight")
                    mWidth = newWidth
                    mHeight = newHeight
                    dimensionsListener?.onVideoDimensionsChanged(mWidth, mHeight)
                }
            } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break
            } else {
                break
            }
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
        // Init will be called on first decode call
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
            // Search for NAL units and identify type
            var i = offset
            val limit = offset + size - 5
            while (i < limit) {
                if (buffer[i] == 0.toByte() && buffer[i+1] == 0.toByte() && buffer[i+2] == 0.toByte() && buffer[i+3] == 1.toByte()) {
                    val header = buffer[i+4]
                    // H.264 SPS is type 7
                    if ((header.toInt() and 0x1F) == 7) return CodecType.H264
                    // H.265 SPS is type 33 (header >> 1 & 0x3F)
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
            AppLog.e("No suitable decoder found for mime type: $mimeType. Prefer Hardware: $preferHardware")
            return null
        }

        private fun isHardwareAccelerated(codecInfo: MediaCodecInfo): Boolean {
            val name = codecInfo.name.lowercase(Locale.ROOT)
            return !name.startsWith("omx.google.") &&
                    !name.startsWith("c2.android.") &&
                    !name.contains(".sw.")
        }

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

        private fun getNalType(ba: ByteArray, offset: Int): Int {
            // NAL unit type is in the byte after the start code (0x00 00 00 01)
            val header = ba[offset + 4].toInt()
            // Detection for H.264
            val h264Type = header and 0x1f
            if (h264Type in 1..12) return h264Type
            
            // Detection for H.265 (HEVC)
            return (header shr 1) and 0x3f
        }
    }
}

// Helper class for reading bits from a byte array
private class BitReader(private val buffer: ByteArray) {
    private var bitPosition = 0

    fun readBit(): Int {
        val byteIndex = bitPosition / 8
        val bitIndex = 7 - (bitPosition % 8)
        bitPosition++
        return (buffer[byteIndex].toInt() shr bitIndex) and 1
    }

    fun readBits(count: Int): Int {
        var result = 0
        for (i in 0 until count) {
            result = (result shl 1) or readBit()
        }
        return result
    }

    // Reads unsigned exponential-golomb coded integer
    fun readUE(): Int {
        var leadingZeroBits = 0
        while (readBit() == 0) {
            leadingZeroBits++
        }
        if (leadingZeroBits == 0) {
            return 0
        }
        val codeNum = (2.0.pow(leadingZeroBits.toDouble()) - 1 + readBits(leadingZeroBits)).toInt()
        return codeNum
    }
}

data class SpsData(val width: Int, val height: Int)

private object SpsParser {
    fun parse(sps: ByteArray): SpsData? {
        // We need to skip the NAL unit header (e.g., 00 00 00 01 67 ...)
        // Let's find the start of the SPS payload
        var payloadIndex = 4 // Default for 00 00 00 01
        if (sps[0].toInt() == 0 && sps[1].toInt() == 0 && sps[2].toInt() == 1) {
            payloadIndex = 3
        }

        // We only need to parse up to the dimensions, no need for a full SPS parser
        try {
            val reader = BitReader(sps.copyOfRange(payloadIndex, sps.size))
            reader.readBits(8) // NAL unit type, already know it's 7, but read it from payload
            val profileIdc = reader.readBits(8)
            reader.readBits(16) // flags and level_idc
            reader.readUE() // seq_parameter_set_id

            if (profileIdc == 100 || profileIdc == 110 || profileIdc == 122 || profileIdc == 244 || profileIdc == 44 || profileIdc == 83 || profileIdc == 86 || profileIdc == 118 || profileIdc == 128) {
                val chromaFormatIdc = reader.readUE()
                if (chromaFormatIdc == 3) {
                    reader.readBit() // separate_colour_plane_flag
                }
                reader.readUE() // bit_depth_luma_minus8
                reader.readUE() // bit_depth_chroma_minus8
                reader.readBit() // qpprime_y_zero_transform_bypass_flag
                val seqScalingMatrixPresentFlag = reader.readBit()
                if (seqScalingMatrixPresentFlag == 1) {
                    for (i in 0 until if (chromaFormatIdc != 3) 8 else 12) {
                        val seqScalingListPresentFlag = reader.readBit()
                        if (seqScalingListPresentFlag == 1) {
                            // Skip scaling list data
                            var lastScale = 8
                            var nextScale = 8
                            val sizeOfScalingList = if (i < 6) 16 else 64
                            for (j in 0 until sizeOfScalingList) {
                                if (nextScale != 0) {
                                    val deltaScale = reader.readUE() // Can be signed, but we just skip
                                    nextScale = (lastScale + deltaScale + 256) % 256
                                }
                                if (nextScale != 0) {
                                    lastScale = nextScale
                                }
                            }
                        }
                    }
                }
            }

            reader.readUE() // log2_max_frame_num_minus4
            val picOrderCntType = reader.readUE()
            if (picOrderCntType == 0) {
                reader.readUE() // log2_max_pic_order_cnt_lsb_minus4
            } else if (picOrderCntType == 1) {
                reader.readBit() // delta_pic_order_always_zero_flag
                reader.readUE() // offset_for_non_ref_pic (signed)
                reader.readUE() // offset_for_top_to_bottom_field (signed)
                val numRefFramesInPicOrderCntCycle = reader.readUE()
                for (i in 0 until numRefFramesInPicOrderCntCycle) {
                    reader.readUE() // offset_for_ref_frame (signed)
                }
            }

            reader.readUE() // max_num_ref_frames
            reader.readBit() // gaps_in_frame_num_value_allowed_flag

            val picWidthInMbsMinus1 = reader.readUE()
            val picHeightInMapUnitsMinus1 = reader.readUE()
            val frameMbsOnlyFlag = reader.readBit()

            val width = (picWidthInMbsMinus1 + 1) * 16
            var height = (2 - frameMbsOnlyFlag) * (picHeightInMapUnitsMinus1 + 1) * 16

            if (frameMbsOnlyFlag == 0) {
                reader.readBit() // mb_adaptive_frame_field_flag
            }
            reader.readBit() // direct_8x8_inference_flag

            var frameCropLeftOffset = 0
            var frameCropRightOffset = 0
            var frameCropTopOffset = 0
            var frameCropBottomOffset = 0

            val frameCroppingFlag = reader.readBit()
            if (frameCroppingFlag == 1) {
                frameCropLeftOffset = reader.readUE()
                frameCropRightOffset = reader.readUE()
                frameCropTopOffset = reader.readUE()
                frameCropBottomOffset = reader.readUE()
            }

            val finalWidth = width - (frameCropLeftOffset * 2) - (frameCropRightOffset * 2)
            val finalHeight = height - (frameCropTopOffset * 2) - (frameCropBottomOffset * 2)

            return SpsData(finalWidth, finalHeight)
        } catch (e: Exception) {
            AppLog.e("SPS parsing failed: ${e.message}")
            return null
        }
    }
}
