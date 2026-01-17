package com.healthtracker.service.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.healthtracker.service.step.StepCounterService
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Broadcast receiver for device boot completion.
 * 
 * Reschedules all health reminders and starts step counter service after device restart.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.d("Device boot completed, starting services")
            
            // Start step counter service
            StepCounterService.startService(context)
            
            // TODO: Reschedule all health reminders
            // TODO: Reschedule medication reminders
            // TODO: Reschedule vaccination reminders
        }
    }
}
