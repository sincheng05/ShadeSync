package com.example.shadesync.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import com.example.shadesync.feature.shadematch.domain.RgbColor
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal data class CapturedTargetImage(
    val jpegBytes: ByteArray,
    val sampledColor: RgbColor,
    val capturedAtUnixMs: Long,
    val widthPx: Int,
    val heightPx: Int
)

internal class TargetImageCaptureController(
    private val targetFrameSpec: TargetFrameSpec = TargetSampling.DefaultToothTarget
) {
    private val captureExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val imageCaptureRef = AtomicReference<ImageCapture?>(null)
    private val viewportRef = AtomicReference(ViewportSize.Unspecified)

    fun bind(imageCapture: ImageCapture) {
        imageCaptureRef.set(imageCapture)
    }

    fun clearBinding() {
        imageCaptureRef.set(null)
    }

    fun updateViewportSize(viewportSize: ViewportSize) {
        viewportRef.set(viewportSize)
    }

    fun currentViewportSize(): ViewportSize = viewportRef.get()

    fun shutdown() {
        captureExecutor.shutdown()
    }

    suspend fun captureTargetJpeg(): CapturedTargetImage {
        val imageCapture = imageCaptureRef.get()
            ?: throw IllegalStateException("Camera capture is not ready yet.")

        return suspendCancellableCoroutine { continuation ->
            imageCapture.takePicture(
                captureExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            val sourceBitmap = image.decodeCapturedBitmap()
                            val roi = TargetSampling.imageRoiForTarget(
                                imageWidth = image.width,
                                imageHeight = image.height,
                                viewportSize = currentViewportSize(),
                                targetFrameSpec = targetFrameSpec
                            ).clampTo(
                                imageWidth = sourceBitmap.width,
                                imageHeight = sourceBitmap.height
                            )

                            val croppedBitmap = Bitmap.createBitmap(
                                sourceBitmap,
                                roi.left,
                                roi.top,
                                roi.width,
                                roi.height
                            )
                            val rotatedBitmap = croppedBitmap.rotateIfNeeded(
                                image.imageInfo.rotationDegrees
                            )
                            val sampledColor = rotatedBitmap.averageRgb()
                            val jpegBytes = rotatedBitmap.toJpegByteArray()
                            val widthPx = rotatedBitmap.width
                            val heightPx = rotatedBitmap.height

                            rotatedBitmap.recycle()
                            if (rotatedBitmap !== croppedBitmap) {
                                croppedBitmap.recycle()
                            }
                            sourceBitmap.recycle()

                            continuation.resume(
                                CapturedTargetImage(
                                    jpegBytes = jpegBytes,
                                    sampledColor = sampledColor,
                                    capturedAtUnixMs = System.currentTimeMillis(),
                                    widthPx = widthPx,
                                    heightPx = heightPx
                                )
                            )
                        } catch (throwable: Throwable) {
                            continuation.resumeWithException(throwable)
                        } finally {
                            image.close()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        continuation.resumeWithException(exception)
                    }
                }
            )
        }
    }
}

private fun ImageProxy.decodeCapturedBitmap(): Bitmap {
    return when (format) {
        ImageFormat.JPEG -> {
            val jpegBytes = planes[0].buffer.toByteArray()
            BitmapFactory.decodeByteArray(
                jpegBytes,
                0,
                jpegBytes.size
            ) ?: error("Unable to decode JPEG capture.")
        }

        ImageFormat.YUV_420_888 -> {
            val nv21 = toNv21ByteArray()
            val jpegStream = ByteArrayOutputStream()
            YuvImage(nv21, ImageFormat.NV21, width, height, null).compressToJpeg(
                Rect(0, 0, width, height),
                100,
                jpegStream
            )
            val jpegBytes = jpegStream.toByteArray()
            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                ?: error("Unable to decode YUV capture.")
        }

        else -> error("Unsupported image format for capture: $format")
    }
}

private fun ImageProxy.toNv21ByteArray(): ByteArray {
    val yPlane = planes[0].toDenseByteArray(width, height)
    val uPlane = planes[1].toDenseByteArray(width / 2, height / 2)
    val vPlane = planes[2].toDenseByteArray(width / 2, height / 2)
    val nv21 = ByteArray(yPlane.size + uPlane.size + vPlane.size)

    System.arraycopy(yPlane, 0, nv21, 0, yPlane.size)
    var outputIndex = yPlane.size
    for (index in vPlane.indices) {
        nv21[outputIndex++] = vPlane[index]
        nv21[outputIndex++] = uPlane[index]
    }
    return nv21
}

private fun ImageProxy.PlaneProxy.toDenseByteArray(
    planeWidth: Int,
    planeHeight: Int
): ByteArray {
    val output = ByteArray(planeWidth * planeHeight)
    val source = buffer.duplicate().apply { rewind() }
    val rowData = ByteArray(rowStride)
    var outputIndex = 0

    for (row in 0 until planeHeight) {
        val rowStart = row * rowStride
        source.position(rowStart.coerceAtMost(source.limit()))
        val rowLength = minOf(rowStride, source.remaining())
        source.get(rowData, 0, rowLength)

        var column = 0
        while (column < planeWidth && (column * pixelStride) < rowLength) {
            output[outputIndex++] = rowData[column * pixelStride]
            column++
        }
    }

    return output
}

private fun ByteBuffer.toByteArray(): ByteArray {
    val duplicate = duplicate().apply { rewind() }
    return ByteArray(duplicate.remaining()).also { duplicate.get(it) }
}

private fun Bitmap.rotateIfNeeded(rotationDegrees: Int): Bitmap {
    if (rotationDegrees % 360 == 0) {
        return this
    }

    val matrix = Matrix().apply {
        postRotate(rotationDegrees.toFloat())
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

private fun Bitmap.toJpegByteArray(
    quality: Int = 92
): ByteArray {
    val stream = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, quality, stream)
    return stream.toByteArray()
}

private fun Bitmap.averageRgb(
    sampleStep: Int = 4
): RgbColor {
    var rSum = 0L
    var gSum = 0L
    var bSum = 0L
    var count = 0L

    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val pixel = getPixel(x, y)
            rSum += android.graphics.Color.red(pixel)
            gSum += android.graphics.Color.green(pixel)
            bSum += android.graphics.Color.blue(pixel)
            count++
            x += sampleStep
        }
        y += sampleStep
    }

    return if (count == 0L) {
        RgbColor(255, 255, 255)
    } else {
        RgbColor(
            r = (rSum / count).toInt(),
            g = (gSum / count).toInt(),
            b = (bSum / count).toInt()
        )
    }
}

private fun ImageRoi.clampTo(
    imageWidth: Int,
    imageHeight: Int
): ImageRoi {
    val safeLeft = left.coerceIn(0, imageWidth - 1)
    val safeTop = top.coerceIn(0, imageHeight - 1)
    val safeRight = right.coerceIn(safeLeft + 1, imageWidth)
    val safeBottom = bottom.coerceIn(safeTop + 1, imageHeight)
    return copy(
        left = safeLeft,
        top = safeTop,
        right = safeRight,
        bottom = safeBottom
    )
}
