package com.kitsune.core.security.di

import com.kitsune.core.security.vault.VaultKeyProvider
import com.kitsune.core.security.vault.VaultKeyProviderImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindVaultKeyProvider(impl: VaultKeyProviderImpl): VaultKeyProvider
}
