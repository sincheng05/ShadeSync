package com.example.shadesync.camera

import com.example.shadesync.feature.shadematch.domain.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdaptiveLightingTest {
    @Test
    fun sceneLuminance_mapsBlackAndWhiteIntoNormalizedRange() {
        assertEquals(0.0, AdaptiveLightingPolicy.sceneLuminance(RgbColor(0, 0, 0)), 0.0001)
        assertEquals(1.0, AdaptiveLightingPolicy.sceneLuminance(RgbColor(255, 255, 255)), 0.0001)
    }

    @Test
    fun recommendTorchLevel_startsFromStrongOutputForVeryDarkScene() {
        val result = AdaptiveLightingPolicy.recommendTorchLevel(
            sceneLuminance = 0.12,
            previousTorchLevel = TorchLevel.OFF
        )

        assertEquals(TorchLevel.HIGH, result)
    }

    @Test
    fun recommendTorchLevel_usesHysteresisToAvoidImmediateShutdown() {
        val result = AdaptiveLightingPolicy.recommendTorchLevel(
            sceneLuminance = 0.58,
            previousTorchLevel = TorchLevel.LOW
        )

        assertEquals(TorchLevel.LOW, result)
    }

    @Test
    fun recommendTorchLevel_releasesTorchOnceSceneIsClearlyBrightEnough() {
        val result = AdaptiveLightingPolicy.recommendTorchLevel(
            sceneLuminance = 0.68,
            previousTorchLevel = TorchLevel.LOW
        )

        assertEquals(TorchLevel.OFF, result)
    }

    @Test
    fun strengthFor_scalesRequestedTorchLevelAcrossAvailableFlashRange() {
        assertEquals(3, AdaptiveLightingPolicy.strengthFor(TorchLevel.LOW, maxStrengthLevel = 8))
        assertEquals(5, AdaptiveLightingPolicy.strengthFor(TorchLevel.MEDIUM, maxStrengthLevel = 8))
        assertEquals(8, AdaptiveLightingPolicy.strengthFor(TorchLevel.HIGH, maxStrengthLevel = 8))
    }

    @Test
    fun manualStrengthForPercent_scalesManualTorchBrightnessAcrossHardwareRange() {
        assertNull(AdaptiveLightingPolicy.manualStrengthForPercent(0, maxStrengthLevel = 8))
        assertEquals(2, AdaptiveLightingPolicy.manualStrengthForPercent(25, maxStrengthLevel = 8))
        assertEquals(5, AdaptiveLightingPolicy.manualStrengthForPercent(60, maxStrengthLevel = 8))
        assertEquals(8, AdaptiveLightingPolicy.manualStrengthForPercent(100, maxStrengthLevel = 8))
    }
}
