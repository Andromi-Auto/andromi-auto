package com.andrerinas.openheadunit.aap

import android.os.SystemClock
import com.andrerinas.openheadunit.connection.projection.ProjectionConnection
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.any

/** Exercise the transport entry point, including the handoff to TLS, not just the byte parser. */
class VersionHandshakeFramingTest {
    private lateinit var log: org.mockito.MockedStatic<android.util.Log>
    @org.junit.Before fun stubLog() {
        log = mockStatic(android.util.Log::class.java)
        log.`when`<String> { android.util.Log.getStackTraceString(any<Throwable>()) }.thenReturn("test exception")
    }
    @org.junit.After fun restoreLog() { log.close() }
    private val response = byteArrayOf(0, 3, 0, 8, 0, 2, 0, 1, 0, 7, 0, 0)
    private val tlsFrame = byteArrayOf(0, 3, 0, 3, 0, 3, 22)

    private fun transport(ssl: AapSsl): AapTransport {
        val transport = mock(AapTransport::class.java, CALLS_REAL_METHODS)
        AapTransport::class.java.getDeclaredField("ssl").apply { isAccessible = true }.set(transport, ssl)
        return transport
    }

    private fun run(transport: AapTransport, connection: ProjectionConnection) {
        val method = AapTransport::class.java.getDeclaredMethod("handshake", ProjectionConnection::class.java)
            .apply { isAccessible = true }
        // A mock TLS engine returns false after we prove it was reached (or not reached).
        assertEquals(false, method.invoke(transport, connection))
    }

    @Test fun `split response survives retry and leaves coalesced TLS bytes for SSL`() = mockStatic(SystemClock::class.java).use { clock ->
        for (split in 1 until response.size) {
            var now = 0L
            clock.`when`<Long> { SystemClock.elapsedRealtime() }.thenAnswer { now }
            val connection = mock(ProjectionConnection::class.java)
            `when`(connection.isSingleMessage).thenReturn(true)
            `when`(connection.isConnected).thenReturn(true)
            `when`(connection.sendBlocking(any(), anyInt(), anyInt())).thenReturn(10)
            val stream = response + tlsFrame
            var position = 0
            var timedOut = false
            `when`(connection.recvBlocking(any(), anyInt(), anyInt(), anyBoolean())).thenAnswer {
                assertFalse(it.getArgument<Boolean>(3))
                if (position == split && !timedOut) {
                    timedOut = true
                    now += it.getArgument<Int>(2)
                    0
                } else {
                    val count = minOf(it.getArgument<Int>(1),
                        if (position < split) split - position else stream.size - position)
                    stream.copyInto(it.getArgument<ByteArray>(0), 0, position, position + count)
                    position += count
                    count
                }
            }
            val ssl = mock(AapSsl::class.java)
            val transport = transport(ssl)
            run(transport, connection)
            verify(ssl).performHandshake(connection)
            assertEquals(response.size, position)
            assertEquals(7, transport.negotiatedVersion!!.minor)
            verify(connection, times(2)).sendBlocking(any(), anyInt(), anyInt())
        }
    }

    @Test fun `rejected truncated and malformed responses never reach TLS`() = mockStatic(SystemClock::class.java).use {
        val invalid = listOf(
            response.copyOf().also { it[10] = -1; it[11] = -1 },
            byteArrayOf(0, 3, 0, 2, 0, 2),
            byteArrayOf(0, 3, 0, 2, -1, -1),
            byteArrayOf(0, 3, 0, 2, 0, 15),
            response.copyOf().also { it[1] = 7 }
        )
        for (frame in invalid) {
            val connection = mock(ProjectionConnection::class.java)
            `when`(connection.isSingleMessage).thenReturn(true)
            `when`(connection.isConnected).thenReturn(true)
            `when`(connection.sendBlocking(any(), anyInt(), anyInt())).thenReturn(10)
            var position = 0
            `when`(connection.recvBlocking(any(), anyInt(), anyInt(), anyBoolean())).thenAnswer {
                val count = minOf(it.getArgument<Int>(1), frame.size - position)
                frame.copyInto(it.getArgument<ByteArray>(0), 0, position, position + count)
                position += count
                count
            }
            val ssl = mock(AapSsl::class.java)
            val transport = transport(ssl)
            run(transport, connection)
            verifyNoInteractions(ssl)
            assertNull(transport.negotiatedVersion)
            assertEquals(if (frame[1] != 3.toByte()) AapTransport.HandshakeFailure.TRANSPORT_ERROR
                else AapTransport.HandshakeFailure.OTHER, transport.lastHandshakeFailure)
        }
    }

    @Test fun `partial request write fails without sending a second header`() = mockStatic(SystemClock::class.java).use {
        val connection = mock(ProjectionConnection::class.java)
        `when`(connection.isSingleMessage).thenReturn(true)
        `when`(connection.isConnected).thenReturn(true)
        `when`(connection.sendBlocking(any(), anyInt(), anyInt())).thenReturn(5)
        val ssl = mock(AapSsl::class.java)
        val transport = transport(ssl)
        run(transport, connection)
        assertEquals(AapTransport.HandshakeFailure.TRANSPORT_ERROR, transport.lastHandshakeFailure)
        verify(connection, times(1)).sendBlocking(any(), anyInt(), anyInt())
        verify(connection, never()).recvBlocking(any(), anyInt(), anyInt(), anyBoolean())
        verifyNoInteractions(ssl)
    }

    @Test fun `USB stale encrypted traffic after the drain is skipped before version and TLS`() = mockStatic(SystemClock::class.java).use {
        val connection = mock(ProjectionConnection::class.java)
        `when`(connection.type).thenReturn(ProjectionConnection.Type.USB)
        `when`(connection.isConnected).thenReturn(true)
        `when`(connection.sendBlocking(any(), anyInt(), anyInt())).thenReturn(10)
        val stale = byteArrayOf(5, 9, 0, 3, -1, -1, -1, -1, 1, 2, 3) +
            byteArrayOf(5, 8, 0, 1, 4) + byteArrayOf(5, 10, 0, 0) + byteArrayOf(5, 11, 0, 1, 5)
        val stream = stale + response + tlsFrame
        var position = 0
        var draining = true
        `when`(connection.recvBlocking(any(), anyInt(), anyInt(), anyBoolean())).thenAnswer { call ->
            if (draining) {
                draining = false
                assertEquals(50, call.getArgument<Int>(2))
                0 // Old traffic arrives only after the initial drain went quiet.
            } else {
                assertTrue(call.getArgument<Int>(2) >= 100)
                call.getArgument<ByteArray>(0)[0] = stream[position++]
                1
            }
        }
        val lines = mutableListOf<String>()
        val original = com.andrerinas.openheadunit.utils.AppLog.LOGGER
        com.andrerinas.openheadunit.utils.AppLog.LOGGER = object : com.andrerinas.openheadunit.utils.AppLog.Logger {
            override fun println(priority: Int, tag: String, msg: String) { lines.add(msg) }
        }
        try {
            val ssl = mock(AapSsl::class.java)
            run(transport(ssl), connection)
            verify(ssl).performHandshake(connection)
            assertEquals(stale.size + response.size, position)
            assertTrue(lines.any { line -> line.contains("the phone selected 1.7 (we asked for 1.2)") })
            assertTrue(lines.any { line -> line.contains("Version response recv ret: 12") })
        } finally { com.andrerinas.openheadunit.utils.AppLog.LOGGER = original }
    }

    @Test fun `negative or short request writes never replay on USB or WiFi`() = mockStatic(SystemClock::class.java).use {
        for (type in ProjectionConnection.Type.entries) for (result in listOf(-7, -1, 0, 5)) {
            val connection = mock(ProjectionConnection::class.java)
            `when`(connection.type).thenReturn(type)
            `when`(connection.isConnected).thenReturn(true)
            // A subsequent call could succeed, but the first error doesn't prove zero delivery.
            // In particular, #1128 logs libusb's count while still returning a negative timeout.
            `when`(connection.sendBlocking(any(), anyInt(), anyInt())).thenReturn(result, 10)
            val ssl = mock(AapSsl::class.java)
            val transport = transport(ssl)
            run(transport, connection)
            verify(connection, times(1)).sendBlocking(any(), anyInt(), anyInt())
            verifyNoInteractions(ssl)
            assertEquals(AapTransport.HandshakeFailure.TRANSPORT_ERROR, transport.lastHandshakeFailure)
        }
    }
}
