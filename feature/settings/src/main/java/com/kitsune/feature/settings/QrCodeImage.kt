package com.kitsune.feature.settings

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** Renders [payload] as a black-on-white QR bitmap — used for the account-transfer pairing code
 * (see core:transfer's TransferPairing). Pure zxing-core, no camera/Play-Services dependency. */
@Composable
fun QrCodeImage(payload: String, sizePx: Int, modifier: Modifier = Modifier) {
    val bitmap = remember(payload, sizePx) { generateQrBitmap(payload, sizePx) }
    Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = modifier)
}

private fun generateQrBitmap(payload: String, sizePx: Int): Bitmap {
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap
}
