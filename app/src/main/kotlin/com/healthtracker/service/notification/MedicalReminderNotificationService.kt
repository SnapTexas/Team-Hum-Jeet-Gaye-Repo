package com.healthtracker.service.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.healthtracker.R
import com.healthtracker.domain.model.HealthReminder
import com.healthtracker.domain.model.MedicalReminderType
import com.healthtracker.domain.model.RepeatType
import com.healthtracker.domain.model.MedicalScheduledReminder
import com.healthtracker.presentation.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing medical reminder notifications.
 * 
 * Handles:
 * - Scheduling notifications with AlarmManager
 * - Creating notification channels with custom sounds
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
        private const val PREFS_NAME = "reminder_settings"
        private const val KEY_RINGTONE_URI = "ringtone_uri"
        
        // Action constants for notification buttons
        const val ACTION_MARK_TAKEN = "com.healthtracker.ACTION_MARK_TAKEN"
        const val ACTION_SNOOZE = "com.healthtracker.ACTION_SNOOZE"
        const val ACTION_DISMISS = "com.healthtracker.ACTION_DISMISS"
        const val ACTION_FULL_SCREEN_ALARM = "com.healthtracker.ACTION_FULL_SCREEN_ALARM"
        
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_SCHEDULED_ID = "scheduled_id"
    }
    
    private val alarmManager: AlarmManager by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }
    
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private val workManager by lazy {
        WorkManager.getInstance(context)
    }
    
    private val vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
    
    init {
        createNotificationChannels()
    }
    
    /**
     * Check if notification permission is granted (Android 13+)
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    /**
     * Gets the saved ringtone URI or default alarm sound.
     */
    fun getRingtoneUri(): Uri {
        val savedUri = prefs.getString(KEY_RINGTONE_URI, null)
        return if (savedUri != null) {
            Uri.parse(savedUri)
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
    }
    
    /**
     * Saves the selected ringtone URI.
     */
    fun setRingtoneUri(uri: Uri) {
        prefs.edit().putString(KEY_RINGTONE_URI, uri.toString()).apply()
        // Recreate channels with new sound
        createNotificationChannels()
    }
    
    /**
     * Creates notification channels for different reminder types.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            val ringtoneUri = getRingtoneUri()
            
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
            
            // Medicine reminders channel (high priority with alarm sound)
            val medicineChannel = NotificationChannel(
                CHANNEL_ID_MEDICINE,
                "Medicine Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders to take your medicine"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                enableLights(true)
                setSound(ringtoneUri, audioAttributes)
                setBypassDnd(true)
            }
            
            // Vaccination reminders channel
            val vaccinationChannel = NotificationChannel(
                CHANNEL_ID_VACCINATION,
                "Vaccination Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for upcoming vaccinations"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                setSound(ringtoneUri, audioAttributes)
            }
            
            // Appointment reminders channel
            val appointmentChannel = NotificationChannel(
                CHANNEL_ID_APPOINTMENT,
                "Appointment Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for medical appointments"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500)
                setSound(ringtoneUri, audioAttributes)
            }
            
            // General health reminders channel
            val generalChannel = NotificationChannel(
                CHANNEL_ID_GENERAL,
                "Health Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "General health reminders"
                enableVibration(true)
                setSound(ringtoneUri, audioAttributes)
            }
            
            // Delete old channels and recreate with new settings
            notificationManager.deleteNotificationChannel(CHANNEL_ID_MEDICINE)
            notificationManager.deleteNotificationChannel(CHANNEL_ID_VACCINATION)
            notificationManager.deleteNotificationChannel(CHANNEL_ID_APPOINTMENT)
            notificationManager.deleteNotificationChannel(CHANNEL_ID_GENERAL)
            
            notificationManager.createNotificationChannels(
                listOf(medicineChannel, vaccinationChannel, appointmentChannel, generalChannel)
            )
        }
    }
    
    /**
     * Schedules notifications for a reminder.
     * Uses both AlarmManager and WorkManager for reliability on MIUI/Chinese ROMs.
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
        
        Timber.d("Scheduling ${nextOccurrences.size} occurrences for reminder ${reminder.id}")
        
        for ((index, occurrence) in nextOccurrences.withIndex()) {
            val triggerTime = occurrence.atZone(zoneId).toInstant().toEpochMilli()
            val delayMillis = triggerTime - System.currentTimeMillis()
            
            if (delayMillis <= 0) {
                Timber.d("Skipping past occurrence: $occurrence")
                continue
            }
            
            val requestCode = generateRequestCode(reminder.id, index)
            
            // Method 1: AlarmManager (may not work on MIUI)
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
                        alarmManager.setAlarmClock(
                            AlarmManager.AlarmClockInfo(triggerTime, pendingIntent),
                            pendingIntent
                        )
                        Timber.d("Scheduled AlarmClock for ${reminder.id} at $occurrence")
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                        Timber.d("Scheduled inexact alarm for ${reminder.id} at $occurrence")
                    }
                } else {
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(triggerTime, pendingIntent),
                        pendingIntent
                    )
                    Timber.d("Scheduled AlarmClock for ${reminder.id} at $occurrence")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to schedule alarm for ${reminder.id}")
            }
            
            // Method 2: WorkManager as backup (more reliable on MIUI)
            try {
                val workData = Data.Builder()
                    .putString("reminder_id", reminder.id)
                    .putString("reminder_type", reminder.type.name)
                    .putString("reminder_title", reminder.title)
                    .putString("reminder_message", getReminderMessage(reminder))
                    .build()
                
                val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                    .setInputData(workData)
                    .addTag("reminder_${reminder.id}")
                    .build()
                
                workManager.enqueueUniqueWork(
                    "reminder_${reminder.id}_$index",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
                Timber.d("Scheduled WorkManager for ${reminder.id} at $occurrence (delay: ${delayMillis}ms)")
            } catch (e: Exception) {
                Timber.e(e, "Failed to schedule WorkManager for ${reminder.id}")
            }
            
            scheduledCount++
        }
        
        Timber.d("Total scheduled: $scheduledCount for reminder ${reminder.id}")
        return scheduledCount
    }
    
    /**
     * Cancels all scheduled notifications for a reminder.
     */
    fun cancelReminder(reminderId: String) {
        // Cancel AlarmManager alarms
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
        
        // Cancel WorkManager jobs
        workManager.cancelAllWorkByTag("reminder_$reminderId")
        
        Timber.d("Cancelled all alarms for reminder $reminderId")
    }
    
    /**
     * Sends a reminder notification immediately with alarm sound and vibration.
     */
    fun sendReminderNotification(scheduledReminder: MedicalScheduledReminder) {
        val channelId = getChannelForType(scheduledReminder.type)
        
        // Play alarm sound
        playAlarmSound()
        
        // Vibrate
        vibrateForReminder()
        
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
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(NOTIFICATION_GROUP)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(getRingtoneUri())
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
        
        // Add action buttons
        actions.forEach { action ->
            builder.addAction(action)
        }
        
        try {
            if (hasNotificationPermission()) {
                NotificationManagerCompat.from(context).notify(
                    scheduledReminder.reminderId.hashCode(),
                    builder.build()
                )
                Timber.d("Sent notification for reminder ${scheduledReminder.reminderId}")
            } else {
                Timber.w("Notification permission not granted")
            }
        } catch (e: SecurityException) {
            Timber.w(e, "Notification permission not granted")
        }
    }
    
    /**
     * Play alarm sound using MediaPlayer for more reliable playback.
     */
    private fun playAlarmSound() {
        try {
            val ringtoneUri = getRingtoneUri()
            val mediaPlayer = MediaPlayer().apply {
                setDataSource(context, ringtoneUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = false
                prepare()
                start()
            }
            
            // Stop after 10 seconds
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    if (mediaPlayer.isPlaying) {
                        mediaPlayer.stop()
                    }
                    mediaPlayer.release()
                } catch (e: Exception) {
                    Timber.e(e, "Error stopping alarm sound")
                }
            }, 10000)
            
            Timber.d("Playing alarm sound: $ringtoneUri")
        } catch (e: Exception) {
            Timber.e(e, "Failed to play alarm sound")
        }
    }
    
    /**
     * Vibrate the device for reminder.
     */
    private fun vibrateForReminder() {
        try {
            val pattern = longArrayOf(0, 500, 200, 500, 200, 500, 200, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
            Timber.d("Vibrating for reminder")
        } catch (e: Exception) {
            Timber.e(e, "Failed to vibrate")
        }
    }
    
    /**
     * Test the alarm - plays sound and vibration immediately.
     */
    fun testAlarm() {
        Timber.d("Testing alarm...")
        playAlarmSound()
        vibrateForReminder()
        
        // Also send a test notification
        val testReminder = MedicalScheduledReminder(
            reminderId = "test_${System.currentTimeMillis()}",
            scheduledTime = Instant.now(),
            title = "🔔 Test Alarm",
            message = "This is a test notification. Your reminders are working!",
            type = MedicalReminderType.CUSTOM
        )
        
        val channelId = CHANNEL_ID_GENERAL
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(testReminder.title)
            .setContentText(testReminder.message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
        
        try {
            if (hasNotificationPermission()) {
                NotificationManagerCompat.from(context).notify(
                    testReminder.reminderId.hashCode(),
                    builder.build()
                )
            }
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

/**
 * WorkManager Worker for reminder notifications.
 * This is a backup mechanism for MIUI and other Chinese ROMs that kill AlarmManager.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    
    override fun doWork(): Result {
        val reminderId = inputData.getString("reminder_id") ?: return Result.failure()
        val reminderType = inputData.getString("reminder_type") ?: "CUSTOM"
        val title = inputData.getString("reminder_title") ?: "Health Reminder"
        val message = inputData.getString("reminder_message") ?: ""
        
        Timber.d("ReminderWorker triggered for: $reminderId")
        
        val type = try {
            MedicalReminderType.valueOf(reminderType)
        } catch (e: Exception) {
            MedicalReminderType.CUSTOM
        }
        
        val scheduledReminder = MedicalScheduledReminder(
            reminderId = reminderId,
            scheduledTime = Instant.now(),
            title = title,
            message = message,
            type = type
        )
        
        // Send notification directly
        sendNotification(scheduledReminder)
        
        return Result.success()
    }
    
    private fun sendNotification(reminder: MedicalScheduledReminder) {
        val channelId = when (reminder.type) {
            MedicalReminderType.MEDICINE -> MedicalReminderNotificationService.CHANNEL_ID_MEDICINE
            MedicalReminderType.VACCINATION -> MedicalReminderNotificationService.CHANNEL_ID_VACCINATION
            MedicalReminderType.APPOINTMENT -> MedicalReminderNotificationService.CHANNEL_ID_APPOINTMENT
            else -> MedicalReminderNotificationService.CHANNEL_ID_GENERAL
        }
        
        // Play sound
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(applicationContext, ringtoneUri)
            ringtone?.play()
            
            // Stop after 10 seconds
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                ringtone?.stop()
            }, 10000)
        } catch (e: Exception) {
            Timber.e(e, "Failed to play ringtone")
        }
        
        // Vibrate
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = applicationContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to vibrate")
        }
        
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            reminder.reminderId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(reminder.title)
            .setContentText(reminder.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    NotificationManagerCompat.from(applicationContext).notify(
                        reminder.reminderId.hashCode(),
                        builder.build()
                    )
                }
            } else {
                NotificationManagerCompat.from(applicationContext).notify(
                    reminder.reminderId.hashCode(),
                    builder.build()
                )
            }
            Timber.d("WorkManager sent notification for ${reminder.reminderId}")
        } catch (e: SecurityException) {
            Timber.w(e, "Notification permission not granted")
        }
    }
}
