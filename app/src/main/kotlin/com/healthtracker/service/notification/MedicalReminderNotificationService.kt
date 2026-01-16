package com.healthtracker.service.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.healthtracker.R
import com.healthtracker.domain.model.HealthReminder
import com.healthtracker.domain.model.MedicalReminderType
import com.healthtracker.domain.model.RepeatType
import com.healthtracker.domain.model.MedicalScheduledReminder
import com.healthtracker.domain.repository.MedicalRepository
import com.healthtracker.presentation.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing medical reminder notifications.
 * 
 * Handles:
 * - Scheduling notifications with AlarmManager
 * - Creating notification channels
 * - Sending reminder notifications
 * - Canceling scheduled notifications
 */
@Singleton
class MedicalReminderNotificationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        const val CHANNEL_ID_MEDICINE = "medicine_reminders"
        const val CHANNEL_ID_VACCINATION = "vaccination_reminders"
        const val CHANNEL_ID_APPOINTMENT = "appointment_reminders"
        const val CHANNEL_ID_GENERAL = "general_health_reminders"
        
        private const val NOTIFICATION_GROUP = "com.healthtracker.REMINDER_GROUP"
        
        // Action constants for notification buttons
        const val ACTION_MARK_TAKEN = "com.healthtracker.ACTION_MARK_TAKEN"
        const val ACTION_SNOOZE = "com.healthtracker.ACTION_SNOOZE"
        const val ACTION_DISMISS = "com.healthtracker.ACTION_DISMISS"
        
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_SCHEDULED_ID = "scheduled_id"
    }
    
    private val alarmManager: AlarmManager by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }
    
    init {
        createNotificationChannels()
    }
    
    /**
     * Creates notification channels for different reminder types.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            
            // Medicine reminders channel (high priority)
            val medicineChannel = NotificationChannel(
                CHANNEL_ID_MEDICINE,
                "Medicine Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders to take your medicine"
                enableVibration(true)
                enableLights(true)
            }
            
            // Vaccination reminders channel
            val vaccinationChannel = NotificationChannel(
                CHANNEL_ID_VACCINATION,
                "Vaccination Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for upcoming vaccinations"
                enableVibration(true)
            }
            
            // Appointment reminders channel
            val appointmentChannel = NotificationChannel(
                CHANNEL_ID_APPOINTMENT,
                "Appointment Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for medical appointments"
                enableVibration(true)
            }
            
            // General health reminders channel
            val generalChannel = NotificationChannel(
                CHANNEL_ID_GENERAL,
                "Health Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "General health reminders"
            }
            
            notificationManager.createNotificationChannels(
                listOf(medicineChannel, vaccinationChannel, appointmentChannel, generalChannel)
            )
        }
    }
    
    /**
     * Schedules notifications for a reminder.
     * 
     * @param reminder The reminder to schedule
     * @return Number of notifications scheduled
     */
    fun scheduleReminder(reminder: HealthReminder): Int {
        if (!reminder.enabled) {
            Timber.d("Reminder ${reminder.id} is disabled, not scheduling")
            return 0
        }
        
        var scheduledCount = 0
        val now = LocalDateTime.now()
        val zoneId = ZoneId.systemDefault()
        
        // Calculate next occurrences based on schedule
        val nextOccurrences = calculateNextOccurrences(reminder, now, 7) // Schedule up to 7 days ahead
        
        for ((index, occurrence) in nextOccurrences.withIndex()) {
            val triggerTime = occurrence.atZone(zoneId).toInstant().toEpochMilli()
            val requestCode = generateRequestCode(reminder.id, index)
            
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminder.id)
                putExtra(ReminderReceiver.EXTRA_REMINDER_TYPE, reminder.type.name)
                putExtra(ReminderReceiver.EXTRA_REMINDER_TITLE, reminder.title)
                putExtra(ReminderReceiver.EXTRA_REMINDER_MESSAGE, getReminderMessage(reminder))
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                        scheduledCount++
                    } else {
                        // Fall back to inexact alarm
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                        scheduledCount++
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    scheduledCount++
                }
                
                Timber.d("Scheduled reminder ${reminder.id} for $occurrence")
            } catch (e: Exception) {
                Timber.e(e, "Failed to schedule reminder ${reminder.id}")
            }
        }
        
        return scheduledCount
    }
    
    /**
     * Cancels all scheduled notifications for a reminder.
     */
    fun cancelReminder(reminderId: String) {
        // Cancel up to 100 potential scheduled alarms for this reminder
        for (index in 0 until 100) {
            val requestCode = generateRequestCode(reminderId, index)
            
            val intent = Intent(context, ReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            
            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
        }
        
        Timber.d("Cancelled all alarms for reminder $reminderId")
    }
    
    /**
     * Sends a reminder notification immediately.
     */
    fun sendReminderNotification(scheduledReminder: MedicalScheduledReminder) {
        val channelId = getChannelForType(scheduledReminder.type)
        
        // Create intent to open app
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("reminder_id", scheduledReminder.reminderId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            scheduledReminder.reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create action buttons based on reminder type
        val actions = createActionsForType(scheduledReminder)
        
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(getIconForType(scheduledReminder.type))
            .setContentTitle(scheduledReminder.title)
            .setContentText(scheduledReminder.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(scheduledReminder.message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(NOTIFICATION_GROUP)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        
        // Add action buttons
        actions.forEach { action ->
            builder.addAction(action)
        }
        
        try {
            NotificationManagerCompat.from(context).notify(
                scheduledReminder.reminderId.hashCode(),
                builder.build()
            )
            Timber.d("Sent notification for reminder ${scheduledReminder.reminderId}")
        } catch (e: SecurityException) {
            Timber.w(e, "Notification permission not granted")
        }
    }
    
    /**
     * Sends a notification for a HealthReminder.
     */
    fun sendReminderNotification(reminder: HealthReminder) {
        val scheduledReminder = MedicalScheduledReminder(
            reminderId = reminder.id,
            scheduledTime = Instant.now(),
            title = reminder.title,
            message = getReminderMessage(reminder),
            type = reminder.type
        )
        sendReminderNotification(scheduledReminder)
    }
    
    /**
     * Calculates the next occurrences of a reminder.
     */
    private fun calculateNextOccurrences(
        reminder: HealthReminder,
        from: LocalDateTime,
        maxDays: Int
    ): List<LocalDateTime> {
        val occurrences = mutableListOf<LocalDateTime>()
        val schedule = reminder.schedule
        
        when (schedule.repeatType) {
            RepeatType.ONCE -> {
                // Single occurrence
                for (time in schedule.times) {
                    val occurrence = LocalDateTime.of(schedule.startDate, time)
                    if (occurrence.isAfter(from)) {
                        occurrences.add(occurrence)
                    }
                }
            }
            
            RepeatType.DAILY -> {
                // Daily occurrences
                var currentDate = schedule.startDate
                val endDate = schedule.endDate ?: schedule.startDate.plusDays(maxDays.toLong())
                val maxDate = from.toLocalDate().plusDays(maxDays.toLong())
                
                while (!currentDate.isAfter(endDate) && !currentDate.isAfter(maxDate)) {
                    for (time in schedule.times) {
                        val occurrence = LocalDateTime.of(currentDate, time)
                        if (occurrence.isAfter(from)) {
                            occurrences.add(occurrence)
                        }
                    }
                    currentDate = currentDate.plusDays(1)
                }
            }
            
            RepeatType.WEEKLY -> {
                // Weekly occurrences on specific days
                val daysOfWeek = schedule.daysOfWeek ?: return occurrences
                var currentDate = schedule.startDate
                val endDate = schedule.endDate ?: schedule.startDate.plusDays(maxDays.toLong())
                val maxDate = from.toLocalDate().plusDays(maxDays.toLong())
                
                while (!currentDate.isAfter(endDate) && !currentDate.isAfter(maxDate)) {
                    if (currentDate.dayOfWeek in daysOfWeek) {
                        for (time in schedule.times) {
                            val occurrence = LocalDateTime.of(currentDate, time)
                            if (occurrence.isAfter(from)) {
                                occurrences.add(occurrence)
                            }
                        }
                    }
                    currentDate = currentDate.plusDays(1)
                }
            }
            
            RepeatType.MONTHLY -> {
                // Monthly occurrences on the same day of month
                var currentDate = schedule.startDate
                val endDate = schedule.endDate ?: schedule.startDate.plusMonths(maxDays.toLong() / 30)
                
                while (!currentDate.isAfter(endDate)) {
                    for (time in schedule.times) {
                        val occurrence = LocalDateTime.of(currentDate, time)
                        if (occurrence.isAfter(from)) {
                            occurrences.add(occurrence)
                        }
                    }
                    currentDate = currentDate.plusMonths(1)
                }
            }
            
            RepeatType.CUSTOM -> {
                // Custom handling - treat as daily for now
                var currentDate = schedule.startDate
                val endDate = schedule.endDate ?: schedule.startDate.plusDays(maxDays.toLong())
                
                while (!currentDate.isAfter(endDate)) {
                    for (time in schedule.times) {
                        val occurrence = LocalDateTime.of(currentDate, time)
                        if (occurrence.isAfter(from)) {
                            occurrences.add(occurrence)
                        }
                    }
                    currentDate = currentDate.plusDays(1)
                }
            }
        }
        
        return occurrences.sorted()
    }
    
    private fun generateRequestCode(reminderId: String, index: Int): Int {
        return (reminderId.hashCode() + index) and 0x7FFFFFFF
    }
    
    private fun getChannelForType(type: MedicalReminderType): String {
        return when (type) {
            MedicalReminderType.MEDICINE -> CHANNEL_ID_MEDICINE
            MedicalReminderType.VACCINATION -> CHANNEL_ID_VACCINATION
            MedicalReminderType.APPOINTMENT -> CHANNEL_ID_APPOINTMENT
            MedicalReminderType.CHECKUP, MedicalReminderType.CUSTOM -> CHANNEL_ID_GENERAL
        }
    }
    
    private fun getIconForType(type: MedicalReminderType): Int {
        // Using launcher icon as placeholder - in production, use specific icons
        return R.drawable.ic_launcher_foreground
    }
    
    private fun getReminderMessage(reminder: HealthReminder): String {
        return reminder.description ?: when (reminder.type) {
            MedicalReminderType.MEDICINE -> "Time to take your medicine: ${reminder.title}"
            MedicalReminderType.VACCINATION -> "Vaccination reminder: ${reminder.title}"
            MedicalReminderType.APPOINTMENT -> "Upcoming appointment: ${reminder.title}"
            MedicalReminderType.CHECKUP -> "Health checkup reminder: ${reminder.title}"
            MedicalReminderType.CUSTOM -> reminder.title
        }
    }
    
    private fun createActionsForType(reminder: MedicalScheduledReminder): List<NotificationCompat.Action> {
        val actions = mutableListOf<NotificationCompat.Action>()
        
        when (reminder.type) {
            MedicalReminderType.MEDICINE -> {
                // Mark as taken action
                val takenIntent = Intent(context, ReminderActionReceiver::class.java).apply {
                    action = ACTION_MARK_TAKEN
                    putExtra(EXTRA_REMINDER_ID, reminder.reminderId)
                }
                val takenPendingIntent = PendingIntent.getBroadcast(
                    context,
                    (reminder.reminderId + "_taken").hashCode(),
                    takenIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                actions.add(
                    NotificationCompat.Action.Builder(
                        R.drawable.ic_launcher_foreground,
                        "Mark Taken",
                        takenPendingIntent
                    ).build()
                )
                
                // Snooze action
                val snoozeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
                    action = ACTION_SNOOZE
                    putExtra(EXTRA_REMINDER_ID, reminder.reminderId)
                }
                val snoozePendingIntent = PendingIntent.getBroadcast(
                    context,
                    (reminder.reminderId + "_snooze").hashCode(),
                    snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                actions.add(
                    NotificationCompat.Action.Builder(
                        R.drawable.ic_launcher_foreground,
                        "Snooze 10 min",
                        snoozePendingIntent
                    ).build()
                )
            }
            
            else -> {
                // Dismiss action for other types
                val dismissIntent = Intent(context, ReminderActionReceiver::class.java).apply {
                    action = ACTION_DISMISS
                    putExtra(EXTRA_REMINDER_ID, reminder.reminderId)
                }
                val dismissPendingIntent = PendingIntent.getBroadcast(
                    context,
                    (reminder.reminderId + "_dismiss").hashCode(),
                    dismissIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                actions.add(
                    NotificationCompat.Action.Builder(
                        R.drawable.ic_launcher_foreground,
                        "Dismiss",
                        dismissPendingIntent
                    ).build()
                )
            }
        }
        
        return actions
    }
    
    /**
     * Cancels all reminder notifications.
     */
    fun cancelAllNotifications() {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
