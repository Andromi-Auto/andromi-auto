package com.andrerinas.openheadunit.connection.wifi.modes.nativeaa

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EarlyWakePolicyTest {

    private val none = Triple("", "", "")
    private val real = Triple("DIRECT-ab", "192.168.49.1", "aa:bb:cc:dd:ee:ff")

    @Test
    fun `wakes early once the listeners are open`() {
        assertTrue(
            EarlyWakePolicy.mayWakeBeforeCredentials(
                listenersOpen = true, credentialsPresent = false,
                userExited = false, sessionUp = false
            )
        )
    }

    @Test
    fun `never wakes before the listeners are accepting`() {
        assertFalse(
            EarlyWakePolicy.mayWakeBeforeCredentials(
                listenersOpen = false, credentialsPresent = false,
                userExited = false, sessionUp = false
            )
        )
    }

    @Test
    fun `credentials already there is the ordinary path, not the early one`() {
        assertFalse(
            EarlyWakePolicy.mayWakeBeforeCredentials(
                listenersOpen = true, credentialsPresent = true,
                userExited = false, sessionUp = false
            )
        )
    }

    @Test
    fun `a user exit and a live session both hold the wake`() {
        assertFalse(
            EarlyWakePolicy.mayWakeBeforeCredentials(
                listenersOpen = true, credentialsPresent = false,
                userExited = true, sessionUp = false
            )
        )
        assertFalse(
            EarlyWakePolicy.mayWakeBeforeCredentials(
                listenersOpen = true, credentialsPresent = false,
                userExited = false, sessionUp = true
            )
        )
    }

    @Test
    fun `credentials arriving under an early loop does not restart it`() {
        assertFalse(EarlyWakePolicy.shouldRestartLoop(loopActive = true, previousKey = none, newKey = real))
    }

    @Test
    fun `an unchanged key does not restart the loop`() {
        assertFalse(EarlyWakePolicy.shouldRestartLoop(loopActive = true, previousKey = real, newKey = real))
    }

    @Test
    fun `a genuinely new group restarts the loop`() {
        val other = Triple("DIRECT-zz", "192.168.49.1", "11:22:33:44:55:66")
        assertTrue(EarlyWakePolicy.shouldRestartLoop(loopActive = true, previousKey = real, newKey = other))
    }

    @Test
    fun `no loop running always starts one`() {
        assertTrue(EarlyWakePolicy.shouldRestartLoop(loopActive = false, previousKey = real, newKey = real))
        assertTrue(EarlyWakePolicy.shouldRestartLoop(loopActive = false, previousKey = null, newKey = none))
    }

    @Test
    fun `credentials going away restarts the loop`() {
        assertTrue(EarlyWakePolicy.shouldRestartLoop(loopActive = true, previousKey = real, newKey = none))
    }
}
