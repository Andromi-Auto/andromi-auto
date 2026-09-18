package com.andrerinas.openheadunit.connection.wifi.direct

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupIdentityStabilityPolicyTest {

    private val name = "DIRECT-K7-HeadUnit"
    private val a = "DE:B3:88:55:B3:92"
    private val b = "DE:B3:88:55:B3:93"

    private fun assess(
        keep: Boolean = true,
        requested: String? = name,
        ssid: String = name,
        bssid: String = a,
        usable: Boolean = true,
        override: Boolean = false,
        previous: ObservedP2pGroup? = null,
        appNamesGroup: Boolean = true,
        nameChangesSoFar: Int = 0,
    ) = GroupIdentityStabilityPolicy.assess(
        keep, requested, ssid, bssid, usable, override, previous, appNamesGroup, nameChangesSoFar
    )

    @Test
    fun `the first group under a name is unproven and is remembered`() {
        val v = assess()
        assertEquals(GroupIdentityStability.UNPROVEN, v.stability)
        assertEquals(ObservedP2pGroup(name, a), v.remember)
    }

    @Test
    fun `the same name and address twice is stable`() {
        val v = assess(previous = ObservedP2pGroup(name, a))
        assertEquals(GroupIdentityStability.STABLE, v.stability)
        assertEquals(ObservedP2pGroup(name, a), v.remember)
    }

    @Test
    fun `the same name with a moved address is changed, and the new address is what is kept`() {
        val v = assess(bssid = b, previous = ObservedP2pGroup(name, a))
        assertEquals(GroupIdentityStability.CHANGED, v.stability)
        assertEquals(ObservedP2pGroup(name, b), v.remember)
        assertTrue(v.reason, v.reason.contains(a) && v.reason.contains(b))
    }

    @Test
    fun `a unit that re-addresses every create never reaches stable`() {
        var previous: ObservedP2pGroup? = null
        val seen = mutableListOf<GroupIdentityStability>()
        for (mac in listOf(a, b, a, b)) {
            val v = assess(bssid = mac, previous = previous)
            seen += v.stability
            previous = v.remember
        }
        assertEquals(
            listOf(GroupIdentityStability.UNPROVEN, GroupIdentityStability.CHANGED,
                GroupIdentityStability.CHANGED, GroupIdentityStability.CHANGED),
            seen,
        )
    }

    @Test
    fun `a new identity resets the comparison rather than reading as changed`() {
        val v = assess(ssid = "DIRECT-Q2-HeadUnit", requested = "DIRECT-Q2-HeadUnit", previous = ObservedP2pGroup(name, a))
        assertEquals(GroupIdentityStability.UNPROVEN, v.stability)
        assertEquals(ObservedP2pGroup("DIRECT-Q2-HeadUnit", a), v.remember)
    }

    @Test
    fun `a static BSSID setting is stable at once`() {
        val v = assess(override = true)
        assertEquals(GroupIdentityStability.STABLE, v.stability)
    }

    @Test
    fun `not keeping the identity is never stable and teaches nothing`() {
        val v = assess(keep = false, previous = ObservedP2pGroup(name, a))
        assertEquals(GroupIdentityStability.UNPROVEN, v.stability)
        assertNull(v.remember)
    }

    @Test
    fun `a group the platform renamed cannot be the one the phone stored`() {
        val v = assess(ssid = "DIRECT-zz-Android", previous = ObservedP2pGroup(name, a))
        assertEquals(GroupIdentityStability.UNPROVEN, v.stability)
        assertNull(v.remember)
        assertTrue(v.reason.contains(name))
    }

    @Test
    fun `an unreadable BSSID is unproven and does not overwrite what was learned`() {
        val v = assess(bssid = "02:00:00:00:00:00", usable = false, previous = ObservedP2pGroup(name, a))
        assertEquals(GroupIdentityStability.UNPROVEN, v.stability)
        assertNull(v.remember)
    }

    // --- a platform that names the group itself -------------------------------------------------

    @Test
    fun `a changed name stays unproven while the app is the one that could have renamed it`() {
        // Above API 29 the app asks for the name, so a different one is a rotation of ours and the
        // next create settles it however many times it happens.
        var changes = 0
        repeat(5) {
            val v = assess(
                requested = "DIRECT-zz-New", ssid = "DIRECT-zz-New",
                previous = ObservedP2pGroup(name, a), appNamesGroup = true, nameChangesSoFar = changes
            )
            assertEquals(GroupIdentityStability.UNPROVEN, v.stability)
            assertEquals(0, v.nameChanges)
            changes = v.nameChanges
        }
    }

    @Test
    fun `below the naming API a changed name is counted, and measured on the third`() {
        var changes = 0
        val seen = mutableListOf<GroupIdentityStability>()
        repeat(GroupIdentityStabilityPolicy.NAME_CHANGES_BEFORE_MEASURED) { i ->
            val v = assess(
                requested = null, ssid = "DIRECT-x$i-HeadUnit",
                previous = ObservedP2pGroup(name, a), appNamesGroup = false, nameChangesSoFar = changes
            )
            seen += v.stability
            changes = v.nameChanges
        }
        assertEquals(GroupIdentityStabilityPolicy.NAME_CHANGES_BEFORE_MEASURED, changes)
        assertEquals(GroupIdentityStability.UNPROVEN, seen.first())
        assertEquals(GroupIdentityStability.RENAMED, seen.last())
    }

    @Test
    fun `the measured verdict says the setting cannot reach it, rather than that the next one decides`() {
        val v = assess(
            requested = null, ssid = "DIRECT-zz-Other", previous = ObservedP2pGroup(name, a),
            appNamesGroup = false,
            nameChangesSoFar = GroupIdentityStabilityPolicy.NAME_CHANGES_BEFORE_MEASURED,
        )
        assertEquals(GroupIdentityStability.RENAMED, v.stability)
        assertTrue(v.reason.contains("no setting reaches it"))
        assertFalse("the reason must not promise a comparison that will never happen",
            v.reason.contains("cannot be compared yet"))
    }

    @Test
    fun `a name that repeats clears the count where the app asked for that name`() {
        val v = assess(
            previous = ObservedP2pGroup(name, a), appNamesGroup = true,
            nameChangesSoFar = GroupIdentityStabilityPolicy.NAME_CHANGES_BEFORE_MEASURED - 1,
        )
        assertEquals(GroupIdentityStability.STABLE, v.stability)
        assertEquals(0, v.nameChanges)
    }

    @Test
    fun `below the naming API a repeated name does not clear the count`() {
        // Measured on an API 19 tablet: every bring-up is assessed twice, and the first callback
        // re-reads the group the last one left up, which repeats the name without a create.
        val near = GroupIdentityStabilityPolicy.NAME_CHANGES_BEFORE_MEASURED - 1
        for (bssid in listOf(a, b)) {
            val v = assess(
                requested = null, bssid = bssid, previous = ObservedP2pGroup(name, a),
                appNamesGroup = false, nameChangesSoFar = near,
            )
            assertEquals(near, v.nameChanges)
        }
    }

    @Test
    fun `four bring-ups of two callbacks each reach the measured verdict on the third`() {
        // The shape the API 19 tablet produces and the branch tests all missed: a leftover group
        // re-read under the old name, then the new one, per bring-up. Names from that round.
        val names = listOf("DIRECT-LR-Nav", "DIRECT-oX-Nav", "DIRECT-bA-Nav", "DIRECT-e6-Nav")
        var changes = 0
        var previous = ObservedP2pGroup(names[0], a)
        val reached = mutableListOf<GroupIdentityStability>()
        for (settled in names.drop(1)) {
            val ephemeral = assess(
                requested = null, ssid = previous.ssid, bssid = b, previous = previous,
                appNamesGroup = false, nameChangesSoFar = changes,
            )
            changes = ephemeral.nameChanges
            previous = ephemeral.remember!!
            val v = assess(
                requested = null, ssid = settled, bssid = a, previous = previous,
                appNamesGroup = false, nameChangesSoFar = changes,
            )
            changes = v.nameChanges
            previous = v.remember!!
            reached += v.stability
        }
        assertEquals(
            listOf(
                GroupIdentityStability.UNPROVEN,
                GroupIdentityStability.UNPROVEN,
                GroupIdentityStability.RENAMED,
            ),
            reached
        )
    }

    @Test
    fun `a branch that observed nothing about the name carries the count rather than erasing it`() {
        val so_far = GroupIdentityStabilityPolicy.NAME_CHANGES_BEFORE_MEASURED - 1
        val previous = ObservedP2pGroup(name, a)
        assertEquals(so_far, assess(keep = false, nameChangesSoFar = so_far).nameChanges)
        assertEquals(so_far, assess(ssid = "DIRECT-zz-Other", nameChangesSoFar = so_far).nameChanges)
        assertEquals(so_far, assess(usable = false, nameChangesSoFar = so_far).nameChanges)
        assertEquals(so_far, assess(previous = null, nameChangesSoFar = so_far).nameChanges)
        assertEquals(0, assess(previous = previous, nameChangesSoFar = so_far).nameChanges)
        assertEquals(0, assess(bssid = b, previous = previous, nameChangesSoFar = so_far).nameChanges)
    }

    @Test
    fun `one unreadable BSSID between two renames does not lose the first of them`() {
        var changes = assess(
            requested = null, ssid = "DIRECT-x1-HeadUnit", previous = ObservedP2pGroup(name, a),
            appNamesGroup = false, nameChangesSoFar = 0
        ).nameChanges
        assertEquals(1, changes)
        changes = assess(
            requested = null, usable = false, appNamesGroup = false, nameChangesSoFar = changes
        ).nameChanges
        changes = assess(
            requested = null, ssid = "DIRECT-x3-HeadUnit", previous = ObservedP2pGroup(name, a),
            appNamesGroup = false, nameChangesSoFar = changes
        ).nameChanges
        assertEquals(GroupIdentityStabilityPolicy.NAME_CHANGES_BEFORE_MEASURED - 1, changes)
    }

    @Test
    fun `the existing verdicts are untouched by the new inputs defaulting`() {
        assertEquals(GroupIdentityStability.UNPROVEN, assess().stability)
        assertEquals(GroupIdentityStability.STABLE, assess(previous = ObservedP2pGroup(name, a)).stability)
        assertEquals(
            GroupIdentityStability.CHANGED,
            assess(bssid = b, previous = ObservedP2pGroup(name, a)).stability
        )
        assertEquals(GroupIdentityStability.UNPROVEN, assess(keep = false).stability)
        assertEquals(GroupIdentityStability.STABLE, assess(override = true).stability)
    }

    @Test
    fun `every verdict carries a reason and every stability a label`() {
        for (v in listOf(assess(), assess(keep = false), assess(override = true),
            assess(previous = ObservedP2pGroup(name, a)), assess(bssid = b, previous = ObservedP2pGroup(name, a)))) {
            assertTrue(v.reason.isNotBlank())
        }
        for (s in GroupIdentityStability.values()) {
            assertTrue(GroupIdentityStabilityPolicy.label(s).isNotBlank())
        }
    }
}
