package com.andrerinas.openheadunit.connection.wifi.server

import android.os.SystemClock

class WirelessServerHistory {

    // Rebuild bookkeeping for WirelessServerRestartPolicy. The handshake asks about every 4s while a
    // phone keeps arriving, so without a bound a port that cannot bind becomes a rebuild loop.
    var lastRebuildAtMs = 0L
        private set
    var rebuildsInWindow = 0
        private set
    var rebuildWindowStartedAtMs = 0L
        private set

    fun reset() {
        lastRebuildAtMs = 0L
        rebuildsInWindow = 0
        rebuildWindowStartedAtMs = 0L
    }

    fun attempt() {
        val now = SystemClock.elapsedRealtime()

        rebuildsInWindow = WirelessServerRestartPolicy.nextRebuildCount(
            now, rebuildWindowStartedAtMs, rebuildsInWindow,
        )
        rebuildWindowStartedAtMs =
            WirelessServerRestartPolicy.nextWindowStart(
                now,
                rebuildWindowStartedAtMs,
            )
        lastRebuildAtMs = now
    }
}
