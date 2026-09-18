package com.andrerinas.openheadunit.connection.wifi.modes.nativeaa

/**
 * Whether an escalated wake costs this head unit its hands-free link for good.
 *
 * A poke displaces the phone's single hands-free slot by design - the Audio Gateway closes the
 * connection it already holds to that address when it accepts ours - and no API can put the link
 * back. Whether the unit's own client re-establishes is a property of its stack, so it is measured
 * once rather than asked: the first escalated wake is the probe, and a unit that stays down is left
 * alone from then on.
 */
object NativeAaWakeDamagePolicy {

    /**
     * How long the link is given to come back on its own, with the device left unpoked. Long enough
     * to cover a stack that reconnects on its own policy; the units that fail stayed down for
     * minutes, so nothing is gained by waiting longer.
     */
    const val PROBE_WINDOW_MS = 30_000L

    /** What this unit is known to do with its hands-free link when a wake takes the slot. */
    enum class Verdict {
        /** Never measured. The next escalated wake is the probe. */
        UNKNOWN,

        /** The link came back on its own, so a wake costs a blip. */
        SAFE,

        /** The link did not come back. Nothing escalates on this unit again. */
        DESTRUCTIVE;

        companion object {
            /** Stored as an int so an unknown value from a newer build reads as unmeasured. */
            fun of(stored: Int): Verdict = entries.getOrNull(stored) ?: UNKNOWN
        }
    }

    /** Whether a stand-down may yield at all. Only a measured failure withholds the wake. */
    fun allowsEscalation(verdict: Verdict): Boolean = verdict != Verdict.DESTRUCTIVE

    /** Whether this wake is the one being measured, so the probe window is armed behind it. */
    fun isProbe(verdict: Verdict): Boolean = verdict == Verdict.UNKNOWN

    /**
     * What the probe made of the link. A null reading is the adapter refusing to answer, which is
     * not evidence either way: stay unmeasured and probe again rather than condemn the unit.
     */
    fun verdictFrom(linkReturned: Boolean?): Verdict = when (linkReturned) {
        true -> Verdict.SAFE
        false -> Verdict.DESTRUCTIVE
        null -> Verdict.UNKNOWN
    }

    /** Whether a probe result is worth storing. An unreadable one would only overwrite a measurement. */
    fun recordable(verdict: Verdict): Boolean = verdict != Verdict.UNKNOWN
}
