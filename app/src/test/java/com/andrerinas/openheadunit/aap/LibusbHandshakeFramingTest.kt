package com.andrerinas.openheadunit.aap

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.SystemClock
import com.andrerinas.openheadunit.connection.projection.LibusbProjectionConnection
import com.andrerinas.openheadunit.connection.usb.UsbNative
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import java.nio.ByteBuffer

class LibusbHandshakeFramingTest {
    @Test fun `timeout bytes from JNI pass through USB leftovers and both handshake phases once`() =
        mockStatic(SystemClock::class.java).use {
            val stale = byteArrayOf(5, 9, 0, 3, -1, -1, -1, -1, 1, 2, 3)
            val version = byteArrayOf(0, 3, 0, 8, 0, 2, 0, 1, 0, 7, 0, 0)
            val tls = byteArrayOf(0, 3, 0, 5, 0, 3, 22, 3, 3)
            val stream = stale + version + tls
            for (split in 1 until stale.size + version.size) {
                val native = mock(UsbNative::class.java)
                val connection = LibusbProjectionConnection(mock(UsbManager::class.java), mock(UsbDevice::class.java))
                connection.isConnected = true
                LibusbProjectionConnection::class.java.getDeclaredField("usbNative").apply {
                    isAccessible = true
                }.set(connection, native)
                var nativeReads = 0
                `when`(native.read(any(), anyInt())).thenAnswer { call ->
                    // JNI returns the count even on a libusb timeout (#1128). The real USB
                    // connection must retain excess bytes when #1127 asks for a smaller header.
                    val from = if (nativeReads == 0) 0 else split
                    val to = if (nativeReads == 0) split else stream.size
                    assertTrue("unexpected reread of consumed data", nativeReads++ < 2)
                    call.getArgument<ByteBuffer>(0).put(stream, from, to - from)
                    to - from
                }
                val versionReader = HandshakeFrameReader({ 0L }, 100, skipEncryptedFrames = true) { out, length, timeout ->
                    connection.recvBlocking(out, length, timeout, false)
                }
                assertArrayEquals(version, versionReader.readFrame(1000))
                // The TLS phase gets a fresh strict reader but shares the USB leftover buffer.
                val tlsReader = HandshakeFrameReader({ 0L }, 100) { out, length, timeout ->
                    connection.recvBlocking(out, length, timeout, false)
                }
                assertArrayEquals(tls, tlsReader.readFrame(1000))
                assertEquals(2, nativeReads)
                assertTrue(connection.isConnected)
            }
        }
}
