package com.andrerinas.openheadunit.aap

/**
 * Decides when a dropped reference frame has gone unrepaired long enough to earn a real focus
 * release/regain cycle instead of the gain-only nudge [AapTransport] normally sends.
 *
 * ### Why the nudge is not enough
 *
 * The gain-only unsolicited [com.andrerinas.openheadunit.aap.protocol.messages.VideoFocusEvent] is
 * measured inert, three times over. Dropped-frame-keyframe rounds 1 and 2 fired it 419 times under
 * sustained drops and the keyframes that followed arrived on the phone's own cadence, not sooner;
 * round 4 reproduced that on a clean stream. In every round the median wait came out at half the
 * natural keyframe interval, which is the wait a *random* observer of a periodic process gets - the
 * arithmetic of no causal link. It is kept as the first response because it costs one message and
 * one phone is not the fleet, not because it is expected to work.
 *
 * Round 4 also ruled out every cheaper alternative in the protocol. AAP has no keyframe-request
 * message at all; an `UpdateUiConfigRequest` is acknowledged by the phone and changes nothing about
 * the video (16 of 16); and `VIDEO_FOCUS_NATIVE_TRANSIENT`, which should in principle let the phone
 * hold its session, produced a full sink stop and a new session on all five attempts - it costs
 * exactly what a `VIDEO_FOCUS_NATIVE` release costs. **The release/regain cycle is the only lever
 * that exists**, and it produces a keyframe 0.52-0.78s after the release.
 *
 * ### Why the trigger is "unrepaired" rather than "sustained"
 *
 * The first version of this policy escalated after a drop *episode* had run unbroken for 150s. That
 * can never fire for the fault it was written for: the phone emits keyframes on a fixed ~69s GOP
 * (round 4: eight gaps, median 69.4s, spread under 2s), so a single shed reference frame leaves the
 * picture washed for an average of ~35s and then heals on its own. That is one drop, not 150s of
 * them - an episode of length zero.
 *
 * So the clock here measures how long the picture has been *unrepaired*: it starts at the first drop
 * and stops when a keyframe reaches the codec ([VideoKeyframeScanner]). Escalating after
 * [ESCALATE_AFTER_UNREPAIRED_MS] trades ~2.5s of corruption for the ~35s average of waiting the GOP
 * out.
 *
 * ### Why the gates are still reluctant
 *
 * Releasing focus across a healthy, rendering stream is the precondition of issue #755, where one
 * dropped frame turned into a permanent few-fps freeze on some head unit / phone combinations. That
 * has not reappeared - round 3 measured one cycle clean, and round 4 put seven real sink-stop/start
 * cycles through this rig in under five minutes with fps recovering every time and the codec
 * component never re-initialised - but that is one rig and one phone. Hence a budget rather than a
 * free hand: [MAX_CYCLES_PER_SESSION] in total, no two closer together than [CYCLE_COOLDOWN_MS].
 *
 * ### Who arms the clock
 *
 * A shed reference frame is no longer the only caller. A decoder that has just been rebuilt, and one
 * that is waiting for a keyframe rather than rebuilding, both arm it too - those are the cases where
 * the picture is most certainly gone and least able to return on its own, since a rebuilt codec
 * resumes on a P-frame and the next scheduled keyframe is up to a GOP away. They used to do the
 * opposite: a rebuild *cancelled* the clock and armed nothing, so a decoder restarting every ten
 * seconds could never reach an escalation, which is how one corrupt access unit turned into a
 * permanent black screen. The [WarmRelaunchKeyframePolicy] overlap that motivated the old wiring is
 * handled by [FocusCycleLever] rather than by declining to ask.
 *
 * Deliberately blind to everything except its own timeline and the keyframe signal. An earlier
 * attempt (commit 66e59b2c) latched on a `waitingForKeyframe` flag that gated the *feed*, and could
 * stick for a whole session with no timeout. Nothing here can stick: the clock clears on a keyframe,
 * the budget is a hard ceiling, and frames keep flowing regardless of what this decides.
 */
object KeyframeCycleEscalationPolicy {

    /**
     * Fastest isolated keyframe gap ever measured, in milliseconds.
     *
     * Nothing reads this at runtime; it is here because it is the number
     * [ESCALATE_AFTER_UNREPAIRED_MS] is chosen against, and a test holds the two in the ratio the
     * choice assumed. It bounds how quickly the phone has ever been seen to repair a stream unaided,
     * and it comes from rounds 1 and 2's forced-software-decoding path rather than a healthy one -
     * continuous drops pull the cadence around, which is why it sits so far below the ~69s the clean
     * path runs at.
     */
    const val NATURAL_CADENCE_MIN_OBSERVED_MS = 7_467L

    /**
     * How long the picture must stay unrepaired before the cycle is spent on it.
     *
     * Bounded from both sides by measurement. Below, it must clear two throttle windows so at least
     * one nudge has demonstrably gone unanswered before anything more expensive happens - see
     * [VideoRecoveryPolicy.KEYFRAME_REQUEST_THROTTLE_MS]. Above, it must stay well under
     * [NATURAL_CADENCE_MIN_OBSERVED_MS]: past a third of the fastest unaided repair ever measured,
     * the phone's own cadence would often beat the escalation and the disturbance buys nothing.
     *
     * Widening it is cheap and safe; narrowing it means firing a focus release closer to every
     * transient drop, which is what the #755 gates exist to avoid.
     */
    const val ESCALATE_AFTER_UNREPAIRED_MS = 2_000L

    /**
     * Minimum spacing between two focus cycles in one session.
     *
     * This is the decay on the budget: the picture has to have been healthy for a stretch before
     * another cycle is earned, so a stream that is failing continuously cannot spend the whole
     * allowance inside one bad minute.
     *
     * It also carries a second job. The regain half of a cycle is a single shared runnable on the
     * send handler, replaced rather than tracked per cycle, which is only sound if two cycles can
     * never be in flight together. At sixty seconds against
     * [WarmRelaunchKeyframePolicy.FOCUS_CYCLE_GAP_MS]'s 400ms that is not close, and a test pins the
     * ratio. Shortening this without giving each release its own pending
     * regain would let a second release silently strand the first one's.
     */
    const val CYCLE_COOLDOWN_MS = 60_000L

    /**
     * Hard cap on focus cycles per session, ever.
     *
     * Was 1, chosen when the only evidence was round 3's single forced cycle. Round 4 put seven real
     * sink-stop/start cycles through this rig in under five minutes - more disruption in less time,
     * on the same mechanism - with fps recovering every time, `dropped=0` throughout, and the decoder
     * component never re-initialised. Three, with [CYCLE_COOLDOWN_MS] between them, stays well inside
     * what has been measured while covering a drive that hits more than one bad patch.
     *
     * Still a cap rather than a free hand: this releases focus across a stream that *is* rendering,
     * the exact precondition of #755, and seven clean cycles on one rig with one phone is evidence,
     * not proof.
     */
    const val MAX_CYCLES_PER_SESSION = 3

    enum class Action {
        /** Send the unsolicited focus gain, the existing behaviour. */
        NUDGE,

        /** Release video focus and take it back, so the phone rebuilds the stream. */
        CYCLE_FOCUS,
    }

    /**
     * @param unrepairedSinceMs when the picture was last known good - the first drop with no keyframe
     *   since. Zero means nothing is broken, and nothing is escalated.
     * @param cyclesUsedThisSession how many [Action.CYCLE_FOCUS] escalations this session has spent.
     * @param lastCycleMs when the last one fired, or zero if none has.
     */
    fun decide(
        nowMs: Long,
        unrepairedSinceMs: Long,
        cyclesUsedThisSession: Int,
        lastCycleMs: Long,
    ): Action {
        if (unrepairedSinceMs == 0L) return Action.NUDGE
        if (nowMs - unrepairedSinceMs < ESCALATE_AFTER_UNREPAIRED_MS) return Action.NUDGE
        if (cyclesUsedThisSession >= MAX_CYCLES_PER_SESSION) return Action.NUDGE
        if (lastCycleMs != 0L && nowMs - lastCycleMs < CYCLE_COOLDOWN_MS) return Action.NUDGE
        return Action.CYCLE_FOCUS
    }
}
