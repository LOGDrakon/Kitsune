package com.kitsune.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kitsune.core.designsystem.KitsuneTheme

/**
 * Empty, loading and error states.
 *
 * These exist as components because v1 didn't have them: a list with nothing in it showed a bare
 * centred `Text("Aucun persona")`, a loading list showed a spinner in the middle of a blank screen,
 * and a failed call showed a red `Text` with the exception message. Three of the most-seen moments
 * in the app were the three least designed.
 *
 * The rule: an empty state always names the thing that's missing, says one sentence about why you'd
 * want one, and offers the action that creates it. No dead ends.
 */
@Composable
fun KitsuneEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    val colors = KitsuneTheme.colors
    val spacing = KitsuneTheme.spacing
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.xxl, vertical = spacing.xxxl)
    ) {
        if (icon != null) {
            // A large, very dim glyph rather than an illustration: it reads as texture at a glance
            // and never competes with the copy or the action.
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textFaint,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(spacing.xl))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.text
        )
        if (body != null) {
            Spacer(Modifier.height(spacing.sm))
            Text(text = body, style = KitsuneTheme.type.supporting, color = colors.textSecondary)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(spacing.xl))
            KitsuneButton(text = actionLabel, onClick = onAction, fillWidth = false)
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(spacing.xs))
            KitsuneQuietButton(text = secondaryLabel, onClick = onSecondary)
        }
    }
}

/**
 * Failure state. Carries the retry, and shows the technical detail only as dim small print — the
 * headline is always something a person can act on.
 */
@Composable
fun KitsuneErrorState(
    title: String = "Quelque chose n'a pas fonctionné",
    detail: String? = null,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    val colors = KitsuneTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = KitsuneTheme.spacing.xxl, vertical = KitsuneTheme.spacing.xxxl)
    ) {
        Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            tint = colors.error,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.height(KitsuneTheme.spacing.lg))
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text)
        if (detail != null) {
            Spacer(Modifier.height(KitsuneTheme.spacing.sm))
            Text(detail, style = KitsuneTheme.type.supporting, color = colors.textDim)
        }
        if (onRetry != null) {
            Spacer(Modifier.height(KitsuneTheme.spacing.xl))
            KitsuneSecondaryButton(text = "Réessayer", onClick = onRetry, fillWidth = false)
        }
    }
}

/** Centred spinner for a whole screen that has nothing to show yet. Prefer [KitsuneSkeletonList]. */
@Composable
fun KitsuneLoading(modifier: Modifier = Modifier, label: String? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize()
    ) {
        CircularProgressIndicator(
            color = KitsuneTheme.colors.accent,
            strokeWidth = 2.dp,
            modifier = Modifier.size(28.dp)
        )
        if (label != null) {
            Spacer(Modifier.height(KitsuneTheme.spacing.lg))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = KitsuneTheme.colors.textDim)
        }
    }
}

/**
 * A pulsing placeholder block.
 *
 * Preferred over a spinner for anything list-shaped: it shows the *shape* of what's coming, so the
 * screen doesn't reflow the instant data lands. The pulse is an alpha fade, not a moving gradient
 * sweep — a shimmer sweep across a near-black surface looks like a rendering glitch.
 */
@Composable
fun KitsuneSkeleton(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    widthFraction: Float = 1f,
    circle: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = KitsuneTheme.motion.standardEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )
    Box(
        modifier
            .alpha(alpha)
            .then(if (circle) Modifier.size(height) else Modifier.fillMaxWidth(widthFraction).height(height))
            .background(
                KitsuneTheme.colors.skeleton,
                if (circle) CircleShape else RoundedCornerShape(6.dp)
            )
    )
}

/** The standard "list is loading" placeholder: [count] rows shaped like [KitsuneRow]. */
@Composable
fun KitsuneSkeletonList(
    modifier: Modifier = Modifier,
    count: Int = 4,
    withAvatar: Boolean = true
) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KitsuneTheme.spacing.md)
    ) {
        repeat(count) { index ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KitsuneTheme.colors.surface, KitsuneTheme.shape.md)
                    .padding(KitsuneTheme.spacing.lg)
            ) {
                if (withAvatar) {
                    KitsuneSkeleton(height = 44.dp, circle = true)
                    Spacer(Modifier.width(KitsuneTheme.spacing.md))
                }
                Column(Modifier.weight(1f)) {
                    // Staggered widths so the block doesn't read as a table of identical bars.
                    KitsuneSkeleton(height = 14.dp, widthFraction = if (index % 2 == 0) 0.55f else 0.42f)
                    Spacer(Modifier.height(KitsuneTheme.spacing.sm))
                    KitsuneSkeleton(height = 11.dp, widthFraction = if (index % 2 == 0) 0.8f else 0.68f)
                }
            }
        }
    }
}
