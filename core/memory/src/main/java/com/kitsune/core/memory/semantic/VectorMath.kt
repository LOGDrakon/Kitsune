package com.kitsune.core.memory.semantic

/** Cosine similarity over embeddings produced by [RemoteTextEmbedder] (memory level 4). */
object VectorMath {

    /** Returns 0 for empty/mismatched/all-zero vectors instead of NaN or throwing. */
    fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) return 0f

        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA == 0f || normB == 0f) return 0f

        return (dot / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))).coerceIn(-1f, 1f)
    }
}
