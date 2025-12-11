package com.andrerinas.headunitrevived.utils

/**
 * A data class to hold the definitive screen specifications for the Android Auto session.
 * This ensures all components use a single, consistent source of truth.
 */
data class ScreenSpec(
    val width: Int,
    val height: Int,
    val densityDpi: Int
)
