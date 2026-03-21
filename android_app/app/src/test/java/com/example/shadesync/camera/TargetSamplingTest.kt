package com.example.shadesync.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetSamplingTest {

    @Test
    fun imageRoiForTarget_fallsBackToCenteredImageRegion_whenViewportIsUnknown() {
        val roi = TargetSampling.imageRoiForTarget(
            imageWidth = 1000,
            imageHeight = 500,
            viewportSize = ViewportSize.Unspecified
        )

        assertEquals(440, roi.left)
        assertEquals(195, roi.top)
        assertEquals(560, roi.right)
        assertEquals(305, roi.bottom)
    }

    @Test
    fun imageRoiForTarget_matchesCenteredGuideInsideFillCenterPreview() {
        val roi = TargetSampling.imageRoiForTarget(
            imageWidth = 1280,
            imageHeight = 720,
            viewportSize = ViewportSize(widthPx = 360, heightPx = 260)
        )

        assertEquals(580, roi.left)
        assertEquals(281, roi.top)
        assertEquals(700, roi.right)
        assertEquals(439, roi.bottom)
        assertTrue(roi.width < 160)
        assertTrue(roi.height < 200)
    }

    @Test
    fun imageRoiForTarget_clampsCustomFrameToImageBounds() {
        val roi = TargetSampling.imageRoiForTarget(
            imageWidth = 640,
            imageHeight = 480,
            viewportSize = ViewportSize(widthPx = 320, heightPx = 240),
            targetFrameSpec = TargetFrameSpec(
                widthFraction = 0.4f,
                heightFraction = 0.4f,
                centerXFraction = 0.95f,
                centerYFraction = 0.08f
            )
        )

        assertEquals(480, roi.left)
        assertEquals(0, roi.top)
        assertEquals(640, roi.right)
        assertEquals(134, roi.bottom)
    }
}
