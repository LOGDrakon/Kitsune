package com.kitsune.core.network.di

import com.kitsune.core.backend.KitsuneBackendClient
import com.kitsune.core.network.BuildConfig
import com.kitsune.core.network.api.BackendChatApi
import com.kitsune.core.network.api.BackendCatalogApi
import com.kitsune.core.network.preferences.NetworkPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BackendRetrofit

private const val CONNECT_TIMEOUT_SECONDS = 15L
// The server-side chain this client talks to is built around a 120s budget for a single
// completion (nginx.conf `proxy_read_timeout 120s`, MammouthProxyService's own
// `requestTimeoutMillis = 120_000` to Mammouth.ai) — a heavy generation call (persona/universe
// sheets, 4096 max tokens) can legitimately take well over 60s under model load. A shorter
// client-side read timeout doesn't fail faster in any useful sense: it just disconnects while the
// backend is still working and would have delivered a real result (bug report, "timeout" at
// exactly 60.0s after the call started). Kept comfortably above the server's own ceiling so the
// server-side timeout — not this one — is always what fires first.
private const val READ_TIMEOUT_SECONDS = 130L
private const val WRITE_TIMEOUT_SECONDS = 15L

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    @BackendRetrofit
    fun provideBackendRetrofit(json: Json, backendClient: KitsuneBackendClient): Retrofit {
        val client = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(backendAuthInterceptor(backendClient))
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
                }
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(backendClient.getBackendUrl())
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideBackendChatApi(@BackendRetrofit retrofit: Retrofit): BackendChatApi =
        retrofit.create(BackendChatApi::class.java)

    @Provides
    @Singleton
    // Goes through @BackendRetrofit, not a separate anonymous client: the catalog is assembled by our
    // backend from OpenRouter, whose key only the backend holds, so this call is authenticated like
    // any other. The old @CatalogRetrofit (hardcoded api.mammouth.ai, no auth) was removed with it.
    fun provideBackendCatalogApi(@BackendRetrofit retrofit: Retrofit): BackendCatalogApi =
        retrofit.create(BackendCatalogApi::class.java)

    private fun backendAuthInterceptor(backendClient: KitsuneBackendClient): Interceptor =
        Interceptor { chain ->
            val token = backendClient.getAccessToken()
            val request = if (!token.isNullOrBlank()) {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .header("Content-Type", "application/json")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }
}