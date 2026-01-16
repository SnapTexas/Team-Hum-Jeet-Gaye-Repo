package com.healthtracker.service.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.healthtracker.R
import com.healthtracker.domain.model.Anomaly
import com.healthtracker.domain.model.AnomalySeverity
import com.healthtracker.domain.model.AnomalyType
import com.healthtracker.presentation.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for sending anomaly alert notifications.
 * 
 * Creates notification channels and sends push notifications
 * when health anomalies are detected.
 */
@Singleton
class AnomalyNotificationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        const val CHANNEL_ID_ANOMALY_ALERTS = "anomaly_alerts"
        const val CHANNEL_ID_ANOMALY_WARNINGS = "anomaly_warnings"
        const val CHANNEL_ID_ANOMALY_INFO = "anomaly_info"
        
        private const val NOTIFICATION_GROUP = "com.healthtracker.ANOMALY_GROUP"
        private const val SUMMARY_NOTIFICATION_ID = 0
    }
    
    init {
        createNotificationChannels()
    }
    
    /**
     * Creates notification channels for different anomaly severities.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            
            // Alert channel (high priority)
            val alertChannel = NotificationChannel(
                CHANNEL_ID_ANOMALY_ALERTS,
                "Health Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical health anomaly alerts requiring immediate attention"
                enableVibration(true)
                enableLights(true)
            }
            
            // Warning channel (default priority)
            val warningChannel = NotificationChannel(
                CHANNEL_ID_ANOMALY_WARNINGS,
                "Health Warnings",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Health anomaly warnings that may need attention"
                enableVibration(true)
            }
            
            // Info channel (low priority)
            val infoChannel = NotificationChannel(
                CHANNEL_ID_ANOMALY_INFO,
                "Health Information",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Informational health notifications"
            }
            
            notificationManager.createNotificationChannels(
                listOf(alertChannel, warningChannel, infoChannel)
            )
        }
    }
    
    /**
     * Sends a notification for a detected anomaly.
     * 
     * @param anomaly The anomaly to notify about
     */
    fun sendAnomalyNotification(anomaly: Anomaly) {
        val channelId = getChannelForSeverity(anomaly.severity)
        val (title, icon) = getTitleAndIconForType(anomaly.type)
        
        // Create intent to open app
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("anomaly_id", anomaly.id)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            anomaly.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(anomaly.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(anomaly.message))
            .setPriority(getPriorityForSeverity(anomaly.severity))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(NOTIFICATION_GROUP)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        
        try {
            NotificationManagerCompat.from(context).notify(
                anomaly.id.hashCode(),
                notification
            )
        } catch (e: SecurityException) {
            // Notification permission not granted
            android.util.Log.w("AnomalyNotification", "Notification permission not granted", e)
        }
    }
    
    /**
     * Sends notifications for multiple anomalies with a summary.
     * 
     * @param anomalies List of anomalies to notify about
     */
    fun sendAnomalyNotifications(anomalies: List<Anomaly>) {
        if (anomalies.isEmpty()) return
        
        // Send individual notifications
        anomalies.forEach { sendAnomalyNotification(it) }
        
        // Send summary notification if multiple anomalies
        if (anomalies.size > 1) {
            sendSummaryNotification(anomalies)
        }
    }
    
    private fun sendSummaryNotification(anomalies: List<Anomaly>) {
        val highestSeverity = anomalies.maxByOrNull { it.severity.ordinal }?.severity 
            ?: AnomalySeverity.INFO
        val channelId = getChannelForSeverity(highestSeverity)
        
        val summaryNotification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("${anomalies.size} Health Anomalies Detected")
            .setContentText("Tap to view details")
            .setPriority(getPriorityForSeverity(highestSeverity))
            .setGroup(NOTIFICATION_GROUP)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        
        try {
            NotificationManagerCompat.from(context).notify(
                SUMMARY_NOTIFICATION_ID,
                summaryNotification
            )
        } catch (e: SecurityException) {
            android.util.Log.w("AnomalyNotification", "Notification permission not granted", e)
        }
    }
    
    private fun getChannelForSeverity(severity: AnomalySeverity): String {
        return when (severity) {
            AnomalySeverity.ALERT -> CHANNEL_ID_ANOMALY_ALERTS
            AnomalySeverity.WARNING -> CHANNEL_ID_ANOMALY_WARNINGS
            AnomalySeverity.INFO -> CHANNEL_ID_ANOMALY_INFO
        }
    }
    
    private fun getPriorityForSeverity(severity: AnomalySeverity): Int {
        return when (severity) {
            AnomalySeverity.ALERT -> NotificationCompat.PRIORITY_HIGH
            AnomalySeverity.WARNING -> NotificationCompat.PRIORITY_DEFAULT
            AnomalySeverity.INFO -> NotificationCompat.PRIORITY_LOW
        }
    }
    
    private fun getTitleAndIconForType(type: AnomalyType): Pair<String, Int> {
        return when (type) {
            AnomalyType.LOW_ACTIVITY -> "Low Activity Detected" to R.drawable.ic_launcher_foreground
            AnomalyType.EXCESSIVE_SCREEN_TIME -> "High Screen Time" to R.drawable.ic_launcher_foreground
            AnomalyType.IRREGULAR_SLEEP -> "Irregular Sleep Pattern" to R.drawable.ic_launcher_foreground
            AnomalyType.ELEVATED_HEART_RATE -> "Elevated Heart Rate" to R.drawable.ic_launcher_foreground
            AnomalyType.HIGH_STRESS -> "High Stress Detected" to R.drawable.ic_launcher_foreground
            AnomalyType.MISSED_HYDRATION -> "Hydration Reminder" to R.drawable.ic_launcher_foreground
        }
    }
    
    /**
     * Cancels all anomaly notifications.
     */
    fun cancelAllNotifications() {
        NotificationManagerCompat.from(context).cancelAll()
    }
    
    /**
     * Cancels a specific anomaly notification.
     * 
     * @param anomalyId ID of the anomaly notification to cancel
     */
    fun cancelNotification(anomalyId: String) {
        NotificationManagerCompat.from(context).cancel(anomalyId.hashCode())
    }
}
