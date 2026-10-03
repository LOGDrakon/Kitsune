package com.kitsune.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kitsune.core.designsystem.DecryptedImage
import com.kitsune.core.designsystem.KitsuneSerif
import com.kitsune.core.designsystem.KitsuneTheme

/**
 * Avatars for personas, universes and creators.
 *
 * The fallback matters more than the image: most personas have no portrait until the user pays to
 * generate one, so the no-image case is the *common* case and has to look deliberate. v1 showed a
 * grey Material `Person` icon in a grey circle, which made a fresh library look broken. Here the
 * fallback is the character's initials on a tint derived from their name — stable, distinguishable
 * at a glance in a list, and it never looks like a missing asset.
 */
enum class AvatarSize(val dp: Dp, val fontSize: Int) {
    /** In a dense row or a comment. */
    Small(32.dp, 13),
    /** The default list row. */
    Medium(44.dp, 16),
    /** A card header or chat top bar. */
    Large(64.dp, 22),
    /** A detail screen hero. */
    Hero(112.dp, 38)
}

/**
 * Tints for the initials fallback: desaturated, all readable under [KitsuneColors.text], and chosen
 * so two adjacent rows are very unlikely to collide.
 */
private val FallbackTints = listOf(
    Color(0xFF3A2C22),
    Color(0xFF243029),
    Color(0xFF2A2536),
    Color(0xFF33262B),
    Color(0xFF22303A),
    Color(0xFF362F22),
    Color(0xFF2B3326),
    Color(0xFF3A2430)
)

@Composable
fun KitsuneAvatar(
    name: String,
    modifier: Modifier = Modifier,
    imageBytes: ByteArray? = null,
    size: AvatarSize = AvatarSize.Medium,
    shape: Shape = CircleShape,
    /** Draws an accent ring — for the persona a chat currently belongs to, or a live universe cast. */
    highlighted: Boolean = false
) {
    val colors = KitsuneTheme.colors
    val tint = remember(name) { FallbackTints[fallbackTintIndex(name)] }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size.dp)
            .clip(shape)
            .background(if (imageBytes != null) colors.surfaceVariant else tint)
            .then(
                if (highlighted) Modifier.border(1.5.dp, colors.accent, shape)
                else Modifier.border(1.dp, colors.outline, shape)
            )
    ) {
        if (imageBytes != null) {
            DecryptedImage(
                bytes = imageBytes,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(shape)
            )
        } else {
            Text(
                text = initialsOf(name),
                fontSize = size.fontSize.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = KitsuneSerif,
                color = colors.text.copy(alpha = 0.82f)
            )
        }
    }
}

/**
 * Stable, non-negative index into [FallbackTints]. `abs(Int.MIN_VALUE)` is itself, so that one value
 * is excluded explicitly rather than left to overflow into a negative index.
 */
private fun fallbackTintIndex(name: String): Int {
    val h = name.hashCode()
    val positive = if (h == Int.MIN_VALUE) 0 else if (h < 0) -h else h
    return positive % FallbackTints.size
}

/**
 * Up to two initials. Takes the first letter of the first two words, so "Aria Valdenn" → "AV" and
 * "Le Val Silencieux" → "LV"; a single-word name falls back to its first letter alone rather than
 * two letters of the same word, which reads as an abbreviation nobody chose.
 */
private fun initialsOf(name: String): String {
    val words = name.trim().split(' ', '-', '_').filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}

/**
 * An avatar with a small overlapping second avatar, for an ensemble/universe chat where there is no
 * single protagonist — v1 rendered those with the same single avatar as a one-on-one chat, so the
 * two modes were indistinguishable in the story list.
 */
@Composable
fun KitsuneAvatarPair(
    primaryName: String,
    secondaryName: String,
    modifier: Modifier = Modifier,
    primaryBytes: ByteArray? = null,
    secondaryBytes: ByteArray? = null,
    size: AvatarSize = AvatarSize.Medium
) {
    Box(modifier.size(size.dp)) {
        KitsuneAvatar(
            name = secondaryName,
            imageBytes = secondaryBytes,
            size = size,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(size.dp * 0.66f)
        )
        KitsuneAvatar(
            name = primaryName,
            imageBytes = primaryBytes,
            size = size,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(size.dp * 0.72f)
        )
    }
}
