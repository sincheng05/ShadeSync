package com.example.shadesync.feature.shadematch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RgbDistanceMatcherTest {
    @Test
    fun squaredDistance_isZeroForSameColor() {
        val sample = RgbColor(120, 110, 100)

        assertEquals(0, RgbDistanceMatcher.squaredDistance(sample, sample))
    }

    @Test
    fun nearest_returnsClosestReferenceKey() {
        val result = RgbDistanceMatcher.nearest(
            sample = RgbColor(100, 100, 100),
            references = mapOf(
                "A1" to RgbColor(95, 98, 102),
                "B1" to RgbColor(150, 150, 150),
                "C1" to RgbColor(40, 45, 50)
            )
        )

        assertEquals("A1", result)
    }

    @Test
    fun nearest_returnsNullWhenReferenceSetIsEmpty() {
        val result = RgbDistanceMatcher.nearest(RgbColor(100, 100, 100), emptyMap<String, RgbColor>())

        assertNull(result)
    }
}
