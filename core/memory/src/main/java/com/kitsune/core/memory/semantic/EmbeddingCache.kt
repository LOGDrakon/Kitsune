package com.kitsune.core.memory.semantic

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Caches parsed fragment embeddings so retrieval stops re-parsing them from TEXT on every turn.
 *
 * Embeddings live in SQLite as a comma-joined decimal string (`Converters.toEmbedding`), so loading
 * one costs `split(",")` into 1536 `String`s plus `map` into 1536 boxed `Float`s — roughly 4600
 * allocations *per fragment*. On a chat with a few hundred fragments that is well over a million
 * allocations and several megabytes of transient garbage on every single retrieval, against about
 * one or two milliseconds of actual cosine arithmetic. Parsing, not maths, dominates the cost.
 *
 * Caching the parsed [FloatArray] plus its precomputed L2 norm removes essentially all of it after
 * the first turn. This is deliberately preferred over converting the column to a BLOB: that would
 * mean rewriting every row inside a Room `Migration`, i.e. an unbounded data rewrite on the vault
 * unlock path — the same code family that produced BUG-002 and the data loss of BUG-034 — for a
 * disk-space win that isn't the bottleneck. See IDEAS.md (2026-08-15) for the safe lazy-migration
 * route if the disk saving is ever wanted.
 *
 * Keyed by fragment id, so a re-indexed fragment (same id, new text and embedding) must be
 * [invalidate]d by whoever writes it.
 */
@Singleton
class EmbeddingCache @Inject constructor() {

    /** Parsed vector and its L2 norm, so cosine similarity needs one pass instead of three. */
    class Vector(val values: FloatArray, val norm: Float)

    /** Access-ordered [LinkedHashMap] rather than `android.util.LruCache`: this module runs its
     *  tests on the JVM with `unitTests.isReturnDefaultValues = true`, under which every android.jar
     *  call silently returns null — the cache would appear to work while never actually caching,
     *  and no test could tell. Guarded by the object's own monitor since retrieval can run from
     *  several chat coroutines at once. */
    private val cache = object : LinkedHashMap<String, Vector>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Vector>): Boolean = size > MAX_ENTRIES
    }

    @Synchronized
    fun get(fragmentId: String, embedding: List<Float>): Vector {
        cache[fragmentId]?.let { cached ->
            // Guard against an id whose embedding changed without an invalidate() — a stale vector
            // would silently skew every score, which is far worse than re-parsing once.
            if (cached.values.size == embedding.size) return cached
        }
        return build(embedding).also { cache[fragmentId] = it }
    }

    @Synchronized
    fun invalidate(fragmentId: String) {
        cache.remove(fragmentId)
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }

    @Synchronized
    internal fun size(): Int = cache.size

    @Synchronized
    internal fun peek(fragmentId: String): Vector? = cache[fragmentId]

    /** Cosine similarity against a pre-normalised query vector. Mirrors [VectorMath.cosineSimilarity]
     *  (0 for empty, mismatched or zero-norm inputs) but skips recomputing the fragment's norm. */
    fun similarity(query: Vector, fragment: Vector): Float {
        if (query.values.isEmpty() || query.values.size != fragment.values.size) return 0f
        if (query.norm == 0f || fragment.norm == 0f) return 0f

        var dot = 0f
        for (i in query.values.indices) dot += query.values[i] * fragment.values[i]
        return (dot / (query.norm * fragment.norm)).coerceIn(-1f, 1f)
    }

    fun build(embedding: List<Float>): Vector {
        val values = FloatArray(embedding.size) { embedding[it] }
        var sumOfSquares = 0f
        for (value in values) sumOfSquares += value * value
        return Vector(values, sqrt(sumOfSquares))
    }

    private companion object {
        /** ~3 MB at 1536 float32 dimensions — comfortably more than a single long chat holds. */
        const val MAX_ENTRIES = 512
    }
}
