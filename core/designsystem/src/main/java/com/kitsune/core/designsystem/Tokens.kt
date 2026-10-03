package com.kitsune.core.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The non-colour, non-type half of the design system.
 *
 * These exist because v1's inconsistency was mostly *here*, not in the palette: padding was written
 * as a literal at each call site (`12.dp` on one card, `16.dp` on the next, `14.dp` in a third),
 * corner radii ranged from 4 to 28 with no rule, and every screen animated at whatever duration its
 * author typed. A shared scale is what makes unrelated screens look like the same app.
 */

/**
 * Spacing scale, 4dp-based. Only these values are allowed in layout code — if a gap needs a value
 * that isn't here, the layout is usually wrong, not the scale.
 *
 * [gutter] is the one to reach for by default: it is the horizontal page margin, and keeping it
 * identical on every screen is what stops the app feeling like a collection of separate apps.
 */
data class KitsuneSpacing(
    /** 2dp — optical nudges only (icon baseline alignment). */
    val hair: Dp = 2.dp,
    /** 4dp — between a label and the thing it labels. */
    val xs: Dp = 4.dp,
    /** 8dp — inside a chip, between stacked metadata lines. */
    val sm: Dp = 8.dp,
    /** 12dp — between items in a dense list. */
    val md: Dp = 12.dp,
    /** 16dp — default padding inside a card; between cards in a list. */
    val lg: Dp = 16.dp,
    /** 24dp — between a section and the next. */
    val xl: Dp = 24.dp,
    /** 32dp — above a section header that starts a new idea. */
    val xxl: Dp = 32.dp,
    /** 48dp — around an empty state or a hero block. */
    val xxxl: Dp = 48.dp,
    /** 20dp — the page's horizontal margin, everywhere, no exceptions. */
    val gutter: Dp = 20.dp,
    /** 44dp — minimum tappable size (Material says 48; 44 with generous padding reads less chunky
     *  and still clears the accessibility floor when the row's own padding is counted). */
    val touchTarget: Dp = 44.dp,
    /** Bottom padding on a scrollable page so the last row clears the bottom bar and the FAB. */
    val scrollBottom: Dp = 96.dp
)

/**
 * Corner radii. Three steps plus a pill — the v2 rule is that radius encodes *size*, not emphasis:
 * small things get [sm], cards get [md], sheets get [lg], and anything pill-shaped is a control.
 */
data class KitsuneShapes(
    /** 8dp — chips, small buttons, avatars-as-squares, inline badges. */
    val sm: CornerBasedShape = RoundedCornerShape(8.dp),
    /** 14dp — cards, list rows, text fields, dialogs. The app's signature radius. */
    val md: CornerBasedShape = RoundedCornerShape(14.dp),
    /** 20dp — hero cards, images, large media. */
    val lg: CornerBasedShape = RoundedCornerShape(20.dp),
    /** Top-rounded only, for bottom sheets. */
    val sheet: CornerBasedShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    /** Fully rounded: primary buttons, tabs, pills. */
    val pill: CornerBasedShape = RoundedCornerShape(percent = 50),
    /** A message bubble from the AI — square on the side it "grows" from. */
    val bubbleIncoming: CornerBasedShape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
    /** A message bubble from the user. */
    val bubbleOutgoing: CornerBasedShape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomEnd = 16.dp, bottomStart = 16.dp)
)

/**
 * Motion. Two durations and two easings cover the whole app; the point of naming them is that a
 * transition somewhere new automatically matches every existing one.
 *
 * v1 used 300ms slide transitions on *every* navigation, including pushing a settings sub-page —
 * long enough to feel sluggish on the tenth repetition. v2 moves faster and fades more.
 */
data class KitsuneMotion(
    /** 120ms — state changes that must feel instant: press, check, chip select. */
    val instant: Int = 120,
    /** 220ms — the default. Enter/exit, crossfade, expand. */
    val standard: Int = 220,
    /** 380ms — only for a full-screen context change (opening a chat, entering novel mode). */
    val deliberate: Int = 380,
    /** Content entering: decelerate. */
    val enter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f),
    /** Content leaving: accelerate. */
    val exit: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f),
    /** Anything that both starts and ends on screen. */
    val standardEasing: Easing = FastOutSlowInEasing
)

/**
 * Elevation, expressed as the surface *token* to use rather than a shadow depth.
 *
 * On a near-black ground, Material's shadow-based elevation is invisible — depth has to come from
 * the surface colour stepping up. Shadows are therefore used only where a thing genuinely floats
 * over scrolling content (FAB, bottom sheet scrim edge).
 */
data class KitsuneElevation(
    val none: Dp = 0.dp,
    /** Cards and rows. No shadow; the surface step does the work. */
    val raised: Dp = 0.dp,
    /** Floating chrome over scrolling content. */
    val floating: Dp = 6.dp,
    /** Modal surfaces. */
    val modal: Dp = 12.dp
)

val KitsuneSpacingDefault = KitsuneSpacing()
val KitsuneShapesDefault = KitsuneShapes()
val KitsuneMotionDefault = KitsuneMotion()
val KitsuneElevationDefault = KitsuneElevation()
