package com.andrerinas.openheadunit.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateLinkPolicyTest {

    private val base = "https://github.com/andreknieriem/open-headunit/releases"
    private val page = "$base/tag/v.3.5.0-beta3"
    private val apk = "$base/download/v.3.5.0-beta3/com.andrerinas.headunitrevived_3.5.0-beta3.apk"
    private val debugApk = "$base/download/v.3.5.0-beta3/com.andrerinas.headunitrevived_3.5.0-beta3_debug.apk"

    @Test
    fun picksTheReleaseApkAndSkipsTheDebugBuild() {
        val assets = listOf(
            "com.andrerinas.headunitrevived_3.5.0-beta3_debug.apk" to debugApk,
            "com.andrerinas.headunitrevived_3.5.0-beta3.apk" to apk,
        )
        assertEquals(apk, UpdateLinkPolicy.pickApk(assets))
    }

    @Test
    fun noReleaseApkGivesNull() {
        assertNull(UpdateLinkPolicy.pickApk(emptyList()))
        assertNull(UpdateLinkPolicy.pickApk(listOf("notes.txt" to "$base/notes.txt")))
        assertNull(UpdateLinkPolicy.pickApk(listOf("x_debug.apk" to debugApk)))
    }

    @Test
    fun oldUnitGetsTheApk() {
        assertEquals(apk, UpdateLinkPolicy.linkToOpen(page, apk, needsDirectLink = true))
    }

    @Test
    fun newUnitGetsTheReleasePage() {
        assertEquals(page, UpdateLinkPolicy.linkToOpen(page, apk, needsDirectLink = false))
    }

    @Test
    fun oldUnitWithNoApkFallsBackToThePage() {
        assertEquals(page, UpdateLinkPolicy.linkToOpen(page, null, needsDirectLink = true))
    }
}
