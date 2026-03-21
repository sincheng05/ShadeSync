package com.example.shadesync

import com.example.shadesync.feature.shadematch.domain.ColorMath
import com.example.shadesync.feature.shadematch.domain.LabColor
import com.example.shadesync.feature.shadematch.domain.RgbColor
import java.util.Locale

internal data class ScientificColorReadout(
    val rgb: RgbColor,
    val lab: LabColor
) {
    val hex: String = rgb.toHexString()

    fun rgbDisplay(): String {
        return "RGB (8-bit sRGB): ${rgb.r}, ${rgb.g}, ${rgb.b}"
    }

    fun labDisplay(locale: Locale = Locale.US): String {
        return lab.toDisplayString(locale)
    }
}

internal fun RgbColor.toScientificColorReadout(): ScientificColorReadout {
    return ScientificColorReadout(
        rgb = this,
        lab = ColorMath.rgbToLab(this)
    )
}

internal fun RgbColor.toHexString(): String {
    return String.format(Locale.US, "#%02X%02X%02X", r, g, b)
}

internal fun LabColor.toDisplayString(locale: Locale = Locale.US): String {
    // Use fixed decimal precision so exported values remain consistent across screens and datasets.
    return String.format(
        locale,
        "CIELAB (D65): L*=%.2f, a*=%.2f, b*=%.2f",
        l,
        a,
        b
    )
}
