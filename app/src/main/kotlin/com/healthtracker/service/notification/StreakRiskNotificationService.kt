package com.healthtracker.service.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.healthtracker.domain.model.Streak
import com.healthtracker.domain.model.StreakType
import com.healthtracker.presentation.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for sending streak risk notifications.
 * Notifies users when their streaks are at risk of being lost.
 */
@Singleton
class StreakRiskNotificationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        const val CHANNEL_ID = "streak_risk_channel"
        const val CHANNEL_NAME = "Streak Alerts"
        const val CHANNEL_DESCRIPTION = "Notifications when your streaks are at risk"
        
        private const val NOTIFICATION_ID_BASE = 3000
    }
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                enableLights(true)
            }
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Send a notification for a streak at risk.
     */
    fun sendStreakRiskNotification(streak: Streak) {
        val title = getNotificationTitle(streak.type)
        val message = getNotificationMessage(streak)
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "gamification")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            streak.type.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        try {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID_BASE + streak.type.ordinal, notification)
            Timber.d("Streak risk notification sent for ${streak.type}")
        } catch (e: SecurityException) {
            Timber.e(e, "Permission denied for sending notification")
        }
    }
    
    /**
     * Send notifications for multiple at-risk streaks.
     */
    fun sendStreakRiskNotifications(streaks: List<Streak>) {
        streaks.forEach { streak ->
            sendStreakRiskNotification(streak)
        }
    }
    
    private fun getNotificationTitle(type: StreakType): String {
        return when (type) {
            StreakType.DAILY_STEPS -> "🔥 Your Step Streak is at Risk!"
            StreakType.DAILY_WATER -> "💧 Your Hydration Streak is at Risk!"
            StreakType.DAILY_WORKOUT -> "💪 Your Workout Streak is at Risk!"
            StreakType.DAILY_MEDITATION -> "🧘 Your Meditation Streak is at Risk!"
            StreakType.WEEKLY_GOALS -> "🎯 Your Weekly Goals Streak is at Risk!"
        }
    }
    
    private fun getNotificationMessage(streak: Streak): String {
        val streakDays = streak.currentCount
        val action = when (streak.type) {
            StreakType.DAILY_STEPS -> "reach your step goal"
            StreakType.DAILY_WATER -> "log your water intake"
            StreakType.DAILY_WORKOUT -> "complete a workout"
            StreakType.DAILY_MEDITATION -> "do a meditation session"
            StreakType.WEEKLY_GOALS -> "complete your weekly goals"
        }
        
        return "You have a $streakDays-day streak! Don't lose it - $action before midnight to keep it going! 💪"
    }
    
    /**
     * Cancel a streak risk notification.
     */
    fun cancelStreakRiskNotification(type: StreakType) {
        NotificationManagerCompat.from(context)
            .cancel(NOTIFICATION_ID_BASE + type.ordinal)
    }
    
    /**
     * Cancel all streak risk notifications.
     */
    fun cancelAllStreakRiskNotifications() {
        StreakType.entries.forEach { type ->
            cancelStreakRiskNotification(type)
        }
    }
}
