package com.andrerinas.openheadunit.utils

import com.andrerinas.openheadunit.aap.protocol.proto.Control
import com.andrerinas.openheadunit.utils.AnnouncedDensityPolicy.Reason
import com.andrerinas.openheadunit.utils.SystemOptimizer.DisplaySizePreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncedDensityPolicyTest {

    private val small = DisplaySizePreset.SMALL_7_8

    @Test
    fun `the floor's maximum density per codec size`() {
        assertEquals(170, AnnouncedDensityPolicy.huigMaxDpi(800, 480))
        assertEquals(256, AnnouncedDensityPolicy.huigMaxDpi(1280, 720))
        assertEquals(384, AnnouncedDensityPolicy.huigMaxDpi(1920, 1080))
    }

    @Test
    fun `171 at 800x480 is announced as 170`() {
        val d = AnnouncedDensityPolicy.decide(171, small, 800, 480)
        assertEquals(170, d.announced)
        assertEquals(171, d.requested)
        assertEquals(Reason.HEIGHT_FLOOR, d.reason)
    }

    @Test
    fun `175 is clamped and 160 is kept at 800x480`() {
        assertEquals(170, AnnouncedDensityPolicy.decide(175, small, 800, 480).announced)
        val kept = AnnouncedDensityPolicy.decide(160, small, 800, 480)
        assertEquals(160, kept.announced)
        assertEquals(Reason.AS_STORED, kept.reason)
    }

    @Test
    fun `a width bound canvas names the width floor`() {
        val d = AnnouncedDensityPolicy.decide(230, small, 1000, 700)
        assertEquals(213, d.announced)
        assertEquals(Reason.WIDTH_FLOOR, d.reason)
    }

    @Test
    fun `values that meet the floor at 720p stay`() {
        for (v in intArrayOf(215, 240)) {
            val d = AnnouncedDensityPolicy.decide(v, small, 1280, 720)
            assertEquals(v, d.announced)
            assertEquals(Reason.AS_STORED, d.reason)
        }
    }

    @Test
    fun `automatic is codec based`() {
        val d = AnnouncedDensityPolicy.decide(0, small, 800, 480)
        assertEquals(150, d.announced)
        assertEquals(Reason.AUTOMATIC, d.reason)
        assertEquals(113, AnnouncedDensityPolicy.decide(0, DisplaySizePreset.STANDARD_9_10, 800, 480).announced)
    }

    @Test
    fun `portrait is not clamped`() {
        val d = AnnouncedDensityPolicy.decide(240, small, 480, 800)
        assertEquals(240, d.announced)
        assertEquals(Reason.PORTRAIT, d.reason)
    }

    @Test
    fun `unknown geometry keeps the stored value or 160`() {
        assertEquals(171, AnnouncedDensityPolicy.decide(171, small, 0, 0).announced)
        assertEquals(160, AnnouncedDensityPolicy.decide(0, small, 0, 0).announced)
    }

    @Test
    fun `margins shrink the visible area the floor is checked on`() {
        assertEquals(213, AnnouncedDensityPolicy.decide(213, small, 1024, 600).announced)
        assertEquals(213, AnnouncedDensityPolicy.decide(215, small, 1024, 600).announced)
    }

    @Test
    fun `automatic keeps the canvas at or above the floor for every size`() {
        for (r in Settings.Resolution.values()) {
            if (r.width <= r.height) continue
            for (p in DisplaySizePreset.values()) {
                val dpi = AnnouncedDensityPolicy.decide(0, p, r.width, r.height).announced
                assertTrue("$p ${r.resName} -> $dpi", AnnouncedDensityPolicy.canvasDp(r.width, dpi) >= 748)
                assertTrue("$p ${r.resName} -> $dpi", AnnouncedDensityPolicy.canvasDp(r.height, dpi) >= 450)
            }
        }
    }

    @Test
    fun `the fallback density scales by the width ratio`() {
        assertEquals(160, AnnouncedDensityPolicy.fallbackDensity(240, 1920, 1280))
        assertEquals(120, AnnouncedDensityPolicy.fallbackDensity(240, 2560, 1280))
        assertEquals(80, AnnouncedDensityPolicy.fallbackDensity(240, 3840, 1280))
        assertEquals(240, AnnouncedDensityPolicy.fallbackDensity(240, 0, 1280))
    }

    @Test
    fun `the fallback keeps the primary dp canvas`() {
        val primary = Control.Service.MediaSinkService.VideoConfiguration.VideoCodecResolutionType._1920x1080
        val fb = VideoFallbackPolicy.fallbackFor(primary)!!
        val (pw, ph) = VideoFallbackPolicy.sizeOf(primary)
        val (fw, fh) = VideoFallbackPolicy.sizeOf(fb)
        val m = VideoFallbackPolicy.scaledMargins(pw, ph, 120, 60, fb)
        val dpi = 240
        val fdpi = AnnouncedDensityPolicy.fallbackDensity(dpi, pw, fw)
        assertTrue(Math.abs(AnnouncedDensityPolicy.canvasDp(pw - 120, dpi) - AnnouncedDensityPolicy.canvasDp(fw - m.width, fdpi)) <= 1)
        assertTrue(Math.abs(AnnouncedDensityPolicy.canvasDp(ph - 60, dpi) - AnnouncedDensityPolicy.canvasDp(fh - m.height, fdpi)) <= 1)
    }

    @Test
    fun `describe pins the rig's grep targets`() {
        assertEquals(
            "170 (dp canvas 752x451, requested 171, clamped to the 450 dp height floor)",
            AnnouncedDensityPolicy.describe(AnnouncedDensityPolicy.decide(171, small, 800, 480), 800, 480)
        )
        assertEquals(
            "150 (dp canvas 853x512, requested automatic, codec-based)",
            AnnouncedDensityPolicy.describe(AnnouncedDensityPolicy.decide(0, small, 800, 480), 800, 480)
        )
    }
}
