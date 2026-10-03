package com.kitsune.core.designsystem.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** The 4-step download-tier ladder (10/50/100/500 by default) as ONE combined widget — the
 * "paliers" visualization, rather than four separate unrelated bars. Each tier gets an
 * equal-width segment (not a literal linear 0-500 scale) so the early, more-achievable tiers
 * aren't visually crushed to nothing by the 500 tier spanning two orders of magnitude more. */
@Composable
fun DownloadTierProgressBar(
    currentValue: Int,
    modifier: Modifier = Modifier,
    tiers: List<Int> = listOf(10, 50, 100, 500)
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            tiers.forEachIndexed { index, tier ->
                val segmentStart = if (index == 0) 0 else tiers[index - 1]
                val segmentRange = (tier - segmentStart).coerceAtLeast(1)
                val segmentFill = ((currentValue - segmentStart).toFloat() / segmentRange).coerceIn(0f, 1f)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(segmentFill)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            tiers.forEach { tier ->
                Text(
                    text = tier.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (currentValue >= tier) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

/** Generic single-threshold bar, e.g. PROLIFIC ("14 / 20 annonces") or RATING_4PLUS
 * ("3.8 / 4.0 ★"). [valueLabel] is pre-formatted by the caller. */
@Composable
fun SingleThresholdProgressBar(
    currentValue: Float,
    targetValue: Float,
    valueLabel: String,
    modifier: Modifier = Modifier
) {
    val fraction = if (targetValue > 0f) (currentValue / targetValue).coerceIn(0f, 1f) else 0f
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Text(
            text = valueLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
