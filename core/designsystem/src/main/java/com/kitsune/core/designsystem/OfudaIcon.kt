package com.kitsune.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Kitsune's in-app currency icon (the Ofuda charm). Use instead of spelling out "Ofuda"/"credit"
 * wherever a compact amount is shown (chat balance, cost badges) — see [OfudaAmount].
 */
@Composable
fun OfudaIcon(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp
) {
    Image(
        painter = painterResource(R.drawable.ic_ofuda),
        contentDescription = "Ofuda",
        modifier = modifier.size(size)
    )
}

/** Icon + number, e.g. for "🪙 120" balance chips or "🪙 2" per-turn cost badges. */
@Composable
fun OfudaAmount(
    amount: Int,
    modifier: Modifier = Modifier,
    iconSize: Dp = 16.dp,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    textColor: androidx.compose.ui.graphics.Color = LocalContentColor.current
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        OfudaIcon(size = iconSize)
        Spacer(Modifier.width(4.dp))
        Text(text = amount.toString(), style = textStyle, color = textColor)
    }
}
