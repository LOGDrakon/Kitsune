package com.kitsune.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Kitsune's type scale, v2.
 *
 * v1 shipped `Typography()` — the untouched Material 3 default — which is why every screen read as
 * "a generic Android app" no matter what the colours did. A roleplay app is a *reading* app: most
 * sessions are an hour of prose. So the scale is built for prose first.
 *
 * Two families, split by role and never mixed inside one role:
 * - **Serif** ([KitsuneSerif], Noto Serif on Android) for display/headline/title — anything that
 *   names a thing. It is what makes a story list look like a shelf of books instead of a settings
 *   menu, and it costs nothing: it is a platform family, not a bundled asset.
 * - **Sans** ([KitsuneSans], Roboto) for body, labels and all UI chrome, where a serif at 13sp on a
 *   dark ground turns to mud.
 *
 * Line heights on the body styles are deliberately loose (1.6× on `bodyLarge`, against Material's
 * 1.5×) — that single number is most of the difference between "wall of text" and "page".
 */

val KitsuneSerif: FontFamily = FontFamily.Serif
val KitsuneSans: FontFamily = FontFamily.SansSerif

/**
 * Optical correction for the serif styles: large serif text set at default tracking looks loose, so
 * it is tightened, and the tightening scales with size.
 */
private fun serif(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    tracking: Double = 0.0
) = TextStyle(
    fontFamily = KitsuneSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.em
)

private fun sans(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    tracking: Double = 0.0
) = TextStyle(
    fontFamily = KitsuneSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.em
)

val KitsuneTypography = Typography(
    // Display — reserved for the one hero line on a screen (a story title, an empty state).
    displayLarge = serif(40, 48, FontWeight.Normal, -0.022),
    displayMedium = serif(32, 40, FontWeight.Normal, -0.018),
    displaySmall = serif(28, 36, FontWeight.Normal, -0.014),

    // Headline — screen titles and section leads.
    headlineLarge = serif(26, 34, FontWeight.Normal, -0.012),
    headlineMedium = serif(22, 30, FontWeight.Normal, -0.010),
    headlineSmall = serif(20, 28, FontWeight.Normal, -0.008),

    // Title — the name of a row, a card, a dialog. titleLarge stays serif (it names things);
    // titleMedium/Small are sans, because at those sizes they are labels doing structural work.
    titleLarge = serif(18, 26, FontWeight.Medium, -0.006),
    titleMedium = sans(16, 22, FontWeight.SemiBold),
    titleSmall = sans(14, 20, FontWeight.SemiBold),

    // Body — prose. bodyLarge is the message-bubble style and the one to get right.
    bodyLarge = sans(16, 26),
    bodyMedium = sans(14, 22),
    bodySmall = sans(13, 20),

    // Label — buttons, chips, tabs, metadata. Tracked out slightly; labelSmall is the eyebrow.
    labelLarge = sans(14, 20, FontWeight.SemiBold, 0.010),
    labelMedium = sans(12, 16, FontWeight.SemiBold, 0.030),
    labelSmall = sans(11, 14, FontWeight.SemiBold, 0.080)
)

/**
 * Extra styles the Material scale has no slot for, reached through `KitsuneTheme.type`.
 */
data class KitsuneTextStyles(
    /** All-caps section eyebrow. Pair with [KitsuneColors.textDim]. */
    val eyebrow: TextStyle = sans(11, 14, FontWeight.SemiBold, 0.100),
    /** In-chat prose: the single most-read style in the app. */
    val message: TextStyle = sans(16, 27),
    /** Narration/action text inside a message (`*she turns away*`). Italic is applied at use site. */
    val narration: TextStyle = serif(16, 27, FontWeight.Normal, -0.004),
    /** Long-form reading mode (novel view), centred measure, larger and looser still. */
    val prose: TextStyle = serif(18, 32, FontWeight.Normal, -0.006),
    /** Numerals that must line up in a column (Ofuda counts, stats). */
    val numeric: TextStyle = sans(15, 20, FontWeight.Medium, 0.010),
    /** Quiet metadata under a title: timestamps, counts, "il y a 2 h". */
    val meta: TextStyle = sans(12, 16, FontWeight.Normal, 0.010),
    /** Centred supporting copy in empty states and paywalls. */
    val supporting: TextStyle = sans(14, 22, FontWeight.Normal).copy(textAlign = TextAlign.Center)
)

val KitsuneTextStylesDefault = KitsuneTextStyles()

/**
 * Discreet-mode scale: everything shrinks and the serif is dropped entirely. Small, uniform,
 * low-contrast sans is markedly harder to read at an angle than a typographically varied page.
 */
val KitsuneDiscreetTypography = Typography(
    displayLarge = sans(20, 26),
    displayMedium = sans(18, 24),
    displaySmall = sans(17, 23),
    headlineLarge = sans(17, 23),
    headlineMedium = sans(16, 22),
    headlineSmall = sans(15, 21),
    titleLarge = sans(15, 21, FontWeight.Medium),
    titleMedium = sans(14, 19, FontWeight.Medium),
    titleSmall = sans(13, 18, FontWeight.Medium),
    bodyLarge = sans(13, 18),
    bodyMedium = sans(12, 17),
    bodySmall = sans(11, 15),
    labelLarge = sans(12, 16, FontWeight.Medium),
    labelMedium = sans(11, 15, FontWeight.Medium),
    labelSmall = sans(10, 13, FontWeight.Medium)
)
