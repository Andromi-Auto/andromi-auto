package com.andrerinas.openheadunit.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.andrerinas.openheadunit.utils.SettingsBackupManager
import org.junit.Test

class PerformanceOverlayPolicyTest {

    @Test
    fun `app cpu is a share of the whole device, not of one core`() {
        // Two threads busy for a 1000 ms window on a quad core is half the device.
        assertEquals(50, PerformanceOverlayPolicy.appCpuPercent(2000L, 1000L, 4))
        assertEquals(25, PerformanceOverlayPolicy.appCpuPercent(1000L, 1000L, 4))
        assertEquals(100, PerformanceOverlayPolicy.appCpuPercent(4000L, 1000L, 4))
    }

    @Test
    fun `app cpu is clamped when the core count is under-reported`() {
        // A unit that hotplugs cores offline answers fewer than were actually busy.
        assertEquals(100, PerformanceOverlayPolicy.appCpuPercent(2000L, 1000L, 1))
    }

    @Test
    fun `app cpu guards a zero or negative delta`() {
        assertEquals(0, PerformanceOverlayPolicy.appCpuPercent(0L, 0L, 4))
        assertEquals(0, PerformanceOverlayPolicy.appCpuPercent(-500L, 1000L, 4))
        assertEquals(100, PerformanceOverlayPolicy.appCpuPercent(1000L, -1L, 1))
    }

    @Test
    fun `total cpu is the busy share of the aggregate line`() {
        assertEquals(0, PerformanceOverlayPolicy.totalCpuPercent(1000L, 1000L))
        assertEquals(100, PerformanceOverlayPolicy.totalCpuPercent(1000L, 0L))
        assertEquals(40, PerformanceOverlayPolicy.totalCpuPercent(1000L, 600L))
    }

    @Test
    fun `temperature takes the hottest zone and scales milli-degrees`() {
        assertEquals(67, PerformanceOverlayPolicy.temperatureC(listOf(65940, 67210)))
        assertEquals(67, PerformanceOverlayPolicy.temperatureC(listOf(67)))
    }

    @Test
    fun `temperature drops a zone outside the sanity range`() {
        assertEquals(67, PerformanceOverlayPolicy.temperatureC(listOf(67210, 200000, 5)))
        assertNull(PerformanceOverlayPolicy.temperatureC(listOf(200000)))
    }

    @Test
    fun `temperature is null only when no zone answered`() {
        assertNull(PerformanceOverlayPolicy.temperatureC(emptyList()))
        assertNull(PerformanceOverlayPolicy.temperatureC(listOf(null, null)))
    }

    @Test
    fun `an unreadable zone does not discard the zones that answered`() {
        // D-HU's measured shape: 17 zones read, osctsen and outtsen answer EINVAL.
        val zones = List(17) { 60000 + it * 500 } + listOf(null, null)
        assertEquals(68, PerformanceOverlayPolicy.temperatureC(zones))
    }

    private val all = PerformanceOverlayField.values().toSet()

    private fun text(fields: Set<PerformanceOverlayField>) =
        PerformanceOverlayPolicy.format(fields, 60, 12, 40, null, 45, 16L)

    @Test
    fun `all four lines print the text the overlay printed before`() {
        assertEquals("FPS: 60\nCPU: app 12% / sys 40%\nTemp: 45C\nFrame: 16ms", text(all))
    }

    @Test
    fun `fps alone is one line with no newline`() {
        assertEquals("FPS: 60", text(setOf(PerformanceOverlayField.FPS)))
    }

    @Test
    fun `each field alone prints only its own line`() {
        assertEquals("FPS: 60", text(setOf(PerformanceOverlayField.FPS)))
        assertEquals("CPU: app 12% / sys 40%", text(setOf(PerformanceOverlayField.CPU)))
        assertEquals("Temp: 45C", text(setOf(PerformanceOverlayField.TEMP)))
        assertEquals("Frame: 16ms", text(setOf(PerformanceOverlayField.FRAME)))
    }

    @Test
    fun `lines keep the order fps cpu temp frame`() {
        val set = setOf(PerformanceOverlayField.FRAME, PerformanceOverlayField.FPS)
        assertEquals("FPS: 60\nFrame: 16ms", text(set))
    }

    @Test
    fun `no field prints nothing`() {
        assertEquals("", text(emptySet()))
        assertTrue(PerformanceOverlayPolicy.isEmpty(emptySet()))
    }

    @Test
    fun `an unreadable value prints dashes`() {
        assertEquals(
            "FPS: --\nCPU: app -- / sys --\nTemp: --\nFrame: --",
            PerformanceOverlayPolicy.format(all, null, null, null, null, null, null)
        )
    }

    @Test
    fun `system cpu falls back to the load average`() {
        assertEquals(
            "CPU: app 12% / sys 1.50 load",
            PerformanceOverlayPolicy.format(
                setOf(PerformanceOverlayField.CPU), null, 12, null, 1.5, null, null
            )
        )
    }

    @Test
    fun `the field set round-trips through its stored int`() {
        for (bits in 0..15) {
            val fields = PerformanceOverlayPolicy.fromBits(bits)
            assertEquals(bits, PerformanceOverlayPolicy.toBits(fields))
            assertEquals(fields, PerformanceOverlayPolicy.fromBits(PerformanceOverlayPolicy.toBits(fields)))
        }
    }

    @Test
    fun `the stored default is all four lines`() {
        assertEquals(all, PerformanceOverlayPolicy.fromBits(PerformanceOverlayPolicy.DEFAULT_BITS))
    }

    @Test
    fun `unknown bits are ignored`() {
        assertEquals(all, PerformanceOverlayPolicy.fromBits(0xFF))
    }

    @Test
    fun `sampling names only the sources a line needs`() {
        assertEquals(emptySet<PerformanceOverlaySource>(),
            PerformanceOverlayPolicy.sampling(setOf(PerformanceOverlayField.FPS, PerformanceOverlayField.FRAME)))
        assertEquals(setOf(PerformanceOverlaySource.CPU),
            PerformanceOverlayPolicy.sampling(setOf(PerformanceOverlayField.CPU)))
        assertEquals(setOf(PerformanceOverlaySource.TEMP),
            PerformanceOverlayPolicy.sampling(setOf(PerformanceOverlayField.TEMP)))
        assertEquals(PerformanceOverlaySource.values().toSet(), PerformanceOverlayPolicy.sampling(all))
    }

    @Test
    fun `describe names the fields and the sources`() {
        assertEquals("fields=FPS sources=none",
            PerformanceOverlayPolicy.describe(setOf(PerformanceOverlayField.FPS)))
        assertEquals("fields=FPS,CPU,TEMP,FRAME sources=cpu,temp", PerformanceOverlayPolicy.describe(all))
        assertEquals("fields=none sources=none", PerformanceOverlayPolicy.describe(emptySet()))
    }

    @Test
    fun `overlay-fields is in the settings backup as an int`() {
        assertEquals(SettingsBackupManager.ValueType.INT, SettingsBackupManager.backupKeys["overlay-fields"])
    }
}
