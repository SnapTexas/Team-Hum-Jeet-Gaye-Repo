package com.healthtracker.di

import com.healthtracker.core.error.DefaultErrorReporter
import com.healthtracker.core.error.ErrorReporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing error handling dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ErrorModule {
    
    /**
     * Binds ErrorReporter implementation.
     */
    @Binds
    @Singleton
    abstract fun bindErrorReporter(
        impl: DefaultErrorReporter
    ): ErrorReporter
}
