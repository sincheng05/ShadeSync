package com.example.shadesync.feature.shadematch.domain

import kotlin.math.pow
import kotlin.math.sqrt

object ColorMath {
    private const val XN = 0.95047
    private const val YN = 1.0
    private const val ZN = 1.08883

    fun applyWhiteBalance(rgb: RgbColor, whiteBalance: WhiteBalanceGain): RgbColor {
        val r = (rgb.r * whiteBalance.rGain).roundToIntInRange()
        val g = (rgb.g * whiteBalance.gGain).roundToIntInRange()
        val b = (rgb.b * whiteBalance.bGain).roundToIntInRange()
        return RgbColor(r, g, b)
    }

    fun rgbToLab(rgb: RgbColor, whiteBalance: WhiteBalanceGain = WhiteBalanceGain.Neutral): LabColor {
        val balanced = applyWhiteBalance(rgb, whiteBalance)
        val r = srgbToLinear(balanced.r / 255.0)
        val g = srgbToLinear(balanced.g / 255.0)
        val b = srgbToLinear(balanced.b / 255.0)

        val x = (r * 0.4124) + (g * 0.3576) + (b * 0.1805)
        val y = (r * 0.2126) + (g * 0.7152) + (b * 0.0722)
        val z = (r * 0.0193) + (g * 0.1192) + (b * 0.9505)

        val fx = labPivot(x / XN)
        val fy = labPivot(y / YN)
        val fz = labPivot(z / ZN)

        val l = (116.0 * fy) - 16.0
        val a = 500.0 * (fx - fy)
        val bStar = 200.0 * (fy - fz)
        return LabColor(l, a, bStar)
    }

    fun deltaE76(a: LabColor, b: LabColor): Double {
        val dl = a.l - b.l
        val da = a.a - b.a
        val db = a.b - b.b
        return sqrt((dl * dl) + (da * da) + (db * db))
    }

    private fun srgbToLinear(c: Double): Double {
        return if (c <= 0.04045) {
            c / 12.92
        } else {
            ((c + 0.055) / 1.055).pow(2.4)
        }
    }

    private fun labPivot(t: Double): Double {
        return if (t > 0.008856) {
            t.pow(1.0 / 3.0)
        } else {
            (7.787 * t) + (16.0 / 116.0)
        }
    }

    private fun Double.roundToIntInRange(): Int {
        return when {
            this < 0.0 -> 0
            this > 255.0 -> 255
            else -> this.toInt()
        }
    }
}
