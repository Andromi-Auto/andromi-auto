package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.KeyframeCycleEscalationPolicy.Action
import com.andrerinas.openheadunit.aap.KeyframeCycleEscalationPolicy.CYCLE_COOLDOWN_MS
import com.andrerinas.openheadunit.aap.KeyframeCycleEscalationPolicy.ESCALATE_AFTER_UNREPAIRED_MS
import com.andrerinas.openheadunit.aap.WarmRelaunchKeyframePolicy.FOCUS_CYCLE_GAP_MS
import com.andrerinas.openheadunit.aap.KeyframeCycleEscalationPolicy.MAX_CYCLES_PER_SESSION
import com.andrerinas.openheadunit.aap.KeyframeCycleEscalationPolicy.NATURAL_CADENCE_MIN_OBSERVED_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A focus release across a stream that is rendering is the precondition of issue #755, so escalation
 * past the gain-only nudge has to be earned: the picture has to have stayed broken past the point
 * where the phone would plausibly have repaired it, and the cycles are a decaying budget rather than
 * something a bad minute can spend all at once.
 */
class KeyframeCycleEscalationPolicyTest {

    private fun decide(
        nowMs: Long,
        unrepairedSinceMs: Long,
        cyclesUsedThisSession: Int = 0,
        lastCycleMs: Long = 0L,
    ) = KeyframeCycleEscalationPolicy.decide(nowMs, unrepairedSinceMs, cyclesUsedThisSession, lastCycleMs)

    // --- The trigger ------------------------------------------------------------------------

    @Test
    fun `a repaired picture never escalates`() {
        // The clock is only set while something is broken; zero means there is nothing to repair,
        // however long the session has been running or however many cycles are left.
        assertEquals(Action.NUDGE, decide(nowMs = 1_000_000L, unrepairedSinceMs = 0L))
    }

    @Test
    fun `the drop that starts the clock only gets the nudge`() {
        assertEquals(Action.NUDGE, decide(nowMs = 12_345L, unrepairedSinceMs = 12_345L))
    }

    @Test
    fun `the cycle is earned the moment the window elapses, not before`() {
        val broken = 10_000L
        assertEquals(
            Action.NUDGE,
            decide(nowMs = broken + ESCALATE_AFTER_UNREPAIRED_MS - 1, unrepairedSinceMs = broken)
        )
        assertEquals(
            Action.CYCLE_FOCUS,
            decide(nowMs = broken + ESCALATE_AFTER_UNREPAIRED_MS, unrepairedSinceMs = broken)
        )
    }

    // --- The budget -------------------------------------------------------------------------

    @Test
    fun `a spent budget degrades to the nudge`() {
        val broken = 10_000L
        assertEquals(
            Action.NUDGE,
            decide(
                nowMs = broken + ESCALATE_AFTER_UNREPAIRED_MS,
                unrepairedSinceMs = broken,
                cyclesUsedThisSession = MAX_CYCLES_PER_SESSION,
            )
        )
    }

    @Test
    fun `every cycle up to the cap is available`() {
        val broken = 1_000_000L
        for (spent in 0 until MAX_CYCLES_PER_SESSION) {
            assertEquals(
                "cycle ${spent + 1} of $MAX_CYCLES_PER_SESSION should still be available",
                Action.CYCLE_FOCUS,
                decide(
                    nowMs = broken + ESCALATE_AFTER_UNREPAIRED_MS,
                    unrepairedSinceMs = broken,
                    cyclesUsedThisSession = spent,
                    // Far enough back that the cooldown is not what is being tested here.
                    lastCycleMs = broken - CYCLE_COOLDOWN_MS,
                )
            )
        }
    }

    // --- The decay --------------------------------------------------------------------------

    @Test
    fun `a second cycle waits out the cooldown even with budget left`() {
        val firstCycle = 100_000L
        val broken = firstCycle + 5_000L
        assertEquals(
            Action.NUDGE,
            decide(
                nowMs = firstCycle + CYCLE_COOLDOWN_MS - 1,
                unrepairedSinceMs = broken,
                cyclesUsedThisSession = 1,
                lastCycleMs = firstCycle,
            )
        )
        assertEquals(
            Action.CYCLE_FOCUS,
            decide(
                nowMs = firstCycle + CYCLE_COOLDOWN_MS,
                unrepairedSinceMs = broken,
                cyclesUsedThisSession = 1,
                lastCycleMs = firstCycle,
            )
        )
    }

    @Test
    fun `the first cycle of a session is not held back by an unset cooldown stamp`() {
        // lastCycleMs is 0 before anything has fired, and must not read as "a cycle at time zero" -
        // that would suppress the first escalation of every session for the first minute of uptime.
        val broken = 10_000L
        assertEquals(
            Action.CYCLE_FOCUS,
            decide(
                nowMs = broken + ESCALATE_AFTER_UNREPAIRED_MS,
                unrepairedSinceMs = broken,
                cyclesUsedThisSession = 0,
                lastCycleMs = 0L,
            )
        )
    }

    // --- Constants held against what was measured -------------------------------------------

    @Test
    fun `the escalation window clears two throttle windows`() {
        // Escalating before a nudge has demonstrably gone unanswered would spend a focus release on
        // a request the phone had not had a chance to answer yet.
        assertTrue(
            "escalation window ${ESCALATE_AFTER_UNREPAIRED_MS}ms does not clear two " +
                "${VideoRecoveryPolicy.KEYFRAME_REQUEST_THROTTLE_MS}ms throttle windows",
            ESCALATE_AFTER_UNREPAIRED_MS >= VideoRecoveryPolicy.KEYFRAME_REQUEST_THROTTLE_MS * 2
        )
    }

    @Test
    fun `the escalation window stays well under the fastest unaided repair ever measured`() {
        // Past a third of the fastest natural keyframe gap on record, the phone's own cadence would
        // often beat the escalation and the disturbance buys nothing.
        assertTrue(
            "escalation window ${ESCALATE_AFTER_UNREPAIRED_MS}ms is not clearly under a third of the " +
                "${NATURAL_CADENCE_MIN_OBSERVED_MS}ms fastest natural keyframe gap",
            ESCALATE_AFTER_UNREPAIRED_MS <= NATURAL_CADENCE_MIN_OBSERVED_MS / 3
        )
    }

    @Test
    fun `the cooldown keeps two focus cycles from ever being in flight together`() {
        // AapTransport carries one shared regain runnable and replaces it rather than tracking one
        // per cycle. That is only sound while a second release cannot land inside the first's regain
        // gap. Shortening the cooldown towards the gap needs that bookkeeping first.
        assertTrue(
            "cooldown ${CYCLE_COOLDOWN_MS}ms is not comfortably clear of the ${FOCUS_CYCLE_GAP_MS}ms regain gap",
            CYCLE_COOLDOWN_MS >= FOCUS_CYCLE_GAP_MS * 10
        )
    }
}
