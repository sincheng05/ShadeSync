package com.example.shadesync.feature.shadematch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMathTest {
    @Test
    fun applyWhiteBalance_clampsChannelsIntoRgbRange() {
        val balanced = ColorMath.applyWhiteBalance(
            rgb = RgbColor(200, 100, 10),
            whiteBalance = WhiteBalanceGain(rGain = 2.0, gGain = 0.5, bGain = -1.0)
        )

        assertEquals(RgbColor(255, 50, 0), balanced)
    }

    @Test
    fun rgbToLab_blackMapsToLabOrigin() {
        val lab = ColorMath.rgbToLab(RgbColor(0, 0, 0))

        assertEquals(0.0, lab.l, 0.0001)
        assertEquals(0.0, lab.a, 0.0001)
        assertEquals(0.0, lab.b, 0.0001)
    }

    @Test
    fun rgbToLab_whiteProducesNearNeutralWhite() {
        val lab = ColorMath.rgbToLab(RgbColor(255, 255, 255))

        assertEquals(100.0, lab.l, 0.1)
        assertEquals(0.0, lab.a, 0.6)
        assertEquals(0.0, lab.b, 0.6)
    }

    @Test
    fun deltaE76_isZeroForIdenticalColors() {
        val sample = LabColor(65.4, 4.2, -12.8)

        assertEquals(0.0, ColorMath.deltaE76(sample, sample), 0.0001)
    }

    @Test
    fun deltaE76_increasesForDifferentColors() {
        val left = ColorMath.rgbToLab(RgbColor(80, 90, 100))
        val right = ColorMath.rgbToLab(RgbColor(180, 170, 160))

        assertTrue(ColorMath.deltaE76(left, right) > 0.0)
    }
}
