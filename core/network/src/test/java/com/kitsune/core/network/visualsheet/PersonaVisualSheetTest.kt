package com.kitsune.core.network.visualsheet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaVisualSheetTest {

    private val sheet = PersonaVisualSheet(
        physicalTraits = "Tall, athletic build, long silver hair, emerald green eyes",
        artStyle = "semi-realistic digital painting, soft cinematic lighting",
        colorPalette = "emerald green, silver, deep navy blue",
        defaultOutfit = "black leather jacket over a navy turtleneck"
    )

    @Test
    fun `encode then decode round-trips to an equal sheet`() {
        val decoded = PersonaVisualSheet.decodeOrNull(sheet.encode())

        assertEquals(sheet, decoded)
    }

    @Test
    fun `decodeOrNull returns null for null input`() {
        assertNull(PersonaVisualSheet.decodeOrNull(null))
    }

    @Test
    fun `decodeOrNull returns null for blank input`() {
        assertNull(PersonaVisualSheet.decodeOrNull("   "))
    }

    @Test
    fun `decodeOrNull returns null for malformed json instead of throwing`() {
        assertNull(PersonaVisualSheet.decodeOrNull("not json at all"))
    }

    @Test
    fun `EMPTY sheet is blank`() {
        assertTrue(PersonaVisualSheet.EMPTY.isBlank)
    }

    @Test
    fun `a sheet with any field filled is not blank`() {
        assertFalse(sheet.isBlank)
        assertFalse(PersonaVisualSheet.EMPTY.copy(artStyle = "anime").isBlank)
    }

    @Test
    fun `toPromptContext includes the name and every non-blank field`() {
        val context = sheet.toPromptContext("Aria")

        assertTrue(context.contains("Aria"))
        assertTrue(context.contains(sheet.physicalTraits))
        assertTrue(context.contains(sheet.artStyle))
        assertTrue(context.contains(sheet.colorPalette))
        assertTrue(context.contains(sheet.defaultOutfit))
    }

    @Test
    fun `toPromptContext omits blank fields`() {
        val partial = PersonaVisualSheet(physicalTraits = "red hair", artStyle = "", colorPalette = "", defaultOutfit = "")

        val context = partial.toPromptContext("Aria")

        assertTrue(context.contains("red hair"))
        assertFalse(context.contains("Art style"))
        assertFalse(context.contains("Color palette"))
        assertFalse(context.contains("Default outfit"))
    }
}
