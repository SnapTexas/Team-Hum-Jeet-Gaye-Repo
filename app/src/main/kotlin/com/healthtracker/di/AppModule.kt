package com.healthtracker.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.healthtracker.data.local.HealthTrackerDatabase
import com.healthtracker.data.local.dao.AnomalyDao
import com.healthtracker.data.local.dao.GamificationDao
import com.healthtracker.data.local.dao.HealthMetricsDao
import com.healthtracker.data.local.dao.MedicalDao
import com.healthtracker.data.local.dao.PrivacyDao
import com.healthtracker.data.local.dao.SocialDao
import com.healthtracker.data.local.dao.SuggestionDao
import com.healthtracker.data.local.dao.UserBaselineDao
import com.healthtracker.data.local.dao.UserDao
import com.healthtracker.data.repository.AnalyticsRepositoryImpl
import com.healthtracker.data.repository.AnomalyRepositoryImpl
import com.healthtracker.data.repository.DietRepositoryImpl
import com.healthtracker.data.repository.GamificationRepositoryImpl
import com.healthtracker.data.repository.HealthDataRepositoryImpl
import com.healthtracker.data.repository.MedicalRepositoryImpl
import com.healthtracker.data.repository.MentalHealthRepositoryImpl
import com.healthtracker.data.repository.PlanRepositoryImpl
import com.healthtracker.data.repository.PrivacyRepositoryImpl
import com.healthtracker.data.repository.SocialRepositoryImpl
import com.healthtracker.data.repository.SuggestionRepositoryImpl
import com.healthtracker.data.repository.UserRepositoryImpl
import com.healthtracker.domain.repository.AnalyticsRepository
import com.healthtracker.domain.repository.AnomalyRepository
import com.healthtracker.domain.repository.DietRepository
import com.healthtracker.domain.repository.GamificationRepository
import com.healthtracker.domain.repository.HealthDataRepository
import com.healthtracker.domain.repository.MedicalRepository
import com.healthtracker.domain.repository.MentalHealthRepository
import com.healthtracker.domain.repository.PlanRepository
import com.healthtracker.domain.repository.PrivacyRepository
import com.healthtracker.domain.repository.SocialRepository
import com.healthtracker.domain.repository.SuggestionRepository
import com.healthtracker.domain.repository.UserRepository
import com.healthtracker.domain.usecase.AISuggestionUseCase
import com.healthtracker.domain.usecase.AnalyticsUseCase
import com.healthtracker.domain.usecase.AnomalyDetectionUseCase
import com.healthtracker.domain.usecase.AvatarUseCase
import com.healthtracker.domain.usecase.DataCollectionUseCase
import com.healthtracker.domain.usecase.DietTrackingUseCase
import com.healthtracker.domain.usecase.GamificationUseCase
import com.healthtracker.domain.usecase.MedicalUseCase
import com.healthtracker.domain.usecase.MentalHealthUseCase
import com.healthtracker.domain.usecase.OnboardingUseCase
import com.healthtracker.domain.usecase.PlanningUseCase
import com.healthtracker.domain.usecase.PrivacyUseCase
import com.healthtracker.domain.usecase.SocialUseCase
import com.healthtracker.domain.usecase.TriageUseCase
import com.healthtracker.domain.usecase.impl.AISuggestionUseCaseImpl
import com.healthtracker.domain.usecase.impl.AnalyticsUseCaseImpl
import com.healthtracker.domain.usecase.impl.AnomalyDetectionUseCaseImpl
import com.healthtracker.domain.usecase.impl.AvatarUseCaseImpl
import com.healthtracker.domain.usecase.impl.DataCollectionUseCaseImpl
import com.healthtracker.domain.usecase.impl.DietTrackingUseCaseImpl
import com.healthtracker.domain.usecase.impl.GamificationUseCaseImpl
import com.healthtracker.domain.usecase.impl.MedicalUseCaseImpl
import com.healthtracker.domain.usecase.impl.MentalHealthUseCaseImpl
import com.healthtracker.domain.usecase.impl.OnboardingUseCaseImpl
import com.healthtracker.domain.usecase.impl.PlanningUseCaseImpl
import com.healthtracker.domain.usecase.impl.PrivacyUseCaseImpl
import com.healthtracker.domain.usecase.impl.SocialUseCaseImpl
import com.healthtracker.domain.usecase.impl.TriageUseCaseImpl
import com.healthtracker.domain.repository.TriageRepository
import com.healthtracker.data.repository.TriageRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.healthtracker.core.sensor.StepCounterManager
import com.healthtracker.core.sensor.SmartWatchManager
import com.healthtracker.service.notification.MedicalReminderNotificationService
import com.healthtracker.service.ai.LocalLLMService
import kotlinx.serialization.json.Json
import javax.inject.Singleton

/**
 * Hilt module providing application-level dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    /**
     * Provides the Room database instance.
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): HealthTrackerDatabase {
        return Room.databaseBuilder(
            context,
            HealthTrackerDatabase::class.java,
            HealthTrackerDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }
    
    /**
     * Provides the HealthMetricsDao.
     */
    @Provides
    @Singleton
    fun provideHealthMetricsDao(database: HealthTrackerDatabase): HealthMetricsDao {
        return database.healthMetricsDao()
    }
    
    /**
     * Provides the UserBaselineDao.
     */
    @Provides
    @Singleton
    fun provideUserBaselineDao(database: HealthTrackerDatabase): UserBaselineDao {
        return database.userBaselineDao()
    }
    
    /**
     * Provides the AnomalyDao.
     */
    @Provides
    @Singleton
    fun provideAnomalyDao(database: HealthTrackerDatabase): AnomalyDao {
        return database.anomalyDao()
    }
    
    /**
     * Provides the SuggestionDao.
     */
    @Provides
    @Singleton
    fun provideSuggestionDao(database: HealthTrackerDatabase): SuggestionDao {
        return database.suggestionDao()
    }
    
    /**
     * Provides the UserDao.
     */
    @Provides
    @Singleton
    fun provideUserDao(database: HealthTrackerDatabase): UserDao {
        return database.userDao()
    }
    
    /**
     * Provides the GamificationDao.
     */
    @Provides
    @Singleton
    fun provideGamificationDao(database: HealthTrackerDatabase): GamificationDao {
        return database.gamificationDao()
    }
    
    /**
     * Provides the SocialDao.
     */
    @Provides
    @Singleton
    fun provideSocialDao(database: HealthTrackerDatabase): SocialDao {
        return database.socialDao()
    }
    
    /**
     * Provides the MedicalDao.
     */
    @Provides
    @Singleton
    fun provideMedicalDao(database: HealthTrackerDatabase): MedicalDao {
        return database.medicalDao()
    }
    
    /**
     * Provides the PrivacyDao.
     */
    @Provides
    @Singleton
    fun providePrivacyDao(database: HealthTrackerDatabase): PrivacyDao {
        return database.privacyDao()
    }
    
    /**
     * Provides Gson instance for JSON serialization.
     */
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return Gson()
    }
    
    /**
     * Provides kotlinx.serialization Json instance.
     */
    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
    
    /**
     * Provides FirebaseAuth instance.
     */
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }
    
    /**
     * Provides FirebaseFirestore instance.
     */
    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }
    
    /**
     * Provides FirebaseStorage instance.
     */
    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage {
        return FirebaseStorage.getInstance()
    }
    
    /**
     * Provides SmartWatchManager for smartwatch integration.
     */
    @Provides
    @Singleton
    fun provideSmartWatchManager(
        @ApplicationContext context: Context
    ): SmartWatchManager {
        return SmartWatchManager(context)
    }
    
    /**
     * Provides StepCounterManager for live step counting from device sensor.
     * Uses the SAME sensor as Xiaomi App Vault!
     * NOW WITH SMARTWATCH PRIORITY!
     */
    @Provides
    @Singleton
    fun provideStepCounterManager(
        @ApplicationContext context: Context,
        smartWatchManager: SmartWatchManager
    ): StepCounterManager {
        return StepCounterManager(context, smartWatchManager)
    }
    
    /**
     * Provides MedicalReminderNotificationService for scheduling medical reminders.
     */
    @Provides
    @Singleton
    fun provideMedicalReminderNotificationService(
        @ApplicationContext context: Context
    ): MedicalReminderNotificationService {
        return MedicalReminderNotificationService(context)
    }
    
    /**
     * Provides LocalLLMService for on-device AI (Qwen 0.5B).
     */
    @Provides
    @Singleton
    fun provideLocalLLMService(
        @ApplicationContext context: Context
    ): LocalLLMService {
        return LocalLLMService(context)
    }
    
    /**
     * Provides EdgeTTSService for natural voice output.
     */
    @Provides
    @Singleton
    fun provideEdgeTTSService(
        @ApplicationContext context: Context
    ): com.healthtracker.service.ai.EdgeTTSService {
        return com.healthtracker.service.ai.EdgeTTSService(context)
    }
}

/**
 * Hilt module for binding interfaces to implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    
    /**
     * Binds UserRepositoryImpl to UserRepository interface.
     */
    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository
    
    /**
     * Binds OnboardingUseCaseImpl to OnboardingUseCase interface.
     */
    @Binds
    @Singleton
    abstract fun bindOnboardingUseCase(impl: OnboardingUseCaseImpl): OnboardingUseCase
    
    /**
     * Binds HealthDataRepositoryImpl to HealthDataRepository interface.
     */
    @Binds
    @Singleton
    abstract fun bindHealthDataRepository(impl: HealthDataRepositoryImpl): HealthDataRepository
    
    /**
     * Binds DataCollectionUseCaseImpl to DataCollectionUseCase interface.
     */
    @Binds
    @Singleton
    abstract fun bindDataCollectionUseCase(impl: DataCollectionUseCaseImpl): DataCollectionUseCase
    
    /**
     * Binds AnalyticsUseCaseImpl to AnalyticsUseCase interface.
     */
    @Binds
    @Singleton
    abstract fun bindAnalyticsUseCase(impl: AnalyticsUseCaseImpl): AnalyticsUseCase
    
    /**
     * Binds AnalyticsRepositoryImpl to AnalyticsRepository interface.
     */
    @Binds
    @Singleton
    abstract fun bindAnalyticsRepository(impl: AnalyticsRepositoryImpl): AnalyticsRepository
    
    /**
     * Binds AnomalyRepositoryImpl to AnomalyRepository interface.
     */
    @Binds
    @Singleton
    abstract fun bindAnomalyRepository(impl: AnomalyRepositoryImpl): AnomalyRepository
    
    /**
     * Binds AnomalyDetectionUseCaseImpl to AnomalyDetectionUseCase interface.
     */
    @Binds
    @Singleton
    abstract fun bindAnomalyDetectionUseCase(impl: AnomalyDetectionUseCaseImpl): AnomalyDetectionUseCase
    
    /**
     * Binds SuggestionRepositoryImpl to SuggestionRepository interface.
     */
    @Binds
    @Singleton
    abstract fun bindSuggestionRepository(impl: SuggestionRepositoryImpl): SuggestionRepository
    
    /**
     * Binds AISuggestionUseCaseImpl to AISuggestionUseCase interface.
     */
    @Binds
    @Singleton
    abstract fun bindAISuggestionUseCase(impl: AISuggestionUseCaseImpl): AISuggestionUseCase
    
    /**
     * Binds AvatarUseCaseImpl to AvatarUseCase interface.
     */
    @Binds
    @Singleton
    abstract fun bindAvatarUseCase(impl: AvatarUseCaseImpl): AvatarUseCase
    
    /**
     * Binds PlanRepositoryImpl to PlanRepository interface.
     */
    @Binds
    @Singleton
    abstract fun bindPlanRepository(impl: PlanRepositoryImpl): PlanRepository
    
    /**
     * Binds PlanningUseCaseImpl to PlanningUseCase interface.
     */
    @Binds
    @Singleton
    abstract fun bindPlanningUseCase(impl: PlanningUseCaseImpl): PlanningUseCase
    
    /**
     * Binds DietRepositoryImpl to DietRepository interface.
     */
    @Binds
    @Singleton
    abstract fun bindDietRepository(impl: DietRepositoryImpl): DietRepository
    
    /**
     * Binds DietTrackingUseCaseImpl to DietTrackingUseCase interface.
     */
    @Binds
    @Singleton
    abstract fun bindDietTrackingUseCase(impl: DietTrackingUseCaseImpl): DietTrackingUseCase
    
    /**
     * Binds MentalHealthRepositoryImpl to MentalHealthRepository interface.
     */
    @Binds
    @Singleton
    abstract fun bindMentalHealthRepository(impl: MentalHealthRepositoryImpl): MentalHealthRepository
    
    /**
     * Binds MentalHealthUseCaseImpl to MentalHealthUseCase interface.
     */
    @Binds
    @Singleton
    abstract fun bindMentalHealthUseCase(impl: MentalHealthUseCaseImpl): MentalHealthUseCase
    
    /**
     * Binds GamificationRepositoryImpl to GamificationRepository interface.
     */
    @Binds
    @Singleton
    abstract fun bindGamificationRepository(impl: GamificationRepositoryImpl): GamificationRepository
    
    /**
     * Binds GamificationUseCaseImpl to GamificationUseCase interface.
     */
    @Binds
    @Singleton
    abstract fun bindGamificationUseCase(impl: GamificationUseCaseImpl): GamificationUseCase
    
    /**
     * Binds SocialRepositoryImpl to SocialRepository interface.
     */
    @Binds
    @Singleton
    abstract fun bindSocialRepository(impl: SocialRepositoryImpl): SocialRepository
    
    /**
     * Binds SocialUseCaseImpl to SocialUseCase interface.
     */
    @Binds
    @Singleton
    abstract fun bindSocialUseCase(impl: SocialUseCaseImpl): SocialUseCase
    
    /**
     * Binds TriageRepositoryImpl to TriageRepository interface.
     */
    @Binds
    @Singleton
    abstract fun bindTriageRepository(impl: TriageRepositoryImpl): TriageRepository
    
    /**
     * Binds TriageUseCaseImpl to TriageUseCase interface.
     */
    @Binds
    @Singleton
    abstract fun bindTriageUseCase(impl: TriageUseCaseImpl): TriageUseCase
    
    /**
     * Binds MedicalRepositoryImpl to MedicalRepository interface.
     */
    @Binds
    @Singleton
    abstract fun bindMedicalRepository(impl: MedicalRepositoryImpl): MedicalRepository
    
    /**
     * Binds MedicalUseCaseImpl to MedicalUseCase interface.
     */
    @Binds
    @Singleton
    abstract fun bindMedicalUseCase(impl: MedicalUseCaseImpl): MedicalUseCase
    
    /**
     * Binds PrivacyRepositoryImpl to PrivacyRepository interface.
     */
    @Binds
    @Singleton
    abstract fun bindPrivacyRepository(impl: PrivacyRepositoryImpl): PrivacyRepository
    
    /**
     * Binds PrivacyUseCaseImpl to PrivacyUseCase interface.
     */
    @Binds
    @Singleton
    abstract fun bindPrivacyUseCase(impl: PrivacyUseCaseImpl): PrivacyUseCase
}
