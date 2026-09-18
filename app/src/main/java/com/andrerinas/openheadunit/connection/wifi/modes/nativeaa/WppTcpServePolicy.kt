package com.andrerinas.openheadunit.connection.wifi.modes.nativeaa

/**
 * Whether to run the handshake for a phone that dialled our WPP TCP port.
 *
 * Withholding the endpoint stops us handing out a new one; it does not stop a phone that already
 * has one from dialling. Serving that dial hands it this group's name, which the next create
 * replaces, and it then retries the dead one for as long as Android Auto runs instead of falling
 * back to Bluetooth. So a dial is answered exactly when [WppEndpointPolicy] would advertise.
 */
object WppTcpServePolicy {

    /** True when this decision is one we would have advertised, which is the only safe dial. */
    fun servesDial(decision: WppEndpointDecision): Boolean =
        decision is WppEndpointDecision.Advertise

    /** Why a dial is refused, written to be logged as-is. */
    fun refusalReason(decision: WppEndpointDecision): String = when (decision) {
        is WppEndpointDecision.Withhold -> decision.reason
        is WppEndpointDecision.Advertise -> "it is served"
    }
}
