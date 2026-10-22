package com.andrerinas.openheadunit.utils

import android.content.Context
import com.andrerinas.openheadunit.aap.protocol.proto.Control
import com.andrerinas.openheadunit.aap.protocol.proto.Media

/**
 * Andromi Auto: which video size Android Auto really sends. Service discovery records what was
 * offered (first choice and fallback); the phone's Start says which one it took. The result is
 * kept so Settings can show it, because the picture quality depends on it.
 */
object VideoStreamInfo {
    private const val PREFS = "andromi_video_stream"
    private const val KEY = "last"

    @Volatile private var primary: Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType? = null
    @Volatile private var fallback: Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType? = null
    @Volatile private var codec: Media.MediaCodecType? = null
    @Volatile private var fps: Int = 0

    fun recordOffer(
        primary: Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType,
        fallback: Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType?,
        codec: Media.MediaCodecType,
        fps: Int
    ) {
        this.primary = primary; this.fallback = fallback; this.codec = codec; this.fps = fps
    }

    /** Called when the phone starts the video with the configuration it chose. */
    fun recordStart(context: Context, configurationIndex: Int) {
        val p = primary ?: return
        val usedFallback = configurationIndex == VideoFallbackPolicy.FALLBACK_INDEX && fallback != null
        val used = if (usedFallback) fallback!! else p
        val size = VideoFallbackPolicy.sizeOf(used)
        val codecName = when (codec) {
            Media.MediaCodecType.MEDIA_CODEC_VIDEO_H265 -> "H.265"
            else -> "H.264"
        }
        val offered = VideoFallbackPolicy.sizeOf(p)
        val text = "${size.first}×${size.second} · $codecName · $fps fps" +
            if (usedFallback) "|${offered.first}×${offered.second}" else "|"
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, text).apply()
        AppLog.i("VideoStreamInfo: Android Auto sends $text")
    }

    /** "1080×1920 · H.264 · 60 fps" and, when the phone refused the first size, that size. */
    fun last(context: Context): Pair<String, String?>? {
        val s = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        val parts = s.split("|", limit = 2)
        return Pair(parts[0], parts.getOrNull(1)?.takeIf { it.isNotEmpty() })
    }
}
