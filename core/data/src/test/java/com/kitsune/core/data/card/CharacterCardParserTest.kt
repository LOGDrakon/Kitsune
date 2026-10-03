package com.kitsune.core.data.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.CRC32

/**
 * Character cards come from the wild: written by hand, by a dozen tools, across two spec versions,
 * re-encoded by whichever uploader they passed through. The parser's job is not to be strict — it is
 * to get as much as possible out of a file the user already believes in, and to never crash on one
 * that turns out to be an ordinary photo.
 */
class CharacterCardParserTest {

    private val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply { update(typeBytes); update(data) }.value.toInt()
        fun int(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())
        return int(data.size) + typeBytes + data + int(crc)
    }

    private fun textChunk(keyword: String, text: String) =
        chunk("tEXt", keyword.toByteArray(Charsets.US_ASCII) + byteArrayOf(0) + text.toByteArray(Charsets.ISO_8859_1))

    /** A minimal but structurally valid PNG carrying the given text chunks. */
    private fun png(vararg text: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(signature)
        out.write(chunk("IHDR", ByteArray(13)))
        text.forEach { (k, v) -> out.write(textChunk(k, v)) }
        out.write(chunk("IEND", ByteArray(0)))
        return out.toByteArray()
    }

    private fun b64(s: String) = Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))

    private fun cardJson(name: String, spec: String = "chara_card_v2") = """
        {"spec":"$spec","spec_version":"2.0","data":{
          "name":"$name","description":"Une mercenaire","personality":"Taciturne",
          "scenario":"Une taverne","first_mes":"Elle lève les yeux.","mes_example":"<START>",
          "alternate_greetings":["Sur le port.","Trois ans plus tard."],
          "character_book":{"name":"Monde","entries":[{"keys":["la Louve"],"content":"Son surnom.","name":"Surnom"}]},
          "tags":["fantasy","slow-burn"],"creator":"someone"}}
    """.trimIndent()

    // --- The happy paths ---

    @Test
    fun `a V2 card is read`() {
        val card = CharacterCardParser.parse(png("chara" to b64(cardJson("Aria"))))
        requireNotNull(card)
        assertEquals("Aria", card.name)
        assertEquals("Une mercenaire", card.description)
        assertEquals("Elle lève les yeux.", card.firstMessage)
    }

    @Test
    fun `a V3 card is read`() {
        val card = CharacterCardParser.parse(png("ccv3" to b64(cardJson("Bram", "chara_card_v3"))))
        assertEquals("Bram", requireNotNull(card).name)
    }

    @Test
    fun `when a card carries both chunks, V3 wins`() {
        // Chub ships both for compatibility; every other reader prefers V3, and disagreeing would
        // silently import an older version of the same character.
        val card = CharacterCardParser.parse(
            png("chara" to b64(cardJson("OldV2")), "ccv3" to b64(cardJson("NewV3", "chara_card_v3")))
        )
        assertEquals("NewV3", requireNotNull(card).name)
    }

    @Test
    fun `the two fields nobody else imports survive`() {
        // Alternate greetings become entry scenes and the character book becomes lore entries — both
        // exist in Kitsune, which is the whole reason this import is worth more here than elsewhere.
        val card = requireNotNull(CharacterCardParser.parse(png("chara" to b64(cardJson("Aria")))))
        assertEquals(listOf("Sur le port.", "Trois ans plus tard."), card.alternateGreetings)
        assertEquals("Surnom", requireNotNull(card.characterBook).entries.single().name)
        assertEquals(listOf("la Louve"), card.characterBook!!.entries.single().keys)
    }

    // --- The wild ---

    @Test
    fun `an ordinary photo yields nothing rather than an error`() {
        assertNull(CharacterCardParser.parse(png()))
    }

    @Test
    fun `a file that is not a PNG yields nothing`() {
        assertNull(CharacterCardParser.parse("this is a text file".toByteArray()))
        assertNull(CharacterCardParser.parse(ByteArray(0)))
    }

    @Test
    fun `a truncated file does not crash the import`() {
        val full = png("chara" to b64(cardJson("Aria")))
        // Cut mid-chunk: a declared length that runs past the end of the file must stop the walk,
        // not index into nothing.
        assertNull(CharacterCardParser.parse(full.copyOfRange(0, full.size / 2)))
    }

    @Test
    fun `a chunk that is not valid base64 yields nothing`() {
        assertNull(CharacterCardParser.parse(png("chara" to "!!!not base64!!!")))
    }

    @Test
    fun `a very old card with no envelope still imports`() {
        // Pre-spec TavernAI cards are the bare character object.
        val bare = """{"name":"Vieux","description":"Sans enveloppe","first_mes":"Bonjour."}"""
        val card = CharacterCardParser.parse(png("chara" to b64(bare)))
        assertEquals("Vieux", requireNotNull(card).name)
    }

    @Test
    fun `a card whose tool forgot the base64 step still imports`() {
        val card = CharacterCardParser.parse(png("chara" to cardJson("Direct")))
        assertEquals("Direct", requireNotNull(card).name)
    }

    @Test
    fun `a malformed character book costs the lore, never the character`() {
        // Losing the lore is recoverable by hand; losing the character is not.
        val json = """{"spec":"chara_card_v2","data":{"name":"Aria","character_book":"oops"}}"""
        val card = CharacterCardParser.parse(png("chara" to b64(json)))
        assertEquals("Aria", requireNotNull(card).name)
        assertNull(card.characterBook)
    }

    @Test
    fun `unknown fields from other tools are ignored`() {
        val json = """{"spec":"chara_card_v3","data":{"name":"Aria","nickname":"A","assets":[],"group_only_greetings":[]}}"""
        assertEquals("Aria", requireNotNull(CharacterCardParser.parse(png("chara" to b64(json)))).name)
    }

    // --- Writing ---

    @Test
    fun `an exported card round-trips through the parser`() {
        val source = png()
        val written = requireNotNull(CharacterCardParser.embed(source, cardJson("Exportée")))
        val card = requireNotNull(CharacterCardParser.parse(written))
        assertEquals("Exportée", card.name)
        assertEquals(listOf("Sur le port.", "Trois ans plus tard."), card.alternateGreetings)
    }

    @Test
    fun `an exported card carries both chunks so any reader finds one`() {
        val written = requireNotNull(CharacterCardParser.embed(png(), cardJson("Aria")))
        val text = String(written, Charsets.ISO_8859_1)
        assertTrue("V2 readers need chara", text.contains("chara"))
        assertTrue("V3 readers prefer ccv3", text.contains("ccv3"))
    }

    @Test
    fun `re-exporting does not accumulate stale copies`() {
        val once = requireNotNull(CharacterCardParser.embed(png(), cardJson("Aria")))
        val twice = requireNotNull(CharacterCardParser.embed(once, cardJson("Aria renommée")))
        assertEquals("Aria renommée", requireNotNull(CharacterCardParser.parse(twice)).name)
        // Two writes must not leave four card chunks behind.
        val occurrences = Regex("ccv3").findAll(String(twice, Charsets.ISO_8859_1)).count()
        assertEquals(1, occurrences)
    }

    @Test
    fun `embedding into something that is not a PNG fails cleanly`() {
        assertNull(CharacterCardParser.embed("not a png".toByteArray(), cardJson("Aria")))
    }
}
