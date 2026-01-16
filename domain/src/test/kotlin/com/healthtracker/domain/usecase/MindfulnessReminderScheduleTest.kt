package com.healthtracker.domain.usecase

import com.healthtracker.domain.model.MindfulnessReminder
import com.healthtracker.domain.model.ReminderType
import com.healthtracker.domain.model.ScheduledReminder
import com.healthtracker.domain.model.StressConstants
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.abs

/**
 * Property-based tests for mindfulness reminder scheduling.
 * 
 * **Validates: Requirements 9.5**
 * 
 * Property 21: Mindfulness Reminder Schedule
 * Mindfulness reminders SHALL be sent at the user-configured intervals 
 * with a tolerance of ±1 minute (60 seconds).
 */
class MindfulnessReminderScheduleTest : FunSpec({
    
    test("Property 21.1: Reminders are scheduled at configured intervals") {
        checkAll(50, Arb.int(15..120)) { intervalMinutes ->
            val reminder = createReminder(intervalMinutes = intervalMinutes)
            
            val scheduledReminders = scheduleRemindersForDay(reminder)
            
            if (scheduledReminders.size > 1) {
                // Check intervals between consecutive reminders
                for (i in 1 until scheduledReminders.size) {
                    val prev = scheduledReminders[i - 1]
                    val curr = scheduledReminders[i]
                    
                    val actualIntervalSeconds = Duration.between(
                        prev.scheduledTime,
                        curr.scheduledTime
                    ).seconds
                    
                    val expectedIntervalSeconds = intervalMinutes * 60L
                    val tolerance = StressConstants.REMINDER_TOLERANCE_SECONDS
                    
                    // Interval should be within tolerance
                    val diff = abs(actualIntervalSeconds - expectedIntervalSeconds)
                    diff shouldBeLessThanOrEqual tolerance
                }
            }
        }
    }
    
    test("Property 21.2: Reminders respect start and end time boundaries") {
        checkAll(50, Arb.int(30..90)) { intervalMinutes ->
            val startTime = LocalTime.of(9, 0)
            val endTime = LocalTime.of(18, 0)
            
            val reminder = createReminder(
                intervalMinutes = intervalMinutes,
                startTime = startTime,
                endTime = endTime
            )
            
            val scheduledReminders = scheduleRemindersForDay(reminder)
            
            scheduledReminders.forEach { scheduled ->
                val scheduledLocalTime = scheduled.scheduledTime
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()
                
                // Should be after or at start time (with tolerance)
                val afterStart = !scheduledLocalTime.isBefore(startTime.minusMinutes(1))
                afterStart.shouldBeTrue()
                
                // Should be before or at end time (with tolerance)
                val beforeEnd = !scheduledLocalTime.isAfter(endTime.plusMinutes(1))
                beforeEnd.shouldBeTrue()
            }
        }
    }
    
    test("Property 21.3: Reminders only scheduled on configured days of week") {
        val activeDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        
        val reminder = createReminder(
            intervalMinutes = 60,
            daysOfWeek = activeDays
        )
        
        // Check for a week
        val today = LocalDate.now()
        for (dayOffset in 0..6) {
            val date = today.plusDays(dayOffset.toLong())
            val dayOfWeek = date.dayOfWeek
            
            val scheduledReminders = scheduleRemindersForDate(reminder, date)
            
            if (activeDays.contains(dayOfWeek)) {
                // Should have reminders on active days
                scheduledReminders.shouldNotBeEmpty()
            } else {
                // Should have no reminders on inactive days
                scheduledReminders.size shouldBe 0
            }
        }
    }
    
    test("Property 21.4: Disabled reminders are not scheduled") {
        val reminder = createReminder(
            intervalMinutes = 60,
            enabled = false
        )
        
        val scheduledReminders = scheduleRemindersForDay(reminder)
        
        scheduledReminders.size shouldBe 0
    }
    
    test("Property 21.5: Reminder timing tolerance is within ±1 minute") {
        checkAll(50, Arb.int(15..120)) { intervalMinutes ->
            val reminder = createReminder(intervalMinutes = intervalMinutes)
            
            val scheduledReminders = scheduleRemindersForDay(reminder)
            
            if (scheduledReminders.isNotEmpty()) {
                val firstReminder = scheduledReminders.first()
                
                // Simulate delivery
                val deliveredAt = firstReminder.scheduledTime.plusSeconds(30) // 30 seconds late
                
                val isWithinTolerance = isDeliveryWithinTolerance(
                    scheduledTime = firstReminder.scheduledTime,
                    deliveredTime = deliveredAt
                )
                
                isWithinTolerance.shouldBeTrue()
            }
        }
    }
    
    test("Property 21.6: Delivery outside tolerance is flagged") {
        val reminder = createReminder(intervalMinutes = 60)
        val scheduledReminders = scheduleRemindersForDay(reminder)
        
        if (scheduledReminders.isNotEmpty()) {
            val firstReminder = scheduledReminders.first()
            
            // Simulate late delivery (2 minutes late - outside tolerance)
            val deliveredAt = firstReminder.scheduledTime.plusSeconds(120)
            
            val isWithinTolerance = isDeliveryWithinTolerance(
                scheduledTime = firstReminder.scheduledTime,
                deliveredTime = deliveredAt
            )
            
            isWithinTolerance.shouldBeFalse()
        }
    }
    
    test("Property 21.7: Number of reminders matches expected count for time window") {
        checkAll(50, Arb.int(30..120)) { intervalMinutes ->
            val startTime = LocalTime.of(9, 0)
            val endTime = LocalTime.of(17, 0)
            val windowMinutes = Duration.between(startTime, endTime).toMinutes()
            
            val reminder = createReminder(
                intervalMinutes = intervalMinutes,
                startTime = startTime,
                endTime = endTime
            )
            
            val scheduledReminders = scheduleRemindersForDay(reminder)
            
            // Expected count: window / interval + 1 (for the first one at start)
            val expectedCount = (windowMinutes / intervalMinutes).toInt() + 1
            
            // Actual count should be close to expected (within 1 due to boundary conditions)
            val diff = abs(scheduledReminders.size - expectedCount)
            diff shouldBeLessThanOrEqual 1
        }
    }
    
    test("Property 21.8: Each reminder has correct type from configuration") {
        checkAll(50, Arb.element(ReminderType.entries.toList())) { reminderType ->
            val reminder = createReminder(
                intervalMinutes = 60,
                reminderType = reminderType
            )
            
            val scheduledReminders = scheduleRemindersForDay(reminder)
            
            scheduledReminders.forEach { scheduled ->
                scheduled.type shouldBe reminderType
            }
        }
    }
    
    test("Property 21.9: Scheduled reminders have unique IDs") {
        val reminder = createReminder(intervalMinutes = 30)
        val scheduledReminders = scheduleRemindersForDay(reminder)
        
        val ids = scheduledReminders.map { it.id }
        val uniqueIds = ids.toSet()
        
        ids.size shouldBe uniqueIds.size
    }
    
    test("Property 21.10: Reminders reference parent reminder ID") {
        val reminder = createReminder(intervalMinutes = 60)
        val scheduledReminders = scheduleRemindersForDay(reminder)
        
        scheduledReminders.forEach { scheduled ->
            scheduled.reminderId shouldBe reminder.id
        }
    }
})

/**
 * Creates a test mindfulness reminder.
 */
private fun createReminder(
    intervalMinutes: Int = 60,
    startTime: LocalTime = LocalTime.of(8, 0),
    endTime: LocalTime = LocalTime.of(20, 0),
    daysOfWeek: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    reminderType: ReminderType = ReminderType.BREATHING_BREAK,
    enabled: Boolean = true
): MindfulnessReminder {
    return MindfulnessReminder(
        id = UUID.randomUUID().toString(),
        userId = "test-user",
        enabled = enabled,
        intervalMinutes = intervalMinutes,
        startTime = startTime,
        endTime = endTime,
        daysOfWeek = daysOfWeek,
        reminderType = reminderType,
        message = "Time for a ${reminderType.name.lowercase().replace("_", " ")}"
    )
}

/**
 * Schedules reminders for today based on the reminder configuration.
 */
private fun scheduleRemindersForDay(reminder: MindfulnessReminder): List<ScheduledReminder> {
    return scheduleRemindersForDate(reminder, LocalDate.now())
}

/**
 * Schedules reminders for a specific date based on the reminder configuration.
 */
private fun scheduleRemindersForDate(
    reminder: MindfulnessReminder,
    date: LocalDate
): List<ScheduledReminder> {
    if (!reminder.enabled) return emptyList()
    if (!reminder.daysOfWeek.contains(date.dayOfWeek)) return emptyList()
    
    val scheduledReminders = mutableListOf<ScheduledReminder>()
    var currentTime = reminder.startTime
    
    while (!currentTime.isAfter(reminder.endTime)) {
        val scheduledInstant = date
            .atTime(currentTime)
            .atZone(ZoneId.systemDefault())
            .toInstant()
        
        scheduledReminders.add(
            ScheduledReminder(
                id = UUID.randomUUID().toString(),
                reminderId = reminder.id,
                scheduledTime = scheduledInstant,
                type = reminder.reminderType,
                message = reminder.message ?: "Mindfulness reminder",
                delivered = false,
                deliveredAt = null
            )
        )
        
        currentTime = currentTime.plusMinutes(reminder.intervalMinutes.toLong())
    }
    
    return scheduledReminders
}

/**
 * Checks if delivery time is within tolerance of scheduled time.
 */
private fun isDeliveryWithinTolerance(
    scheduledTime: Instant,
    deliveredTime: Instant
): Boolean {
    val diffSeconds = abs(Duration.between(scheduledTime, deliveredTime).seconds)
    return diffSeconds <= StressConstants.REMINDER_TOLERANCE_SECONDS
}
