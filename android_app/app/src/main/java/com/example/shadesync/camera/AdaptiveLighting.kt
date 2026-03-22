package com.example.shadesync.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.Camera
import com.example.shadesync.feature.shadematch.domain.RgbColor
import kotlin.math.roundToInt

internal enum class LightingAssistMode {
    OFF,
    AUTO,
    MANUAL
}

internal enum class TorchLevel(val displayName: String) {
    OFF("Off"),
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High")
}

internal data class LightingAssistState(
    val sceneLuminance: Double,
    val autoTorchTarget: TorchLevel
) {
    val scenePercent: Int
        get() = (sceneLuminance * 100).roundToInt()

    fun sceneDescription(): String {
        return when {
            sceneLuminance < 0.20 -> "Very dark"
            sceneLuminance < 0.36 -> "Dark"
            sceneLuminance < 0.55 -> "Dim"
            else -> "Well lit"
        }
    }
}

internal data class TorchRequest(
    val mode: LightingAssistMode,
    val autoTorchTarget: TorchLevel = TorchLevel.OFF,
    val manualBrightnessPercent: Int = 0
)

internal data class TorchCapability(
    val isReady: Boolean = false,
    val hasFlashUnit: Boolean = false,
    val cameraId: String? = null,
    val maxStrengthLevel: Int = 1
) {
    val supportsStrengthControl: Boolean
        get() = hasFlashUnit &&
            cameraId != null &&
            maxStrengthLevel > 1 &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

internal object AdaptiveLightingPolicy {
    fun sceneLuminance(rgb: RgbColor): Double {
        val luminance = (0.2126 * rgb.r) + (0.7152 * rgb.g) + (0.0722 * rgb.b)
        return (luminance / 255.0).coerceIn(0.0, 1.0)
    }

    fun recommendTorchLevel(
        sceneLuminance: Double,
        previousTorchLevel: TorchLevel
    ): TorchLevel {
        val luminance = sceneLuminance.coerceIn(0.0, 1.0)

        return when (previousTorchLevel) {
            TorchLevel.OFF -> {
                when {
                    luminance < 0.18 -> TorchLevel.HIGH
                    luminance < 0.34 -> TorchLevel.MEDIUM
                    luminance < 0.52 -> TorchLevel.LOW
                    else -> TorchLevel.OFF
                }
            }

            TorchLevel.LOW -> {
                when {
                    luminance < 0.28 -> TorchLevel.MEDIUM
                    luminance > 0.62 -> TorchLevel.OFF
                    else -> TorchLevel.LOW
                }
            }

            TorchLevel.MEDIUM -> {
                when {
                    luminance < 0.16 -> TorchLevel.HIGH
                    luminance > 0.44 -> TorchLevel.LOW
                    else -> TorchLevel.MEDIUM
                }
            }

            TorchLevel.HIGH -> {
                when {
                    luminance > 0.26 -> TorchLevel.MEDIUM
                    else -> TorchLevel.HIGH
                }
            }
        }
    }

    fun strengthFor(
        torchLevel: TorchLevel,
        maxStrengthLevel: Int
    ): Int? {
        val safeMaxStrength = maxStrengthLevel.coerceAtLeast(1)

        return when (torchLevel) {
            TorchLevel.OFF -> null
            TorchLevel.LOW -> (safeMaxStrength * 0.35).roundToInt().coerceAtLeast(1)
            TorchLevel.MEDIUM -> (safeMaxStrength * 0.65).roundToInt().coerceAtLeast(1)
            TorchLevel.HIGH -> safeMaxStrength
        }
    }

    fun manualStrengthForPercent(
        brightnessPercent: Int,
        maxStrengthLevel: Int
    ): Int? {
        val normalizedPercent = brightnessPercent.coerceIn(0, 100)
        if (normalizedPercent == 0) {
            return null
        }

        val safeMaxStrength = maxStrengthLevel.coerceAtLeast(1)
        return ((normalizedPercent / 100.0) * safeMaxStrength)
            .roundToInt()
            .coerceAtLeast(1)
    }
}

internal class AdaptiveTorchController(context: Context) {
    private val cameraManager = context.getSystemService(CameraManager::class.java)

    fun inspect(camera: Camera): TorchCapability {
        val hasFlashUnit = camera.cameraInfo.hasFlashUnit()
        if (!hasFlashUnit) {
            return TorchCapability(isReady = true, hasFlashUnit = false)
        }

        val cameraId = runCatching {
            Camera2CameraInfo.from(camera.cameraInfo).cameraId
        }.getOrNull()

        val maxStrengthLevel = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            cameraId != null
        ) {
            runCatching {
                cameraManager
                    .getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)
                    ?: 1
            }.getOrDefault(1)
        } else {
            1
        }

        return TorchCapability(
            isReady = true,
            hasFlashUnit = true,
            cameraId = cameraId,
            maxStrengthLevel = maxStrengthLevel.coerceAtLeast(1)
        )
    }

    fun requestTorch(
        camera: Camera,
        capability: TorchCapability,
        request: TorchRequest
    ) {
        if (!capability.hasFlashUnit) {
            return
        }

        when (request.mode) {
            LightingAssistMode.OFF -> {
                requestTorchOff(camera, capability)
            }

            LightingAssistMode.AUTO -> {
                requestAutoTorch(
                    camera = camera,
                    capability = capability,
                    torchLevel = request.autoTorchTarget
                )
            }

            LightingAssistMode.MANUAL -> {
                requestManualTorch(
                    camera = camera,
                    capability = capability,
                    manualBrightnessPercent = request.manualBrightnessPercent
                )
            }
        }
    }

    private fun requestAutoTorch(
        camera: Camera,
        capability: TorchCapability,
        torchLevel: TorchLevel
    ) {
        if (torchLevel == TorchLevel.OFF) {
            requestTorchOff(camera, capability)
            return
        }

        if (capability.supportsStrengthControl && capability.cameraId != null) {
            val strength = AdaptiveLightingPolicy.strengthFor(torchLevel, capability.maxStrengthLevel)
            if (strength != null) {
                runCatching {
                    cameraManager.turnOnTorchWithStrengthLevel(capability.cameraId, strength)
                }.getOrElse {
                    camera.cameraControl.enableTorch(true)
                }
                return
            }
        }

        camera.cameraControl.enableTorch(true)
    }

    private fun requestManualTorch(
        camera: Camera,
        capability: TorchCapability,
        manualBrightnessPercent: Int
    ) {
        val normalizedPercent = manualBrightnessPercent.coerceIn(0, 100)
        if (normalizedPercent == 0) {
            requestTorchOff(camera, capability)
            return
        }

        if (capability.supportsStrengthControl && capability.cameraId != null) {
            val strength = AdaptiveLightingPolicy.manualStrengthForPercent(
                brightnessPercent = normalizedPercent,
                maxStrengthLevel = capability.maxStrengthLevel
            )
            if (strength != null) {
                runCatching {
                    cameraManager.turnOnTorchWithStrengthLevel(capability.cameraId, strength)
                }.getOrElse {
                    camera.cameraControl.enableTorch(true)
                }
                return
            }
        }

        camera.cameraControl.enableTorch(true)
    }

    fun requestTorchOff(camera: Camera, capability: TorchCapability) {
        if (!capability.hasFlashUnit) {
            return
        }

        if (capability.supportsStrengthControl && capability.cameraId != null) {
            runCatching {
                cameraManager.setTorchMode(capability.cameraId, false)
            }.getOrElse {
                camera.cameraControl.enableTorch(false)
            }
            return
        }

        camera.cameraControl.enableTorch(false)
    }
}
