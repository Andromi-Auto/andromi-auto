package com.andrerinas.openheadunit.aap

/**
 * Deliberately breaks the video fragment stream, so the reassembler's failure paths can be exercised
 * on a head unit that is working correctly.
 *
 * The artifact reports this exists for - a melting or smearing picture - come from units we do not
 * have, and the conditions that cause it are properties of the link rather than of the app: a
 * fragment that never arrives, or a first fragment whose payload does not start where the protocol
 * says it should. A healthy rig produces none of them, which is why three rounds of hardware testing
 * on the decoder measured `dropped=0` and never reproduced the bug at all.
 *
 * This turns that around. Injecting the exact loss pattern reproduces each failure mode on any unit,
 * deterministically, so "the fix works" becomes something that can be measured rather than inferred
 * from a reporter's next drive.
 *
 * Deterministic on purpose: every Nth matching message rather than a random draw, so two builds
 * given the same stream see the same faults and an A/B comparison means something.
 *
 * Off unless explicitly enabled. When it is on, every injected fault is logged, so a log that has
 * been captured with this left on can never be mistaken for a log of a real fault.
 *
 * ### What it cannot reach
 *
 * This injector runs in [AapVideo.process], which is **downstream of [FragmentedMessageAudit]** -
 * that check runs in the readers, on the header, before decryption ([AapReadSingleMessage] and
 * [AapReadMultipleMessages] both call `auditFragment` there). Every message the injector pretends
 * never arrived has already been counted by the audit as arriving. So no mode here can produce a
 * framing-audit outcome, and an earlier version of this file said the opposite for all four of them.
 * Hardware measured it: **449 faults injected across the three drop modes, and not one
 * `AapRead:` line attributable to any of them.** (Audit lines did appear in those captures - the
 * connect-time `DELTA_CHANGED` burst that this commit's scaling fix removes - but they landed in the
 * opening seconds, before the injector had dropped anything.) Only [VideoFragmentAssembler]'s
 * counters - `headless=`, `orphan=`, `truncated=` - respond to this injector.
 */
class VideoFaultInjector(private val mode: Mode, rate: Int) {

    /** What the injector does to the stream. */
    enum class Mode(val value: Int) {
        /** Nothing. The only mode a user should ever be in. */
        OFF(0),

        /**
         * Swallow first fragments. The 8s and 10 behind each one arrive with no run open, so expect
         * `orphan=` on the reassembly summary. Nothing from the framing audit - see the note above.
         */
        DROP_FIRST_FRAGMENT(1),

        /**
         * Swallow middle fragments.
         *
         * The interesting one: the run still looks intact to [VideoFragmentAssembler] - a first,
         * some middles, a last, in order - so the frame is assembled with a hole in it and decoded
         * as though it were whole, and the reassembly summary stays at zero.
         *
         * **Nothing reports it, by construction.** [FragmentedMessageAudit] is the check that could,
         * but it sits upstream of this injector and has already counted the fragment this mode
         * discards. That makes this mode a test of the *decoder's* tolerance of a holed access unit
         * and nothing else - and hardware found it has none: at one fault in three the
         * decoder stopped emitting frames entirely, burned its four-restart budget in 33 seconds and
         * never recovered. Reproducing the audit's own detection needs a fault injected in the
         * reader, which this class deliberately is not.
         */
        DROP_MIDDLE_FRAGMENT(2),

        /**
         * Swallow last fragments. The run stays open until the next 9 or 11, so expect `truncated=`
         * on the reassembly summary. Nothing from the framing audit - see the note above.
         */
        DROP_LAST_FRAGMENT(3),

        /**
         * Present a first fragment as having no start code at either offset, which is the exact
         * shape of the case that used to be assembled headless and silently. The bytes are not
         * touched - only what the reassembler is told about them - because the buffer is shared.
         *
         * Expect `headless=` on the reassembly summary. Nothing from the framing audit - and here
         * the run genuinely is complete, every byte having arrived, so this is the one mode where
         * that silence would be correct even if the injector could reach it.
         */
        HIDE_START_CODE(4);

        companion object {
            fun fromInt(value: Int): Mode? = entries.firstOrNull { it.value == value }
        }
    }

    /** What the caller should do with the message it just asked about. */
    enum class Effect {
        /** Handle it normally. */
        NONE,

        /** Behave as though it never arrived. */
        DROP,

        /** Handle it, but as a fragment whose payload starts at neither known offset. */
        HIDE_START_CODE,
    }

    /** Faults are applied to one in this many matching messages. */
    val rate: Int = rate.coerceIn(MIN_RATE, MAX_RATE)

    private var matching = 0L
    private var injected = 0L

    /** How many faults have been injected so far, for the log. */
    val injectedCount: Long get() = injected

    /**
     * How many messages the current mode has targeted so far - the denominator [injectedCount] is a
     * share of, and the number that says whether a rate is doing anything.
     */
    val matchingCount: Long get() = matching

    /**
     * One line saying what the injector is set to and what it has actually managed to do.
     *
     * Worth printing periodically rather than only per fault, because the interesting failure is the
     * one where nothing is injected: [rate] counts the messages carrying the flag this mode attacks,
     * and how often a frame fragments at all is a property of what the phone happens to be
     * projecting. A five-minute run at one in twenty produced zero faults on a rig where an earlier
     * run at one in three produced ten in ninety seconds - same code, same setting, different
     * screen. Without the candidate count that reads as "the setting did not take" rather than "the
     * stream did not fragment", and the only way to tell was to read this file.
     */
    fun describe(): String =
        "$mode 1-in-$rate, $matching candidates seen, $injected injected"

    /** Whether this injector will ever do anything. */
    val isActive: Boolean get() = mode != Mode.OFF

    /**
     * Decides what to do with a message carrying [flags].
     *
     * Counts only the messages the current mode targets, so the rate means "one in N of the flag we
     * are attacking" rather than one in N of all video traffic - at three fragments per frame the
     * two differ by a factor of three, and the brief has to be able to say how often a fault lands.
     */
    fun effectFor(flags: Int): Effect {
        val target = targetFlag(mode) ?: return Effect.NONE
        if (flags != target) return Effect.NONE
        matching++
        if (matching % rate != 0L) return Effect.NONE
        injected++
        return when (mode) {
            Mode.HIDE_START_CODE -> Effect.HIDE_START_CODE
            Mode.OFF -> Effect.NONE
            else -> Effect.DROP
        }
    }

    companion object {
        /** How often [describe] is worth repeating while a mode is active. */
        const val SUMMARY_INTERVAL_MS = 15_000L

        /** One in one would break every frame and never reach a picture at all. */
        const val MIN_RATE = 2

        const val MAX_RATE = 100000

        /**
         * One in 300, which at the ~50 messages per second a healthy link carries is a fault every
         * few seconds - often enough to measure in a five-minute run, rare enough that the picture
         * in between is a fair sample of normal behaviour.
         */
        const val DEFAULT_RATE = 300

        /** Which fragment flag a mode attacks, or null if it attacks nothing. */
        fun targetFlag(mode: Mode): Int? = when (mode) {
            Mode.OFF -> null
            Mode.DROP_FIRST_FRAGMENT, Mode.HIDE_START_CODE -> VideoFragmentAssembler.FLAG_FIRST
            Mode.DROP_MIDDLE_FRAGMENT -> VideoFragmentAssembler.FLAG_MIDDLE
            Mode.DROP_LAST_FRAGMENT -> VideoFragmentAssembler.FLAG_LAST
        }
    }
}
