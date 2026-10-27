package com.andrerinas.openheadunit.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndromiUpdatePolicyTest {

    private val sha = "a".repeat(64)

    @Test
    fun `the marker at the end of a release's notes is read`() {
        val notes = "Fixes.\n\n<!-- versionCode: 2000000040 versionName: 4.0.0-dev.40 sha256: $sha -->"
        val info = AndromiUpdatePolicy.parseNotes(notes)
        assertEquals(2_000_000_040, info.versionCode)
        assertEquals("4.0.0-dev.40", info.versionName)
        assertEquals(sha, info.sha256)
        assertEquals("Fixes.", AndromiUpdatePolicy.readableNotes(notes))
    }

    @Test
    fun `notes without a marker give nothing`() {
        val info = AndromiUpdatePolicy.parseNotes("Andromi Auto 4.0.0-alpha, build 22 (commit 295d886).")
        assertNull(info.versionCode)
        assertNull(info.versionName)
        assertNull(info.sha256)
    }

    @Test
    fun `a build of the old tag scheme still gets its version code`() {
        assertEquals(2_000_000_022, AndromiUpdatePolicy.versionCodeOfOldTag("v4.0.0-alpha-build22"))
        assertNull(AndromiUpdatePolicy.versionCodeOfOldTag("v4.0.0"))
        assertNull(AndromiUpdatePolicy.versionCodeOfOldTag("development"))
    }

    @Test
    fun `the channel follows the version name until the user chooses`() {
        assertEquals(AndromiUpdatePolicy.CHANNEL_STABLE, AndromiUpdatePolicy.channelOfVersionName("4.0.0"))
        assertEquals(AndromiUpdatePolicy.CHANNEL_DEV, AndromiUpdatePolicy.channelOfVersionName("4.0.0-dev.35"))
        assertEquals(AndromiUpdatePolicy.CHANNEL_STABLE, AndromiUpdatePolicy.normalizeChannel("stable"))
        assertEquals(AndromiUpdatePolicy.CHANNEL_DEV, AndromiUpdatePolicy.normalizeChannel("anything else"))
    }

    @Test
    fun `the installed build comes from the versionCode`() {
        assertEquals(20, AndromiUpdatePolicy.buildOfVersionCode(2_000_000_020L))
        // A local build has no run number and must never look newer than a release.
        assertEquals(0, AndromiUpdatePolicy.buildOfVersionCode(2_000_000_000L))
        assertEquals(0, AndromiUpdatePolicy.buildOfVersionCode(5L))
    }

    @Test
    fun `builds are compared by version code`() {
        assertEquals(AndromiUpdatePolicy.Verdict.UPDATE_AVAILABLE, AndromiUpdatePolicy.verdict(2_000_000_018, 2_000_000_020))
        assertEquals(AndromiUpdatePolicy.Verdict.UP_TO_DATE, AndromiUpdatePolicy.verdict(2_000_000_020, 2_000_000_020))
        assertEquals(AndromiUpdatePolicy.Verdict.AHEAD_OF_CHANNEL, AndromiUpdatePolicy.verdict(2_000_000_025, 2_000_000_020))
    }

    @Test
    fun `only a spent hourly limit sends the check to update json`() {
        assertTrue(AndromiUpdatePolicy.isRateLimited(403, "0"))
        assertTrue(AndromiUpdatePolicy.isRateLimited(429, "12"))
        assertFalse(AndromiUpdatePolicy.isRateLimited(403, "41"))
        assertFalse(AndromiUpdatePolicy.isRateLimited(200, "0"))
    }
}
