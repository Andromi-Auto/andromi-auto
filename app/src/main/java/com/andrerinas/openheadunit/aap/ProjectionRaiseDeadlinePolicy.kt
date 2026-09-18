package com.andrerinas.openheadunit.aap

/**
 * What to do when a handshake has completed and the projection screen has still not come up.
 *
 * The read loop only starts once [com.andrerinas.openheadunit.aap.AapProjectionActivity] has a
 * surface, so a raise that goes nowhere leaves a live socket with no traffic on it: measured at
 * 6m10s on a unit whose raise had fallen back to a notification nobody tapped.
 */
object ProjectionRaiseDeadlinePolicy {

    /** Long enough for a slow unit to inflate the activity, short enough to beat a phone's own
     *  patience, which ran out at about 35 s on the unit this was measured on. */
    const val DEADLINE_MS = 8_000L

    /** One retry, then the session is ended so the phone can start a fresh one. */
    const val MAX_RAISES = 2

    enum class Action { RETRY_RAISE, END_SESSION }

    /**
     * Armed only when a raise was actually attempted. A raise skipped on purpose - picture in
     * picture, a `no_ui` command, the settings screen, which each raise it themselves later - must
     * not end the session it was deliberately keeping.
     */
    fun arms(raiseAttempted: Boolean): Boolean = raiseAttempted

    /** [raisesMade] counts the raises already attempted for this session, the first included. */
    fun actionFor(raisesMade: Int): Action =
        if (raisesMade < MAX_RAISES) Action.RETRY_RAISE else Action.END_SESSION
}
