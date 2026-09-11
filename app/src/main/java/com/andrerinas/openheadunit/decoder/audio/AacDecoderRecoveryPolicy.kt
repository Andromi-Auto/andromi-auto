package com.andrerinas.openheadunit.decoder.audio

/**
 * Whether a failed AAC decoder is rebuilt or the sink goes quiet. A codec that fails hands out no
 * more input buffers, so without a rebuild every frame waits its timeout and is dropped for the
 * rest of the session. Bounded so a decoder that fails at once does not churn.
 */
object AacDecoderRecoveryPolicy {

    /** Rebuilds allowed per track lifetime. */
    const val MAX_REBUILDS = 3

    /** Minimum spacing between rebuilds. */
    const val MIN_SPACING_MS = 10_000L

    fun allowsRebuild(rebuildsSoFar: Int, sinceLastRebuildMs: Long): Boolean =
        rebuildsSoFar < MAX_REBUILDS && (rebuildsSoFar == 0 || sinceLastRebuildMs >= MIN_SPACING_MS)
}
