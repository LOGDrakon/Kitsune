package com.kitsune.core.data.di

import com.kitsune.core.data.local.database.DecoyNotesDatabaseProvider
import com.kitsune.core.data.local.database.DecoyNotesDatabaseProviderImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DecoyNotesDatabaseModule {

    @Binds
    @Singleton
    abstract fun bindDecoyNotesDatabaseProvider(impl: DecoyNotesDatabaseProviderImpl): DecoyNotesDatabaseProvider
}
