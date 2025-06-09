package com.example.notifier.Calendar

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.notifier.MainActivity
import com.example.notifier.R
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventSummary = intent.getStringExtra("EVENT_SUMMARY") ?: "Event"
        val eventStartTime = intent.getStringExtra("EVENT_START_TIME") ?: ""
        val notificationId = intent.getIntExtra("NOTIFICATION_ID", 0)

        val formattedTime = try {
            val odt = OffsetDateTime.parse(eventStartTime)
            val formatter = DateTimeFormatter.ofPattern("hh:mm a")
            odt.format(formatter)
        } catch (e: Exception) { "" }

        // Create an intent to open the app when the notification is tapped
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(context, 0, tapIntent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, "CALENDAR_REMINDERS")
            .setSmallIcon(R.drawable.ic_calendar) // Your calendar icon
            .setContentTitle(eventSummary)
            .setContentText("Starts at $formattedTime")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(Notification.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_SYSTEM)
            // --- DIAGNOSTIC TEST: Temporarily make the notification ongoing ---
//            .setOngoing(true)

        with(NotificationManagerCompat.from(context)) {
            // notificationId is unique for each notification
            notify(notificationId, builder.build())
        }
    }
}