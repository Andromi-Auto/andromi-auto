package com.andrerinas.openheadunit.view

import java.util.Locale

/** One overlay line each. Declaration order is the print order; the bit is the stored flag. */
enum class PerformanceOverlayField(val bit: Int) { FPS(1), CPU(2), TEMP(4), FRAME(8) }

/** What the sampler must read for the chosen lines. */
enum class PerformanceOverlaySource { CPU, TEMP }

/**
 * The decidable half of the projection performance overlay: which lines print, what they say, what
 * must be sampled for them, the two CPU figures and the temperature it picks out of the thermal zones.
 */
object PerformanceOverlayPolicy {

    const val DEFAULT_BITS = 0b1111

    fun toBits(fields: Set<PerformanceOverlayField>): Int = fields.fold(0) { acc, f -> acc or f.bit }

    fun fromBits(bits: Int): Set<PerformanceOverlayField> =
        PerformanceOverlayField.values().filter { bits and it.bit != 0 }.toSet()

    fun isEmpty(fields: Set<PerformanceOverlayField>): Boolean = fields.isEmpty()

    fun format(
        fields: Set<PerformanceOverlayField>,
        fps: Int?,
        appCpu: Int?,
        totalCpu: Int?,
        loadAverage: Double?,
        tempC: Int?,
        frameAgeMs: Long?
    ): String = PerformanceOverlayField.values().filter { it in fields }.joinToString("\n") { field ->
        when (field) {
            PerformanceOverlayField.FPS -> "FPS: ${fps?.toString() ?: "--"}"
            PerformanceOverlayField.CPU -> {
                val app = appCpu?.let { "$it%" } ?: "--"
                val sys = totalCpu?.let { "$it%" }
                    ?: loadAverage?.let { String.format(Locale.US, "%.2f load", it) }
                    ?: "--"
                "CPU: app $app / sys $sys"
            }
            PerformanceOverlayField.TEMP -> "Temp: ${tempC?.let { "${it}C" } ?: "--"}"
            PerformanceOverlayField.FRAME -> "Frame: ${frameAgeMs?.let { "${it}ms" } ?: "--"}"
        }
    }

    fun sampling(fields: Set<PerformanceOverlayField>): Set<PerformanceOverlaySource> {
        val sources = mutableSetOf<PerformanceOverlaySource>()
        if (PerformanceOverlayField.CPU in fields) sources.add(PerformanceOverlaySource.CPU)
        if (PerformanceOverlayField.TEMP in fields) sources.add(PerformanceOverlaySource.TEMP)
        return sources
    }

    fun describe(fields: Set<PerformanceOverlayField>): String {
        val names = PerformanceOverlayField.values().filter { it in fields }.joinToString(",") { it.name }
        val sources = PerformanceOverlaySource.values().filter { it in sampling(fields) }
            .joinToString(",") { it.name.lowercase(Locale.US) }
        return "fields=${names.ifEmpty { "none" }} sources=${sources.ifEmpty { "none" }}"
    }

    /**
     * Process CPU time sums every thread, so on a multi-core unit it can exceed the wall clock it
     * is measured over. Divided by the core count it reads on the same 0-100 scale as the system
     * figure printed next to it, and the clamp covers a unit that hotplugs cores offline.
     */
    fun appCpuPercent(cpuDeltaMs: Long, elapsedDeltaMs: Long, coreCount: Int): Int {
        val elapsed = elapsedDeltaMs.coerceAtLeast(1L)
        val cpu = cpuDeltaMs.coerceAtLeast(0L)
        val cores = coreCount.coerceAtLeast(1)
        return ((cpu.toDouble() / (elapsed * cores)) * 100.0).toInt().coerceIn(0, 100)
    }

    /** Busy share of /proc/stat's aggregate cpu line, which already sums every core. */
    fun totalCpuPercent(totalDelta: Long, idleDelta: Long): Int {
        val total = totalDelta.coerceAtLeast(1L)
        val idle = idleDelta.coerceAtLeast(0L)
        return (((total - idle).toDouble() / total) * 100.0).toInt().coerceIn(0, 100)
    }

    /**
     * The hottest zone that answered. A zone that could not be read is a null entry and is skipped:
     * one EINVAL zone used to discard the whole scan, including the seventeen that read fine.
     */
    fun temperatureC(rawZoneValues: List<Int?>): Int? = rawZoneValues
        .mapNotNull { raw ->
            when {
                raw == null -> null
                raw in 10000..125000 -> raw / 1000
                raw in 10..125 -> raw
                else -> null
            }
        }
        .maxOrNull()
}
