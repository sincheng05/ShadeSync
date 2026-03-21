package com.example.shadesync.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shadesync.ui.theme.ShadeSyncTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SplashEnterDurationMs = 900
private const val SplashHoldDurationMs = 450L

/**
 * Full-screen launch cover for ShadeSync.
 * The animation stays quiet and minimal while still giving the app a clear identity.
 */
@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    val palette = rememberSplashPalette()
    val logoScale = remember { Animatable(0.84f) }
    val logoAlpha = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }
    val contentOffset = remember { Animatable(16f) }
    val guideProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                logoScale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = SplashEnterDurationMs,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            launch {
                logoAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 520,
                        easing = LinearOutSlowInEasing
                    )
                )
            }
            launch {
                guideProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 1100,
                        delayMillis = 80,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            launch {
                contentOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 820,
                        delayMillis = 140,
                        easing = FastOutSlowInEasing
                    )
                )
            }
            launch {
                contentAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 620,
                        delayMillis = 200,
                        easing = LinearOutSlowInEasing
                    )
                )
            }
        }

        delay(SplashHoldDurationMs)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(palette.backgroundTop, palette.backgroundBottom)
                )
            )
            .padding(horizontal = 24.dp, vertical = 32.dp)
    ) {
        SplashBackgroundArtwork(
            modifier = Modifier.fillMaxSize(),
            palette = palette
        )

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value)
            ) {
                ShadeSyncBrandMark(
                    modifier = Modifier.fillMaxSize(),
                    palette = palette,
                    guideProgress = guideProgress.value
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Column(
                modifier = Modifier
                    .offset(y = contentOffset.value.dp)
                    .alpha(contentAlpha.value),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ShadeSync",
                    color = palette.title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Dental shade matching",
                    color = palette.subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Surface(
                    color = palette.badgeSurface,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = palette.badgeBorder
                    )
                ) {
                    Text(
                        text = "Open-source workflow for dentists and lab technicians",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        color = palette.subtitle,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun SplashBackgroundArtwork(
    modifier: Modifier = Modifier,
    palette: SplashPalette
) {
    Canvas(modifier = modifier) {
        drawCircle(
            color = palette.accent.copy(alpha = 0.10f),
            radius = size.minDimension * 0.34f,
            center = Offset(x = size.width * 0.86f, y = size.height * 0.16f)
        )
        drawCircle(
            color = palette.accentSoft.copy(alpha = 0.28f),
            radius = size.minDimension * 0.46f,
            center = Offset(x = size.width * 0.12f, y = size.height * 0.92f)
        )
        drawRoundRect(
            color = palette.line.copy(alpha = 0.14f),
            topLeft = Offset(x = size.width * 0.14f, y = size.height * 0.73f),
            size = Size(width = size.width * 0.72f, height = 2.dp.toPx()),
            cornerRadius = CornerRadius(x = 999f, y = 999f)
        )
    }
}

@Composable
private fun ShadeSyncBrandMark(
    modifier: Modifier = Modifier,
    palette: SplashPalette,
    guideProgress: Float
) {
    Box(
        modifier = modifier
            .border(
                width = 1.dp,
                color = palette.cardBorder,
                shape = RoundedCornerShape(32.dp)
            )
            .background(
                color = palette.cardSurface,
                shape = RoundedCornerShape(32.dp)
            )
            .padding(18.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val frameWidth = size.width * 0.52f
            val frameHeight = size.height * 0.72f
            val frameLeft = (size.width - frameWidth) / 2f
            val frameTop = (size.height - frameHeight) / 2f
            val frameCorner = CornerRadius(x = frameWidth * 0.24f, y = frameWidth * 0.24f)
            val frameStroke = 2.5.dp.toPx()
            val guideStroke = 3.5.dp.toPx()
            val guideLength = size.width * 0.18f * guideProgress

            drawRoundRect(
                color = palette.accent.copy(alpha = 0.16f),
                topLeft = Offset(frameLeft - 6.dp.toPx(), frameTop - 6.dp.toPx()),
                size = Size(width = frameWidth + 12.dp.toPx(), height = frameHeight + 12.dp.toPx()),
                cornerRadius = frameCorner
            )
            drawRoundRect(
                color = palette.accent,
                topLeft = Offset(frameLeft, frameTop),
                size = Size(width = frameWidth, height = frameHeight),
                cornerRadius = frameCorner,
                style = Stroke(width = frameStroke)
            )

            val swatchWidth = size.width * 0.22f
            val swatchHeight = size.height * 0.50f
            val swatchLeft = (size.width - swatchWidth) / 2f
            val swatchTop = (size.height - swatchHeight) / 2f
            val swatchCorner = CornerRadius(x = swatchWidth / 2f, y = swatchWidth / 2f)

            drawRoundRect(
                color = palette.swatchFill,
                topLeft = Offset(swatchLeft, swatchTop),
                size = Size(width = swatchWidth, height = swatchHeight),
                cornerRadius = swatchCorner
            )
            drawRoundRect(
                color = palette.line.copy(alpha = 0.22f),
                topLeft = Offset(swatchLeft, swatchTop),
                size = Size(width = swatchWidth, height = swatchHeight),
                cornerRadius = swatchCorner,
                style = Stroke(width = 1.4.dp.toPx())
            )

            val centerY = size.height / 2f
            drawLine(
                color = palette.accent,
                start = Offset(x = size.width * 0.10f, y = centerY),
                end = Offset(x = size.width * 0.10f + guideLength, y = centerY),
                strokeWidth = guideStroke
            )
            drawLine(
                color = palette.accent,
                start = Offset(x = size.width * 0.90f - guideLength, y = centerY),
                end = Offset(x = size.width * 0.90f, y = centerY),
                strokeWidth = guideStroke
            )
        }
    }
}

private fun rememberSplashPalette(): SplashPalette {
    return SplashPalette(
        backgroundTop = Color(0xFFF8F4EE),
        backgroundBottom = Color(0xFFEDE3D5),
        cardSurface = Color.White.copy(alpha = 0.62f),
        cardBorder = Color(0xFFE1D4C0),
        title = Color(0xFF2F241D),
        subtitle = Color(0xFF665140),
        accent = Color(0xFFD5A56B),
        accentSoft = Color(0xFFF1DDC1),
        swatchFill = Color(0xFFFFFCF7),
        badgeSurface = Color.White.copy(alpha = 0.74f),
        badgeBorder = Color(0xFFE4D7C6),
        line = Color(0xFF7D6654)
    )
}

@Immutable
private data class SplashPalette(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val cardSurface: Color,
    val cardBorder: Color,
    val title: Color,
    val subtitle: Color,
    val accent: Color,
    val accentSoft: Color,
    val swatchFill: Color,
    val badgeSurface: Color,
    val badgeBorder: Color,
    val line: Color
)

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SplashScreenPreview() {
    ShadeSyncTheme {
        SplashScreen(onSplashFinished = {})
    }
}
