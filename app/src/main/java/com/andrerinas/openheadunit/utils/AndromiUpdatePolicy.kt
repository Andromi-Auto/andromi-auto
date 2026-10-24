package com.andrerinas.openheadunit.utils

/**
 * Andromi Auto: the rules of the stable-release update check, kept free of Android so they can be
 * tested. A stable release is tagged `v<versionName>-build<run number>` by the "Build APKs"
 * workflow on main, and every build's versionCode is [VERSION_CODE_BASE] + the same run number,
 * so builds compare by that number whatever their version name says.
 */
object AndromiUpdatePolicy {

    const val VERSION_CODE_BASE = 2_000_000_000

    enum class Verdict {
        /** The stable release is a newer build than the installed one. */
        UPDATE_AVAILABLE,
        /** The installed build is the stable release. */
        UP_TO_DATE,
        /** The installed build is newer than any stable release: a development build. */
        AHEAD_OF_STABLE,
    }

    private val BUILD_IN_TAG = Regex("""-build(\d+)$""")
    private val TAG_IN_URL = Regex("""/releases/tag/([^/?#]+)""")

    /** The build number in a release tag, or null for a tag this workflow did not write. */
    fun buildOfTag(tag: String): Int? = BUILD_IN_TAG.find(tag.trim())?.groupValues?.get(1)?.toIntOrNull()

    /** The installed build number, from the versionCode. A local build without a run number is 0. */
    fun buildOfVersionCode(versionCode: Long): Int =
        (versionCode - VERSION_CODE_BASE).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

    /**
     * The tag that `github.com/<repo>/releases/latest` redirects to, read from its Location header.
     * This is the fallback when the API's hourly limit (60 per address without a login) is spent:
     * the web page is not counted against it.
     */
    fun tagFromLatestRedirect(location: String?): String? {
        if (location.isNullOrBlank()) return null
        val tag = TAG_IN_URL.find(location)?.groupValues?.get(1) ?: return null
        return java.net.URLDecoder.decode(tag, "UTF-8").takeIf { it.isNotBlank() }
    }

    fun verdict(installedBuild: Int, latestBuild: Int): Verdict = when {
        latestBuild > installedBuild -> Verdict.UPDATE_AVAILABLE
        latestBuild == installedBuild -> Verdict.UP_TO_DATE
        else -> Verdict.AHEAD_OF_STABLE
    }

    /** Whether the API answer says the hourly limit is spent, so the web fallback should answer. */
    fun isRateLimited(httpCode: Int, remainingHeader: String?): Boolean =
        httpCode == 429 || (httpCode == 403 && (remainingHeader == null || remainingHeader.trim() == "0"))
}
