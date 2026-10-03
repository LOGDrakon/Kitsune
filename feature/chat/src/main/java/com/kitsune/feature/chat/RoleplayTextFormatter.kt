package com.kitsune.feature.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Common roleplay text conventions: `**bold**` for emphasis, `*italic*` for actions/emphasis,
 * `(asides)` for out-of-character notes or stage directions — styled distinctly so they read at
 * a glance instead of as raw punctuation.
 */
fun formatRoleplayText(raw: String, actionColor: Color, asideColor: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    val plainText = StringBuilder()
    val styles = mutableListOf<Triple<Int, Int, SpanStyle>>()

    while (i < raw.length) {
        when {
            raw.startsWith("**", i) -> {
                val end = raw.indexOf("**", i + 2)
                if (end == -1) {
                    plainText.append(raw.substring(i))
                    i = raw.length
                } else {
                    val content = raw.substring(i + 2, end)
                    val start = plainText.length
                    plainText.append(content)
                    styles.add(Triple(start, plainText.length, SpanStyle(fontWeight = FontWeight.Bold)))
                    i = end + 2
                }
            }

            raw[i] == '*' -> {
                val end = raw.indexOf('*', i + 1)
                if (end == -1) {
                    plainText.append(raw.substring(i))
                    i = raw.length
                } else {
                    val content = raw.substring(i + 1, end)
                    val start = plainText.length
                    plainText.append(content)
                    styles.add(Triple(start, plainText.length, SpanStyle(fontStyle = FontStyle.Italic, color = actionColor)))
                    i = end + 1
                }
            }

            raw[i] == '(' -> {
                val end = raw.indexOf(')', i + 1)
                if (end == -1) {
                    plainText.append(raw.substring(i))
                    i = raw.length
                } else {
                    val content = raw.substring(i, end + 1)
                    val start = plainText.length
                    plainText.append(content)
                    styles.add(Triple(start, plainText.length, SpanStyle(fontStyle = FontStyle.Italic, color = asideColor)))
                    i = end + 1
                }
            }

            else -> {
                plainText.append(raw[i])
                i++
            }
        }
    }

    append(plainText.toString())
    styles.forEach { (start, end, style) ->
        addStyle(style, start, end)
    }
}
