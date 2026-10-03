package com.kitsune.core.memory.semantic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorMathTest {

    @Test
    fun `identical vectors have a cosine similarity of 1`() {
        val vector = listOf(1f, 2f, 3f)

        assertEquals(1f, VectorMath.cosineSimilarity(vector, vector), 0.0001f)
    }

    @Test
    fun `orthogonal vectors have a cosine similarity of 0`() {
        val a = listOf(1f, 0f)
        val b = listOf(0f, 1f)

        assertEquals(0f, VectorMath.cosineSimilarity(a, b), 0.0001f)
    }

    @Test
    fun `opposite vectors have a cosine similarity of -1`() {
        val a = listOf(1f, 0f)
        val b = listOf(-1f, 0f)

        assertEquals(-1f, VectorMath.cosineSimilarity(a, b), 0.0001f)
    }

    @Test
    fun `all-zero vectors return 0 instead of NaN`() {
        val a = listOf(0f, 0f, 0f)
        val b = listOf(0f, 0f, 0f)

        assertEquals(0f, VectorMath.cosineSimilarity(a, b), 0.0001f)
    }

    @Test
    fun `mismatched or empty vectors return 0 instead of throwing`() {
        assertEquals(0f, VectorMath.cosineSimilarity(emptyList(), emptyList()), 0.0001f)
        assertEquals(0f, VectorMath.cosineSimilarity(listOf(1f), listOf(1f, 2f)), 0.0001f)
    }

    @Test
    fun `result is always within the valid -1 to 1 range`() {
        val a = listOf(0.5f, 0.5f, 0.5f)
        val b = listOf(0.4f, 0.6f, 0.2f)

        val score = VectorMath.cosineSimilarity(a, b)

        assertTrue(score in -1f..1f)
    }
}
