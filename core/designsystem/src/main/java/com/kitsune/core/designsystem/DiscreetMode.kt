package com.kitsune.core.designsystem

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp

/**
 * Applies a blur when the device runs Android 12+ (API 31), where Compose's [Modifier.blur]
 * is supported. On older devices it falls back to a very low alpha so the content stays
 * unreadable from a distance without using an unsupported API.
 */
fun Modifier.discreetBlur(radius: Dp): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        blur(radius)
    } else {
        alpha(0.08f)
    }
