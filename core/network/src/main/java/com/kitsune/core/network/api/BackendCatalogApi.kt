package com.kitsune.core.network.api

import com.kitsune.core.network.dto.BackendModelCatalogResponse
import retrofit2.Response
import retrofit2.http.GET

/**
 * Model catalog, served by our own backend rather than fetched from OpenRouter directly.
 *
 * Goes through the authenticated backend retrofit rather than OpenRouter's own (public,
 * unauthenticated) `GET /models`: the backend still owns the OpenRouter key (needed for completions,
 * not for the listing itself) and stays the single place the app learns about models from, unchanged
 * since the old Mammouth `public/models` days.
 */
interface BackendCatalogApi {
    @GET("config/model-catalog")
    suspend fun listModels(): Response<BackendModelCatalogResponse>
}
