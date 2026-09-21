package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.protocol.proto.Control
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AapVersionNegotiationTest {

    /** ch 0, flags 3, len, type 0x0002, then major/minor/status. */
    private fun frame(major: Int, minor: Int, status: Int, tail: ByteArray = ByteArray(0)): ByteArray {
        fun hi(v: Int) = ((v shr 8) and 0xFF).toByte()
        fun lo(v: Int) = (v and 0xFF).toByte()
        return byteArrayOf(0, 3, 0, 8, 0, 2, hi(major), lo(major), hi(minor), lo(minor),
            hi(status), lo(status)) + tail
    }

    @Test
    fun `reads the version the phone selected, not the one we asked for`() {
        val r = AapVersionNegotiation.parse(frame(1, 6, 0), 12)!!
        assertEquals(1, r.major)
        assertEquals(6, r.minor)
        assertEquals(0, r.status)
        assertEquals("STATUS_SUCCESS", r.statusName)
    }

    @Test
    fun `a negative status arrives as an unsigned short and is named`() {
        val r = AapVersionNegotiation.parse(frame(1, 2, 0xFFFF), 12)!!
        assertEquals(-1, r.status)
        assertEquals("STATUS_NO_COMPATIBLE_VERSION", r.statusName)
    }

    @Test
    fun `the one six feature set is gated on the negotiated minor`() {
        assertFalse(AapVersionNegotiation.parse(frame(1, 2, 0), 12)!!.supports16)
        assertFalse(AapVersionNegotiation.parse(frame(1, 5, 0), 12)!!.supports16)
        assertTrue(AapVersionNegotiation.parse(frame(1, 6, 0), 12)!!.supports16)
        assertTrue(AapVersionNegotiation.parse(frame(2, 0, 0), 12)!!.supports16)
    }

    @Test
    fun `what we announce today does not reach the one six feature set`() {
        val announced = AapVersionNegotiation.parse(
            frame(AapVersionNegotiation.ANNOUNCED_MAJOR, AapVersionNegotiation.ANNOUNCED_MINOR, 0), 12
        )!!
        assertFalse(announced.supports16)
    }

    @Test
    fun `the connection parameters the phone asks for are read back`() {
        val opts = Control.VersionResponseOptions.newBuilder()
            .setConnectionConfiguration(
                Control.ConnectionConfiguration.newBuilder().setPingConfiguration(
                    Control.PingConfiguration.newBuilder().setTimeoutMs(9000)
                )
            ).build().toByteArray()
        val f = frame(1, 6, 0, opts)
        val r = AapVersionNegotiation.parse(f, f.size)!!
        assertEquals(9000, r.requestedConfig!!.pingConfiguration.timeoutMs)
    }

    @Test
    fun `no options means no request, not a failure`() {
        assertNull(AapVersionNegotiation.parse(frame(1, 2, 0), 12)!!.requestedConfig)
    }

    @Test
    fun `trailing bytes that are not the options proto do not lose the version`() {
        val f = frame(1, 4, 0, byteArrayOf(-1, -1, -1, -1))
        val r = AapVersionNegotiation.parse(f, f.size)!!
        assertEquals(4, r.minor)
        assertNull(r.requestedConfig)
    }

    @Test
    fun `a frame too short to hold a version is refused`() {
        assertNull(AapVersionNegotiation.parse(frame(1, 2, 0), 11))
    }
}
