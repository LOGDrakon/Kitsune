package com.kitsune.core.network.catalog

interface ModelCatalogRepository {
    /** Fetches the live catalog, using a short-lived in-memory cache unless [forceRefresh]. */
    suspend fun getModels(forceRefresh: Boolean = false): Result<List<ModelInfo>>
}
