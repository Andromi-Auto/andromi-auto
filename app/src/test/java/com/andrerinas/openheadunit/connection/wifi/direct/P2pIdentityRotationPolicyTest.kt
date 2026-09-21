package com.andrerinas.openheadunit.connection.wifi.direct

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P2pIdentityRotationPolicyTest {

    // --- mechanism ---

    @Test
    fun `from API 29 the app names the group itself`() {
        assertEquals(
            P2pIdentityRotationPolicy.Mechanism.NAMED_RECREATE,
            P2pIdentityRotationPolicy.mechanism(29)
        )
        assertEquals(
            P2pIdentityRotationPolicy.Mechanism.NAMED_RECREATE,
            P2pIdentityRotationPolicy.mechanism(36)
        )
    }

    @Test
    fun `below API 29 the stored profile is what has to go`() {
        assertEquals(
            P2pIdentityRotationPolicy.Mechanism.PURGE_PROFILE_THEN_RECREATE,
            P2pIdentityRotationPolicy.mechanism(17)
        )
        assertEquals(
            P2pIdentityRotationPolicy.Mechanism.PURGE_PROFILE_THEN_RECREATE,
            P2pIdentityRotationPolicy.mechanism(28)
        )
    }

    // --- applyNow ---

    @Test
    fun `an idle native WiFi Direct group is rotated now`() {
        assertTrue(
            P2pIdentityRotationPolicy.applyNow(
                sessionLive = false,
                handshakeInFlight = false,
                nativeWifiDirectActive = true,
            )
        )
        assertNull(
            P2pIdentityRotationPolicy.deferralReason(
                sessionLive = false,
                handshakeInFlight = false,
                nativeWifiDirectActive = true,
            )
        )
    }

    @Test
    fun `a create still being answered is not built over`() {
        assertFalse(
            P2pIdentityRotationPolicy.applyNow(
                sessionLive = false,
                handshakeInFlight = false,
                nativeWifiDirectActive = true,
                createOutstanding = true,
            )
        )
        assertNotNull(
            P2pIdentityRotationPolicy.deferralReason(
                sessionLive = false,
                handshakeInFlight = false,
                nativeWifiDirectActive = true,
                createOutstanding = true,
            )
        )
    }

    @Test
    fun `a projecting session keeps its group`() {
        assertFalse(
            P2pIdentityRotationPolicy.applyNow(
                sessionLive = true,
                handshakeInFlight = false,
                nativeWifiDirectActive = true,
            )
        )
        assertTrue(
            P2pIdentityRotationPolicy.deferralReason(
                sessionLive = true,
                handshakeInFlight = false,
                nativeWifiDirectActive = true,
            )!!.contains("projecting")
        )
    }

    @Test
    fun `a handshake mid-exchange keeps its group`() {
        assertFalse(
            P2pIdentityRotationPolicy.applyNow(
                sessionLive = false,
                handshakeInFlight = true,
                nativeWifiDirectActive = true,
            )
        )
    }

    @Test
    fun `nothing is rotated when this mode hosts no group`() {
        assertFalse(
            P2pIdentityRotationPolicy.applyNow(
                sessionLive = false,
                handshakeInFlight = false,
                nativeWifiDirectActive = false,
            )
        )
        assertNotNull(
            P2pIdentityRotationPolicy.deferralReason(
                sessionLive = false,
                handshakeInFlight = false,
                nativeWifiDirectActive = false,
            )
        )
    }

    // --- purgeBeforeCreate ---

    @Test
    fun `a pending rotation purges the stored profile below API 29`() {
        assertTrue(
            P2pIdentityRotationPolicy.purgeBeforeCreate(17, keepIdentity = true, rotationPending = true)
        )
    }

    @Test
    fun `a new network on every connection purges every create below API 29`() {
        assertTrue(
            P2pIdentityRotationPolicy.purgeBeforeCreate(17, keepIdentity = false, rotationPending = false)
        )
    }

    @Test
    fun `an ordinary create below API 29 keeps the profile the setting asks it to keep`() {
        assertFalse(
            P2pIdentityRotationPolicy.purgeBeforeCreate(17, keepIdentity = true, rotationPending = false)
        )
    }

    @Test
    fun `nothing is ever purged from API 29`() {
        assertFalse(
            P2pIdentityRotationPolicy.purgeBeforeCreate(29, keepIdentity = true, rotationPending = true)
        )
        assertFalse(
            P2pIdentityRotationPolicy.purgeBeforeCreate(29, keepIdentity = false, rotationPending = true)
        )
        assertFalse(
            P2pIdentityRotationPolicy.purgeBeforeCreate(36, keepIdentity = false, rotationPending = false)
        )
    }

    @Test
    fun `below Q any group this unit owns is read rather than recreated`() {
        // There is no name to compare: the platform named it, and a recreate would mint another.
        assertTrue(
            P2pIdentityRotationPolicy.readsExistingGroup(
                sdkInt = 17, isGroupOwner = true,
                liveName = "DIRECT-l9-Tablet", requestedName = "DIRECT-PB-HeadUnit",
            )
        )
    }

    @Test
    fun `a group this unit does not own is never read`() {
        for (sdk in listOf(17, P2pIdentityRotationPolicy.NAMED_CREATE_SDK)) {
            assertFalse(
                "sdk $sdk",
                P2pIdentityRotationPolicy.readsExistingGroup(
                    sdkInt = sdk, isGroupOwner = false,
                    liveName = "DIRECT-PB-HeadUnit", requestedName = "DIRECT-PB-HeadUnit",
                )
            )
        }
    }

    @Test
    fun `from Q the name is still the test`() {
        assertTrue(
            P2pIdentityRotationPolicy.readsExistingGroup(
                sdkInt = P2pIdentityRotationPolicy.NAMED_CREATE_SDK, isGroupOwner = true,
                liveName = "DIRECT-PB-HeadUnit", requestedName = "DIRECT-PB-HeadUnit",
            )
        )
        assertFalse(
            P2pIdentityRotationPolicy.readsExistingGroup(
                sdkInt = P2pIdentityRotationPolicy.NAMED_CREATE_SDK, isGroupOwner = true,
                liveName = "DIRECT-XX-Other", requestedName = "DIRECT-PB-HeadUnit",
            )
        )
    }
}
