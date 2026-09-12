package com.andrerinas.openheadunit.connection.wifi.modes.nativeaa.zbt

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ZbtDaemonReachabilityTest {

    private var dials = 0

    @Before
    fun clear() {
        ZbtDaemonReachability.forget()
        dials = 0
    }

    @After
    fun leaveNothingBehind() = ZbtDaemonReachability.forget()

    private fun dial(answer: Boolean): () -> Boolean = { dials++; answer }

    @Test
    fun `nothing is known before anything is asked`() {
        assertNull(ZbtDaemonReachability.cached(nowMs = 1_000L))
    }

    @Test
    fun `a fresh answer is dialled once and then read`() {
        assertTrue(ZbtDaemonReachability.resolve({ 1_000L }, dial(true)))
        assertTrue(ZbtDaemonReachability.resolve({ 1_000L }, dial(true)))
        assertEquals(1, dials)
        assertTrue(ZbtDaemonReachability.cached(nowMs = 1_000L) == true)
    }

    @Test
    fun `a refusal is remembered like an answer`() {
        assertFalse(ZbtDaemonReachability.resolve({ 1_000L }, dial(false)))
        assertFalse(ZbtDaemonReachability.resolve({ 1_000L }, dial(false)))
        assertEquals(1, dials)
    }

    @Test
    fun `a stale answer is measured again`() {
        // A daemon that was down at app start can be up by the time the user tries again.
        ZbtDaemonReachability.resolve({ 1_000L }, dial(false))
        val later = 1_000L + ZbtDaemonReachability.RECHECK_AFTER_MS
        assertNull(ZbtDaemonReachability.cached(nowMs = later))
        assertTrue(ZbtDaemonReachability.resolve({ later }, dial(true)))
        assertEquals(2, dials)
    }

    @Test
    fun `what the carrier saw replaces what the dial predicted`() {
        ZbtDaemonReachability.resolve({ 1_000L }, dial(false))
        ZbtDaemonReachability.record(true, nowMs = 1_500L)
        assertTrue(ZbtDaemonReachability.cached(nowMs = 1_500L) == true)
        assertTrue(ZbtDaemonReachability.resolve({ 1_500L }, dial(false)))
        assertEquals(1, dials)
    }

    @Test
    fun `forgetting re-arms the measurement`() {
        ZbtDaemonReachability.resolve({ 1_000L }, dial(true))
        ZbtDaemonReachability.forget()
        assertNull(ZbtDaemonReachability.cached(nowMs = 1_000L))
        ZbtDaemonReachability.resolve({ 1_000L }, dial(true))
        assertEquals(2, dials)
    }
}
