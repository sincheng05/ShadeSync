package com.example.shadesync

import com.example.shadesync.feature.shadematch.domain.LabColor
import com.example.shadesync.feature.shadematch.domain.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Test

class ScientificColorReadoutTest {
    @Test
    fun toHexString_formatsUppercaseHexForDatasetStability() {
        assertEquals("#C3BAA0", RgbColor(195, 186, 160).toHexString())
    }

    @Test
    fun toDisplayString_formatsLabWithFixedPrecision() {
        val formatted = LabColor(75.61154, -1.15178, 14.33103).toDisplayString()

        assertEquals("CIELAB (D65): L*=75.61, a*=-1.15, b*=14.33", formatted)
    }

    @Test
    fun toScientificColorReadout_preservesRgbAndComputesLabValues() {
        val readout = RgbColor(195, 186, 160).toScientificColorReadout()

        assertEquals("#C3BAA0", readout.hex)
        assertEquals(RgbColor(195, 186, 160), readout.rgb)
        assertEquals(75.61154, readout.lab.l, 0.0001)
        assertEquals(-1.15178, readout.lab.a, 0.0001)
        assertEquals(14.33103, readout.lab.b, 0.0001)
    }
}
