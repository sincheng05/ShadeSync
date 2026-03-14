package com.example.shadesync.feature.shadematch.data

import com.example.shadesync.feature.shadematch.domain.LabColor
import com.example.shadesync.feature.shadematch.domain.ShadeSwatch

object Vita16Repository {
    private const val PLACEHOLDER_SOURCE = "VITA 16 placeholder"
    private const val PLACEHOLDER_VERSION = "0.0"

    val shades: List<ShadeSwatch> = listOf(
        ShadeSwatch("A1", LabColor(0.0, 0.0, 0.0), source = PLACEHOLDER_SOURCE, version = PLACEHOLDER_VERSION, isPlaceholder = true),
        ShadeSwatch("A2", LabColor(0.0, 0.0, 0.0), source = PLACEHOLDER_SOURCE, version = PLACEHOLDER_VERSION, isPlaceholder = true),
        ShadeSwatch("A3", LabColor(0.0, 0.0, 0.0), source = PLACEHOLDER_SOURCE, version = PLACEHOLDER_VERSION, isPlaceholder = true),
        ShadeSwatch("A3.5", LabColor(0.0, 0.0, 0.0), source = PLACEHOLDER_SOURCE, version = PLACEHOLDER_VERSION, isPlaceholder = true),
        ShadeSwatch("A4", LabColor(0.0, 0.0, 0.0), source = PLACEHOLDER_SOURCE, version = PLACEHOLDER_VERSION, isPlaceholder = true),
        ShadeSwatch("B1", LabColor(0.0, 0.0, 0.0), source = PLACEHOLDER_SOURCE, version = PLACEHOLDER_VERSION, isPlaceholder = true),
        ShadeSwatch("B2", LabColor(0.0, 0.0, 0.0), source = PLACEHOLDER_SOURCE, version = PLACEHOLDER_VERSION, isPlaceholder = true),
        ShadeSwatch("B3", LabColor(0.0, 0.0, 0.0), source = PLACEHOLDER_SOURCE, version = PLACEHOLDER_VERSION, isPlaceholder = true),
        ShadeSwatch("B4", LabColor(0.0, 0.0, 0.0), source = PLACEHOLDER_SOURCE, version = PLACEHOLDER_VERSION, isPlaceholder = true),
        ShadeSwatch("C1", LabColor(0.0, 0.0, 0.0), source = PLACEHOLDER_SOURCE, version = PLACEHOLDER_VERSION, isPlaceholder = true),
        ShadeSwatch("C2", LabColor(0.0, 0.0, 0.0), source = PLACEHOLDER_SOURCE, version = PLACEHOLDER_VERSION, isPlaceholder = true),
        ShadeSwatch("C3", LabColor(0.0, 0.0, 0.0), source = PLACEHOLDER_SOURCE, version = PLACEHOLDER_VERSION, isPlaceholder = true),
        ShadeSwatch("C4", LabColor(0.0, 0.0, 0.0), source = PLACEHOLDER_SOURCE, version = PLACEHOLDER_VERSION, isPlaceholder = true),
        ShadeSwatch("D2", LabColor(0.0, 0.0, 0.0), source = PLACEHOLDER_SOURCE, version = PLACEHOLDER_VERSION, isPlaceholder = true),
        ShadeSwatch("D3", LabColor(0.0, 0.0, 0.0), source = PLACEHOLDER_SOURCE, version = PLACEHOLDER_VERSION, isPlaceholder = true),
        ShadeSwatch("D4", LabColor(0.0, 0.0, 0.0), source = PLACEHOLDER_SOURCE, version = PLACEHOLDER_VERSION, isPlaceholder = true)
    )
}
