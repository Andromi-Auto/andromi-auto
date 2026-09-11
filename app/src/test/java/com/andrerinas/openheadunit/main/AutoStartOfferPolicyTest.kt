package com.andrerinas.openheadunit.main

import com.andrerinas.openheadunit.main.AutoStartOfferPolicy.Action
import com.andrerinas.openheadunit.main.AutoStartOfferPolicy.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoStartOfferPolicyTest {

    private val phone = "AA:BB:CC:DD:EE:FF"

    @Test
    fun `one phone that has never been asked about is offered`() {
        assertEquals(
            Action.ASK,
            AutoStartOfferPolicy.decide(
                phonesPaired = 1, connectedMac = phone,
                answeredMacs = emptySet(), autoStartConfigured = false
            )
        )
    }

    @Test
    fun `a phone already answered for is not asked again`() {
        assertEquals(
            Action.NOTHING,
            AutoStartOfferPolicy.decide(
                phonesPaired = 1, connectedMac = phone,
                answeredMacs = setOf(phone), autoStartConfigured = false
            )
        )
    }

    @Test
    fun `the answered list is matched without case, which is how addresses come back`() {
        assertEquals(
            Action.NOTHING,
            AutoStartOfferPolicy.decide(
                phonesPaired = 1, connectedMac = phone.lowercase(),
                answeredMacs = setOf(phone), autoStartConfigured = false
            )
        )
    }

    @Test
    fun `nothing is offered when auto-start is already set up`() {
        assertEquals(
            Action.NOTHING,
            AutoStartOfferPolicy.decide(
                phonesPaired = 1, connectedMac = phone,
                answeredMacs = emptySet(), autoStartConfigured = true
            )
        )
    }

    @Test
    fun `a second phone resets a trigger that no longer names anybody`() {
        assertEquals(
            Action.RESET,
            AutoStartOfferPolicy.decide(
                phonesPaired = 2, connectedMac = phone,
                answeredMacs = emptySet(), autoStartConfigured = true
            )
        )
    }

    @Test
    fun `a second phone with nothing stored has nothing to reset`() {
        assertEquals(
            Action.NOTHING,
            AutoStartOfferPolicy.decide(
                phonesPaired = 2, connectedMac = phone,
                answeredMacs = emptySet(), autoStartConfigured = false
            )
        )
    }

    @Test
    fun `a phone that cannot be named is never offered`() {
        assertEquals(
            Action.NOTHING,
            AutoStartOfferPolicy.decide(
                phonesPaired = 1, connectedMac = "",
                answeredMacs = emptySet(), autoStartConfigured = false
            )
        )
    }

    @Test
    fun `no paired phones offers nothing`() {
        assertEquals(
            Action.NOTHING,
            AutoStartOfferPolicy.decide(
                phonesPaired = 0, connectedMac = phone,
                answeredMacs = emptySet(), autoStartConfigured = false
            )
        )
    }

    @Test
    fun `the reset stands whether or not this phone was answered for`() {
        assertEquals(
            Action.RESET,
            AutoStartOfferPolicy.decide(
                phonesPaired = 3, connectedMac = phone,
                answeredMacs = setOf(phone), autoStartConfigured = true
            )
        )
    }

    @Test
    fun `the reset belongs to the home screen, where a second phone is noticed`() {
        assertTrue(AutoStartOfferPolicy.actsNow(Action.RESET, Trigger.HOME_SCREEN))
        assertFalse(AutoStartOfferPolicy.actsNow(Action.RESET, Trigger.PROJECTION_START))
    }

    @Test
    fun `the question is asked over the picture, never on the home screen`() {
        assertTrue(AutoStartOfferPolicy.actsNow(Action.ASK, Trigger.PROJECTION_START))
        assertFalse(AutoStartOfferPolicy.actsNow(Action.ASK, Trigger.HOME_SCREEN))
    }

    @Test
    fun `nothing acts at either moment`() {
        assertFalse(AutoStartOfferPolicy.actsNow(Action.NOTHING, Trigger.HOME_SCREEN))
        assertFalse(AutoStartOfferPolicy.actsNow(Action.NOTHING, Trigger.PROJECTION_START))
    }

    @Test
    fun `every action has exactly one moment it belongs to, or none`() {
        for (action in Action.values()) {
            val moments = Trigger.values().count { AutoStartOfferPolicy.actsNow(action, it) }
            assertTrue("$action acts at $moments moments", moments <= 1)
        }
    }

    @Test
    fun `the offer answers itself after twenty seconds`() {
        assertEquals(20_000L, AutoStartOfferPolicy.OFFER_TIMEOUT_MS)
    }
}
