package com.andrerinas.headunitrevived.decoder

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import com.andrerinas.headunitrevived.utils.AppLog
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class AudioTrackWrapper(
    stream: Int,
    sampleRateInHz: Int,
    bitDepth: Int,
    channelCount: Int,
    private val isAac: Boolean = false
) : Thread() {

    private val audioTrack: AudioTrack
    private var decoder: MediaCodec? = null
    private var codecHandlerThread: HandlerThread? = null
    private val freeInputBuffers = LinkedBlockingQueue<Int>()
    private val writeExecutor = Executors.newSingleThreadExecutor()

    // Limit queue capacity to provide backpressure to the network thread if audio playback is slow
    private val dataQueue = LinkedBlockingQueue<ByteArray>()
    @Volatile
    private var isRunning = true

    init {

        this.name = "AudioPlaybackThread"
        audioTrack = createAudioTrack(stream, sampleRateInHz, bitDepth, channelCount)

        audioTrack.play()



        if (isAac) {

            initDecoder(sampleRateInHz, channelCount)

        }



        this.start()

    }


    private fun initDecoder(sampleRate: Int, channels: Int) {

        try {

            val mime = "audio/mp4a-latm"

            val format = MediaFormat.createAudioFormat(mime, sampleRate, channels)

            format.setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC
            )

            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)


            // CSD enabled for RAW AAC (MEDIA_CODEC_AUDIO_AAC_LC)

            val csd = makeAacCsd(sampleRate, channels)

            format.setByteBuffer("csd-0", java.nio.ByteBuffer.wrap(csd))



            decoder = MediaCodec.createDecoderByType(mime)



            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

                codecHandlerThread = HandlerThread("AacCodecThread")

                codecHandlerThread!!.start()

                val callback = object : MediaCodec.Callback() {

                    override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {

                        freeInputBuffers.offer(index)

                    }


                    override fun onOutputBufferAvailable(
                        codec: MediaCodec,
                        index: Int,
                        info: MediaCodec.BufferInfo
                    ) {

                        try {

                            val outputBuffer = codec.getOutputBuffer(index)

                            if (outputBuffer != null) {

                                val chunk = ByteArray(info.size)

                                outputBuffer.position(info.offset)

                                outputBuffer.get(chunk)

                                outputBuffer.clear()


                                // Write to AudioTrack using executor


                                writeExecutor.submit {


                                    if (isRunning) {


                                        audioTrack.write(chunk, 0, chunk.size)


                                    }


                                }

                            }

                            codec.releaseOutputBuffer(index, false)

                        } catch (e: Exception) {

                            AppLog.e("Error processing AAC output", e)

                        }

                    }


                    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {

                        AppLog.e("AAC Codec Error", e)

                    }


                    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {

                        AppLog.i("AAC Output Format Changed: $format")

                    }

                }



                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                    val handler = Handler(codecHandlerThread!!.looper)

                    decoder!!.setCallback(callback, handler)

                } else {

                    decoder!!.setCallback(callback)

                }

            }



            decoder?.configure(format, null, null, 0)

            decoder?.start()

            AppLog.i("AAC Decoder started for $sampleRate Hz, $channels channels with is-adts (Async)")

        } catch (e: Exception) {

            AppLog.e("Failed to init AAC decoder", e)

        }

    }


    override fun run() {

        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)



        while (isRunning) {

            try {

                val buffer = dataQueue.take()

                if (isRunning) {

                    if (isAac && decoder != null) {

                        queueInput(buffer)

                    } else {

                        // PCM path - direct write in this high-priority thread

                        audioTrack.write(buffer, 0, buffer.size)

                    }

                }

            } catch (e: InterruptedException) {

                break

            } catch (e: Exception) {

                AppLog.e("Error in AudioTrackWrapper run loop", e)

                isRunning = false

            }

        }

        cleanup()

        AppLog.i("AudioTrackWrapper thread finished.")

    }


    private fun queueInput(inputData: ByteArray) {

        try {

            // Wait for input buffer (with timeout to avoid deadlock if codec dies)

            val inputIndex = freeInputBuffers.poll(200, TimeUnit.MILLISECONDS)



            if (inputIndex != null && inputIndex >= 0) {

                val inputBuffer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

                    decoder?.getInputBuffer(inputIndex)

                } else {

                    @Suppress("DEPRECATION")

                    decoder?.inputBuffers?.get(inputIndex)

                }



                inputBuffer?.clear()

                inputBuffer?.put(inputData)

                decoder?.queueInputBuffer(inputIndex, 0, inputData.size, 0, 0)

            } else {

                AppLog.w("AAC Input Buffer timeout - dropping frame")

            }

        } catch (e: Exception) {

            AppLog.e("Error queuing AAC input", e)

        }

    }


    private fun makeAacCsd(sampleRate: Int, channelCount: Int): ByteArray {
        val sampleRateIndex = getFrequencyIndex(sampleRate)
        val audioObjectType = 2 // AAC-LC

        // AudioSpecificConfig: 5 bits AOT, 4 bits Frequency Index, 4 bits Channel Config
        val config = (audioObjectType shl 11) or (sampleRateIndex shl 7) or (channelCount shl 3)
        val csd = ByteArray(2)
        csd[0] = ((config shr 8) and 0xFF).toByte()
        csd[1] = (config and 0xFF).toByte()
        return csd
    }

    private fun getFrequencyIndex(sampleRate: Int): Int {
        return when (sampleRate) {
            96000 -> 0
            88200 -> 1
            64000 -> 2
            48000 -> 3
            44100 -> 4
            32000 -> 5
            24000 -> 6
            22050 -> 7
            16000 -> 8
            12000 -> 9
            11025 -> 10
            8000 -> 11
            7350 -> 12
            else -> 4 // Default 44100
        }
    }

    private fun createAudioTrack(
        stream: Int,
        sampleRateInHz: Int,
        bitDepth: Int,
        channelCount: Int
    ): AudioTrack {
        val channelConfig =
            if (channelCount == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val dataFormat =
            if (bitDepth == 16) AudioFormat.ENCODING_PCM_16BIT else AudioFormat.ENCODING_PCM_8BIT

        val minBufferSize = AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, dataFormat)
        // Larger buffer (32x) to prevent stuttering on jittery connections
        val bufferSize = minBufferSize * 8

        AppLog.i("Audio stream: $stream buffer size: $bufferSize (min: $minBufferSize) sampleRateInHz: $sampleRateInHz channelCount: $channelCount")

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val audioAttributes = AudioAttributes.Builder()
                .setLegacyStreamType(stream)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(sampleRateInHz)
                .setChannelMask(channelConfig)
                .setEncoding(dataFormat)
                .build()

            AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                stream,
                sampleRateInHz,
                channelConfig,
                dataFormat,
                bufferSize,
                AudioTrack.MODE_STREAM
            )
        }
    }

    fun write(buffer: ByteArray, offset: Int, size: Int) {
        if (!isRunning) return

        val chunk = buffer.copyOfRange(offset, offset + size)

        try {
            // put() blocks if queue is full (Backpressure)
            dataQueue.put(chunk)
        } catch (e: InterruptedException) {
            AppLog.w("Interrupted while putting audio data to queue")
        }
    }

    fun stopPlayback() {
        isRunning = false
        this.interrupt()
    }

    private fun cleanup() {
        writeExecutor.shutdown()
        if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
            try {
                audioTrack.pause()
                audioTrack.flush()
            } catch (e: IllegalStateException) {
                AppLog.e("Error during audio track cleanup", e)
            }
        }
        try {
            audioTrack.release()
        } catch (e: Exception) {
            AppLog.e("Error releasing audio track", e)
        }

        try {
            decoder?.stop()
            decoder?.release()
            decoder = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                codecHandlerThread?.quitSafely()
            } else {
                codecHandlerThread?.quit()
            }
            codecHandlerThread = null
        } catch (e: Exception) {
            AppLog.e("Error releasing audio decoder", e)
        }
    }
}
