package com.example.notifier.Calendar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventSummary = intent.getStringExtra("EVENT_SUMMARY") ?: "Event"
        val eventStartTime = intent.getStringExtra("EVENT_START_TIME") ?: ""

        val formattedTime = try {
            val odt = OffsetDateTime.parse(eventStartTime)
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            odt.format(formatter)
        } catch (e: Exception) { "" }

        // Play only the notification sound without posting a notification
        try {
            val notificationUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, notificationUri)
            ringtone?.play()
        } catch (e: Exception) {
            // Ignore if sound cannot be played
        }
    }
}