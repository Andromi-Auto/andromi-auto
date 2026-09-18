package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.protocol.proto.Control

/**
 * The ping and TCP parameters we ask Android Auto for in ServiceDiscoveryResponse field 16.
 *
 * The protocol's defaults assume a link that is either up or gone. A head unit whose radio goes off
 * channel for a few seconds is neither, and the 3 s ping timeout ends the session before it returns.
 */
object ConnectionConfigPolicy {

    /** Long enough to ride out a station scan taking the radio off channel, measured at 6 to 22 s. */
    const val PING_TIMEOUT_MS = 15000

    /** The read timeout tracks the ping timeout: a shorter one would close the socket first. */
    const val SOCKET_READ_TIMEOUT_MS = 15000

    /** Android Auto documents 16384 as the default and 65536 as the ceiling. */
    const val SOCKET_BUFFER_BYTES = 65536

    /** Null when the announcement is off, which is the default. */
    fun announce(enabled: Boolean): Control.ConnectionConfiguration? {
        if (!enabled) return null
        return Control.ConnectionConfiguration.newBuilder()
            .setPingConfiguration(
                // interval, latency threshold and tracked count are left at Android Auto's
                // defaults: the timeout is the only one a stalled link turns into a teardown.
                Control.PingConfiguration.newBuilder()
                    .setTimeoutMs(PING_TIMEOUT_MS)
                    .build()
            )
            .setWirelessTcpConfiguration(
                Control.WirelessTcpConfiguration.newBuilder()
                    .setSocketReadTimeoutMs(SOCKET_READ_TIMEOUT_MS)
                    .setSocketReceiveBufferSize(SOCKET_BUFFER_BYTES)
                    .setSocketSendBufferSize(SOCKET_BUFFER_BYTES)
                    .build()
            )
            .build()
    }
}
