package com.example.notifier

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class AppNotificationListenerService : NotificationListenerService() {
    private val seenKeys = mutableSetOf<String>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras

        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val key = "${sbn.packageName}|${sbn.id}|${sbn.tag}|${title}|${text}"
        val isGroupSummary = extras.getBoolean("android.support.isGroupSummary", false)

        // Skip group summaries, empty content, or duplicates
        if (isGroupSummary || (title.isEmpty() && text.isEmpty()) || key in seenKeys) return

        seenKeys.add(key)

        LocalBroadcastManager.getInstance(this).sendBroadcast(
            Intent("NEW_NOTIFICATION").apply {
                putExtra("key", key)
                putExtra("title", title)
                putExtra("text", text)
                putExtra("package", sbn.packageName)
            }
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val key = "${sbn.packageName}|${sbn.id}|${sbn.tag}"
        seenKeys.remove(key)
    }
}
