package com.andrerinas.openheadunit.connection.wifi.direct

/**
 * What to do when the platform answers BUSY to a group create it has already accepted one of.
 *
 * A createGroup the framework accepts puts it in GroupCreatingState, where every later createGroup
 * and removeGroup falls through to a blanket BUSY until GROUP_CREATING_TIMED_OUT fires two minutes
 * later. Retrying and removing inside that window cannot succeed; cancelConnect is the one call
 * that state accepts, and it drops straight back to Inactive.
 */
object P2pCreateWedgePolicy {

    /** [android.net.wifi.p2p.WifiP2pManager.BUSY]. */
    const val BUSY = 2

    /** AOSP `WifiP2pServiceImpl.GROUP_CREATING_WAIT_TIME_MS`, unchanged since KitKat. */
    const val FRAMEWORK_CREATE_TIMEOUT_MS = 120_000L

    /**
     * How long an accepted create is left alone before it counts as stuck.
     *
     * A healthy create reaches the group in tens of milliseconds; this sits well above that and well
     * below the 20 s the group-info retry loop spends, so a create that is merely slow is never cut off.
     */
    const val CREATE_STALL_FLOOR_MS = 8_000L

    enum class Step {
        /** Ask again on the existing ladder. */
        RETRY,

        /** Cancel the creation the platform is still holding, then ask again. */
        CANCEL_FIRST,
    }

    /**
     * [msSinceAcceptedCreate] is null when no create of ours has been accepted without producing a
     * group, which is when a BUSY really does belong to somebody else and there is nothing to cancel.
     */
    fun stepAfterBusy(
        reason: Int,
        msSinceAcceptedCreate: Long?,
        cancelAlreadySpent: Boolean,
    ): Step = when {
        cancelAlreadySpent -> Step.RETRY
        !isOurCreatePending(reason, msSinceAcceptedCreate) -> Step.RETRY
        msSinceAcceptedCreate!! < CREATE_STALL_FLOOR_MS -> Step.RETRY
        else -> Step.CANCEL_FIRST
    }

    /**
     * Whether the terminal rung may call this unit's refusal a refusal.
     *
     * A BUSY arriving while the platform is still finishing a create of ours describes our own
     * timing, not the radio's ability to host a group, and a banner saying otherwise sends the user
     * to change a setting that had nothing to do with it.
     */
    fun isRefusalHonest(reason: Int, msSinceAcceptedCreate: Long?): Boolean =
        !isOurCreatePending(reason, msSinceAcceptedCreate)

    private fun isOurCreatePending(reason: Int, msSinceAcceptedCreate: Long?): Boolean =
        reason == BUSY &&
            msSinceAcceptedCreate != null &&
            msSinceAcceptedCreate < FRAMEWORK_CREATE_TIMEOUT_MS
}
