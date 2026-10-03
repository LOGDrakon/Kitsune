package com.kitsune.core.network.json

import com.kitsune.core.network.json.AiJsonParser.stringField
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class AiJsonParserTest {

    @Test
    fun parseObject_acceptsFencedJson() {
        val json = AiJsonParser.parseObject(
            """
            ```json
            {"name":"Kitsune"}
            ```
            """.trimIndent()
        )

        assertEquals("Kitsune", json.stringField("name"))
    }

    @Test
    fun parseObject_extractsJsonFromExtraText() {
        val json = AiJsonParser.parseObject(
            """
            Voici le JSON demandé :
            {
              "name": "Kitsune",
              "nested": {"value": "with } inside"}
            }
            Bonne création !
            """.trimIndent()
        )

        assertEquals("Kitsune", json.stringField("name"))
        assertEquals("with } inside", json["nested"]!!.jsonObject.stringField("value"))
    }

    @Test
    fun parseObject_acceptsTrailingCommas() {
        val json = AiJsonParser.parseObject(
            """
            {
              "name": "Kitsune",
              "tags": ["fox", "ai",],
            }
            """.trimIndent()
        )

        assertEquals("Kitsune", json.stringField("name"))
        assertEquals("ai", json["tags"]!!.jsonArray[1].jsonPrimitive.content)
    }

    @Test
    fun parseObject_acceptsRawNewlinesInsideStringValues() {
        val json = AiJsonParser.parseObject(
            """
            {
              "name": "Kitsune",
              "firstMessage": "Salut !
            Je suis prête pour l'aventure.",
              "exampleDialogues": "User: Bonjour
            Kitsune: Bonjour à toi !"
            }
            """.trimIndent()
        )

        assertEquals("Kitsune", json.stringField("name"))
        assertEquals("Salut !\nJe suis prête pour l'aventure.", json.stringField("firstMessage"))
        assertEquals("User: Bonjour\nKitsune: Bonjour à toi !", json.stringField("exampleDialogues"))
    }

    @Test
    fun parseObject_acceptsUnescapedQuotesInsideDescription() {
        val json = AiJsonParser.parseObject(
            """
            {
              "name": "Kitsune",
              "description": "Elle est surnommée "la Renarde", une guide rusée et élégante.",
              "personality": "Joueuse"
            }
            """.trimIndent()
        )

        assertEquals("Elle est surnommée \"la Renarde\", une guide rusée et élégante.", json.stringField("description"))
        assertEquals("Joueuse", json.stringField("personality"))
    }

    @Test
    fun parseObject_salvagesTruncatedResponseWithKnownKeys() {
        val json = AiJsonParser.parseObject(
            """
            {
              "name": "Kitsune",
              "description": "Une renarde mystérieuse qui adore les défis
            """.trimIndent(),
            knownKeys = listOf("name", "description", "personality")
        )

        assertEquals("Kitsune", json.stringField("name"))
        assertEquals("Une renarde mystérieuse qui adore les défis", json.stringField("description"))
    }

    @Test
    fun parseObject_salvagesUnterminatedStringWithKnownKeys() {
        val json = AiJsonParser.parseObject(
            """
            {
              "name": "Kitsune",
              "description": "Elle dit "bonjour", puis "salut" et enfin "coucou, ça va ?,
              "personality": "Joueuse et rusée"
            }
            """.trimIndent(),
            knownKeys = listOf("name", "description", "personality")
        )

        assertEquals("Kitsune", json.stringField("name"))
        assertEquals("Elle dit \"bonjour\", puis \"salut\" et enfin \"coucou, ça va ?", json.stringField("description"))
        assertEquals("Joueuse et rusée", json.stringField("personality"))
    }

    @Test
    fun parseObject_salvagesEscapedSequencesWithKnownKeys() {
        val json = AiJsonParser.parseObject(
            """{"description": "Ligne 1\nLigne \"deux\", et une "citation brute" cassée, "firstMessage": "Salut !"}""",
            knownKeys = listOf("description", "firstMessage")
        )

        assertEquals("Ligne 1\nLigne \"deux\", et une \"citation brute\" cassée", json.stringField("description"))
        assertEquals("Salut !", json.stringField("firstMessage"))
    }

    @Test
    fun parseObject_recoversKeysLostByEarlyObjectCut() {
        // An unescaped quote desyncs the string tracking, so the "}" inside the description used
        // to end the extracted object early and silently drop every later field.
        val json = AiJsonParser.parseObject(
            """
            {
              "name": "Kitsune",
              "description": "Elle adore le symbole "} et les énigmes.",
              "personality": "Curieuse",
              "scenario": "Une forêt enchantée",
              "firstMessage": "Bienvenue voyageur !",
              "exampleDialogues": "User: Salut
            Kitsune: Salut à toi !"
            }
            """.trimIndent(),
            knownKeys = listOf("name", "description", "personality", "scenario", "firstMessage", "exampleDialogues")
        )

        assertEquals("Kitsune", json.stringField("name"))
        assertEquals("Curieuse", json.stringField("personality"))
        assertEquals("Une forêt enchantée", json.stringField("scenario"))
        assertEquals("Bienvenue voyageur !", json.stringField("firstMessage"))
        assertEquals("User: Salut\nKitsune: Salut à toi !", json.stringField("exampleDialogues"))
    }

    @Test
    fun parseObject_keepsAllPersonaKeysOnTruncatedMidValueResponse() {
        // Simulates a max_tokens cutoff in the middle of the last value.
        val json = AiJsonParser.parseObject(
            """
            {
              "name": "Kitsune",
              "description": "Une renarde espiègle.",
              "personality": "Maligne, loyale.",
              "scenario": "Un sanctuaire caché dans la montagne.",
              "firstMessage": "Tu as trouvé mon sanctuaire...",
              "exampleDialogues": "User: Qui es-tu ?
            Kitsune: Je suis la gardienne de
            """.trimIndent(),
            knownKeys = listOf("name", "description", "personality", "scenario", "firstMessage", "exampleDialogues")
        )

        assertEquals("Kitsune", json.stringField("name"))
        assertEquals("Une renarde espiègle.", json.stringField("description"))
        assertEquals("Maligne, loyale.", json.stringField("personality"))
        assertEquals("Un sanctuaire caché dans la montagne.", json.stringField("scenario"))
        assertEquals("Tu as trouvé mon sanctuaire...", json.stringField("firstMessage"))
        assertEquals("User: Qui es-tu ?\nKitsune: Je suis la gardienne de", json.stringField("exampleDialogues"))
    }

    @Test
    fun parseObject_ignoresProseBeforeTruncatedObject() {
        val json = AiJsonParser.parseObject(
            """
            Voici la fiche "name" demandée :
            {
              "name": "Kitsune",
              "description": "Une renarde
            """.trimIndent(),
            knownKeys = listOf("name", "description")
        )

        assertEquals("Kitsune", json.stringField("name"))
        assertEquals("Une renarde", json.stringField("description"))
    }
}
