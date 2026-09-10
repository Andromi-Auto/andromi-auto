package com.andrerinas.openheadunit.connection.usb

/**
 * Which plain-English hint a USB scan should end with.
 *
 * The dump used to explain itself only when the bus was empty, so the case that actually reaches the
 * tracker - devices present, none of them a phone - said nothing. A wireless adapter that has not
 * found its phone re-enumerates under a different identity every half minute, which is the one shape
 * worth naming on sight.
 */
object UsbBusHintPolicy {

    /** How far back distinct identities are counted. */
    const val IDENTITY_WINDOW_MS = 60_000L

    /** Distinct VID:PIDs inside the window that mean a device is cycling rather than being swapped. */
    const val CYCLING_IDENTITIES = 2

    enum class Hint { NO_HOST_SUPPORT, EMPTY_BUS, CYCLING_ADAPTER, NONE_USABLE }

    /**
     * @param featureDeclared `android.hardware.usb.host`. A ROM that omits it never starts the
     *   framework's host stack, so the bus stays empty whatever is plugged in - a permanent fact
     *   about the unit rather than the three-way guess [Hint.EMPTY_BUS] offers.
     */
    fun hint(
        deviceCount: Int,
        acceptedCount: Int,
        distinctIdentitiesInWindow: Int,
        featureDeclared: Boolean,
    ): Hint? = when {
        // Only when nothing enumerated: some ROMs omit the declaration and host devices anyway, and
        // telling those users their unit cannot do USB would be worse than saying nothing.
        deviceCount == 0 && !featureDeclared -> Hint.NO_HOST_SUPPORT
        deviceCount == 0 -> Hint.EMPTY_BUS
        acceptedCount > 0 -> null
        distinctIdentitiesInWindow >= CYCLING_IDENTITIES -> Hint.CYCLING_ADAPTER
        else -> Hint.NONE_USABLE
    }

    /** Identities seen strictly inside the window, newest first, deduplicated. */
    fun identitiesInWindow(seen: List<Pair<Long, String>>, nowMs: Long): List<String> =
        seen.filter { nowMs - it.first < IDENTITY_WINDOW_MS }
            .sortedByDescending { it.first }
            .map { it.second }
            .distinct()
}
