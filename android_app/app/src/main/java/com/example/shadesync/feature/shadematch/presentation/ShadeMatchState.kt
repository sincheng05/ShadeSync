package com.example.shadesync.feature.shadematch.presentation

import com.example.shadesync.feature.shadematch.domain.LabColor
import com.example.shadesync.feature.shadematch.domain.RgbColor
import com.example.shadesync.feature.shadematch.domain.ShadeMatch
import com.example.shadesync.feature.shadematch.domain.WhiteBalanceGain

data class ShadeMatchState(
    val rgb: RgbColor,
    val whiteBalance: WhiteBalanceGain,
    val lab: LabColor,
    val matches: List<ShadeMatch>,
    val isPlaceholderDataset: Boolean
)
