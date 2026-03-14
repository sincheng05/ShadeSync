package com.example.shadesync.feature.shadematch.domain

data class RgbColor(
    val r: Int,
    val g: Int,
    val b: Int
)

data class LabColor(
    val l: Double,
    val a: Double,
    val b: Double
)

data class WhiteBalanceGain(
    val rGain: Double,
    val gGain: Double,
    val bGain: Double
) {
    companion object {
        val Neutral = WhiteBalanceGain(1.0, 1.0, 1.0)
    }
}

data class ShadeSwatch(
    val id: String,
    val lab: LabColor,
    val rgb: RgbColor? = null,
    val source: String,
    val version: String,
    val isPlaceholder: Boolean
)

data class ShadeMatch(
    val swatch: ShadeSwatch,
    val deltaE: Double
)

data class RoiSample(
    val rgb: RgbColor,
    val lab: LabColor,
    val whiteBalance: WhiteBalanceGain,
    val timestampMs: Long
)

enum class SurfaceFeature {
    WHITE_SPOT,
    BROWN_STAIN,
    GRAY_BAND,
    PIT,
    TRANSLUCENT_EDGE,
    SMOOTH_EROSION
}

data class ObservationRecord(
    val id: String,
    val shadeId: String,
    val deltaE: Double,
    val lab: LabColor,
    val features: Set<SurfaceFeature>,
    val deviceModel: String,
    val calibrationVersion: String,
    val regionCode: String,
    val capturedAtIso: String,
    val isVerified: Boolean,
    val isChild: Boolean,
    val notes: String? = null
)
