package com.andrerinas.openheadunit.aap

import android.content.Context
import android.os.SystemClock
import com.andrerinas.openheadunit.connection.AccessoryConnection
import com.andrerinas.openheadunit.connection.SocketAccessoryConnection
import com.andrerinas.openheadunit.decoder.MicRecorder
import com.andrerinas.openheadunit.utils.AppLog
import com.andrerinas.openheadunit.aap.protocol.Channel
import com.andrerinas.openheadunit.aap.protocol.proto.MediaPlayback
import com.andrerinas.openheadunit.utils.Settings

internal interface AapRead {
    fun read(): Int

    abstract class Base internal constructor(
            private val connection: AccessoryConnection?,
            internal val ssl: AapSsl,
            internal val handler: AapMessageHandler) : AapRead {

        private val fragmentAudit = FragmentedMessageAudit()

        /**
         * Per-outcome print budget: how many have been printed, when the last one was, and how many
         * went unprinted since. See [AuditReportPolicy] for why this refills instead of running out.
         */
        private val auditReports = IntArray(FragmentedMessageAudit.Outcome.entries.size)
        private val auditLastReportMs = LongArray(FragmentedMessageAudit.Outcome.entries.size)
        private val auditSuppressed = IntArray(FragmentedMessageAudit.Outcome.entries.size)

        /**
         * Largest single message body seen, reported each time it crosses a power of two.
         *
         * `msgBuffer` is a flat 4MB in both readers, which on a 1GB unit is a fifth of the whole Java
         * heap standing reserved for a message size nobody has measured. Shrinking it on a guess would
         * turn a large frame into "Invalid message size ... Skipping", so measure it first: at most a
         * dozen lines ever, and they say exactly how small it could safely be.
         */
        private var maxEncLenSeen = 0

        override fun read(): Int {
            if (connection == null) {
                AppLog.e("No connection.")
                return -1
            }

            return doRead(connection)
        }

        /**
         * Cross-checks a fragment run against the total size its first fragment declared.
         *
         * Both readers already read that 4-byte total and discarded it, which left the one corruption
         * mode nothing downstream can see: a missing *middle* fragment leaves the run looking intact
         * to the reassembler, so the frame is assembled with a hole and decoded as though it were
         * whole. See [FragmentedMessageAudit] for why the check learns the convention rather than
         * assuming one.
         *
         * [declaredTotal] is only meaningful on a first fragment and is ignored otherwise.
         */
        protected fun auditFragment(channel: Int, flags: Int, encLen: Int, declaredTotal: Int) {
            if (encLen > maxEncLenSeen) {
                val crossedBoundary = Integer.highestOneBit(encLen) > Integer.highestOneBit(maxEncLenSeen)
                maxEncLenSeen = encLen
                if (crossedBoundary) {
                    AppLog.i("AapRead: largest message body so far: %d bytes (on %s)", encLen, Channel.name(channel))
                }
            }
            val result = fragmentAudit.onMessage(channel, flags, encLen, declaredTotal) ?: return
            val channelName = Channel.name(channel)
            if (result.outcome == FragmentedMessageAudit.Outcome.FIRST_OBSERVATION) {
                AppLog.i("AapRead: fragment accounting established for %s: %s", channelName, result)
                return
            }
            val index = result.outcome.ordinal
            val now = SystemClock.elapsedRealtime()
            if (!AuditReportPolicy.shouldReport(auditReports[index], auditLastReportMs[index], now)) {
                auditSuppressed[index]++
                return
            }
            val suppressed = auditSuppressed[index]
            auditSuppressed[index] = 0
            auditReports[index]++
            auditLastReportMs[index] = now
            val suffix = if (suppressed > 0) " (and $suppressed more since the last report)" else ""
            AppLog.w("AapRead: %s on %s - %s%s", result.outcome, channelName, result, suffix)
        }

        protected abstract fun doRead(connection: AccessoryConnection): Int
    }

    object Factory {
        fun create(
            connection: AccessoryConnection,
            transport: AapTransport,
            recorder: MicRecorder,
            aapAudio: AapAudio,
            aapVideo: AapVideo,
            settings: Settings,
            context: Context,
            onAaMediaMetadata: ((MediaPlayback.MediaMetaData) -> Unit)? = null,
            onAaPlaybackStatus: ((MediaPlayback.MediaPlaybackStatus) -> Unit)? = null
        ): AapRead {
            val handler = AapMessageHandlerType(
                transport,
                recorder,
                aapAudio,
                aapVideo,
                settings,
                context,
                onAaMediaMetadata,
                onAaPlaybackStatus
            )

            // Read framing is a transport-shape question, not a handshake-timing one:
            // every socket-backed connection (Nearby, WiFi Direct, Hotspot, manual IP) frames
            // one AA message per blocking read, same as it always has. Only USB/libusb bulk
            // transfers can batch multiple messages into one read and need the FIFO reassembly
            // in AapReadMultipleMessages. Do NOT key this off isSingleMessage - that flag only
            // controls the Nearby-specific handshake settle-delay/drain skip in
            // AapTransport.handshake() and is unrelated to read framing.
            return if (connection is SocketAccessoryConnection)
                AapReadSingleMessage(connection, transport.ssl, handler)
            else
                AapReadMultipleMessages(connection, transport.ssl, handler)
        }
    }
}
