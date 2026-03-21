package com.example.shadesync.camera

import kotlin.math.max
import kotlin.math.roundToInt

internal data class TargetFrameSpec(
    val widthFraction: Float,
    val heightFraction: Float,
    val centerXFraction: Float = 0.5f,
    val centerYFraction: Float = 0.5f
) {
    init {
        require(widthFraction in 0f..1f) { "widthFraction must be between 0 and 1." }
        require(heightFraction in 0f..1f) { "heightFraction must be between 0 and 1." }
        require(centerXFraction in 0f..1f) { "centerXFraction must be between 0 and 1." }
        require(centerYFraction in 0f..1f) { "centerYFraction must be between 0 and 1." }
    }
}

internal data class ViewportSize(
    val widthPx: Int,
    val heightPx: Int
) {
    val isSpecified: Boolean
        get() = widthPx > 0 && heightPx > 0

    companion object {
        val Unspecified = ViewportSize(widthPx = 0, heightPx = 0)
    }
}

internal data class ImageRoi(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val width: Int
        get() = right - left

    val height: Int
        get() = bottom - top
}

internal object TargetSampling {
    /**
     * The guide is intentionally a little smaller than a single Vita tab crown,
     * so the sampled pixels stay away from the tray, handle, and surrounding glare.
     */
    val DefaultToothTarget = TargetFrameSpec(
        widthFraction = 0.12f,
        heightFraction = 0.22f
    )

    fun imageRoiForTarget(
        imageWidth: Int,
        imageHeight: Int,
        viewportSize: ViewportSize,
        targetFrameSpec: TargetFrameSpec = DefaultToothTarget
    ): ImageRoi {
        require(imageWidth > 0) { "imageWidth must be positive." }
        require(imageHeight > 0) { "imageHeight must be positive." }

        if (!viewportSize.isSpecified) {
            return centeredRoi(
                width = imageWidth.toFloat(),
                height = imageHeight.toFloat(),
                targetFrameSpec = targetFrameSpec
            )
        }

        val imageWidthFloat = imageWidth.toFloat()
        val imageHeightFloat = imageHeight.toFloat()
        val viewportWidth = viewportSize.widthPx.toFloat()
        val viewportHeight = viewportSize.heightPx.toFloat()

        // PreviewView uses FILL_CENTER, so we need to reverse the center-crop to
        // translate the visual guide from view space back into analyzer image space.
        val scale = max(viewportWidth / imageWidthFloat, viewportHeight / imageHeightFloat)
        val displayedImageWidth = imageWidthFloat * scale
        val displayedImageHeight = imageHeightFloat * scale
        val cropX = (displayedImageWidth - viewportWidth) / 2f
        val cropY = (displayedImageHeight - viewportHeight) / 2f

        val targetWidthInView = viewportWidth * targetFrameSpec.widthFraction
        val targetHeightInView = viewportHeight * targetFrameSpec.heightFraction
        val targetCenterXInView = viewportWidth * targetFrameSpec.centerXFraction
        val targetCenterYInView = viewportHeight * targetFrameSpec.centerYFraction

        val left = ((targetCenterXInView - targetWidthInView / 2f) + cropX) / scale
        val top = ((targetCenterYInView - targetHeightInView / 2f) + cropY) / scale
        val right = ((targetCenterXInView + targetWidthInView / 2f) + cropX) / scale
        val bottom = ((targetCenterYInView + targetHeightInView / 2f) + cropY) / scale

        return ImageRoi(
            left = left.roundToInt().coerceIn(0, imageWidth - 1),
            top = top.roundToInt().coerceIn(0, imageHeight - 1),
            right = right.roundToInt().coerceIn(1, imageWidth),
            bottom = bottom.roundToInt().coerceIn(1, imageHeight)
        ).normalized()
    }

    private fun centeredRoi(
        width: Float,
        height: Float,
        targetFrameSpec: TargetFrameSpec
    ): ImageRoi {
        val roiWidth = width * targetFrameSpec.widthFraction
        val roiHeight = height * targetFrameSpec.heightFraction
        val centerX = width * targetFrameSpec.centerXFraction
        val centerY = height * targetFrameSpec.centerYFraction
        return ImageRoi(
            left = (centerX - roiWidth / 2f).roundToInt(),
            top = (centerY - roiHeight / 2f).roundToInt(),
            right = (centerX + roiWidth / 2f).roundToInt(),
            bottom = (centerY + roiHeight / 2f).roundToInt()
        ).normalized()
    }

    private fun ImageRoi.normalized(): ImageRoi {
        val safeLeft = left.coerceAtMost(right - 1)
        val safeTop = top.coerceAtMost(bottom - 1)
        val safeRight = right.coerceAtLeast(safeLeft + 1)
        val safeBottom = bottom.coerceAtLeast(safeTop + 1)
        return copy(left = safeLeft, top = safeTop, right = safeRight, bottom = safeBottom)
    }
}
