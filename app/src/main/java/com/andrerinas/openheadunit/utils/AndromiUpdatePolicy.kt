package com.andrerinas.openheadunit.utils

/**
 * Andromi Auto: the rules of the update check, kept free of Android so they can be tested.
 *
 * There are two channels. Stable releases are tagged `v<version>` (versionName "4.0.0"); the one
 * development release is tagged `development` and replaced on every push (versionName
 * "4.0.0-dev.<run>"). Both come out of the "Build APKs" workflow, and every build's versionCode is
 * [VERSION_CODE_BASE] + its run number, so any newer build installs over an older one whichever
 * channel it came from. Each release's notes end with a marker comment carrying versionCode,
 * versionName and the APK's sha256, and the release also has an `update.json` with the same facts.
 */
object AndromiUpdatePolicy {

    const val VERSION_CODE_BASE = 2_000_000_000

    const val CHANNEL_STABLE = "stable"
    const val CHANNEL_DEV = "dev"

    enum class Verdict {
        /** The channel's release is a newer build than the installed one. */
        UPDATE_AVAILABLE,
        /** The installed build is the channel's release. */
        UP_TO_DATE,
        /** The installed build is newer than the channel's release. */
        AHEAD_OF_CHANNEL,
    }

    /** What the marker comment at the end of a release's notes says. */
    data class NotesInfo(val versionCode: Int?, val versionName: String?, val sha256: String?)

    private val CODE_IN_NOTES = Regex("""versionCode:\s*(\d+)""")
    private val NAME_IN_NOTES = Regex("""versionName:\s*(\S+)""")
    private val SHA_IN_NOTES = Regex("""sha256:\s*([0-9a-fA-F]{64})""")
    private val MARKER = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)
    /** Tags of the first stable builds: `v4.0.0-alpha-build22`. */
    private val BUILD_IN_TAG = Regex("""-build(\d+)$""")

    fun normalizeChannel(value: String?): String = if (value == CHANNEL_STABLE) CHANNEL_STABLE else CHANNEL_DEV

    /** The channel a build belongs to, from its version name: "4.0.0" is stable, "4.0.0-dev.35" is dev. */
    fun channelOfVersionName(versionName: String): String =
        if (versionName.contains("-dev")) CHANNEL_DEV else CHANNEL_STABLE

    fun parseNotes(notes: String): NotesInfo = NotesInfo(
        versionCode = CODE_IN_NOTES.find(notes)?.groupValues?.get(1)?.toIntOrNull(),
        versionName = NAME_IN_NOTES.find(notes)?.groupValues?.get(1),
        sha256 = SHA_IN_NOTES.find(notes)?.groupValues?.get(1),
    )

    /** The notes as people read them, without the marker comment. */
    fun readableNotes(notes: String): String = notes.replace(MARKER, "").trim()

    /** The versionCode of a build from the old tag scheme (`v4.0.0-alpha-build22`), or null. */
    fun versionCodeOfOldTag(tag: String): Int? =
        BUILD_IN_TAG.find(tag.trim())?.groupValues?.get(1)?.toIntOrNull()?.let { VERSION_CODE_BASE + it }

    /** The installed build number, from the versionCode. A local build without a run number is 0. */
    fun buildOfVersionCode(versionCode: Long): Int =
        (versionCode - VERSION_CODE_BASE).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

    fun verdict(installedCode: Int, latestCode: Int): Verdict = when {
        latestCode > installedCode -> Verdict.UPDATE_AVAILABLE
        latestCode == installedCode -> Verdict.UP_TO_DATE
        else -> Verdict.AHEAD_OF_CHANNEL
    }

    /** Whether the API answer says the hourly limit is spent, so the update.json mirror should answer. */
    fun isRateLimited(httpCode: Int, remainingHeader: String?): Boolean =
        httpCode == 429 || (httpCode == 403 && (remainingHeader == null || remainingHeader.trim() == "0"))
}
