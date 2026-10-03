package com.kitsune.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Kitsune's palette — the logo's colours.
 *
 * The fox mark is a single maroon (`#600C1F`), and that maroon is the app's accent again: active
 * tab, focused field, unread dot, primary action. v2's "sombre éditorial" experiment had demoted it
 * to a rare brand tint behind an antique gold; the brand identity won.
 *
 * What v2 got right is kept: the ground is a warm near-black ink, almost every pixel is that ground
 * plus one of four text tints, and the accent marks the one active thing on a screen — it is not a
 * surface colour. Gold survives as the secondary brand colour (stars, "free" labels, highlights),
 * as it was in v1.
 *
 * Names are preserved — a lot of code references them directly. New code should prefer the
 * semantic tokens on [KitsuneColors] (via `KitsuneTheme.colors`) over any raw value in this file.
 */

// ---------------------------------------------------------------------------------------------
// Ink — the ground. Warm-shifted (a green-neutral black looks like a dead LCD next to the accent).
// ---------------------------------------------------------------------------------------------

/** Page background, dark theme. */
val Ink = Color(0xFF0C0A09)
/** Raised surface: cards, sheets, list rows. */
val InkSurface = Color(0xFF15120F)
/** Surface one step further up: input fields, pressed rows, code blocks. */
val InkSurfaceVariant = Color(0xFF1E1A16)
/** Highest surface: menus, dialogs, floating chrome. */
val InkSurfaceBright = Color(0xFF272220)
/** Hairline borders. Barely visible by design — structure comes from spacing, not lines. */
val InkOutline = Color(0xFF302A25)

// ---------------------------------------------------------------------------------------------
// Warm neutrals — the text scale. Four steps, and four is the whole budget.
// ---------------------------------------------------------------------------------------------

/** Body and heading text on ink. */
val Cream = Color(0xFFEDE7DF)
/** Secondary text: subtitles, metadata, inactive tabs. */
val WarmGray = Color(0xFFA1968A)
/** Tertiary text: timestamps, captions, placeholder text. */
val WarmGrayDim = Color(0xFF6F6659)
/** Disabled text and dividers-as-text. */
val WarmGrayFaint = Color(0xFF4A433B)

// ---------------------------------------------------------------------------------------------
// Maroon — the accent, straight from the fox mark.
// ---------------------------------------------------------------------------------------------

/** The logo's maroon. The accent on paper, where it reads at ~13:1. */
val Maroon = Color(0xFF600C1F)
/**
 * The accent on ink. The logo's maroon is too dark to read on a near-black ground (~1.4:1), so the
 * dark theme lifts it to ~3.5:1 (the floor for icons and large text) while staying the same hue;
 * Cream on top of it is ~4.5:1.
 */
val MaroonBright = Color(0xFFB8394F)
/** Pressed accent, and accent text on a maroon-tinted container (dark theme). */
val MaroonLight = Color(0xFFD9707F)
/** Accent at rest behind content — maroon at low strength over ink. */
val MaroonDeep = Color(0xFF3A0E17)
/** Accent container on paper. */
val MaroonPale = Color(0xFFF5D9DC)
/** Pressed accent, and accent text on [MaroonPale] (light theme). */
val MaroonDark = Color(0xFF45081A)

// ---------------------------------------------------------------------------------------------
// Gold — the secondary brand colour (stars, "free" labels, highlights). Never a button.
// ---------------------------------------------------------------------------------------------

/** Gold on ink. */
val Gold = Color(0xFFC9A227)
val GoldBright = Color(0xFFE6C888)
/** Gold at rest behind content, dark theme. */
val GoldDeep = Color(0xFF3A3020)
/** Gold on paper, where #C9A227 fails contrast. */
val GoldPale = Color(0xFF8A6A22)
/** Gold container on paper. */
val GoldContainerLight = Color(0xFFF6EBD4)

// ---------------------------------------------------------------------------------------------
// Paper — the light theme ground. Warm, not white: same family as the dark theme, flipped.
// ---------------------------------------------------------------------------------------------

val Paper = Color(0xFFFAF6F0)
val PaperSurface = Color(0xFFFFFDF9)
val PaperSurfaceVariant = Color(0xFFF1EBE1)
val PaperSurfaceBright = Color(0xFFFFFFFF)
val PaperOutline = Color(0xFFE2D9CC)

val InkText = Color(0xFF1A1512)
val InkTextSecondary = Color(0xFF5E564C)
val InkTextDim = Color(0xFF8A8074)
val InkTextFaint = Color(0xFFB3A99B)

// ---------------------------------------------------------------------------------------------
// Feedback. Kept narrow: one red, one amber, one green, each with a container tint.
// ---------------------------------------------------------------------------------------------

val ErrorRed = Color(0xFFE0747C)
val ErrorRedDeep = Color(0xFFA32432)
val ErrorContainerDark = Color(0xFF3A1519)
val ErrorContainerLight = Color(0xFFFBE3E5)

val WarnAmber = Color(0xFFD9A63C)
val WarnAmberDeep = Color(0xFF8A6511)
val WarnContainerDark = Color(0xFF3A2E12)
val WarnContainerLight = Color(0xFFFBF0D8)

val SuccessGreen = Color(0xFF7FB88A)
val SuccessGreenDeep = Color(0xFF2E6B3C)
val SuccessContainerDark = Color(0xFF16291A)
val SuccessContainerLight = Color(0xFFE2F2E4)

// ---------------------------------------------------------------------------------------------
// Discreet mode — deliberately low-contrast so the screen is unreadable over someone's shoulder.
// Unchanged in intent from v1; retuned to match the new ground.
// ---------------------------------------------------------------------------------------------

val DiscreetBackgroundDark = Color(0xFF191919)
val DiscreetSurfaceDark = Color(0xFF212121)
val DiscreetSurfaceVariantDark = Color(0xFF292929)
val DiscreetTextDark = Color(0xFF4A4A4A)
val DiscreetTextVariantDark = Color(0xFF3A3A3A)
val DiscreetOutlineDark = Color(0xFF333333)

val DiscreetBackgroundLight = Color(0xFFE8E8E8)
val DiscreetSurfaceLight = Color(0xFFE0E0E0)
val DiscreetSurfaceVariantLight = Color(0xFFD8D8D8)
val DiscreetTextLight = Color(0xFFB0B0B0)
val DiscreetTextVariantLight = Color(0xFFC0C0C0)
val DiscreetOutlineLight = Color(0xFFCCCCCC)
