// In app/src/main/java/com/example/notifier/Calendar/CalendarEventReminder.kt

package com.example.notifier.Calendar

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.OffsetDateTime

class CalendarEventReminder {

    fun scheduleReminderForEvent(context: Context, event: CalendarEvent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        try {
            val odt = OffsetDateTime.parse(event.startTime)
            val eventTimeMillis = odt.toInstant().toEpochMilli()
            var reminderTimeMillis = eventTimeMillis - (15 * 60 * 1000) // 15 minutes before
            val currentTimeMillis = System.currentTimeMillis()

            // Schedule the alarm only if the reminder time is in the future
            if (eventTimeMillis > currentTimeMillis) {

                // Check if the standard 15-minute reminder time is in the past
                if (reminderTimeMillis <= currentTimeMillis) {
                    // If it is, set a new reminder time for 1 minute from now
                    reminderTimeMillis = currentTimeMillis + (1 * 60 * 1000)
                }

                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    putExtra("EVENT_SUMMARY", event.summary)
                    putExtra("EVENT_START_TIME", event.startTime)
                    // Use a unique ID for the notification to avoid collisions
                    putExtra("NOTIFICATION_ID", event.hashCode())
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    event.hashCode(), // Use a unique request code for each alarm
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Schedule the alarm to wake up the device
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminderTimeMillis,
                        pendingIntent
                    )
                }
            }
        } catch (e: Exception) {
            // This can happen for all-day events that don't have a specific time
        }
    }

    fun cancelReminderForEvent(context: Context, event: CalendarEvent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("EVENT_SUMMARY", event.summary)
            putExtra("EVENT_START_TIME", event.startTime)
            putExtra("NOTIFICATION_ID", event.hashCode())
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            event.hashCode(), // Use the same request code as when scheduling
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Cancel the alarm
        alarmManager.cancel(pendingIntent)
        
        // Cancel the pending intent to free up resources
        pendingIntent.cancel()
    }

    fun cancelAllRemindersForEvents(context: Context, events: List<CalendarEvent>) {
        events.forEach { event ->
            cancelReminderForEvent(context, event)
        }
    }
}