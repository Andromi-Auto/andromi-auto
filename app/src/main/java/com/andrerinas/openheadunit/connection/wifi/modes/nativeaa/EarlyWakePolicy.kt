package com.andrerinas.openheadunit.connection.wifi.modes.nativeaa

/**
 * Whether the wake poke may start before the WiFi credentials exist.
 *
 * The wake used to be triggered only by a credential delivery, so the phone was not woken until the
 * group was up, its info had come back and its IP had resolved. The handshake does not need that
 * ordering: it sends the version exchange first and waits for credentials afterwards, so the
 * phone's own wake latency can run alongside the group forming instead of after it.
 */
object EarlyWakePolicy {

    /**
     * [listenersOpen] must be the real "the AA RFCOMM listener is accepting" read. A phone woken to
     * a head unit with no listener finds nothing to dial and the log still says the poke worked.
     */
    fun mayWakeBeforeCredentials(
        listenersOpen: Boolean,
        credentialsPresent: Boolean,
        userExited: Boolean,
        sessionUp: Boolean,
    ): Boolean = listenersOpen && !credentialsPresent && !userExited && !sessionUp

    /**
     * Whether a `triggerPoke()` with a new credential key should replace the running loop.
     *
     * False when the only change is credentials arriving under a loop that started without them:
     * the loop re-reads them every pass, so restarting it throws away the head start an early wake
     * just bought and pays the entry wait again.
     */
    fun shouldRestartLoop(
        loopActive: Boolean,
        previousKey: Triple<String, String, String>?,
        newKey: Triple<String, String, String>,
    ): Boolean = when {
        !loopActive -> true
        previousKey == newKey -> false
        previousKey != null && isEmptyKey(previousKey) && !isEmptyKey(newKey) -> false
        else -> true
    }

    private fun isEmptyKey(key: Triple<String, String, String>): Boolean =
        key.first.isEmpty() && key.second.isEmpty() && key.third.isEmpty()
}
