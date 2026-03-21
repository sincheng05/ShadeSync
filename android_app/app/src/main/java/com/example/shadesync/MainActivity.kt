package com.example.shadesync

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as CanvasSize
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.shadesync.camera.TargetFrameSpec
import com.example.shadesync.camera.AdaptiveLightingPolicy
import com.example.shadesync.camera.AdaptiveTorchController
import com.example.shadesync.camera.LightingAssistMode
import com.example.shadesync.camera.LightingAssistState
import com.example.shadesync.camera.TargetSampling
import com.example.shadesync.camera.TorchCapability
import com.example.shadesync.camera.TorchLevel
import com.example.shadesync.camera.ViewportSize
import com.example.shadesync.feature.shadematch.domain.RgbColor
import com.example.shadesync.feature.shadematch.domain.RgbDistanceMatcher
import com.example.shadesync.ui.SplashScreen
import com.example.shadesync.ui.theme.ShadeSyncTheme
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShadeSyncTheme {
                var splashFinished by rememberSaveable { mutableStateOf(false) }

                Crossfade(
                    targetState = splashFinished,
                    label = "shade-sync-launch-transition"
                ) { isReadyForMainContent ->
                    if (isReadyForMainContent) {
                        ShadeSyncApp()
                    } else {
                        SplashScreen(
                            onSplashFinished = { splashFinished = true }
                        )
                    }
                }
            }
        }
    }
}

private enum class AppMode { CALIBRATION, MEASUREMENT }

private enum class VitaShade(val code: String) {
    A1("A1"),
    A2("A2"),
    A3("A3"),
    A35("A3.5"),
    A4("A4"),
    B1("B1"),
    B2("B2"),
    B3("B3"),
    B4("B4"),
    C1("C1"),
    C2("C2"),
    C3("C3"),
    C4("C4"),
    D2("D2"),
    D3("D3"),
    D4("D4")
}

private class CalibrationStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("shade_calibration", Context.MODE_PRIVATE)

    fun save(shade: VitaShade, color: Int) {
        prefs.edit().putInt(shade.name, color).apply()
    }

    fun loadAll(): Map<VitaShade, Int> {
        return VitaShade.entries.mapNotNull { shade ->
            if (prefs.contains(shade.name)) shade to prefs.getInt(shade.name, 0) else null
        }.toMap()
    }
}

@Composable
private fun ShadeSyncApp() {
    val context = LocalContext.current
    val calibrationStore = remember { CalibrationStore(context) }
    val calibrations = remember { mutableStateMapOf<VitaShade, Int>() }
    var liveColor by remember { mutableIntStateOf(Color.WHITE) }
    var mode by remember { mutableStateOf(AppMode.CALIBRATION) }
    var selectedShade by remember { mutableStateOf(VitaShade.A1) }
    var lightingMode by rememberSaveable { mutableStateOf(LightingAssistMode.AUTO) }
    var autoTorchLevel by remember { mutableStateOf(TorchLevel.OFF) }
    var torchCapability by remember { mutableStateOf(TorchCapability()) }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
    }

    LaunchedEffect(Unit) {
        calibrationStore.loadAll().forEach { (shade, color) ->
            calibrations[shade] = color
        }
    }

    val liveRgb = liveColor.toRgbColor()
    val sceneLuminance = AdaptiveLightingPolicy.sceneLuminance(liveRgb)

    LaunchedEffect(lightingMode, sceneLuminance) {
        autoTorchLevel = if (lightingMode == LightingAssistMode.AUTO) {
            AdaptiveLightingPolicy.recommendTorchLevel(
                sceneLuminance = sceneLuminance,
                previousTorchLevel = autoTorchLevel
            )
        } else {
            TorchLevel.OFF
        }
    }

    val requestedTorchLevel = if (lightingMode == LightingAssistMode.AUTO) {
        autoTorchLevel
    } else {
        TorchLevel.OFF
    }
    val lightingAssistState = LightingAssistState(
        sceneLuminance = sceneLuminance,
        torchTarget = requestedTorchLevel
    )

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("ShadeSync", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Align one Vita tab or tooth inside the target frame. ShadeSync samples only that guided window every 250 ms.",
                style = MaterialTheme.typography.bodyMedium
            )

            if (!permissionGranted) {
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant camera permission")
                }
            } else {
                CameraPreviewWithAnalysis(
                    onColorSampled = { sampledColor ->
                        liveColor = sampledColor
                    },
                    requestedTorchLevel = requestedTorchLevel,
                    onTorchCapabilityChanged = { torchCapability = it }
                )
            }

            LightingAssistCard(
                lightingMode = lightingMode,
                onLightingModeChanged = { lightingMode = it },
                lightingAssistState = lightingAssistState,
                torchCapability = torchCapability,
                permissionGranted = permissionGranted
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { mode = AppMode.CALIBRATION }) {
                    Text("Calibration")
                }
                OutlinedButton(onClick = { mode = AppMode.MEASUREMENT }) {
                    Text("Measurement")
                }
            }

            LiveColorCard(liveColor)

            when (mode) {
                AppMode.CALIBRATION -> CalibrationPanel(
                    selectedShade = selectedShade,
                    onSelectedShade = { selectedShade = it },
                    onSave = {
                        calibrationStore.save(selectedShade, liveColor)
                        calibrations[selectedShade] = liveColor
                    },
                    calibrations = calibrations
                )

                AppMode.MEASUREMENT -> MeasurementPanel(
                    liveColor = liveColor,
                    calibrations = calibrations
                )
            }
        }
    }
}

@Composable
private fun CameraPreviewWithAnalysis(
    onColorSampled: (Int) -> Unit,
    requestedTorchLevel: TorchLevel,
    onTorchCapabilityChanged: (TorchCapability) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val targetFrameSpec = remember { TargetSampling.DefaultToothTarget }
    val previewViewport = remember { AtomicReference(ViewportSize.Unspecified) }
    val torchController = remember(context) { AdaptiveTorchController(context) }
    var boundCamera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var torchCapability by remember { mutableStateOf(TorchCapability()) }
    var lastRequestedTorchLevel by remember { mutableStateOf<TorchLevel?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            val camera = boundCamera
            if (camera != null) {
                torchController.requestTorchOff(camera, torchCapability)
            }
            onTorchCapabilityChanged(TorchCapability())
            executor.shutdown()
        }
    }

    LaunchedEffect(boundCamera, torchCapability, requestedTorchLevel) {
        val camera = boundCamera ?: return@LaunchedEffect
        if (!torchCapability.isReady || !torchCapability.hasFlashUnit) {
            return@LaunchedEffect
        }
        if (lastRequestedTorchLevel == requestedTorchLevel) {
            return@LaunchedEffect
        }

        torchController.requestTorchLevel(
            camera = camera,
            capability = torchCapability,
            torchLevel = requestedTorchLevel
        )
        lastRequestedTorchLevel = requestedTorchLevel
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(28.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(28.dp)
            )
            .onSizeChanged { previewViewport.set(ViewportSize(it.width, it.height)) }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                val previewView = PreviewView(viewContext).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(viewContext)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetResolution(Size(1280, 720))
                        .build()

                    analysis.setAnalyzer(
                        executor,
                        TargetColorAnalyzer(
                            onColorSampled = onColorSampled,
                            targetFrameSpec = targetFrameSpec,
                            viewportSizeProvider = { previewViewport.get() }
                        )
                    )

                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                        val capability = torchController.inspect(camera)
                        boundCamera = camera
                        torchCapability = capability
                        lastRequestedTorchLevel = null
                        onTorchCapabilityChanged(capability)
                    } catch (_: Exception) {
                    }
                }, ContextCompat.getMainExecutor(viewContext))

                previewView
            }
        )
        ToothTargetOverlay(
            modifier = Modifier.fillMaxSize(),
            targetFrameSpec = targetFrameSpec
        )
    }
}

@Composable
private fun LightingAssistCard(
    lightingMode: LightingAssistMode,
    onLightingModeChanged: (LightingAssistMode) -> Unit,
    lightingAssistState: LightingAssistState,
    torchCapability: TorchCapability,
    permissionGranted: Boolean
) {
    val requestedStrength = if (torchCapability.supportsStrengthControl) {
        AdaptiveLightingPolicy.strengthFor(
            torchLevel = lightingAssistState.torchTarget,
            maxStrengthLevel = torchCapability.maxStrengthLevel
        )
    } else {
        null
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Adaptive illumination", style = MaterialTheme.typography.titleMedium)
            Text(
                "ShadeSync can use the rear torch to stabilize sampling when the guided target frame is too dark.",
                style = MaterialTheme.typography.bodySmall
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (lightingMode == LightingAssistMode.OFF) {
                    Button(onClick = { onLightingModeChanged(LightingAssistMode.OFF) }) {
                        Text("Lighting off")
                    }
                } else {
                    OutlinedButton(onClick = { onLightingModeChanged(LightingAssistMode.OFF) }) {
                        Text("Lighting off")
                    }
                }

                if (lightingMode == LightingAssistMode.AUTO) {
                    Button(onClick = { onLightingModeChanged(LightingAssistMode.AUTO) }) {
                        Text("Auto lighting")
                    }
                } else {
                    OutlinedButton(onClick = { onLightingModeChanged(LightingAssistMode.AUTO) }) {
                        Text("Auto lighting")
                    }
                }
            }

            val luminanceStatus = if (permissionGranted) {
                "Scene luminance in target frame: ${lightingAssistState.scenePercent}% (${lightingAssistState.sceneDescription()})"
            } else {
                "Scene luminance in target frame will appear after camera permission is granted."
            }

            Text(luminanceStatus, style = MaterialTheme.typography.bodySmall)

            val lightingStatus = when {
                !permissionGranted -> {
                    "Camera permission is required before the rear torch can be inspected."
                }

                !torchCapability.isReady -> {
                    "Preparing rear camera illumination controls..."
                }

                !torchCapability.hasFlashUnit -> {
                    "This device does not expose a rear flash unit. Brightness guidance remains available."
                }

                lightingMode == LightingAssistMode.OFF -> {
                    "Torch target: Off."
                }

                requestedStrength != null -> {
                    "Torch target: ${lightingAssistState.torchTarget.displayName} ($requestedStrength/${torchCapability.maxStrengthLevel})."
                }

                lightingAssistState.torchTarget == TorchLevel.OFF -> {
                    "Torch target: Off."
                }

                else -> {
                    "Torch target: On. This device falls back to binary torch control."
                }
            }

            Text(lightingStatus, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BoxScope.ToothTargetOverlay(
    modifier: Modifier = Modifier,
    targetFrameSpec: TargetFrameSpec
) {
    androidx.compose.foundation.Canvas(
        modifier = modifier
    ) {
        val frameWidth = size.width * targetFrameSpec.widthFraction
        val frameHeight = size.height * targetFrameSpec.heightFraction
        val frameLeft = size.width * targetFrameSpec.centerXFraction - frameWidth / 2f
        val frameTop = size.height * targetFrameSpec.centerYFraction - frameHeight / 2f
        val cornerRadius = CornerRadius(x = frameWidth * 0.28f, y = frameWidth * 0.28f)
        val shadowColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.24f)
        val strokeColor = androidx.compose.ui.graphics.Color(0xFFFFD18A)
        val guideColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.92f)
        val glowStroke = 10.dp.toPx()
        val outlineStroke = 3.dp.toPx()
        val guideStroke = 4.dp.toPx()
        val cornerLength = minOf(frameWidth, frameHeight) * 0.24f
        val frameRight = frameLeft + frameWidth
        val frameBottom = frameTop + frameHeight

        drawRect(
            color = shadowColor,
            topLeft = Offset.Zero,
            size = CanvasSize(width = size.width, height = frameTop.coerceAtLeast(0f))
        )
        drawRect(
            color = shadowColor,
            topLeft = Offset(0f, frameTop),
            size = CanvasSize(width = frameLeft.coerceAtLeast(0f), height = frameHeight)
        )
        drawRect(
            color = shadowColor,
            topLeft = Offset(frameRight, frameTop),
            size = CanvasSize(
                width = (size.width - frameRight).coerceAtLeast(0f),
                height = frameHeight
            )
        )
        drawRect(
            color = shadowColor,
            topLeft = Offset(0f, frameBottom),
            size = CanvasSize(
                width = size.width,
                height = (size.height - frameBottom).coerceAtLeast(0f)
            )
        )

        drawRoundRect(
            color = strokeColor.copy(alpha = 0.28f),
            topLeft = Offset(frameLeft, frameTop),
            size = CanvasSize(frameWidth, frameHeight),
            cornerRadius = cornerRadius,
            style = Stroke(width = glowStroke)
        )
        drawRoundRect(
            color = strokeColor,
            topLeft = Offset(frameLeft, frameTop),
            size = CanvasSize(frameWidth, frameHeight),
            cornerRadius = cornerRadius,
            style = Stroke(width = outlineStroke)
        )

        drawCornerGuide(
            start = Offset(frameLeft, frameTop + cornerLength),
            end = Offset(frameLeft, frameTop),
            color = guideColor,
            strokeWidth = guideStroke
        )
        drawCornerGuide(
            start = Offset(frameLeft, frameTop),
            end = Offset(frameLeft + cornerLength, frameTop),
            color = guideColor,
            strokeWidth = guideStroke
        )
        drawCornerGuide(
            start = Offset(frameRight - cornerLength, frameTop),
            end = Offset(frameRight, frameTop),
            color = guideColor,
            strokeWidth = guideStroke
        )
        drawCornerGuide(
            start = Offset(frameRight, frameTop),
            end = Offset(frameRight, frameTop + cornerLength),
            color = guideColor,
            strokeWidth = guideStroke
        )
        drawCornerGuide(
            start = Offset(frameLeft, frameBottom - cornerLength),
            end = Offset(frameLeft, frameBottom),
            color = guideColor,
            strokeWidth = guideStroke
        )
        drawCornerGuide(
            start = Offset(frameLeft, frameBottom),
            end = Offset(frameLeft + cornerLength, frameBottom),
            color = guideColor,
            strokeWidth = guideStroke
        )
        drawCornerGuide(
            start = Offset(frameRight - cornerLength, frameBottom),
            end = Offset(frameRight, frameBottom),
            color = guideColor,
            strokeWidth = guideStroke
        )
        drawCornerGuide(
            start = Offset(frameRight, frameBottom - cornerLength),
            end = Offset(frameRight, frameBottom),
            color = guideColor,
            strokeWidth = guideStroke
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCornerGuide(
    start: Offset,
    end: Offset,
    color: androidx.compose.ui.graphics.Color,
    strokeWidth: Float
) {
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = strokeWidth
    )
}

private class TargetColorAnalyzer(
    private val onColorSampled: (Int) -> Unit,
    private val targetFrameSpec: TargetFrameSpec,
    private val viewportSizeProvider: () -> ViewportSize
) : ImageAnalysis.Analyzer {
    private var lastTimestamp = 0L

    override fun analyze(image: androidx.camera.core.ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastTimestamp < 250) {
            image.close()
            return
        }
 
        val yPlane = image.planes[0].buffer
        val uPlane = image.planes[1].buffer
        val vPlane = image.planes[2].buffer
        val width = image.width
        val height = image.height

        val yRowStride = image.planes[0].rowStride
        val uRowStride = image.planes[1].rowStride
        val vRowStride = image.planes[2].rowStride
        val uPixelStride = image.planes[1].pixelStride
        val vPixelStride = image.planes[2].pixelStride

        val roi = TargetSampling.imageRoiForTarget(
            imageWidth = width,
            imageHeight = height,
            viewportSize = viewportSizeProvider(),
            targetFrameSpec = targetFrameSpec
        )

        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0L
        val sampleStep = 4

        var y = roi.top
        while (y < roi.bottom) {
            var x = roi.left
            while (x < roi.right) {
                val yIndex = y * yRowStride + x
                val uvX = x / 2
                val uvY = y / 2
                val uIndex = uvY * uRowStride + uvX * uPixelStride
                val vIndex = uvY * vRowStride + uvX * vPixelStride

                val yValue = yPlane.get(yIndex).toInt() and 0xFF
                val uValue = (uPlane.get(uIndex).toInt() and 0xFF) - 128
                val vValue = (vPlane.get(vIndex).toInt() and 0xFF) - 128

                val r = (yValue + 1.402f * vValue).toInt().coerceIn(0, 255)
                val g = (yValue - 0.344136f * uValue - 0.714136f * vValue).toInt().coerceIn(0, 255)
                val b = (yValue + 1.772f * uValue).toInt().coerceIn(0, 255)

                rSum += r
                gSum += g
                bSum += b
                count++

                x += sampleStep
            }
            y += sampleStep
        }

        if (count > 0) {
            val color = Color.rgb((rSum / count).toInt(), (gSum / count).toInt(), (bSum / count).toInt())
            lastTimestamp = now
            onColorSampled(color)
        }

        image.close()
    }
}

@Composable
private fun LiveColorCard(liveColor: Int) {
    val scientificReadout = liveColor.toRgbColor().toScientificColorReadout()

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(androidx.compose.ui.graphics.Color(liveColor))
            )
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Live sampled color: ${scientificReadout.hex}")
                Text(
                    text = scientificReadout.rgbDisplay(),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = scientificReadout.labDisplay(),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun CalibrationPanel(
    selectedShade: VitaShade,
    onSelectedShade: (VitaShade) -> Unit,
    onSave: () -> Unit,
    calibrations: Map<VitaShade, Int>
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Calibration (Vita 16)", style = MaterialTheme.typography.titleMedium)
            Button(onClick = { menuExpanded = true }) {
                Text("Selected shade: ${selectedShade.code}")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                VitaShade.entries.forEach { shade ->
                    DropdownMenuItem(
                        text = { Text(shade.code) },
                        onClick = {
                            onSelectedShade(shade)
                            menuExpanded = false
                        }
                    )
                }
            }
            Button(onClick = onSave) {
                Text("Save current sampled color to selected shade")
            }

            Text("Saved calibration entries: ${calibrations.size}/16")
            LazyColumn(modifier = Modifier.height(160.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(VitaShade.entries) { shade ->
                    val savedColor = calibrations[shade]
                    Text(
                        if (savedColor != null) "${shade.code}: ${savedColor.toRgbColor().toHexString()}"
                        else "${shade.code}: not calibrated"
                    )
                }
            }
        }
    }
}

@Composable
private fun MeasurementPanel(
    liveColor: Int,
    calibrations: Map<VitaShade, Int>
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Real-time shade estimation", style = MaterialTheme.typography.titleMedium)
            if (calibrations.isEmpty()) {
                Text("No calibration data yet. Please calibrate Vita 16 first.")
            } else {
                val bestMatch = nearestShade(liveColor, calibrations)
                if (bestMatch != null) {
                    Text("Best matched shade: ${bestMatch.code}")
                    Text("Current color: ${liveColor.toRgbColor().toHexString()}")
                    Text("Compared against ${calibrations.size} calibrated shades.")
                } else {
                    Text("Unable to match shade.")
                }
            }
        }
    }
}

private fun nearestShade(liveColor: Int, calibrations: Map<VitaShade, Int>): VitaShade? {
    return RgbDistanceMatcher.nearest(
        sample = liveColor.toRgbColor(),
        references = calibrations.mapValues { (_, refColor) -> refColor.toRgbColor() }
    )
}

private fun Int.toRgbColor(): RgbColor {
    return RgbColor(Color.red(this), Color.green(this), Color.blue(this))
}


