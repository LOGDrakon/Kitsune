package com.kitsune.core.network.di

import com.kitsune.core.network.repository.ChatCompletionRepository
import com.kitsune.core.network.repository.ChatCompletionRepositoryImpl
import com.kitsune.core.network.repository.EmbeddingRepository
import com.kitsune.core.network.repository.EmbeddingRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindChatCompletionRepository(impl: ChatCompletionRepositoryImpl): ChatCompletionRepository

    @Binds
    @Singleton
    abstract fun bindEmbeddingRepository(impl: EmbeddingRepositoryImpl): EmbeddingRepository
}
