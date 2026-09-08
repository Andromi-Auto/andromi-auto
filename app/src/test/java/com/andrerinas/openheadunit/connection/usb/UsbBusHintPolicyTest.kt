package com.andrerinas.openheadunit.connection.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsbBusHintPolicyTest {

    @Test
    fun `an empty bus is the empty-bus hint whatever else was seen`() {
        assertEquals(UsbBusHintPolicy.Hint.EMPTY_BUS, UsbBusHintPolicy.hint(0, 0, 4))
    }

    @Test
    fun `a usable device needs no hint`() {
        assertNull(UsbBusHintPolicy.hint(1, 1, 3))
    }

    @Test
    fun `one refused device is not a cycling adapter`() {
        assertEquals(UsbBusHintPolicy.Hint.NONE_USABLE, UsbBusHintPolicy.hint(1, 0, 1))
    }

    @Test
    fun `two identities inside the window is a cycling adapter`() {
        assertEquals(UsbBusHintPolicy.Hint.CYCLING_ADAPTER, UsbBusHintPolicy.hint(1, 0, 2))
    }

    @Test
    fun `the same identity twice is one identity`() {
        val now = 100_000L
        val seen = listOf(now - 30_000 to "0525:A4A7", now - 10_000 to "0525:A4A7")
        assertEquals(listOf("0525:A4A7"), UsbBusHintPolicy.identitiesInWindow(seen, now))
    }

    @Test
    fun `the reporter's adapter reads as two identities, newest first`() {
        val now = 300_000L
        val seen = listOf(
            now - 90_000 to "05AC:12A8",  // outside the window
            now - 40_000 to "0525:A4A7",
            now - 10_000 to "05AC:12A8",
        )
        assertEquals(listOf("05AC:12A8", "0525:A4A7"), UsbBusHintPolicy.identitiesInWindow(seen, now))
    }

    @Test
    fun `an identity exactly at the window edge is out`() {
        val now = 100_000L
        val seen = listOf(now - UsbBusHintPolicy.IDENTITY_WINDOW_MS to "0525:A4A7")
        assertEquals(emptyList<String>(), UsbBusHintPolicy.identitiesInWindow(seen, now))
    }
}
