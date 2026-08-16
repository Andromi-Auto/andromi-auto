package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.connection.CommManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The full state table is spelled out because the watchdog's original inline check accepted only
 * HandshakeComplete — a state the session passes through once, briefly — and returned without
 * re-posting, so it died on the first tick of every session. TransportStarted being live is the
 * regression this table pins.
 */
class ProjectionWatchdogPolicyTest {

    @Test
    fun `the session is live through the handshake window and the whole projection`() {
        assertTrue(ProjectionWatchdogPolicy.isSessionLive(CommManager.ConnectionState.HandshakeComplete))
        assertTrue(ProjectionWatchdogPolicy.isSessionLive(CommManager.ConnectionState.TransportStarted))
    }

    @Test
    fun `every state outside a live session stops the watchdog`() {
        assertFalse(ProjectionWatchdogPolicy.isSessionLive(CommManager.ConnectionState.Disconnected()))
        assertFalse(ProjectionWatchdogPolicy.isSessionLive(CommManager.ConnectionState.Connecting))
        assertFalse(ProjectionWatchdogPolicy.isSessionLive(CommManager.ConnectionState.Connected))
        assertFalse(ProjectionWatchdogPolicy.isSessionLive(CommManager.ConnectionState.StartingTransport))
        assertFalse(ProjectionWatchdogPolicy.isSessionLive(CommManager.ConnectionState.Error("boom")))
    }

    @Test
    fun `no request without the reconnecting overlay`() {
        // The overlay is the watchdog's own 10s-frame-gap signal; without it the picture is fine
        // and a focus request would only disturb a healthy stream.
        assertFalse(ProjectionWatchdogPolicy.shouldRequestVideoFocus(false, nowMs = 100_000, lastRequestMs = 0))
    }

    @Test
    fun `requests are paced by the keyframe throttle`() {
        val throttle = VideoRecoveryPolicy.KEYFRAME_REQUEST_THROTTLE_MS
        // Inside the throttle window: suppressed, boundary included (canRequestKeyframe is strict).
        assertFalse(ProjectionWatchdogPolicy.shouldRequestVideoFocus(true, nowMs = throttle, lastRequestMs = 0))
        // Past it: allowed.
        assertTrue(ProjectionWatchdogPolicy.shouldRequestVideoFocus(true, nowMs = throttle + 1, lastRequestMs = 0))
    }
}
