package com.andrerinas.openheadunit.connection.wifi.direct

/** Whether the network the phone is handed will be the same network next time. */
enum class GroupIdentityStability {
    /** Same name and same BSSID as the last group this unit hosted, or an address the user fixed. */
    STABLE,
    /** Nothing to compare against yet, or nothing readable to compare. */
    UNPROVEN,
    /** Same name, different BSSID: this unit re-addresses the group on every create. */
    CHANGED,
    /** The platform names the group itself and hands out a different name every create. */
    RENAMED,
    /** Not this transport's question: an access point's identity is its own. */
    NOT_MEASURED,
}

/** What a group looked like from the outside, kept so the next one can be compared to it. */
data class ObservedP2pGroup(val ssid: String, val bssid: String)

/**
 * Decides, per unit and from evidence, whether the WiFi Direct group's identity repeats.
 *
 * A persistent group keeps its name and passphrase, but the platform is allowed to re-randomize
 * the group's own address on every create, and whether it does is a per-unit configuration this
 * app cannot read. The phone stores the BSSID beside the name and joins on both, so a record that
 * names a stale address is a network it can never find. The verdict is therefore measured, from
 * two consecutive bring-ups, and never assumed.
 */
object GroupIdentityStabilityPolicy {

    /**
     * How many creates must hand back a different name before it is a property of the unit rather
     * than a comparison that has not settled.
     *
     * Three: one is ordinary, two could be a rotation the user asked for, and by the third nothing
     * else explains it.
     */
    const val NAME_CHANGES_BEFORE_MEASURED = 3

    data class Verdict(
        val stability: GroupIdentityStability,
        /** What to keep for the next comparison, or null when this bring-up taught nothing. */
        val remember: ObservedP2pGroup?,
        val reason: String,
        /**
         * Creates whose name did not match the last one, to be kept for the next. A repeat resets
         * it only where the app can name the group: below Q it cannot, so a name that came back
         * once there is luck rather than a property and the count carries on.
         */
        val nameChanges: Int = 0,
    )

    /**
     * @param appNamesGroup whether this platform lets the app ask for a name at all. Below API 29
     *   `WifiP2pConfig.Builder` does not exist, so the create reinvokes whatever profile the
     *   platform kept and a changed name is the platform's doing rather than a rotation of ours.
     * @param nameChangesSoFar the previous verdict's [Verdict.nameChanges].
     */
    fun assess(
        keepIdentity: Boolean,
        requestedName: String?,
        ssid: String,
        bssid: String,
        bssidUsable: Boolean,
        staticOverride: Boolean,
        previous: ObservedP2pGroup?,
        appNamesGroup: Boolean = true,
        nameChangesSoFar: Int = 0,
    ): Verdict {
        // The count survives every branch below that observed nothing about the name, or a run with
        // one unreadable BSSID in it erases a measurement built from the bring-ups either side. A
        // bring-up is assessed twice on some units, so only a repeat under Q's naming API resets it.
        if (!keepIdentity) {
            return Verdict(
                GroupIdentityStability.UNPROVEN, null, "a new network is made on every create",
                nameChanges = nameChangesSoFar,
            )
        }
        if (requestedName != null && requestedName != ssid) {
            return Verdict(
                GroupIdentityStability.UNPROVEN, null,
                "the platform named the group $ssid instead of the $requestedName asked for",
                nameChanges = nameChangesSoFar,
            )
        }
        if (!bssidUsable) {
            return Verdict(
                GroupIdentityStability.UNPROVEN, null, "no BSSID could be read for this group",
                nameChanges = nameChangesSoFar,
            )
        }
        val observed = ObservedP2pGroup(ssid, bssid)
        if (staticOverride) {
            return Verdict(
                GroupIdentityStability.STABLE, observed,
                "the static BSSID setting fixes the address the phone is told",
                nameChanges = nameChangesSoFar,
            )
        }
        return when {
            previous == null -> Verdict(
                GroupIdentityStability.UNPROVEN, observed,
                "first group under this name; the next one decides",
                nameChanges = nameChangesSoFar,
            )
            previous.ssid != ssid -> nameChangedVerdict(observed, previous, appNamesGroup, nameChangesSoFar)
            previous.bssid == bssid -> Verdict(
                GroupIdentityStability.STABLE, observed,
                "same name and same BSSID as the last group",
                nameChanges = repeatedNameChanges(appNamesGroup, nameChangesSoFar),
            )
            else -> Verdict(
                GroupIdentityStability.CHANGED, observed,
                "same name but the BSSID moved from ${previous.bssid} to $bssid; this unit re-addresses the group on every create",
                nameChanges = repeatedNameChanges(appNamesGroup, nameChangesSoFar),
            )
        }
    }

    /**
     * What a repeated name leaves the count at.
     *
     * Zero where the app asked for the name and got it, because that is the lever working. Below Q
     * there is no lever, and a unit is assessed once per group-info callback rather than once per
     * create, so a repeat there is as likely to be the leftover group seen again as a real one.
     */
    private fun repeatedNameChanges(appNamesGroup: Boolean, nameChangesSoFar: Int): Int =
        if (appNamesGroup) 0 else nameChangesSoFar

    /**
     * A name that did not repeat. On a platform that lets us ask for one this is transient, and the
     * next create settles it. On one that does not, it is the platform choosing, and after
     * [NAME_CHANGES_BEFORE_MEASURED] of them saying "the next one decides" is a promise that will
     * never be kept: no API below 29 names a group, so nothing this app can do will change it.
     */
    private fun nameChangedVerdict(
        observed: ObservedP2pGroup,
        previous: ObservedP2pGroup,
        appNamesGroup: Boolean,
        nameChangesSoFar: Int,
    ): Verdict {
        if (appNamesGroup) {
            return Verdict(
                GroupIdentityStability.UNPROVEN, observed,
                "the name changed since the last group (${previous.ssid}), so the address cannot be compared yet",
                nameChanges = 0,
            )
        }
        val changes = nameChangesSoFar + 1
        if (changes < NAME_CHANGES_BEFORE_MEASURED) {
            return Verdict(
                GroupIdentityStability.UNPROVEN, observed,
                "the name changed since the last group (${previous.ssid}), so the address cannot be compared yet",
                nameChanges = changes,
            )
        }
        return Verdict(
            GroupIdentityStability.RENAMED, observed,
            "this platform names the group itself and has picked a different name on $changes " +
                "creates, so the kept identity cannot apply here and no setting reaches it",
            nameChanges = changes,
        )
    }

    fun label(stability: GroupIdentityStability): String = when (stability) {
        GroupIdentityStability.STABLE -> "yes"
        GroupIdentityStability.UNPROVEN -> "unproven"
        GroupIdentityStability.CHANGED -> "no"
        GroupIdentityStability.RENAMED -> "no (the platform names it)"
        GroupIdentityStability.NOT_MEASURED -> "not measured"
    }
}
