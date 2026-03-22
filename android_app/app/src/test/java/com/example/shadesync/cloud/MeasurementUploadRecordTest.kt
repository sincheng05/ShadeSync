package com.example.shadesync.cloud

import com.example.shadesync.feature.shadematch.domain.RgbColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementUploadRecordTest {

    @Test
    fun toSheetRow_includesExpectedColumnsAndFormatting() {
        val record = MeasurementUploadRecord.fromCapturedSample(
            capturedAtUnixMs = 1_710_000_000_123L,
            rgb = RgbColor(120, 110, 100),
            imageWidthPx = 320,
            imageHeightPx = 180,
            bestMatchShadeCode = "A2",
            bestMatchRgbDistanceSquared = 42
        )

        val row = record.toSheetRow(
            imageFileId = "drive-file-123",
            imageWebLink = "https://drive.google.com/file/d/drive-file-123/view"
        )

        assertEquals(MeasurementUploadRecord.headers.size, row.size)
        assertEquals(1_710_000_000_123L, row[0])
        assertEquals(1_710_000_000L, row[1])
        assertEquals(record.capturedAtIsoUtc, row[2])
        assertEquals("#786E64", row[3])
        assertEquals(120, row[4])
        assertEquals(110, row[5])
        assertEquals(100, row[6])
        assertTrue((row[7] as String).contains("."))
        assertTrue((row[8] as String).contains("."))
        assertTrue((row[9] as String).contains("."))
        assertEquals("A2", row[10])
        assertEquals(42, row[11])
        assertEquals(320, row[12])
        assertEquals(180, row[13])
        assertEquals("drive-file-123", row[14])
        assertEquals("https://drive.google.com/file/d/drive-file-123/view", row[15])
    }

    @Test
    fun toSheetRow_leavesBestMatchCellsBlank_whenCalibrationIsUnavailable() {
        val record = MeasurementUploadRecord.fromCapturedSample(
            capturedAtUnixMs = 1_710_000_000_123L,
            rgb = RgbColor(120, 110, 100),
            imageWidthPx = 320,
            imageHeightPx = 180
        )

        val row = record.toSheetRow(
            imageFileId = "drive-file-123",
            imageWebLink = "https://drive.google.com/file/d/drive-file-123/view"
        )

        assertEquals("", row[10])
        assertEquals("", row[11])
    }
}
