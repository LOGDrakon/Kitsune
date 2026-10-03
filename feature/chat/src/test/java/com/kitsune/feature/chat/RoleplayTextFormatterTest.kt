package com.kitsune.feature.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Test

class RoleplayTextFormatterTest {

    private val actionColor = Color(0xFF888888)
    private val asideColor = Color(0xFF666666)

    @Test
    fun `plain text has no spans`() {
        val result = formatRoleplayText("hello world", actionColor, asideColor)

        assertEquals("hello world", result.text)
        assertEquals(0, result.spanStyles.size)
    }

    @Test
    fun `single asterisks become italic and strip the markers`() {
        val result = formatRoleplayText("she *smiles softly* at you", actionColor, asideColor)

        assertEquals("she smiles softly at you", result.text)
        val span = result.spanStyles.single()
        assertEquals(FontStyle.Italic, span.item.fontStyle)
        assertEquals(actionColor, span.item.color)
        assertEquals("smiles softly", result.text.substring(span.start, span.end))
    }

    @Test
    fun `double asterisks become bold and strip the markers`() {
        val result = formatRoleplayText("this is **very important**, ok?", actionColor, asideColor)

        assertEquals("this is very important, ok?", result.text)
        val span = result.spanStyles.single()
        assertEquals(FontWeight.Bold, span.item.fontWeight)
        assertEquals("very important", result.text.substring(span.start, span.end))
    }

    @Test
    fun `parentheses keep the parens but get styled as an aside`() {
        val result = formatRoleplayText("Sure (I think this is a bad idea).", actionColor, asideColor)

        assertEquals("Sure (I think this is a bad idea).", result.text)
        val span = result.spanStyles.single()
        assertEquals(FontStyle.Italic, span.item.fontStyle)
        assertEquals(asideColor, span.item.color)
        assertEquals("(I think this is a bad idea)", result.text.substring(span.start, span.end))
    }

    @Test
    fun `unclosed markers are left as plain literal text`() {
        val result = formatRoleplayText("this *never closes", actionColor, asideColor)

        assertEquals("this *never closes", result.text)
        assertEquals(0, result.spanStyles.size)
    }

    @Test
    fun `multiple markers in one message are all styled independently`() {
        val result = formatRoleplayText("**Bold** then *italic* then (aside)", actionColor, asideColor)

        assertEquals(3, result.spanStyles.size)
        assertEquals("Bold then italic then (aside)", result.text)
    }
}
