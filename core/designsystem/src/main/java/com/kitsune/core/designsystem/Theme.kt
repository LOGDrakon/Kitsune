package com.kitsune.core.designsystem

import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import android.content.ContextWrapper
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colour tokens — the layer screens are supposed to use.
 *
 * Material's ColorScheme is still provided (many M3 components read it, and third-party components
 * read nothing else), but it is a poor vocabulary for this app: "primary" and "secondary" say
 * nothing about whether a colour is a *ground*, a *text step*, or an *accent*, so v1 ended up using
 * primary for anything that needed to stand out and the accent stopped meaning anything. These names
 * describe the role instead, which makes the wrong choice hard to make.
 */
data class KitsuneColors(
    /** The page. Nothing sits behind this. */
    val background: Color,
    /** A card, row or sheet sitting on [background]. */
    val surface: Color,
    /** An input, a pressed row, a nested block. One step above [surface]. */
    val surfaceVariant: Color,
    /** Menus, dialogs, floating chrome. The top of the stack. */
    val surfaceBright: Color,
    /** Hairline border. Use sparingly — spacing separates better than lines do. */
    val outline: Color,
    /** A border that has to be seen (focused field, selected card). */
    val outlineStrong: Color,

    /** Primary text and icons. */
    val text: Color,
    /** Subtitles, secondary icons, inactive tabs. */
    val textSecondary: Color,
    /** Timestamps, captions, placeholders. */
    val textDim: Color,
    /** Disabled. */
    val textFaint: Color,

    /** The accent. Exactly one thing per screen should normally wear it. */
    val accent: Color,
    /** Accent, brightened — pressed state, or accent text on [accentContainer]. */
    val accentBright: Color,
    /** A filled accent surface at rest (selected tab background, a selected chip). */
    val accentContainer: Color,
    /** Text/icon on a solid [accent] fill. */
    val onAccent: Color,

    /** Gold, the secondary brand colour: stars, "free" labels, highlights. Never a button. */
    val brand: Color,
    val brandContainer: Color,

    val error: Color,
    val errorContainer: Color,
    val onError: Color,
    val warn: Color,
    val warnContainer: Color,
    val success: Color,
    val successContainer: Color,

    /** The AI's message bubble. */
    val bubbleIncoming: Color,
    val onBubbleIncoming: Color,
    /** The user's message bubble. */
    val bubbleOutgoing: Color,
    val onBubbleOutgoing: Color,

    /** Behind a modal. */
    val scrim: Color,
    /** Skeleton placeholder fill. */
    val skeleton: Color,

    /** True when this set is the dark one — for the rare case a component must branch. */
    val isDark: Boolean
)

private val DarkColors = KitsuneColors(
    background = Ink,
    surface = InkSurface,
    surfaceVariant = InkSurfaceVariant,
    surfaceBright = InkSurfaceBright,
    outline = InkOutline,
    outlineStrong = Color(0xFF473F37),
    text = Cream,
    textSecondary = WarmGray,
    textDim = WarmGrayDim,
    textFaint = WarmGrayFaint,
    accent = MaroonBright,
    accentBright = MaroonLight,
    accentContainer = MaroonDeep,
    onAccent = Cream,
    brand = Gold,
    brandContainer = GoldDeep,
    error = ErrorRed,
    errorContainer = ErrorContainerDark,
    onError = Ink,
    warn = WarnAmber,
    warnContainer = WarnContainerDark,
    success = SuccessGreen,
    successContainer = SuccessContainerDark,
    bubbleIncoming = InkSurfaceVariant,
    onBubbleIncoming = Cream,
    // The user's own bubble is the only place a filled accent-adjacent surface appears in the
    // reading flow, and even there it is a tint rather than solid maroon — a page of alternating
    // saturated blocks is unreadable over the hour-long sessions this app is for.
    bubbleOutgoing = Color(0xFF2A1418),
    onBubbleOutgoing = Color(0xFFF2E4E6),
    scrim = Color(0xCC000000),
    skeleton = Color(0xFF231F1B),
    isDark = true
)

private val LightColors = KitsuneColors(
    background = Paper,
    surface = PaperSurface,
    surfaceVariant = PaperSurfaceVariant,
    surfaceBright = PaperSurfaceBright,
    outline = PaperOutline,
    outlineStrong = Color(0xFFCBBFAE),
    text = InkText,
    textSecondary = InkTextSecondary,
    textDim = InkTextDim,
    textFaint = InkTextFaint,
    accent = Maroon,
    accentBright = MaroonDark,
    accentContainer = MaroonPale,
    onAccent = Color(0xFFFFFFFF),
    brand = GoldPale,
    brandContainer = GoldContainerLight,
    error = ErrorRedDeep,
    errorContainer = ErrorContainerLight,
    onError = Color(0xFFFFFFFF),
    warn = WarnAmberDeep,
    warnContainer = WarnContainerLight,
    success = SuccessGreenDeep,
    successContainer = SuccessContainerLight,
    bubbleIncoming = PaperSurfaceVariant,
    onBubbleIncoming = InkText,
    bubbleOutgoing = MaroonPale,
    onBubbleOutgoing = MaroonDark,
    scrim = Color(0x99000000),
    skeleton = Color(0xFFEDE5D9),
    isDark = false
)

private fun discreetColors(dark: Boolean): KitsuneColors = if (dark) {
    KitsuneColors(
        background = DiscreetBackgroundDark,
        surface = DiscreetSurfaceDark,
        surfaceVariant = DiscreetSurfaceVariantDark,
        surfaceBright = DiscreetSurfaceVariantDark,
        outline = DiscreetOutlineDark,
        outlineStrong = DiscreetOutlineDark,
        text = DiscreetTextDark,
        textSecondary = DiscreetTextVariantDark,
        textDim = DiscreetTextVariantDark,
        textFaint = DiscreetOutlineDark,
        accent = DiscreetTextDark,
        accentBright = DiscreetTextDark,
        accentContainer = DiscreetSurfaceVariantDark,
        onAccent = DiscreetBackgroundDark,
        brand = DiscreetTextDark,
        brandContainer = DiscreetSurfaceVariantDark,
        error = DiscreetTextDark,
        errorContainer = DiscreetSurfaceVariantDark,
        onError = DiscreetBackgroundDark,
        warn = DiscreetTextDark,
        warnContainer = DiscreetSurfaceVariantDark,
        success = DiscreetTextDark,
        successContainer = DiscreetSurfaceVariantDark,
        bubbleIncoming = DiscreetSurfaceVariantDark,
        onBubbleIncoming = DiscreetTextDark,
        bubbleOutgoing = DiscreetSurfaceVariantDark,
        onBubbleOutgoing = DiscreetTextDark,
        scrim = Color(0xCC000000),
        skeleton = DiscreetSurfaceVariantDark,
        isDark = true
    )
} else {
    KitsuneColors(
        background = DiscreetBackgroundLight,
        surface = DiscreetSurfaceLight,
        surfaceVariant = DiscreetSurfaceVariantLight,
        surfaceBright = DiscreetSurfaceVariantLight,
        outline = DiscreetOutlineLight,
        outlineStrong = DiscreetOutlineLight,
        text = DiscreetTextLight,
        textSecondary = DiscreetTextVariantLight,
        textDim = DiscreetTextVariantLight,
        textFaint = DiscreetOutlineLight,
        accent = DiscreetTextLight,
        accentBright = DiscreetTextLight,
        accentContainer = DiscreetSurfaceVariantLight,
        onAccent = DiscreetBackgroundLight,
        brand = DiscreetTextLight,
        brandContainer = DiscreetSurfaceVariantLight,
        error = DiscreetTextLight,
        errorContainer = DiscreetSurfaceVariantLight,
        onError = DiscreetBackgroundLight,
        warn = DiscreetTextLight,
        warnContainer = DiscreetSurfaceVariantLight,
        success = DiscreetTextLight,
        successContainer = DiscreetSurfaceVariantLight,
        bubbleIncoming = DiscreetSurfaceVariantLight,
        onBubbleIncoming = DiscreetTextLight,
        bubbleOutgoing = DiscreetSurfaceVariantLight,
        onBubbleOutgoing = DiscreetTextLight,
        scrim = Color(0x99000000),
        skeleton = DiscreetSurfaceVariantLight,
        isDark = false
    )
}

/**
 * Material bridge. Every M3 slot is filled from the semantic set so stock components inherit the
 * right look — including the ones that would otherwise render Material's default purple.
 *
 * The notable mapping: primary is the **accent**, and primaryContainer is the accent's *tint*, not
 * a saturated fill. That is what stops a stock Button from being a slab of maroon.
 */
private fun KitsuneColors.toMaterial() = if (isDark) {
    darkColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = accentContainer, onPrimaryContainer = accentBright,
        inversePrimary = accentContainer,
        secondary = textSecondary, onSecondary = background,
        secondaryContainer = surfaceVariant, onSecondaryContainer = text,
        tertiary = brand, onTertiary = text,
        tertiaryContainer = brandContainer, onTertiaryContainer = text,
        background = background, onBackground = text,
        surface = surface, onSurface = text,
        surfaceVariant = surfaceVariant, onSurfaceVariant = textSecondary,
        surfaceTint = accent,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceVariant,
        surfaceContainerHighest = surfaceBright,
        inverseSurface = text, inverseOnSurface = background,
        outline = outline, outlineVariant = outline,
        error = error, onError = onError,
        errorContainer = errorContainer, onErrorContainer = error,
        scrim = scrim
    )
} else {
    lightColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = accentContainer, onPrimaryContainer = accentBright,
        inversePrimary = accentContainer,
        secondary = textSecondary, onSecondary = background,
        secondaryContainer = surfaceVariant, onSecondaryContainer = text,
        tertiary = brand, onTertiary = Color.White,
        tertiaryContainer = brandContainer, onTertiaryContainer = brand,
        background = background, onBackground = text,
        surface = surface, onSurface = text,
        surfaceVariant = surfaceVariant, onSurfaceVariant = textSecondary,
        surfaceTint = accent,
        surfaceContainerLowest = surfaceBright,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceVariant,
        surfaceContainerHighest = surfaceVariant,
        inverseSurface = text, inverseOnSurface = background,
        outline = outline, outlineVariant = outline,
        error = error, onError = onError,
        errorContainer = errorContainer, onErrorContainer = error,
        scrim = scrim
    )
}

val LocalDiscreetMode = staticCompositionLocalOf { false }

private val LocalKitsuneColors = staticCompositionLocalOf { DarkColors }
private val LocalKitsuneTextStyles = staticCompositionLocalOf { KitsuneTextStylesDefault }
private val LocalKitsuneSpacing = staticCompositionLocalOf { KitsuneSpacingDefault }
private val LocalKitsuneShapes = staticCompositionLocalOf { KitsuneShapesDefault }
private val LocalKitsuneMotion = staticCompositionLocalOf { KitsuneMotionDefault }
private val LocalKitsuneElevation = staticCompositionLocalOf { KitsuneElevationDefault }

/**
 * Accessors, mirroring MaterialTheme's shape so the two read the same at a call site:
 * `KitsuneTheme.colors.textDim`, `KitsuneTheme.spacing.gutter`, `KitsuneTheme.type.eyebrow`.
 */
object KitsuneTheme {
    val colors: KitsuneColors
        @Composable @ReadOnlyComposable get() = LocalKitsuneColors.current
    val type: KitsuneTextStyles
        @Composable @ReadOnlyComposable get() = LocalKitsuneTextStyles.current
    val spacing: KitsuneSpacing
        @Composable @ReadOnlyComposable get() = LocalKitsuneSpacing.current
    val shape: KitsuneShapes
        @Composable @ReadOnlyComposable get() = LocalKitsuneShapes.current
    val motion: KitsuneMotion
        @Composable @ReadOnlyComposable get() = LocalKitsuneMotion.current
    val elevation: KitsuneElevation
        @Composable @ReadOnlyComposable get() = LocalKitsuneElevation.current
}

@Composable
fun KitsuneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    discreet: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors: KitsuneColors = when {
        discreet -> discreetColors(darkTheme)
        darkTheme -> DarkColors
        else -> LightColors
    }
    val typography: Typography = if (discreet) KitsuneDiscreetTypography else KitsuneTypography

    // The app draws edge to edge, so the system bars sit on our own background: their icons must be
    // dark on the light theme, or the clock and battery vanish into the cream.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            var context = view.context
            while (context is ContextWrapper && context !is Activity) context = context.baseContext
            (context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalDiscreetMode provides discreet,
        LocalKitsuneColors provides colors,
        LocalKitsuneTextStyles provides KitsuneTextStylesDefault,
        LocalKitsuneSpacing provides KitsuneSpacingDefault,
        LocalKitsuneShapes provides KitsuneShapesDefault,
        LocalKitsuneMotion provides KitsuneMotionDefault,
        LocalKitsuneElevation provides KitsuneElevationDefault
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = typography,
            // Material's own shape scale is aligned to ours so a stock Card/Dialog/Menu picks up
            // the app's radii without every call site passing `shape =`.
            shapes = Shapes(
                extraSmall = KitsuneShapesDefault.sm,
                small = KitsuneShapesDefault.sm,
                medium = KitsuneShapesDefault.md,
                large = KitsuneShapesDefault.lg,
                extraLarge = KitsuneShapesDefault.lg
            ),
            content = content
        )
    }
}
