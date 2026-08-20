package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.VideoFaultInjector.Effect
import com.andrerinas.openheadunit.aap.VideoFaultInjector.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The test tool that makes the reassembly fixes measurable on a unit that is working correctly.
 *
 * Its own correctness matters as much as the code it exercises: a brief that says "a fault every six
 * seconds" has to be true, or the results cannot be compared between builds.
 */
class VideoFaultInjectorTest {

    private val first = VideoFragmentAssembler.FLAG_FIRST
    private val middleFlag = VideoFragmentAssembler.FLAG_MIDDLE
    private val lastFlag = VideoFragmentAssembler.FLAG_LAST
    private val single = VideoFragmentAssembler.FLAG_SINGLE

    @Test
    fun `off does nothing to anything`() {
        val injector = VideoFaultInjector(Mode.OFF, rate = 2)
        assertFalse(injector.isActive)
        for (flags in listOf(first, middleFlag, lastFlag, single)) {
            repeat(10) { assertEquals(Effect.NONE, injector.effectFor(flags)) }
        }
        assertEquals(0L, injector.injectedCount)
    }

    @Test
    fun `every Nth targeted message is faulted, and nothing else is touched`() {
        val injector = VideoFaultInjector(Mode.DROP_FIRST_FRAGMENT, rate = 3)
        assertTrue(injector.isActive)

        val effects = (1..9).map { injector.effectFor(first) }
        assertEquals(
            "one in three, deterministically",
            listOf(Effect.NONE, Effect.NONE, Effect.DROP, Effect.NONE, Effect.NONE, Effect.DROP, Effect.NONE, Effect.NONE, Effect.DROP),
            effects
        )
        assertEquals(3L, injector.injectedCount)
    }

    @Test
    fun `the rate counts only the flag being attacked`() {
        // A frame is often three or four messages, so counting all video traffic would make the rate
        // mean something three times rarer than the brief says.
        val injector = VideoFaultInjector(Mode.DROP_LAST_FRAGMENT, rate = 2)
        // A full frame's worth of messages that are not the target.
        repeat(20) {
            injector.effectFor(first)
            injector.effectFor(middleFlag)
            injector.effectFor(single)
        }
        assertEquals("untargeted traffic must not advance the counter", 0L, injector.injectedCount)
        assertEquals(Effect.NONE, injector.effectFor(lastFlag))
        assertEquals(Effect.DROP, injector.effectFor(lastFlag))
    }

    @Test
    fun `each mode attacks the flag it says it does`() {
        assertEquals(null, VideoFaultInjector.targetFlag(Mode.OFF))
        assertEquals(first, VideoFaultInjector.targetFlag(Mode.DROP_FIRST_FRAGMENT))
        assertEquals(first, VideoFaultInjector.targetFlag(Mode.HIDE_START_CODE))
        assertEquals(middleFlag, VideoFaultInjector.targetFlag(Mode.DROP_MIDDLE_FRAGMENT))
        assertEquals(lastFlag, VideoFaultInjector.targetFlag(Mode.DROP_LAST_FRAGMENT))
    }

    @Test
    fun `hiding the start code is not a drop`() {
        // The distinction matters: the bytes arrive, so the framing audit sees a complete run and
        // only the reassembler should object.
        val injector = VideoFaultInjector(Mode.HIDE_START_CODE, rate = 2)
        assertEquals(Effect.NONE, injector.effectFor(first))
        assertEquals(Effect.HIDE_START_CODE, injector.effectFor(first))
    }

    @Test
    fun `the rate is clamped to something that can still produce a picture`() {
        assertEquals(VideoFaultInjector.MIN_RATE, VideoFaultInjector(Mode.DROP_FIRST_FRAGMENT, rate = 1).rate)
        assertEquals(VideoFaultInjector.MIN_RATE, VideoFaultInjector(Mode.DROP_FIRST_FRAGMENT, rate = 0).rate)
        assertEquals(VideoFaultInjector.MIN_RATE, VideoFaultInjector(Mode.DROP_FIRST_FRAGMENT, rate = -5).rate)
        assertEquals(VideoFaultInjector.MAX_RATE, VideoFaultInjector(Mode.DROP_FIRST_FRAGMENT, rate = Int.MAX_VALUE).rate)
        assertEquals(300, VideoFaultInjector(Mode.DROP_FIRST_FRAGMENT, rate = 300).rate)
    }

    @Test
    fun `every mode round-trips through the value stored in settings`() {
        for (mode in Mode.entries) {
            assertEquals(mode, Mode.fromInt(mode.value))
        }
        assertEquals("an unknown stored value must not throw", null, Mode.fromInt(-1))
        assertEquals(null, Mode.fromInt(99))
    }

    @Test
    fun `the default rate is rare enough to leave a fair sample of normal behaviour`() {
        // At the ~50 messages per second a healthy link carries, one in 300 is a fault every few
        // seconds. If this ever changes, the test brief's timings change with it.
        assertTrue(VideoFaultInjector.DEFAULT_RATE >= 100)
        assertTrue(VideoFaultInjector.DEFAULT_RATE in VideoFaultInjector.MIN_RATE..VideoFaultInjector.MAX_RATE)
    }

    @Test
    fun `the candidate count follows the targeted flag and nothing else`() {
        // The denominator the log needs. A run can inject nothing because the rate is high or because
        // the stream never fragmented, and only this number tells those apart.
        val injector = VideoFaultInjector(Mode.DROP_MIDDLE_FRAGMENT, rate = 4)
        repeat(20) { injector.effectFor(first) }
        repeat(20) { injector.effectFor(single) }
        assertEquals("untargeted flags are not candidates", 0L, injector.matchingCount)

        repeat(7) { injector.effectFor(middleFlag) }
        assertEquals(7L, injector.matchingCount)
        assertEquals(1L, injector.injectedCount)
    }

    @Test
    fun `the summary carries the setting and both counts`() {
        val injector = VideoFaultInjector(Mode.DROP_MIDDLE_FRAGMENT, rate = 20)
        repeat(8) { injector.effectFor(middleFlag) }
        val text = injector.describe()
        // The exact shape a five-minute run that injected nothing produced, which is the case this
        // line exists for: eight candidates at one in twenty is zero faults and no defect.
        listOf("DROP_MIDDLE_FRAGMENT", "1-in-20", "8 candidates", "0 injected").forEach {
            assertTrue("missing '$it' in: $text", text.contains(it))
        }
    }
}
