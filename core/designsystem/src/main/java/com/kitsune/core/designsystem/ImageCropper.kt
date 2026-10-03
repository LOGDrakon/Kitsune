package com.kitsune.core.designsystem

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Composable
fun ImageCropper(
    bytes: ByteArray,
    onCrop: (ByteArray) -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    bitmap?.let { bmp ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged { containerSize = it }
        ) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            val maxX = (containerSize.width * (scale - 1)) / 2
                            val maxY = (containerSize.height * (scale - 1)) / 2
                            offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                            offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                        }
                    },
                contentScale = ContentScale.Fit
            )

            FloatingActionButton(
                onClick = {
                    val cropped = cropBitmap(bmp, scale, offsetX, offsetY, containerSize)
                    val out = java.io.ByteArrayOutputStream()
                    cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
                    onCrop(out.toByteArray())
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.designsystem_image_cropper_validate))
            }
        }
    }
}

private fun cropBitmap(
    source: Bitmap,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    containerSize: IntSize
): Bitmap {
    if (scale <= 1f) return source

    val imgAspect = source.width.toFloat() / source.height.toFloat()
    val containerAspect = containerSize.width.toFloat() / containerSize.height.toFloat()

    val drawWidth: Float
    val drawHeight: Float
    if (imgAspect > containerAspect) {
        drawHeight = containerSize.height.toFloat()
        drawWidth = drawHeight * imgAspect
    } else {
        drawWidth = containerSize.width.toFloat()
        drawHeight = drawWidth / imgAspect
    }

    val scaledWidth = drawWidth * scale
    val scaledHeight = drawHeight * scale

    val cropX = ((scaledWidth - containerSize.width) / 2 - offsetX) / scaledWidth * source.width
    val cropY = ((scaledHeight - containerSize.height) / 2 - offsetY) / scaledHeight * source.height
    val cropW = containerSize.width.toFloat() / scaledWidth * source.width
    val cropH = containerSize.height.toFloat() / scaledHeight * source.height

    val left = cropX.coerceIn(0f, source.width.toFloat())
    val top = cropY.coerceIn(0f, source.height.toFloat())
    val right = (left + cropW).coerceAtMost(source.width.toFloat())
    val bottom = (top + cropH).coerceAtMost(source.height.toFloat())

    return Bitmap.createBitmap(source, left.toInt(), top.toInt(), (right - left).toInt(), (bottom - top).toInt())
}
