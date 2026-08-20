package com.andrerinas.openheadunit.decoder

import com.andrerinas.openheadunit.decoder.DecoderStallCausePolicy.Cause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three causes and the two gates. The numbers in the "measured" cases are the UNISOC MT50 round
 * that motivated the class - a stall detected 4s after the codec was rebuilt, then every ten seconds
 * after that.
 */
class DecoderStallCausePolicyTest {

    private val idleThreshold = 2000L

    private fun classify(
        stallGapMs: Long = 10_000L,
        inputIdleGapMs: Long = 0L,
        keyframeFedSinceStart: Boolean = false,
        sessionHasRendered: Boolean = true,
    ) = DecoderStallCausePolicy.classify(
        stallGapMs, inputIdleGapMs, idleThreshold, keyframeFedSinceStart, sessionHasRendered
    )

    @Test
    fun `a phone that has stopped sending is not a decoder fault`() {
        assertEquals(Cause.PHONE_IDLE, classify(inputIdleGapMs = idleThreshold + 1))
        // Decided before anything else, so it holds whatever the codec's own state looks like.
        assertEquals(
            Cause.PHONE_IDLE,
            classify(inputIdleGapMs = 30_000L, keyframeFedSinceStart = true, sessionHasRendered = false)
        )
    }

    @Test
    fun `a rebuilt codec that has had no keyframe is starved, not stalled`() {
        // The measured shape: 2s, then 5.5s, then 10s of nothing out while input flowed at ~50fps.
        listOf(2005L, 5524L, 10_001L).forEach {
            assertEquals("stallGap=$it", Cause.STARVED_OF_KEYFRAME, classify(stallGapMs = it))
        }
    }

    @Test
    fun `a codec that has had its keyframe and still produces nothing is stalled`() {
        assertEquals(Cause.STALLED, classify(keyframeFedSinceStart = true))
    }

    @Test
    fun `a cold start stays on the rebuild path`() {
        // Deliberate. Before anything has rendered the codec type is still a guess, and the rebuild
        // is what reaches the one-time fallback to the other codec. Calling that starvation would
        // leave a device with a broken HEVC component waiting fifteen seconds for a keyframe it
        // could not decode anyway.
        assertEquals(Cause.STALLED, classify(sessionHasRendered = false))
    }

    @Test
    fun `patience runs out and the ordinary path resumes`() {
        val patience = DecoderStallCausePolicy.KEYFRAME_STARVATION_PATIENCE_MS
        assertEquals(Cause.STARVED_OF_KEYFRAME, classify(stallGapMs = patience - 1))
        assertEquals(Cause.STALLED, classify(stallGapMs = patience))
    }

    @Test
    fun `patience clears every mechanism meant to resolve the starvation`() {
        // If it did not, the rebuild would pre-empt the repair it is waiting for. The focus cycle
        // produces a keyframe 0.52-0.78s after the release and the warm-relaunch path escalates at
        // 850ms; both have room to spare inside this.
        assertTrue(DecoderStallCausePolicy.KEYFRAME_STARVATION_PATIENCE_MS >= 10_000L)
        // And well short of the phone's own ~69s cadence, so this is a wait for a keyframe we asked
        // for, never a wait for the scheduled one.
        assertTrue(DecoderStallCausePolicy.KEYFRAME_STARVATION_PATIENCE_MS < 60_000L)
    }
}
