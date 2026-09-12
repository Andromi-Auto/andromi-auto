package com.andrerinas.openheadunit.main

/**
 * Whether to offer Bluetooth auto-start for the phone that has connected to this unit.
 *
 * Auto-start used to turn itself on: a completed handshake wrote the peer into the trigger list, so
 * a user who never asked found the app launching itself. Asking once, about the one phone that can
 * be meant, is what replaces that.
 *
 * Pure, so every combination is a unit test rather than a device.
 */
object AutoStartOfferPolicy {

    enum class Action {
        /** Offer auto-start for the connected phone. */
        ASK,

        /** Clear the stored trigger: with more than one phone here it no longer names anybody. */
        RESET,

        NOTHING,
    }

    /** Where the question is being asked from. */
    enum class Trigger {
        /** The home screen, on a resume. */
        HOME_SCREEN,

        /** The first frame of a session, over the picture. */
        PROJECTION_START,
    }

    /** The offer answers itself after this, so it never holds a driver's screen for longer. */
    const val OFFER_TIMEOUT_MS = 20_000L

    /**
     * @param phonesPaired bonded devices classified as phones, never the raw bond count: watches,
     *   dongles and car radios all advertise the record the poke dials.
     * @param connectedMac the phone this session was with, empty when it cannot be named.
     * @param answeredMacs phones already asked about, so a "no" is not asked again.
     * @param autoStartConfigured whether a trigger device is already stored.
     */
    fun decide(
        phonesPaired: Int,
        connectedMac: String,
        answeredMacs: Set<String>,
        autoStartConfigured: Boolean,
    ): Action = when {
        // Two phones and a stored trigger is the combination that starts the app for whoever walks
        // up first. Driver selection is the surface that settles who drives, not auto-start.
        phonesPaired >= 2 -> if (autoStartConfigured) Action.RESET else Action.NOTHING
        phonesPaired != 1 || connectedMac.isEmpty() -> Action.NOTHING
        // Already set up, by this offer or by hand: there is nothing left to offer.
        autoStartConfigured -> Action.NOTHING
        answeredMacs.any { it.equals(connectedMac, ignoreCase = true) } -> Action.NOTHING
        else -> Action.ASK
    }

    /**
     * Whether [trigger] is the moment [action] belongs to.
     *
     * [Action.ASK] is asked over the picture: a phone can wake this unit by itself, so the sessions
     * the question is about are exactly the ones nobody watched the home screen for. [Action.RESET]
     * only rewrites a setting, and the home screen is where a second paired phone is noticed.
     */
    fun actsNow(action: Action, trigger: Trigger): Boolean = when (action) {
        Action.NOTHING -> false
        Action.RESET -> trigger == Trigger.HOME_SCREEN
        Action.ASK -> trigger == Trigger.PROJECTION_START
    }
}
