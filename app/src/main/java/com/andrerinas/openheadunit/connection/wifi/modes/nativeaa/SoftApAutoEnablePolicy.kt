package com.andrerinas.openheadunit.connection.wifi.modes.nativeaa

/**
 * Whether to ask for the hotspot again while none is on air.
 *
 * An enable within about 10 s of a radio change fails, so a failed attempt gets one more, spaced
 * from its failure. None while the car is off, nor just after it wakes: the unit restores its own AP.
 */
object SoftApAutoEnablePolicy {

    const val FIRST_ATTEMPT_AFTER_MS = 5_000L
    const val RETRY_AFTER_MS = 10_000L
    const val MAX_ATTEMPTS = 2
    // An XYAuto unit was measured restoring its own AP about 4 s after ACC-on.
    const val FIRST_ATTEMPT_AFTER_WAKE_MS = 15_000L

    /** [lastAttemptEndedAtMs] is when the last start returned; [sinceWakeMs] is null without a wake. */
    fun shouldAttempt(
        enabledInSettings: Boolean,
        attempts: Int,
        waitedMs: Long,
        lastAttemptEndedAtMs: Long,
        lastAttemptSucceeded: Boolean,
        nowMs: Long,
        accOff: Boolean = false,
        attemptInFlight: Boolean = false,
        sinceWakeMs: Long? = null,
    ): Boolean {
        if (!enabledInSettings || accOff || attemptInFlight || attempts >= MAX_ATTEMPTS) return false
        if (sinceWakeMs != null && sinceWakeMs < FIRST_ATTEMPT_AFTER_WAKE_MS) return false
        if (attempts == 0) return waitedMs >= FIRST_ATTEMPT_AFTER_MS
        return !lastAttemptSucceeded && nowMs - lastAttemptEndedAtMs >= RETRY_AFTER_MS
    }
}
