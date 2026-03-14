package com.example.shadesync.feature.shadematch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.shadesync.feature.shadematch.data.Vita16Repository
import com.example.shadesync.feature.shadematch.domain.ColorMath
import com.example.shadesync.feature.shadematch.domain.LabColor
import com.example.shadesync.feature.shadematch.domain.ObservationRecord
import com.example.shadesync.feature.shadematch.domain.RgbColor
import com.example.shadesync.feature.shadematch.domain.ShadeMatch
import com.example.shadesync.feature.shadematch.domain.SurfaceFeature
import com.example.shadesync.feature.shadematch.domain.WhiteBalanceGain
import com.example.shadesync.ui.theme.ShadeSyncTheme
import java.util.Locale

private enum class AppTab(val label: String, val shortLabel: String) {
    MATCH("Match", "M"),
    COMPARE("Compare", "C"),
    RECORDS("Records", "R"),
    ABOUT("About", "A")
}

@Composable
fun ShadeSyncApp() {
    var currentTab by remember { mutableStateOf(AppTab.MATCH) }
    val tabs = remember { AppTab.entries }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("ShadeSync") })
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Text(tab.shortLabel) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentTab) {
                AppTab.MATCH -> ShadeMatchScreen()
                AppTab.COMPARE -> ComparisonScreen()
                AppTab.RECORDS -> RecordsScreen()
                AppTab.ABOUT -> AboutScreen()
            }
        }
    }
}

@Composable
private fun ShadeMatchScreen() {
    val shades = remember { Vita16Repository.shades }
    val isPlaceholderDataset = remember { shades.any { it.isPlaceholder } }
    var rgb by remember { mutableStateOf(RgbColor(185, 170, 155)) }
    var precisionAssist by remember { mutableStateOf(false) }

    val whiteBalance = if (precisionAssist) {
        WhiteBalanceGain(1.05, 1.0, 0.95)
    } else {
        WhiteBalanceGain.Neutral
    }

    val lab by remember(rgb, precisionAssist) {
        derivedStateOf { ColorMath.rgbToLab(rgb, whiteBalance) }
    }

    val matches by remember(lab) {
        derivedStateOf {
            shades
                .map { ShadeMatch(it, ColorMath.deltaE76(lab, it.lab)) }
                .sortedBy { it.deltaE }
                .take(3)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(title = "Input") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        RgbSliders(label = "RGB sample", rgb = rgb) { rgb = it }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Precision Assist (white balance)")
                            Switch(checked = precisionAssist, onCheckedChange = { precisionAssist = it })
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    ColorSwatch(rgb = rgb)
                }
                Spacer(modifier = Modifier.height(12.dp))
                KeyValueRow("LAB", formatLab(lab))
            }
        }
        item {
            SectionCard(title = "Top matches") {
                if (isPlaceholderDataset) {
                    Text(
                        text = "VITA 16 reference values are placeholders. Replace with calibrated data to get real matches.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                matches.forEach { match ->
                    MatchRow(match)
                }
            }
        }
        item {
            SectionCard(title = "Workflow") {
                Text("1. Capture with a calibrated reference card.")
                Text("2. Select ROI and compute average color.")
                Text("3. Convert RGB to LAB and compare Delta E.")
                Text("4. Store de-identified observation for analysis.")
            }
        }
    }
}

@Composable
private fun ComparisonScreen() {
    var left by remember { mutableStateOf(RgbColor(180, 165, 150)) }
    var right by remember { mutableStateOf(RgbColor(200, 180, 165)) }

    val leftLab by remember(left) { derivedStateOf { ColorMath.rgbToLab(left) } }
    val rightLab by remember(right) { derivedStateOf { ColorMath.rgbToLab(right) } }
    val deltaE by remember(leftLab, rightLab) { derivedStateOf { ColorMath.deltaE76(leftLab, rightLab) } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(title = "Left sample") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        RgbSliders(label = "RGB", rgb = left) { left = it }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    ColorSwatch(rgb = left)
                }
                Spacer(modifier = Modifier.height(8.dp))
                KeyValueRow("LAB", formatLab(leftLab))
            }
        }
        item {
            SectionCard(title = "Right sample") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        RgbSliders(label = "RGB", rgb = right) { right = it }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    ColorSwatch(rgb = right)
                }
                Spacer(modifier = Modifier.height(8.dp))
                KeyValueRow("LAB", formatLab(rightLab))
            }
        }
        item {
            SectionCard(title = "Delta E (CIE76)") {
                Text(text = String.format(Locale.US, "%.2f", deltaE), style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Composable
private fun RecordsScreen() {
    val records = remember { sampleRecords() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(title = "De-identified record format") {
                Text("Fields: shade, LAB, Delta E, surface features, device, calibration, region, timestamp.")
                Text("Optional: age band, verified flag, child flag, notes.")
            }
        }
        if (records.isEmpty()) {
            item {
                SectionCard(title = "Records") {
                    Text("No records yet. Capture a calibrated sample to create a record.")
                }
            }
        } else {
            items(records) { record ->
                RecordCard(record)
            }
        }
    }
}

@Composable
private fun AboutScreen() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionCard(title = "Vision") {
                Text("Build a privacy-preserving dental shade network using calibrated smartphones.")
                Text("Each device becomes a low-cost, non-invasive sensor for regional oral health signals.")
            }
        }
        item {
            SectionCard(title = "Core principles") {
                Text("- Standardization through VITA 16 calibration.")
                Text("- De-identification by default, minimum necessary metadata.")
                Text("- Local governance and context-aware interpretation.")
                Text("- Scientific rigor, risk signals only, not diagnosis.")
                Text("- Open-source and extensible architecture.")
            }
        }
        item {
            SectionCard(title = "Public health hypotheses") {
                Text("- Water quality risk signals from enamel anomalies.")
                Text("- Nutrition and development signals from discoloration and opacity.")
                Text("- Lifestyle exposure patterns from staining and erosion.")
                Text("- Access-to-care inequity from regional variance over time.")
            }
        }
        item {
            SectionCard(title = "Ethics and limits") {
                Text("This system is not a medical diagnosis tool.")
                Text("Use signals as hypotheses that require clinical or environmental validation.")
                Text("Respect consent, privacy, and regional governance in any deployment.")
            }
        }
    }
}

@Composable
private fun MatchRow(match: ShadeMatch) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = match.swatch.id, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(text = String.format(Locale.US, "Delta E %.2f", match.deltaE))
    }
}

@Composable
private fun RecordCard(record: ObservationRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Record ${record.id}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            KeyValueRow("Shade", record.shadeId)
            KeyValueRow("LAB", formatLab(record.lab))
            KeyValueRow("Delta E", String.format(Locale.US, "%.2f", record.deltaE))
            KeyValueRow("Region", record.regionCode)
            KeyValueRow("Captured", record.capturedAtIso)
            KeyValueRow("Verified", if (record.isVerified) "Yes" else "No")
            KeyValueRow("Child", if (record.isChild) "Yes" else "No")
            if (record.features.isNotEmpty()) {
                Text(
                    text = "Features: ${record.features.joinToString()}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            record.notes?.let { notes ->
                Text(text = "Notes: $notes", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun sampleRecords(): List<ObservationRecord> {
    return listOf(
        ObservationRecord(
            id = "001",
            shadeId = "A2",
            deltaE = 1.2,
            lab = LabColor(78.3, -1.1, 14.5),
            features = setOf(SurfaceFeature.WHITE_SPOT),
            deviceModel = "SampleDevice",
            calibrationVersion = "0.1",
            regionCode = "TW-TEST",
            capturedAtIso = "2026-03-08",
            isVerified = false,
            isChild = true,
            notes = "Demo record"
        ),
        ObservationRecord(
            id = "002",
            shadeId = "B1",
            deltaE = 2.4,
            lab = LabColor(82.1, -0.4, 11.8),
            features = emptySet(),
            deviceModel = "SampleDevice",
            calibrationVersion = "0.1",
            regionCode = "TW-TEST",
            capturedAtIso = "2026-03-08",
            isVerified = false,
            isChild = false,
            notes = null
        )
    )
}

private fun formatLab(lab: LabColor): String {
    return String.format(Locale.US, "L %.1f  a %.1f  b %.1f", lab.l, lab.a, lab.b)
}

@Preview(showBackground = true)
@Composable
private fun ShadeSyncAppPreview() {
    ShadeSyncTheme {
        ShadeSyncApp()
    }
}
