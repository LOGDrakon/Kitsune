package com.kitsune.core.network.repository

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class Base64ImageDecoderTest {

    @Test
    fun `decodes a data URI with a base64 marker`() {
        val original = "fake image bytes".toByteArray()
        val encoded = Base64.getEncoder().encodeToString(original)
        val uri = "data:image/png;base64,$encoded"

        val decoded = decodeBase64ImageDataUri(uri)

        assertArrayEquals(original, decoded)
    }

    @Test
    fun `decodes a bare base64 payload without a data URI wrapper`() {
        val original = "more fake bytes".toByteArray()
        val encoded = Base64.getEncoder().encodeToString(original)

        val decoded = decodeBase64ImageDataUri(encoded)

        assertArrayEquals(original, decoded)
    }

    @Test
    fun `returns null for a remote http URL`() {
        assertNull(decodeBase64ImageDataUri("https://example.com/image.png"))
    }

    @Test
    fun `returns null for garbage input`() {
        assertNull(decodeBase64ImageDataUri("not base64 at all !!!"))
    }

    @Test
    fun `encodes then decodes back to the exact original bytes`() {
        val original = "round trip bytes".toByteArray()

        val decoded = decodeBase64ImageDataUri(encodeBase64ImageDataUri(original))

        assertArrayEquals(original, decoded)
    }

    @Test
    fun `encoded uri carries the requested mime type`() {
        val uri = encodeBase64ImageDataUri("x".toByteArray(), mimeType = "image/jpeg")

        assert(uri.startsWith("data:image/jpeg;base64,"))
    }
}
