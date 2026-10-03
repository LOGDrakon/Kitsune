package com.kitsune.core.designsystem

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

/**
 * Renders already-decrypted image bytes (see `core:security`'s `EncryptedImageStore`) — this
 * module never talks to encryption directly, callers decrypt and hand over raw bytes so the
 * design system stays a pure "given data, render UI" layer.
 */
@Composable
fun DecryptedImage(
    bytes: ByteArray?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    if (bytes == null) return
    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    bitmap?.let {
        Image(bitmap = it, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
    }
}
