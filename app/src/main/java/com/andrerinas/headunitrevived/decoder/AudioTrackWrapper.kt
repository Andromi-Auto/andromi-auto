package com.andrerinas.headunitrevived.decoder

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import android.os.SystemClock
import com.andrerinas.headunitrevived.utils.AppLog
import java.util.concurrent.LinkedBlockingQueue

class AudioTrackWrapper(stream: Int, sampleRateInHz: Int, bitDepth: Int, channelCount: Int) : Thread() {

    private val audioTrack: AudioTrack
    // Limit queue capacity to provide backpressure to the network thread if audio playback is slow
    private val dataQueue = LinkedBlockingQueue<ByteArray>(50)
    @Volatile private var isRunning = true

    init {
        this.name = "AudioPlaybackThread"
        audioTrack = createAudioTrack(stream, sampleRateInHz, bitDepth, channelCount)
        audioTrack.play()
        this.start()
    }

    override fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        while (isRunning) {
            try {
                val buffer = dataQueue.take()
                if (isRunning) {
                    // This blocks if the hardware buffer is full -> regulates speed
                    audioTrack.write(buffer, 0, buffer.size)
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

    private fun createAudioTrack(stream: Int, sampleRateInHz: Int, bitDepth: Int, channelCount: Int): AudioTrack {
        val channelConfig = if (channelCount == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val dataFormat = if (bitDepth == 16) AudioFormat.ENCODING_PCM_16BIT else AudioFormat.ENCODING_PCM_8BIT

        val minBufferSize = AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, dataFormat)
        // Larger buffer (32x) to prevent stuttering on jittery connections
        val bufferSize = minBufferSize * 32

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
            AudioTrack(stream, sampleRateInHz, channelConfig, dataFormat, bufferSize, AudioTrack.MODE_STREAM)
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
    }
}
