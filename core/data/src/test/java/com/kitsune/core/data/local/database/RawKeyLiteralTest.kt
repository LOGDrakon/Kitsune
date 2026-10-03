package com.kitsune.core.data.local.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in the exact `x'<hex>'` literal format that both [KitsuneDatabaseProviderImpl.open] and
 * [KitsuneDatabaseProviderImpl.rekey] must agree on (BUG-034, see BUGS.md) — SQLCipher only skips
 * its own internal PBKDF2 stretching when a key buffer's content matches this literal exactly
 * (length `2 + 2*n + 1`, "x'" prefix, valid hex, closing "'"). Any drift between the two call
 * sites reintroduces the same silent key-mismatch bug that caused real data loss.
 */
class RawKeyLiteralTest {

    @Test
    fun `formats a 32-byte key as the exact SQLCipher raw-key literal`() {
        val key = ByteArray(32) { it.toByte() }

        val literal = key.toRawKeyLiteralText()

        assertEquals(67, literal.length) // "x'" + 64 hex chars + "'"
        assertTrue(literal.startsWith("x'"))
        assertTrue(literal.endsWith("'"))
        assertEquals("x'000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f'", literal)
    }

    @Test
    fun `never emits negative-looking hex for high bytes`() {
        val key = ByteArray(32) { 0xFF.toByte() }

        val literal = key.toRawKeyLiteralText()

        assertEquals("x'" + "ff".repeat(32) + "'", literal)
    }

    @Test
    fun `byte-array form is the exact ASCII encoding of the text literal`() {
        val key = ByteArray(32) { (it * 7).toByte() }

        val literalBytes = key.toRawKeyLiteral()

        assertEquals(key.toRawKeyLiteralText(), String(literalBytes, Charsets.US_ASCII))
    }
}
