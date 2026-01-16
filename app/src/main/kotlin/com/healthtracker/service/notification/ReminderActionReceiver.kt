package com.healthtracker.service.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Broadcast receiver for handling reminder notification actions.
 * 
 * Handles:
 * - Mark as taken (for medicine reminders)
 * - Snooze (reschedule for later)
 * - Dismiss
 */
@AndroidEntryPoint
class ReminderActionReceiver : BroadcastReceiver() {
    
    @Inject
    lateinit var notificationService: MedicalReminderNotificationService
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(MedicalReminderNotificationService.EXTRA_REMINDER_ID)
            ?: return
        
        Timber.d("Reminder action received: ${intent.action} for $reminderId")
        
        when (intent.action) {
            MedicalReminderNotificationService.ACTION_MARK_TAKEN -> {
                handleMarkTaken(context, reminderId)
            }
            
            MedicalReminderNotificationService.ACTION_SNOOZE -> {
                handleSnooze(context, reminderId)
            }
            
            MedicalReminderNotificationService.ACTION_DISMISS -> {
                handleDismiss(context, reminderId)
            }
        }
    }
    
    private fun handleMarkTaken(context: Context, reminderId: String) {
        // Cancel the notification
        NotificationManagerCompat.from(context).cancel(reminderId.hashCode())
        
        // Log the action
        Timber.d("Medicine marked as taken for reminder $reminderId")
        
        // TODO: Record in database that medicine was taken
        scope.launch {
            try {
                // Record medicine taken timestamp
                // This would update the reminder's last triggered time
            } catch (e: Exception) {
                Timber.e(e, "Failed to record medicine taken")
            }
        }
    }
    
    private fun handleSnooze(context: Context, reminderId: String) {
        // Cancel current notification
        NotificationManagerCompat.from(context).cancel(reminderId.hashCode())
        
        // Schedule a new alarm for 10 minutes later
        val snoozeTimeMs = System.currentTimeMillis() + (10 * 60 * 1000) // 10 minutes
        
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(ReminderReceiver.EXTRA_REMINDER_TYPE, "MEDICINE")
            putExtra(ReminderReceiver.EXTRA_REMINDER_TITLE, "Snoozed Reminder")
            putExtra(ReminderReceiver.EXTRA_REMINDER_MESSAGE, "Time to take your medicine")
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (reminderId + "_snooze_alarm").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                snoozeTimeMs,
                pendingIntent
            )
            Timber.d("Snoozed reminder $reminderId for 10 minutes")
        } catch (e: Exception) {
            Timber.e(e, "Failed to snooze reminder")
        }
    }
    
    private fun handleDismiss(context: Context, reminderId: String) {
        // Simply cancel the notification
        NotificationManagerCompat.from(context).cancel(reminderId.hashCode())
        Timber.d("Dismissed reminder $reminderId")
    }
}
