package com.kitsune.core.network.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

private val lenientJson = Json { isLenient = true; ignoreUnknownKeys = true }

/**
 * Parses the JSON object produced by an AI response, tolerating markdown fences, surrounding
 * prose, trailing commas, raw newlines and unescaped quotes.
 *
 * When [knownKeys] is provided, a last-resort salvage pass extracts the value of each known key
 * directly from the raw text, so a malformed or truncated response (e.g. "Unterminated string")
 * never throws. After a successful parse, any known key still missing (e.g. because the object
 * was cut early by quote desync) is also recovered from the raw text, so no field is silently
 * dropped.
 */
object AiJsonParser {

    fun parseObject(raw: String, knownKeys: List<String> = emptyList()): JsonObject {
        val text = stripCodeFence(raw)
        val extracted = extractJsonObject(text)

        val parsed = runCatching { parseLenient(extracted) }
            .recoverCatching { error ->
                if (knownKeys.isEmpty()) throw error
                salvageKnownKeys(text, knownKeys)
            }
            .getOrThrow()

        if (knownKeys.isEmpty()) return parsed
        val missing = knownKeys.filterNot { parsed.containsKey(it) }
        if (missing.isEmpty()) return parsed

        val salvaged = runCatching { salvageKnownKeys(text, knownKeys, missing) }.getOrNull()
            ?: return parsed

        return buildJsonObject {
            parsed.forEach { (key, value) -> put(key, value) }
            salvaged.forEach { (key, value) -> if (!parsed.containsKey(key)) put(key, value) }
        }
    }

    /** Mirrors the old Gson `JsonElement.asString`-ish flattening: joins arrays, empty for null. */
    fun JsonObject.stringField(key: String): String = this[key]?.toPlainString().orEmpty()

    private fun JsonElement.toPlainString(): String = when (this) {
        is JsonNull -> ""
        is JsonPrimitive -> content.trim()
        is JsonArray -> joinToString("\n") { it.toPlainString() }.trim()
        is JsonObject -> toString().trim()
    }

    private fun parseLenient(json: String): JsonObject =
        runCatching { lenientJson.parseToJsonElement(json).jsonObject }
            .recoverCatching {
                val repaired = removeTrailingCommas(repairStringContents(json))
                lenientJson.parseToJsonElement(repaired).jsonObject
            }
            .getOrThrow()

    /**
     * Rebuilds a [JsonObject] by slicing the raw text between known key markers (`"key":`).
     * Each value runs until the next known key or the end of the text, so unescaped quotes, raw
     * newlines and truncation inside a value cannot break the extraction. Only [keysToExtract]
     * end up in the result, but every key in [knownKeys] is used as a slice boundary.
     */
    private fun salvageKnownKeys(
        json: String,
        knownKeys: List<String>,
        keysToExtract: List<String> = knownKeys
    ): JsonObject {
        val markers = knownKeys
            .mapNotNull { key -> findKeyMarker(json, key)?.let { (matchStart, valueStart) -> Triple(key, matchStart, valueStart) } }
            .sortedBy { it.second }

        require(markers.isNotEmpty()) { "No known keys found in AI response" }

        return buildJsonObject {
            markers.forEachIndexed { index, (key, _, valueStart) ->
                if (key !in keysToExtract) return@forEachIndexed
                val valueEnd = markers.getOrNull(index + 1)?.second ?: json.length
                put(key, JsonPrimitive(cleanSalvagedValue(json.substring(valueStart, valueEnd))))
            }
        }
    }

    /** Finds `"key"` followed by `:` preceded by `{`, `,` or a line start, returns (matchStart, valueStart). */
    private fun findKeyMarker(json: String, key: String): Pair<Int, Int>? {
        val pattern = Regex("(?:^|[{,\\n\\r])\\s*\"" + Regex.escape(key) + "\"\\s*:", RegexOption.MULTILINE)
        val match = pattern.find(json) ?: return null
        val quoteIndex = match.value.indexOf('"')
        return (match.range.first + quoteIndex) to (match.range.last + 1)
    }

    private fun cleanSalvagedValue(rawValue: String): String {
        var value = rawValue.trim()
        while (value.endsWith(",") || value.endsWith("}") || value.endsWith("]")) {
            value = value.dropLast(1).trimEnd()
        }
        value = value.removePrefix("\"").removeSuffix("\"").trim()
        return unescapeJsonString(value)
    }

    private fun unescapeJsonString(value: String): String = buildString(value.length) {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '\\' && index + 1 < value.length) {
                when (val next = value[index + 1]) {
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    '"', '\\', '/' -> append(next)
                    'u' -> {
                        val hex = value.drop(index + 2).take(4)
                        val code = hex.takeIf { it.length == 4 }?.toIntOrNull(16)
                        if (code != null) {
                            append(code.toChar())
                            index += 4
                        } else {
                            append(next)
                        }
                    }
                    else -> {
                        append(char)
                        append(next)
                    }
                }
                index += 2
            } else {
                append(char)
                index++
            }
        }
    }

    private fun stripCodeFence(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed

        val firstLineEnd = trimmed.indexOf('\n')
        if (firstLineEnd == -1) return trimmed.removeSurrounding("```").trim()

        val withoutOpeningFence = trimmed.substring(firstLineEnd + 1)
        return withoutOpeningFence.removeSuffix("```").trim()
    }

    private fun extractJsonObject(text: String): String {
        var start = -1
        var depth = 0
        var inString = false
        var escaped = false

        text.forEachIndexed { index, char ->
            if (start == -1) {
                if (char == '{') {
                    start = index
                    depth = 1
                }
                return@forEachIndexed
            }

            if (escaped) {
                escaped = false
                return@forEachIndexed
            }

            when (char) {
                '\\' -> if (inString) escaped = true
                '"' -> inString = !inString
                '{' -> if (!inString) depth++
                '}' -> if (!inString) {
                    depth--
                    if (depth == 0) return text.substring(start, index + 1)
                }
            }
        }

        // Truncated object (no balanced closing brace): keep everything from the opening brace
        // so no field is lost, dropping any leading prose.
        return if (start >= 0) text.substring(start) else text.trim()
    }

    private fun repairStringContents(json: String): String = buildString(json.length) {
        var inString = false
        var escaped = false

        json.forEachIndexed { index, char ->
            if (escaped) {
                append(char)
                escaped = false
                return@forEachIndexed
            }

            when (char) {
                '\\' -> {
                    append(char)
                    if (inString) escaped = true
                }
                '"' -> {
                    if (inString && !isClosingQuote(json, index)) {
                        append("\\\"")
                    } else {
                        append(char)
                        inString = !inString
                    }
                }
                '\n' -> if (inString) append("\\n") else append(char)
                '\r' -> if (inString) append("\\r") else append(char)
                '\t' -> if (inString) append("\\t") else append(char)
                else -> append(char)
            }
        }
    }

    private fun isClosingQuote(json: String, quoteIndex: Int): Boolean {
        val nextIndex = nextNonWhitespaceIndex(json, quoteIndex + 1) ?: return true
        return when (json[nextIndex]) {
            ':', '}', ']' -> true
            ',' -> commaEndsStringValue(json, nextIndex)
            else -> false
        }
    }

    private fun commaEndsStringValue(json: String, commaIndex: Int): Boolean {
        val nextIndex = nextNonWhitespaceIndex(json, commaIndex + 1) ?: return true
        if (json[nextIndex] in setOf('}', ']')) return true
        if (json[nextIndex] != '"') return false

        val keyEnd = findClosingQuote(json, nextIndex) ?: return false
        val afterKey = nextNonWhitespaceIndex(json, keyEnd + 1) ?: return false
        // ':' -> next token is an object key; ',' or ']' -> next token is an array element.
        return json[afterKey] == ':' || json[afterKey] == ',' || json[afterKey] == ']'
    }

    private fun findClosingQuote(json: String, startQuoteIndex: Int): Int? {
        var escaped = false
        for (index in startQuoteIndex + 1 until json.length) {
            val char = json[index]
            if (escaped) {
                escaped = false
                continue
            }
            when (char) {
                '\\' -> escaped = true
                '"' -> return index
            }
        }
        return null
    }

    private fun nextNonWhitespaceIndex(text: String, startIndex: Int): Int? =
        (startIndex until text.length).firstOrNull { !text[it].isWhitespace() }

    private fun removeTrailingCommas(json: String): String = buildString(json.length) {
        var inString = false
        var escaped = false
        var index = 0

        while (index < json.length) {
            val char = json[index]

            if (escaped) {
                append(char)
                escaped = false
                index++
                continue
            }

            when (char) {
                '\\' -> {
                    append(char)
                    if (inString) escaped = true
                }
                '"' -> {
                    append(char)
                    inString = !inString
                }
                ',' -> {
                    val nextNonWhitespace = json.asSequence().drop(index + 1).firstOrNull { !it.isWhitespace() }
                    if (inString || nextNonWhitespace !in setOf('}', ']')) append(char)
                }
                else -> append(char)
            }
            index++
        }
    }
}
