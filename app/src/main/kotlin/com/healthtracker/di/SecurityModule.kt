package com.healthtracker.di

import android.content.Context
import com.healthtracker.data.security.CertificatePinnerManager
import com.healthtracker.data.security.CertificatePinnerManagerImpl
import com.healthtracker.data.security.EncryptionService
import com.healthtracker.data.security.EncryptionServiceImpl
import com.healthtracker.data.security.FirebaseSyncEncryption
import com.healthtracker.data.security.FirebaseSyncEncryptionImpl
import com.healthtracker.data.security.RequestSigner
import com.healthtracker.data.security.RequestSignerImpl
import com.healthtracker.data.security.SecureTokenStorage
import com.healthtracker.data.security.SecureTokenStorageImpl
import com.healthtracker.ml.ModelVerifier
import com.healthtracker.ml.ModelVerifierImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing security-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {
    
    /**
     * Binds EncryptionService implementation.
     */
    @Binds
    @Singleton
    abstract fun bindEncryptionService(
        impl: EncryptionServiceImpl
    ): EncryptionService
    
    /**
     * Binds SecureTokenStorage implementation.
     */
    @Binds
    @Singleton
    abstract fun bindSecureTokenStorage(
        impl: SecureTokenStorageImpl
    ): SecureTokenStorage
    
    /**
     * Binds FirebaseSyncEncryption implementation.
     */
    @Binds
    @Singleton
    abstract fun bindFirebaseSyncEncryption(
        impl: FirebaseSyncEncryptionImpl
    ): FirebaseSyncEncryption
    
    /**
     * Binds RequestSigner implementation.
     */
    @Binds
    @Singleton
    abstract fun bindRequestSigner(
        impl: RequestSignerImpl
    ): RequestSigner
    
    /**
     * Binds CertificatePinnerManager implementation.
     */
    @Binds
    @Singleton
    abstract fun bindCertificatePinnerManager(
        impl: CertificatePinnerManagerImpl
    ): CertificatePinnerManager
    
    /**
     * Binds ModelVerifier implementation.
     */
    @Binds
    @Singleton
    abstract fun bindModelVerifier(
        impl: ModelVerifierImpl
    ): ModelVerifier
}
