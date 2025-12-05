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
    private val dataQueue = LinkedBlockingQueue<ByteArray>()
    @Volatile private var isRunning = true

    init {
        // The thread priority will be set in the run() method.
        this.name = "AudioPlaybackThread"
        audioTrack = createAudioTrack(stream, sampleRateInHz, bitDepth, channelCount)
        audioTrack.play()
        // Start the playback thread itself
        this.start()
    }

    override fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        while (isRunning) {
            try {
                // take() blocks until an element is available
                val buffer = dataQueue.take()
                AppLog.d("AudioTrackWrapper: run - taking %d bytes from queue, playState: %d, headPosition: %d, ts=%d",
                    buffer.size, audioTrack.playState, audioTrack.playbackHeadPosition, SystemClock.elapsedRealtime())
                if (isRunning) { // Re-check after waking up
                    audioTrack.write(buffer, 0, buffer.size)
                }
            } catch (e: InterruptedException) {
                AppLog.i("AudioTrackWrapper thread interrupted.")
                // isRunning will be set to false by stop(), so we just exit the loop
                break
            } catch (e: Exception) {
                AppLog.e("Error in AudioTrackWrapper run loop", e)
                isRunning = false
            }
        }
        // Cleanup after the loop finishes
        cleanup()
        AppLog.i("AudioTrackWrapper thread finished.")
    }

    private fun createAudioTrack(stream: Int, sampleRateInHz: Int, bitDepth: Int, channelCount: Int): AudioTrack {
        val channelConfig = if (channelCount == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val dataFormat = if (bitDepth == 16) AudioFormat.ENCODING_PCM_16BIT else AudioFormat.ENCODING_PCM_8BIT

        val minBufferSize = AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, dataFormat)
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
            AudioTrack(stream, sampleRateInHz, channelConfig, dataFormat, bufferSize, AudioTrack.MODE_STREAM)
        }
    }

    // This method is called by the transport thread. It should be fast and non-blocking.
    fun write(buffer: ByteArray, offset: Int, size: Int) {
        if (!isRunning) return

        AppLog.d("AudioTrackWrapper: write - adding %d bytes to queue, current queue size: %d, ts=%d", size, dataQueue.size, SystemClock.elapsedRealtime())

        // We need to copy the relevant part of the buffer to a new array,
        // as the original buffer might be reused by the transport layer.
        val chunk = buffer.copyOfRange(offset, offset + size)
        
        // offer() is non-blocking. If the queue is full (which it shouldn't be with default capacity),
        // it returns false. We can log this if it happens.
        if (!dataQueue.offer(chunk)) {
            AppLog.w("Audio data queue is full. Dropping audio data.")
        }
    }

    fun stopPlayback() {
        isRunning = false
        // Interrupt the thread in case it's blocked on dataQueue.take()
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
        // release() can be slow, but it's essential.
        try {
            audioTrack.release()
        } catch (e: Exception) {
            AppLog.e("Error releasing audio track", e)
        }
    }
}
