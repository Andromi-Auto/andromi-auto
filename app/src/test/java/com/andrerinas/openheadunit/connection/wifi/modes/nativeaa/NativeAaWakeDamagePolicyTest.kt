package com.andrerinas.openheadunit.connection.wifi.modes.nativeaa

import com.andrerinas.openheadunit.connection.wifi.modes.nativeaa.NativeAaWakeDamagePolicy.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAaWakeDamagePolicyTest {

    @Test
    fun `an unmeasured unit is allowed to wake, because that wake is the measurement`() {
        assertTrue(NativeAaWakeDamagePolicy.allowsEscalation(Verdict.UNKNOWN))
        assertTrue(NativeAaWakeDamagePolicy.isProbe(Verdict.UNKNOWN))
    }

    @Test
    fun `a unit whose link came back keeps waking, and is not probed again`() {
        assertTrue(NativeAaWakeDamagePolicy.allowsEscalation(Verdict.SAFE))
        assertFalse(NativeAaWakeDamagePolicy.isProbe(Verdict.SAFE))
    }

    @Test
    fun `a unit that lost its link never wakes again`() {
        assertFalse(NativeAaWakeDamagePolicy.allowsEscalation(Verdict.DESTRUCTIVE))
    }

    @Test
    fun `a link that came back is safe and a link still down is destructive`() {
        assertEquals(Verdict.SAFE, NativeAaWakeDamagePolicy.verdictFrom(true))
        assertEquals(Verdict.DESTRUCTIVE, NativeAaWakeDamagePolicy.verdictFrom(false))
    }

    /** The adapter refusing to answer is not evidence the unit is fine, nor that it is broken. */
    @Test
    fun `an unreadable link leaves the unit unmeasured`() {
        assertEquals(Verdict.UNKNOWN, NativeAaWakeDamagePolicy.verdictFrom(null))
        assertFalse(NativeAaWakeDamagePolicy.recordable(Verdict.UNKNOWN))
    }

    @Test
    fun `both real answers are worth storing`() {
        assertTrue(NativeAaWakeDamagePolicy.recordable(Verdict.SAFE))
        assertTrue(NativeAaWakeDamagePolicy.recordable(Verdict.DESTRUCTIVE))
    }

    @Test
    fun `the stored int round-trips and an unknown one reads as unmeasured`() {
        for (verdict in Verdict.entries) {
            assertEquals(verdict, Verdict.of(verdict.ordinal))
        }
        assertEquals(Verdict.UNKNOWN, Verdict.of(-1))
        assertEquals(Verdict.UNKNOWN, Verdict.of(Verdict.entries.size))
    }

    /** A destructive unit must behave exactly as the shipped stand-down did before this measured. */
    @Test
    fun `only a measured failure withholds the wake`() {
        for (verdict in Verdict.entries) {
            assertEquals(
                verdict.name,
                verdict != Verdict.DESTRUCTIVE,
                NativeAaWakeDamagePolicy.allowsEscalation(verdict)
            )
        }
    }
}
