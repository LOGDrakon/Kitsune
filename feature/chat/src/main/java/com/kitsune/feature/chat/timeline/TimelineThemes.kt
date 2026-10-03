package com.kitsune.feature.chat.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kitsune.core.data.local.entities.StoryMood
import kotlin.math.cos
import kotlin.math.sin

/**
 * Visual reskins for [TimelineScreen], unlocked via the "Personnalisation" section of the Store
 * (`timeline_theme_*` cosmetics). Each theme is pure Compose drawing — no bitmap assets — so it
 * stays crisp at any density and costs nothing to ship.
 */
enum class TimelineThemeId(val cosmeticId: String) {
    GOTHIC("timeline_theme_gothic"),
    MANGA("timeline_theme_manga"),
    WATERCOLOR("timeline_theme_watercolor");

    companion object {
        fun fromCosmeticId(id: String?): TimelineThemeId? = entries.find { it.cosmeticId == id }
    }
}

/** A jewel-toned accent per mood, shared across themes and tuned per-theme via alpha/blend. */
fun moodAccentColor(mood: StoryMood): Color = when (mood) {
    StoryMood.TENDER -> Color(0xFFE8A0BF)
    StoryMood.ROMANTIC -> Color(0xFFE63950)
    StoryMood.DRAMATIC -> Color(0xFF7B4FB8)
    StoryMood.HUMOROUS -> Color(0xFFF2A93B)
    StoryMood.DARK -> Color(0xFF4A4A5E)
    StoryMood.TENSE -> Color(0xFFC0392B)
    StoryMood.MELANCHOLIC -> Color(0xFF5C7A99)
    StoryMood.EXCITING -> Color(0xFFE6B800)
    StoryMood.NEUTRAL -> Color(0xFF8A8A8A)
}

// ---------------------------------------------------------------------------------------------
// Gothic Chronicle — parchment-dark bordeaux/gold, illuminated drop-cap titles, diamond corners.
// ---------------------------------------------------------------------------------------------

private val GothicGold = Color(0xFFC9A227)
private val GothicInk = Color(0xFFF0E6D2)
private val GothicBackgroundBrush = Brush.verticalGradient(listOf(Color(0xFF14060A), Color(0xFF2B0A10), Color(0xFF170709)))

@Composable
fun GothicBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier = modifier.fillMaxSize().background(GothicBackgroundBrush), content = content)
}

@Composable
fun GothicCard(content: @Composable ColumnScope.() -> Unit) {
    val strokePx = with(androidx.compose.ui.platform.LocalDensity.current) { 1.5.dp.toPx() }
    val diamondPx = with(androidx.compose.ui.platform.LocalDensity.current) { 6.dp.toPx() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(brush = Brush.verticalGradient(listOf(Color(0xFF2E1017), Color(0xFF190810))))
                drawRect(color = GothicGold, style = Stroke(width = strokePx))
                listOf(
                    Offset(0f, 0f), Offset(size.width, 0f),
                    Offset(0f, size.height), Offset(size.width, size.height)
                ).forEach { corner -> drawDiamond(corner, diamondPx, GothicGold) }
            }
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

@Composable
fun GothicTitle(text: String) {
    if (text.isEmpty()) return
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.Top) {
        Text(
            text.take(1).uppercase(),
            style = MaterialTheme.typography.displaySmall,
            color = GothicGold,
            fontWeight = FontWeight.Black
        )
        Text(
            text.drop(1),
            style = MaterialTheme.typography.titleMedium,
            color = GothicInk,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp)
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDiamond(center: Offset, r: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - r)
        lineTo(center.x + r, center.y)
        lineTo(center.x, center.y + r)
        lineTo(center.x - r, center.y)
        close()
    }
    drawPath(path, color = color)
}

// ---------------------------------------------------------------------------------------------
// Manga Frise — comic panels, halftone dots, rotated SFX mood badge, speed-lines on hot moments.
// ---------------------------------------------------------------------------------------------

private val MangaInk = Color(0xFF1A1A1A)
private val MangaPaper = Color(0xFFF7F5F0)

@Composable
fun MangaBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MangaPaper)
            .drawBehind {
                val spacing = 16.dp.toPx()
                val radius = 1.3.dp.toPx()
                var y = 0f
                var row = 0
                while (y < size.height) {
                    var x = if (row % 2 == 0) 0f else spacing / 2f
                    while (x < size.width) {
                        drawCircle(MangaInk.copy(alpha = 0.06f), radius = radius, center = Offset(x, y))
                        x += spacing
                    }
                    y += spacing
                    row++
                }
            },
        content = content
    )
}

@Composable
fun MangaCard(mood: StoryMood, content: @Composable ColumnScope.() -> Unit) {
    val speedLines = mood == StoryMood.DRAMATIC || mood == StoryMood.TENSE || mood == StoryMood.EXCITING
    val borderPx = with(androidx.compose.ui.platform.LocalDensity.current) { 2.5.dp.toPx() }
    val linePx = with(androidx.compose.ui.platform.LocalDensity.current) { 1.5.dp.toPx() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(color = Color.White)
                if (speedLines) {
                    val origin = Offset(size.width * 0.06f, size.height * 0.1f)
                    val lineColor = MangaInk.copy(alpha = 0.09f)
                    val reach = size.width * 1.1f
                    for (i in 0 until 16) {
                        val angle = (i / 16f) * 2f * Math.PI.toFloat()
                        val end = Offset(origin.x + cos(angle) * reach, origin.y + sin(angle) * reach)
                        drawLine(lineColor, origin, end, strokeWidth = linePx)
                    }
                }
                drawRect(color = MangaInk, style = Stroke(width = borderPx))
            }
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun MangaMoodBadge(mood: StoryMood, label: String) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Black,
        color = Color.White,
        modifier = Modifier
            .graphicsLayer(rotationZ = -4f)
            .background(moodAccentColor(mood), RoundedCornerShape(2.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

// ---------------------------------------------------------------------------------------------
// Aquarelle & Souvenirs — soft pastel wash, blurred mood "ink drop", oversized rounded cards.
// ---------------------------------------------------------------------------------------------

private val WatercolorBackgroundBrush = Brush.linearGradient(listOf(Color(0xFFFDF6F0), Color(0xFFF3E9F5), Color(0xFFEAF3EE)))

@Composable
fun WatercolorBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier = modifier.fillMaxSize().background(WatercolorBackgroundBrush), content = content)
}

@Composable
fun WatercolorCard(mood: StoryMood, content: @Composable ColumnScope.() -> Unit) {
    val tint = moodAccentColor(mood)
    val cornerPx = with(androidx.compose.ui.platform.LocalDensity.current) { 28.dp.toPx() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(tint.copy(alpha = 0.24f), tint.copy(alpha = 0.07f), Color.White.copy(alpha = 0.92f)),
                        center = Offset(size.width * 0.12f, size.height * 0.08f),
                        radius = size.width * 1.15f
                    ),
                    cornerRadius = CornerRadius(cornerPx)
                )
            }
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

@Composable
fun WatercolorMoodBlob(emoji: String, mood: StoryMood) {
    val tint = moodAccentColor(mood)
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(44.dp)) {
            drawCircle(tint.copy(alpha = 0.32f), radius = size.minDimension / 2f)
            drawCircle(
                tint.copy(alpha = 0.16f),
                radius = size.minDimension / 2f * 1.25f,
                center = center + Offset(5.dp.toPx(), 5.dp.toPx())
            )
        }
        Text(emoji, style = MaterialTheme.typography.headlineSmall)
    }
}
