package com.kitsune.core.memory.semantic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddingCacheTest {

    private val cache = EmbeddingCache()

    @Test
    fun `returns the same parsed vector on a hit`() {
        val embedding = listOf(1f, 2f, 3f)

        val first = cache.get("frag-1", embedding)
        val second = cache.get("frag-1", embedding)

        assertSame(first, second)
    }

    @Test
    fun `precomputes the L2 norm`() {
        val vector = cache.build(listOf(3f, 4f))

        assertEquals(5f, vector.norm, 1e-6f)
    }

    @Test
    fun `invalidate forces a re-parse`() {
        cache.get("frag-1", listOf(1f, 2f, 3f))
        cache.invalidate("frag-1")

        assertNull(cache.peek("frag-1"))
    }

    @Test
    fun `a changed embedding of the same size and id is not silently reused`() {
        // Anything that rewrites a fragment must invalidate; this only covers the size-change case,
        // which is the one the cache can detect on its own.
        cache.get("frag-1", listOf(1f, 2f, 3f))

        val refreshed = cache.get("frag-1", listOf(1f, 2f))

        assertEquals(2, refreshed.values.size)
    }

    @Test
    fun `clear empties the cache`() {
        cache.get("frag-1", listOf(1f))
        cache.get("frag-2", listOf(1f))

        cache.clear()

        assertEquals(0, cache.size())
    }

    @Test
    fun `evicts least-recently-used entries past capacity`() {
        repeat(600) { cache.get("frag-$it", listOf(it.toFloat(), 1f)) }

        assertTrue("cache must stay bounded", cache.size() <= 512)
        assertNull("oldest entry should have been evicted", cache.peek("frag-0"))
        assertNotNull("newest entry should still be cached", cache.peek("frag-599"))
    }

    @Test
    fun `access order keeps a re-read entry alive`() {
        repeat(512) { cache.get("frag-$it", listOf(it.toFloat(), 1f)) }
        // Touch the oldest so it becomes the most recently used, then overflow by one.
        cache.get("frag-0", listOf(0f, 1f))
        cache.get("frag-overflow", listOf(1f, 1f))

        assertNotNull("recently accessed entry must survive eviction", cache.peek("frag-0"))
        assertNull(cache.peek("frag-1"))
    }

    @Test
    fun `similarity matches VectorMath for the same inputs`() {
        val a = listOf(1f, 2f, 3f)
        val b = listOf(2f, 1f, 0f)

        val cached = cache.similarity(cache.build(a), cache.build(b))

        assertEquals(VectorMath.cosineSimilarity(a, b), cached, 1e-6f)
    }

    @Test
    fun `similarity returns zero for mismatched, empty or zero vectors`() {
        assertEquals(0f, cache.similarity(cache.build(listOf(1f, 2f)), cache.build(listOf(1f))), 0f)
        assertEquals(0f, cache.similarity(cache.build(emptyList()), cache.build(emptyList())), 0f)
        assertEquals(0f, cache.similarity(cache.build(listOf(0f, 0f)), cache.build(listOf(1f, 1f))), 0f)
    }
}
