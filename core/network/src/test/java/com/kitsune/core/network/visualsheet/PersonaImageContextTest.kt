package com.kitsune.core.network.visualsheet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaImageContextTest {

    @Test
    fun `uses the structured visual sheet when one is available`() {
        val sheet = PersonaVisualSheet(
            physicalTraits = "short blue hair, amber eyes",
            artStyle = "watercolor",
            colorPalette = "blue, amber, white",
            defaultOutfit = "a white sundress"
        )

        val context = buildPersonaImageContext("Mika", "A cheerful barista", sheet.encode())

        assertTrue(context.contains("Mika"))
        assertTrue(context.contains("short blue hair, amber eyes"))
        assertTrue(context.contains("watercolor"))
        // The old free-text fallback (name: description) must not be used when a sheet exists.
        assertTrue(!context.contains("A cheerful barista"))
    }

    @Test
    fun `falls back to name and short description when there is no visual sheet`() {
        val context = buildPersonaImageContext("Mika", "A cheerful barista", null)

        assertEquals("Mika: A cheerful barista", context)
    }

    @Test
    fun `falls back to name and short description when the visual sheet json is blank`() {
        val context = buildPersonaImageContext("Mika", "A cheerful barista", "   ")

        assertEquals("Mika: A cheerful barista", context)
    }

    @Test
    fun `falls back to name and short description when the decoded sheet is entirely blank`() {
        val context = buildPersonaImageContext("Mika", "A cheerful barista", PersonaVisualSheet.EMPTY.encode())

        assertEquals("Mika: A cheerful barista", context)
    }

    @Test
    fun `falls back to name and short description when the visual sheet json is malformed`() {
        val context = buildPersonaImageContext("Mika", "A cheerful barista", "{not valid json")

        assertEquals("Mika: A cheerful barista", context)
    }
}
