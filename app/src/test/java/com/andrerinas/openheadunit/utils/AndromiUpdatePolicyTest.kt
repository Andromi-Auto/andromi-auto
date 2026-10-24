package com.andrerinas.openheadunit.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndromiUpdatePolicyTest {

    @Test
    fun `the build number is read from a stable release tag`() {
        assertEquals(20, AndromiUpdatePolicy.buildOfTag("v4.0.0-alpha-build20"))
        assertEquals(135, AndromiUpdatePolicy.buildOfTag("v4.1.0-build135"))
    }

    @Test
    fun `a tag without a build number is not a stable release of this app`() {
        assertNull(AndromiUpdatePolicy.buildOfTag("development"))
        assertNull(AndromiUpdatePolicy.buildOfTag("v3.5.0"))
        assertNull(AndromiUpdatePolicy.buildOfTag("test-claude/branch"))
    }

    @Test
    fun `the installed build comes from the versionCode`() {
        assertEquals(20, AndromiUpdatePolicy.buildOfVersionCode(2_000_000_020L))
        // A local build has no run number and must never look newer than a release.
        assertEquals(0, AndromiUpdatePolicy.buildOfVersionCode(2_000_000_000L))
        assertEquals(0, AndromiUpdatePolicy.buildOfVersionCode(5L))
    }

    @Test
    fun `the latest tag is read from the releases latest redirect`() {
        assertEquals("v4.0.0-alpha-build20", AndromiUpdatePolicy.tagFromLatestRedirect(
            "https://github.com/Andromi-Auto/andromi-auto/releases/tag/v4.0.0-alpha-build20"))
        assertNull(AndromiUpdatePolicy.tagFromLatestRedirect(
            "https://github.com/Andromi-Auto/andromi-auto/releases"))
        assertNull(AndromiUpdatePolicy.tagFromLatestRedirect(null))
    }

    @Test
    fun `builds are compared by number`() {
        assertEquals(AndromiUpdatePolicy.Verdict.UPDATE_AVAILABLE, AndromiUpdatePolicy.verdict(18, 20))
        assertEquals(AndromiUpdatePolicy.Verdict.UP_TO_DATE, AndromiUpdatePolicy.verdict(20, 20))
        assertEquals(AndromiUpdatePolicy.Verdict.AHEAD_OF_STABLE, AndromiUpdatePolicy.verdict(25, 20))
    }

    @Test
    fun `only a spent hourly limit sends the check to the web page`() {
        assertTrue(AndromiUpdatePolicy.isRateLimited(403, "0"))
        assertTrue(AndromiUpdatePolicy.isRateLimited(429, "12"))
        assertFalse(AndromiUpdatePolicy.isRateLimited(403, "41"))
        assertFalse(AndromiUpdatePolicy.isRateLimited(200, "0"))
    }
}
