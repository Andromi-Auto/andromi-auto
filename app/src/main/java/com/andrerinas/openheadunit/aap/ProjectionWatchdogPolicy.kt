package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.connection.CommManager

/**
 * Decides when the projection activity's recovery watchdog keeps running and when a black
 * mid-session picture warrants asking the phone for video again.
 *
 * Extracted because the previous inline check cost the watchdog its life on the first tick of
 * every session: it accepted only [CommManager.ConnectionState.HandshakeComplete], but that state
 * is a brief window before video starts - the steady state for the whole drive is
 * [CommManager.ConnectionState.TransportStarted] - and the runnable returned without re-posting
 * itself. Everything hanging off it (the reconnecting overlay, display-stall recovery, the
 * renderer-confirm offer) was unreachable in a normal session, so a video stream that died
 * mid-session stayed black with nothing ever asking for it back.
 */
object ProjectionWatchdogPolicy {

    /**
     * True while the projection session is live: [CommManager.ConnectionState.HandshakeComplete]
     * is the brief window before video starts, [CommManager.ConnectionState.TransportStarted] the
     * steady state after. The watchdog must keep ticking through both.
     */
    fun isSessionLive(state: CommManager.ConnectionState): Boolean =
        state is CommManager.ConnectionState.HandshakeComplete ||
            state is CommManager.ConnectionState.TransportStarted

    /**
     * Whether a black mid-session picture warrants asking the phone for video focus again.
     * Gated on the reconnecting overlay being up, which the watchdog only shows after a 10s
     * frame gap on a live connection, and paced by [VideoRecoveryPolicy] so a stuck session
     * asks about once per throttle window rather than on every tick.
     */
    fun shouldRequestVideoFocus(
        reconnectingOverlayShown: Boolean,
        nowMs: Long,
        lastRequestMs: Long
    ): Boolean = reconnectingOverlayShown && VideoRecoveryPolicy.canRequestKeyframe(nowMs, lastRequestMs)
}
