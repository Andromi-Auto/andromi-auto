package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.protocol.proto.Media

/**
 * Whether an audio sink's frames are AAC, read from the codec type the phone names in its Media
 * Sink Setup rather than from the setting the announcement was built from: the wire is the one
 * source that cannot disagree with what the phone sends. Null for a type that is not an audio codec.
 */
object AudioSinkCodecPolicy {

    fun isAac(setupType: Int): Boolean? = when (setupType) {
        Media.MediaCodecType.MEDIA_CODEC_AUDIO_PCM_VALUE -> false
        Media.MediaCodecType.MEDIA_CODEC_AUDIO_AAC_LC_VALUE,
        Media.MediaCodecType.MEDIA_CODEC_AUDIO_AAC_LC_ADTS_VALUE -> true
        else -> null
    }
}
