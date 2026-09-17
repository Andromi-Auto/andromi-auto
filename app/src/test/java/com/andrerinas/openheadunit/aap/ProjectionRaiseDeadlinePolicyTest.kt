package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectionRaiseDeadlinePolicyTest {

    @Test
    fun `a raise that was attempted arms the deadline`() {
        assertTrue(ProjectionRaiseDeadlinePolicy.arms(raiseAttempted = true))
    }

    /** PiP, a no_ui command and the settings screen each raise the projection themselves later. */
    @Test
    fun `a raise skipped on purpose arms nothing`() {
        assertFalse(ProjectionRaiseDeadlinePolicy.arms(raiseAttempted = false))
    }

    @Test
    fun `the first expiry tries the raise again`() {
        assertEquals(
            ProjectionRaiseDeadlinePolicy.Action.RETRY_RAISE,
            ProjectionRaiseDeadlinePolicy.actionFor(raisesMade = 1)
        )
    }

    @Test
    fun `the second expiry ends the session so the phone can start a fresh one`() {
        assertEquals(
            ProjectionRaiseDeadlinePolicy.Action.END_SESSION,
            ProjectionRaiseDeadlinePolicy.actionFor(raisesMade = 2)
        )
    }

    @Test
    fun `the deadline beats the phone's own patience`() {
        assertTrue(
            ProjectionRaiseDeadlinePolicy.DEADLINE_MS * ProjectionRaiseDeadlinePolicy.MAX_RAISES
                < 35_000L
        )
    }
}
