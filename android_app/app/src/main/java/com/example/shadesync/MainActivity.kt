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
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.shadesync.ui.theme.ShadeSyncTheme
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.pow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShadeSyncTheme {
                ShadeSyncApp()
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
                "Calibrate Vita 16 shades, then match patient tooth color in real time (250 ms updates).",
                style = MaterialTheme.typography.bodyMedium
            )

            if (!permissionGranted) {
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant camera permission")
                }
            } else {
                CameraPreviewWithAnalysis(onColorSampled = { sampledColor ->
                    liveColor = sampledColor
                })
            }

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
private fun CameraPreviewWithAnalysis(onColorSampled: (Int) -> Unit) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        factory = { viewContext ->
            val previewView = PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(viewContext)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(Size(1280, 720))
                    .build()

                analysis.setAnalyzer(executor, CenterColorAnalyzer(onColorSampled))

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                } catch (_: Exception) {
                }
            }, ContextCompat.getMainExecutor(viewContext))

            previewView
        }
    )
}

private class CenterColorAnalyzer(
    private val onColorSampled: (Int) -> Unit
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

        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0L

        val startX = width / 3
        val endX = width * 2 / 3
        val startY = height / 3
        val endY = height * 2 / 3

        var y = startY
        while (y < endY) {
            var x = startX
            while (x < endX) {
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

                x += 8
            }
            y += 8
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
            Text("Live sampled color: ${toHex(liveColor)}")
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
                        if (savedColor != null) "${shade.code}: ${toHex(savedColor)}"
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
                    Text("Current color: ${toHex(liveColor)}")
                    Text("Compared against ${calibrations.size} calibrated shades.")
                } else {
                    Text("Unable to match shade.")
                }
            }
        }
    }
}

private fun nearestShade(liveColor: Int, calibrations: Map<VitaShade, Int>): VitaShade? {
    return calibrations.minByOrNull { (_, refColor) ->
        colorDistance(liveColor, refColor)
    }?.key
}

private fun colorDistance(colorA: Int, colorB: Int): Double {
    val rA = Color.red(colorA)
    val gA = Color.green(colorA)
    val bA = Color.blue(colorA)
    val rB = Color.red(colorB)
    val gB = Color.green(colorB)
    val bB = Color.blue(colorB)

    return (rA - rB).toDouble().pow(2.0) +
        (gA - gB).toDouble().pow(2.0) +
        (bA - bB).toDouble().pow(2.0)
}

private fun toHex(color: Int): String {
    return String.format(Locale.US, "#%02X%02X%02X", Color.red(color), Color.green(color), Color.blue(color))
}
