package com.healthtracker.service.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.healthtracker.domain.model.MedicalReminderType
import com.healthtracker.domain.model.MedicalScheduledReminder
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject

/**
 * Broadcast receiver for scheduled health reminders.
 * 
 * Handles:
 * - Medicine reminders
 * - Vaccination reminders
 * - Appointment reminders
 * - Custom health reminders
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_REMINDER_TYPE = "reminder_type"
        const val EXTRA_REMINDER_TITLE = "reminder_title"
        const val EXTRA_REMINDER_MESSAGE = "reminder_message"
    }
    
    @Inject
    lateinit var notificationService: MedicalReminderNotificationService

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val reminderTypeStr = intent.getStringExtra(EXTRA_REMINDER_TYPE) ?: "CUSTOM"
        val title = intent.getStringExtra(EXTRA_REMINDER_TITLE) ?: "Health Reminder"
        val message = intent.getStringExtra(EXTRA_REMINDER_MESSAGE) ?: ""
        
        Timber.d("Reminder received: id=$reminderId, type=$reminderTypeStr")
        
        val reminderType = try {
            MedicalReminderType.valueOf(reminderTypeStr)
        } catch (e: Exception) {
            MedicalReminderType.CUSTOM
        }
        
        val scheduledReminder = MedicalScheduledReminder(
            reminderId = reminderId,
            scheduledTime = Instant.now(),
            title = title,
            message = message,
            type = reminderType
        )
        
        // Send the notification
        notificationService.sendReminderNotification(scheduledReminder)
        
        Timber.d("Reminder notification sent for $reminderId")
    }
}
