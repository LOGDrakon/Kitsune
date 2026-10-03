package com.kitsune.core.data.di

import com.kitsune.core.data.local.database.KitsuneDatabaseProvider
import com.kitsune.core.data.local.database.KitsuneDatabaseProviderImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseModule {

    @Binds
    @Singleton
    abstract fun bindKitsuneDatabaseProvider(impl: KitsuneDatabaseProviderImpl): KitsuneDatabaseProvider
}
