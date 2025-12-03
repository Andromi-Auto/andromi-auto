package com.andrerinas.headunitrevived.decoder

import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process

import com.andrerinas.headunitrevived.utils.AppLog


/**
 * @author algavris
 * *
 * @date 26/10/2016.
 */
class AudioTrackWrapper(stream: Int, sampleRateInHz: Int, bitDepth: Int, channelCount: Int) {
    private val audioTrack: AudioTrack

    init {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        audioTrack = createAudioTrack(stream, sampleRateInHz, bitDepth, channelCount)
        audioTrack.play()
    }

    private fun createAudioTrack(stream: Int, sampleRateInHz: Int, bitDepth: Int, channelCount: Int): AudioTrack {
        val pcmFrameSize = 2 * channelCount
        val channelConfig = if (channelCount == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val dataFormat = if (bitDepth == 16) AudioFormat.ENCODING_PCM_16BIT else AudioFormat.ENCODING_PCM_8BIT
        val bufferSize = AudioBuffer.getSize(sampleRateInHz, channelConfig, dataFormat, pcmFrameSize)

        AppLog.i("Audio stream: $stream buffer size: $bufferSize sampleRateInHz: $sampleRateInHz channelCount: $channelCount")

        return AudioTrack(stream, sampleRateInHz, channelConfig, dataFormat, bufferSize, AudioTrack.MODE_STREAM)
    }

    fun write(buffer: ByteArray, offset: Int, size: Int): Int {
        val written = audioTrack.write(buffer, offset, size)
        if (written != size) {
            AppLog.e("Error AudioTrack written: $written  len: $size, playState: ${audioTrack.playState}, playbackHeadPosition: ${audioTrack.playbackHeadPosition}, bufferSizeInFrames: ${audioTrack.bufferSizeInFrames}")
        }
        // AppLog.d("AudioTrack written: $written, requested: $size, playState: ${audioTrack.playState}, playbackHeadPosition: ${audioTrack.playbackHeadPosition}, bufferSizeInFrames: ${audioTrack.bufferSizeInFrames}")
        return written
    }

    fun stop() {
        if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.pause()
        }
        val toRelease = audioTrack
        // AudioTrack.release can take some time, so we call it on a background thread.
        object : Thread() {
            override fun run() {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                toRelease.flush()
                toRelease.release()
            }
        }.start()
    }

    private object AudioBuffer {
        /**
         * A minimum length for the [android.media.AudioTrack] buffer, in microseconds.
         */
        private val MIN_BUFFER_DURATION_US: Long = 500000
        /**
         * A multiplication factor to apply to the minimum buffer size requested by the underlying
         * [android.media.AudioTrack].
         */
        private val BUFFER_MULTIPLICATION_FACTOR = 16
        /**
         * A maximum length for the [android.media.AudioTrack] buffer, in microseconds.
         */
        private val MAX_BUFFER_DURATION_US: Long = 1500000

        /**
         * The number of microseconds in one second.
         */
        private val MICROS_PER_SECOND = 1000000L

        internal fun getSize(sampleRate: Int, channelConfig: Int, audioFormat: Int, pcmFrameSize: Int): Int {
            val minBufferSize = android.media.AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val multipliedBufferSize = minBufferSize * BUFFER_MULTIPLICATION_FACTOR
            val minAppBufferSize = durationUsToFrames(MIN_BUFFER_DURATION_US, sampleRate) * pcmFrameSize
            val maxAppBufferSize = Math.max(minBufferSize,
                    durationUsToFrames(MAX_BUFFER_DURATION_US, sampleRate) * pcmFrameSize)
            return if (multipliedBufferSize < minAppBufferSize)
                minAppBufferSize
            else if (multipliedBufferSize > maxAppBufferSize)
                maxAppBufferSize
            else
                multipliedBufferSize
        }

        private fun durationUsToFrames(durationUs: Long, sampleRate: Int): Int {
            return (durationUs * sampleRate / MICROS_PER_SECOND).toInt()
        }
    }
}
