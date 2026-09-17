package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.protocol.proto.Common
import com.andrerinas.openheadunit.aap.protocol.proto.Control

/**
 * The version response the phone sends before the SSL handshake, which this app used to discard.
 *
 * Payload after the 6 byte AAP header is major, minor and status as big-endian shorts, then an
 * optional VersionResponseOptions proto carrying the connection parameters the phone wants us to
 * use. The phone selects the minor version, so it is the negotiated one, not the one we asked for.
 */
object AapVersionNegotiation {

    /** What Messages.versionRequest announces. */
    const val ANNOUNCED_MAJOR = 1
    const val ANNOUNCED_MINOR = 2

    /** Messages added in 1.6 must not be sent to a phone that selected less. */
    const val FEATURE_MINOR_1_6 = 6

    data class Result(
        val major: Int,
        val minor: Int,
        val status: Int,
        val requestedConfig: Control.ConnectionConfiguration?
    ) {
        /** True once the negotiated version carries the 1.6 message set. */
        val supports16: Boolean get() = major > 1 || (major == 1 && minor >= FEATURE_MINOR_1_6)

        val statusName: String
            get() = Common.MessageStatus.forNumber(status)?.name ?: "UNKNOWN($status)"
    }

    /**
     * [frame] is the whole received frame including the 6 byte header, [length] the bytes read.
     * Returns null when the frame is too short to be a version response.
     */
    fun parse(frame: ByteArray, length: Int): Result? {
        if (length < 12) return null
        fun u16(i: Int) = ((frame[i].toInt() and 0xFF) shl 8) or (frame[i + 1].toInt() and 0xFF)
        val status = u16(10)
        var config: Control.ConnectionConfiguration? = null
        if (length > 12) {
            config = try {
                Control.VersionResponseOptions
                    .parseFrom(frame.copyOfRange(12, length))
                    .takeIf { it.hasConnectionConfiguration() }
                    ?.connectionConfiguration
            } catch (_: Exception) {
                // Trailing bytes that are not the options proto: the version is still good.
                null
            }
        }
        // status is a signed 16-bit MessageStatus, so -1 arrives as 0xFFFF.
        val signed = if (status >= 0x8000) status - 0x10000 else status
        return Result(u16(6), u16(8), signed, config)
    }
}
