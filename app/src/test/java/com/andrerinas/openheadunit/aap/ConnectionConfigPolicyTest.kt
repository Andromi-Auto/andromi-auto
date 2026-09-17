package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionConfigPolicyTest {

    @Test
    fun `off by default announces nothing`() {
        assertNull(ConnectionConfigPolicy.announce(false))
    }

    @Test
    fun `the ping timeout outlasts a station scan`() {
        val c = ConnectionConfigPolicy.announce(true)!!
        // Scans were measured taking the radio off channel every 6 to 22 s.
        assertTrue(c.pingConfiguration.timeoutMs >= 15000)
    }

    @Test
    fun `the read timeout never closes the socket before the ping timeout fires`() {
        val c = ConnectionConfigPolicy.announce(true)!!
        assertTrue(
            c.wirelessTcpConfiguration.socketReadTimeoutMs >= c.pingConfiguration.timeoutMs
        )
    }

    @Test
    fun `buffers stay inside the documented ceiling`() {
        val c = ConnectionConfigPolicy.announce(true)!!.wirelessTcpConfiguration
        assertEquals(65536, c.socketReceiveBufferSize)
        assertEquals(65536, c.socketSendBufferSize)
        assertTrue(c.socketReceiveBufferSize <= 65536)
    }

    @Test
    fun `the deprecated kb fields are left unset`() {
        val c = ConnectionConfigPolicy.announce(true)!!.wirelessTcpConfiguration
        assertTrue(!c.hasSocketReceiveBufferSizeKb() && !c.hasSocketSendBufferSizeKb())
    }

    @Test
    fun `only the ping timeout is overridden`() {
        val p = ConnectionConfigPolicy.announce(true)!!.pingConfiguration
        assertTrue(p.hasTimeoutMs())
        assertTrue(!p.hasIntervalMs() && !p.hasHighLatencyThresholdMs() && !p.hasTrackedPingCount())
    }
}
