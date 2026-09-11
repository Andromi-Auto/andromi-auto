package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.utils.Settings

/**
 * What to ask the phone for when the only radio this unit has is a 2.4 GHz one.
 *
 * [com.andrerinas.openheadunit.aap.protocol.messages.ServiceDiscoveryResponse] announces one video
 * configuration, and the protocol has no bitrate field: resolution, 30-versus-60 fps and the codec
 * are the whole of what we can ask for less of. Audio has one lever, AAC instead of PCM, and on
 * such a link uncompressed music is the largest stream, so the cap covers it too.
 *
 * **This used to only advise.** Two units have now produced the same failure - the phone joins,
 * opens the video channel and closes the socket seconds later having sent no frame at all - and on
 * the second the radio has no 5 GHz band at all, so the band is not a remedy anyone can reach. A
 * lower profile held on the same access point in both cases, so the cap is now applied and
 * [Settings.narrowBandProfileCap] is how a user who disagrees turns it off.
 *
 * Pure, so the wording and every gate are a unit test rather than a device.
 */
object NarrowBandProfilePolicy {

    /** The frame rate the wire carries when the user has not asked for less. */
    const val FULL_FRAME_RATE = 60

    /** What the frame rate is lowered to. The only other value the announcement can carry. */
    const val CAPPED_FRAME_RATE = 30

    /** What the resolution is lowered to, and never below: 480p was measured, 720p is the ceiling. */
    val CAPPED_RESOLUTION = Settings.Resolution._1280x720

    /** Above this is 5 GHz. Zero is "the platform would not say", never 2.4 GHz. */
    const val MAX_24GHZ_FREQUENCY_MHZ = 4000

    /**
     * Whether this session runs on a 2.4 GHz link, for either of the two reasons there are.
     *
     * The radio having no 5 GHz band is the one this started as, and only a `false` counts: a
     * `true` describes the station side and a null means the platform would not answer, so neither
     * is grounds for lowering somebody's picture. The second is the network actually in use, which
     * a unit with a 5 GHz radio reaches by choosing 2.4 GHz or by a 5 GHz request falling back. The
     * link is equally narrow either way, and the app already tells that user to expect 720p.
     */
    fun runsNarrow(supports5Ghz: Boolean?, sessionFrequencyMhz: Int): Boolean =
        supports5Ghz == false || sessionFrequencyMhz in 1..MAX_24GHZ_FREQUENCY_MHZ

    /**
     * Whether this session should be asked for less than the user's settings say.
     *
     * A wired session does not care what the radio is doing, and the user can say no; what is left
     * is [runsNarrow]. The frequency defaults to zero so a caller that cannot read one is answered
     * on the radio alone, which is every unit below API 29.
     */
    fun caps(
        supports5Ghz: Boolean?,
        wirelessSession: Boolean,
        capEnabled: Boolean,
        sessionFrequencyMhz: Int = 0
    ): Boolean = wirelessSession && capEnabled && runsNarrow(supports5Ghz, sessionFrequencyMhz)

    /**
     * The frame rate to announce. Only ever lowers: a user already on 30 is left there, and this
     * never raises anybody to 60.
     */
    fun cappedFrameRate(
        fpsLimit: Int,
        supports5Ghz: Boolean?,
        wirelessSession: Boolean,
        capEnabled: Boolean,
        sessionFrequencyMhz: Int = 0
    ): Int =
        if (caps(supports5Ghz, wirelessSession, capEnabled, sessionFrequencyMhz)) minOf(fpsLimit, CAPPED_FRAME_RATE)
        else fpsLimit

    /**
     * Whether to announce AAC for the audio sinks: the user's choice, or the cap's. Only ever adds
     * AAC; a user who turned it on keeps it on every link.
     */
    fun useAac(
        userChoice: Boolean,
        supports5Ghz: Boolean?,
        wirelessSession: Boolean,
        capEnabled: Boolean,
        sessionFrequencyMhz: Int = 0
    ): Boolean = userChoice || caps(supports5Ghz, wirelessSession, capEnabled, sessionFrequencyMhz)

    /**
     * The ceiling this link puts on the resolution, or null when it puts none.
     *
     * A ceiling rather than a value, because the caller already holds one from the panel and must
     * keep taking the lower of the two. Handing back a resolution would raise a user on 480p.
     */
    fun linkCeiling(
        supports5Ghz: Boolean?,
        wirelessSession: Boolean,
        capEnabled: Boolean,
        sessionFrequencyMhz: Int = 0
    ): Settings.Resolution? =
        if (caps(supports5Ghz, wirelessSession, capEnabled, sessionFrequencyMhz)) CAPPED_RESOLUTION else null

    /**
     * One line for the log, or null when there is nothing worth saying.
     *
     * Said whenever the cap applies, including when it changed nothing, because a line that only
     * appears in the unusual case is a line whose absence tells a reader nothing.
     */
    fun advice(
        supports5Ghz: Boolean?,
        fpsLimit: Int,
        wirelessSession: Boolean,
        capEnabled: Boolean = true,
        sessionFrequencyMhz: Int = 0
    ): String? {
        if (!wirelessSession) return null
        if (!runsNarrow(supports5Ghz, sessionFrequencyMhz)) return null
        // Which of the two reasons this link is narrow, because the remedies differ: one is the
        // hardware and the other is a band this user chose and can choose again.
        val why = if (supports5Ghz == false) "This unit has no 5 GHz band, so this session runs over 2.4 GHz"
                  else "This session's network is on 2.4 GHz"
        if (!capEnabled) {
            if (fpsLimit != FULL_FRAME_RATE) return null
            return "$why, and it is " +
                "being offered $FULL_FRAME_RATE fps because lowering the profile on a narrow band " +
                "is switched off in Video settings. Measured on a 2.4 GHz access point, a " +
                "full-rate stream died having sent no frame at all where a lower one held " +
                "indefinitely. Nothing here has been changed for you."
        }
        return "$why. The phone is being " +
            "asked for at most ${CAPPED_RESOLUTION.resName} and $CAPPED_FRAME_RATE fps rather than " +
            "what Video settings say: measured on a 2.4 GHz access point, a full-rate stream died " +
            "having sent no frame at all where a lower one held indefinitely. The music is sent as " +
            "AAC rather than uncompressed PCM, a fraction of the bytes. Turn off \"Lower video on " +
            "a 2.4 GHz link\" in Video settings to be given what you asked for instead."
    }
}
