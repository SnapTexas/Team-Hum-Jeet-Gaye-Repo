package com.healthtracker.di

import com.healthtracker.data.repository.PrivacyRepositoryImpl
import com.healthtracker.domain.repository.PrivacyRepository
import com.healthtracker.domain.usecase.PrivacyUseCase
import com.healthtracker.domain.usecase.impl.PrivacyUseCaseImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing privacy-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PrivacyModule {
    
    /**
     * Binds PrivacyRepository implementation.
     */
    @Binds
    @Singleton
    abstract fun bindPrivacyRepository(
        impl: PrivacyRepositoryImpl
    ): PrivacyRepository
    
    /**
     * Binds PrivacyUseCase implementation.
     */
    @Binds
    @Singleton
    abstract fun bindPrivacyUseCase(
        impl: PrivacyUseCaseImpl
    ): PrivacyUseCase
}
