package com.kitsune.core.background

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BackgroundModule {

    @Binds
    @Singleton
    abstract fun bindGenerationScheduler(impl: WorkManagerGenerationScheduler): GenerationScheduler
}
