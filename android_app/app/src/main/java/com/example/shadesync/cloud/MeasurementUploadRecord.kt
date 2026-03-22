package com.example.shadesync.cloud

import com.example.shadesync.feature.shadematch.domain.ColorMath
import com.example.shadesync.feature.shadematch.domain.LabColor
import com.example.shadesync.feature.shadematch.domain.RgbColor
import com.example.shadesync.toHexString
import java.time.Instant

internal data class MeasurementUploadRecord(
    val capturedAtUnixMs: Long,
    val rgb: RgbColor,
    val lab: LabColor,
    val bestMatchShadeCode: String? = null,
    val bestMatchRgbDistanceSquared: Int? = null,
    val imageWidthPx: Int,
    val imageHeightPx: Int
) {
    val capturedAtUnixSeconds: Long = capturedAtUnixMs / 1000L
    val capturedAtIsoUtc: String = Instant.ofEpochMilli(capturedAtUnixMs).toString()
    val hex: String = rgb.toHexString()

    fun toSheetRow(
        imageFileId: String,
        imageWebLink: String
    ): List<Any> {
        return listOf(
            capturedAtUnixMs,
            capturedAtUnixSeconds,
            capturedAtIsoUtc,
            hex,
            rgb.r,
            rgb.g,
            rgb.b,
            lab.l.formatForSheet(),
            lab.a.formatForSheet(),
            lab.b.formatForSheet(),
            bestMatchShadeCode.orEmpty(),
            bestMatchRgbDistanceSquared ?: "",
            imageWidthPx,
            imageHeightPx,
            imageFileId,
            imageWebLink
        )
    }

    companion object {
        val headers: List<String> = listOf(
            "captured_at_unix_ms",
            "captured_at_unix_s",
            "captured_at_iso_utc",
            "rgb_hex",
            "rgb_r",
            "rgb_g",
            "rgb_b",
            "lab_l",
            "lab_a",
            "lab_b",
            "best_match_shade",
            "best_match_rgb_distance_squared",
            "image_width_px",
            "image_height_px",
            "image_file_id",
            "image_web_link"
        )

        fun fromCapturedSample(
            capturedAtUnixMs: Long,
            rgb: RgbColor,
            imageWidthPx: Int,
            imageHeightPx: Int,
            bestMatchShadeCode: String? = null,
            bestMatchRgbDistanceSquared: Int? = null
        ): MeasurementUploadRecord {
            return MeasurementUploadRecord(
                capturedAtUnixMs = capturedAtUnixMs,
                rgb = rgb,
                lab = ColorMath.rgbToLab(rgb),
                bestMatchShadeCode = bestMatchShadeCode,
                bestMatchRgbDistanceSquared = bestMatchRgbDistanceSquared,
                imageWidthPx = imageWidthPx,
                imageHeightPx = imageHeightPx
            )
        }
    }
}

private fun Double.formatForSheet(): String {
    return String.format(java.util.Locale.US, "%.4f", this)
}
