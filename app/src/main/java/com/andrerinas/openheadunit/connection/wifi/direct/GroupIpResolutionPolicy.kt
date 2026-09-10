package com.andrerinas.openheadunit.connection.wifi.direct

/**
 * How long to look for the group interface's own IP before handing the phone the one the platform
 * fixes anyway.
 *
 * A P2P group owner is always 192.168.49.1, and the delivery already fell back to it - after up to
 * fifteen one-second reads. Waiting for a number we are going to substitute delays the credentials,
 * and the wake poke behind them.
 */
object GroupIpResolutionPolicy {

    /** The address AOSP gives every P2P group owner. */
    const val GROUP_OWNER_IP = "192.168.49.1"

    /** Re-reads a client spends waiting for its DHCP lease, one per second, after the first read. */
    const val CLIENT_RETRIES = 15

    /**
     * Re-reads to spend after the first one. A group owner has a fallback that is always right, so
     * it takes the first answer either way; a client has none and has to wait for its lease.
     */
    fun retriesAfterFirstRead(isGroupOwner: Boolean): Int =
        if (isGroupOwner) 0 else CLIENT_RETRIES

    /**
     * The address to deliver, or null when there is none to send. [readIp] is what the interface
     * answered, or null when it did not.
     */
    fun resolve(readIp: String?, isGroupOwner: Boolean): String? =
        readIp ?: if (isGroupOwner) GROUP_OWNER_IP else null
}
