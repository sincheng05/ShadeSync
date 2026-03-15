package com.example.shadesync.feature.shadematch.domain

object RgbDistanceMatcher {
    fun squaredDistance(a: RgbColor, b: RgbColor): Int {
        val dr = a.r - b.r
        val dg = a.g - b.g
        val db = a.b - b.b
        return (dr * dr) + (dg * dg) + (db * db)
    }

    fun <T> nearest(sample: RgbColor, references: Map<T, RgbColor>): T? {
        return references.minByOrNull { (_, reference) ->
            squaredDistance(sample, reference)
        }?.key
    }
}
