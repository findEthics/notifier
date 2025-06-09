package com.example.notifier
import java.security.MessageDigest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class AppNotificationListenerService : NotificationListenerService() {
    private val allowedPackages = setOf(
        "com.whatsapp","com.mudita.messages","com.mudita.calendar","com.example.notifier"
        // Only listen for notifications from these apps
    )
    private val ignoreNotification = setOf(
        "Ringing…","Calling…"
    )
    private val seenKeys = mutableSetOf<String>()
    private val summaryKeys = mutableMapOf<String, String>() // Group ID → Latest Key
    private val activeNotifications = mutableMapOf<String, StatusBarNotification>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in allowedPackages) {
            Log.d("NotificationListenerService", "Ignoring notification from ${sbn.packageName}")
            cancelNotification(sbn.key)
            return
        }  // Ignore notifications not in the list and remove from system notifications
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        Log.d("NotificationListenerService", "Received notification from ${sbn.packageName}")

        if (sbn.packageName != "com.example.notifier") {
            if (title.isEmpty() || text.isEmpty() || !(extras.containsKey("android.template"))) {
                cancelNotification(sbn.key)
                return
            }
        }// Skip empty and has no template notifications

        val summaryText = extras.getString("android.summaryText") ?: ""
        val isWhatsAppSummary = (summaryText != "")
        if (isWhatsAppSummary && title!="WhatsApp") {
            cancelNotification(sbn.key)
            return
        }

        if ((sbn.packageName == "com.whatsapp") && (text in ignoreNotification)) {
            Log.d("NotificationListenerService", "Ignoring call notification from ${sbn.packageName}")
            cancelNotification(sbn.key)
            return
        }

        val isGroupSummary = extras.getBoolean("android.support.isGroupSummary", false)
        var postTimeProxy = sbn.postTime
        if (text == "Incoming voice call" && sbn.packageName == "com.whatsapp") {
            postTimeProxy = sbn.postTime/100000
        }
        val contentKey = "${sbn.packageName}|${sbn.id}|${title}|${postTimeProxy}".sha256() // Create unique key
        activeNotifications[contentKey] = sbn
        if (!isGroupSummary && contentKey in seenKeys) return // Skip already seen individual notifications (avoid duplicates)

        if (!isGroupSummary) { seenKeys.add(contentKey) } // Track individual notifications

        // Handle group summaries
        if (isGroupSummary || isWhatsAppSummary) {
            // Check if we already have a summary for this package
            val groupId = sbn.notification.group ?: "default_group"
            val newSummaryKey = "SUMMARY|${sbn.packageName}|$groupId|${sbn.postTime}".sha256()

            // Remove previous summary for this group
            summaryKeys[sbn.packageName]?.let { oldKey ->
                if (oldKey != newSummaryKey) {
                    LocalBroadcastManager.getInstance(this).sendBroadcast(
                        Intent("REMOVE_NOTIFICATION").apply {
                            putExtra("key", oldKey)
                        }
                    )
                }
            }

            // Track new summary
            summaryKeys[sbn.packageName] = newSummaryKey

            // Send the new summary
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                Intent("NEW_NOTIFICATION").apply {
                    putExtra("key", newSummaryKey)
                    putExtra("title", title)
                    putExtra("text", text)
                    putExtra("package", sbn.packageName)
                    putExtra("isGroupSummary", isGroupSummary)
                    putExtra("systemKey", sbn.key)
                }
            )

        } else { //Send individual notification
            val key = "INDIVIDUAL|${sbn.packageName}|$contentKey"
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                Intent("NEW_NOTIFICATION").apply {
                    putExtra("key", key)
                    putExtra("title", title)
                    putExtra("text", text)
                    putExtra("package", sbn.packageName)
                    putExtra("isGroupSummary", isGroupSummary)
                    putExtra("systemKey", sbn.key)
                }
            )
        }
    }

    // Receiver to handle cancellation
    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val key = intent.getStringExtra("key") ?: return
            activeNotifications[key]?.let { sbn ->
                cancelNotification(sbn.key) // Cancel the system notification
                activeNotifications.remove(key)
                seenKeys.remove(key.split("|").last()) // Remove individual notification key
                if (key.startsWith("SUMMARY")) { // 3. Handle group summaries
                    summaryKeys.values.remove(key)
                }
            }
        }
    }

    private val cancelSystemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            cancelNotification(intent.getStringExtra("systemKey"))
        }
    }

    override fun onCreate() {
        super.onCreate()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(refreshReceiver, IntentFilter("FORCE_REFRESH"))
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(cancelReceiver, IntentFilter("CANCEL_NOTIFICATION"))
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(cancelSystemReceiver, IntentFilter("CANCEL_SYSTEM_NOTIFICATION"))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getString("android.text") ?: ""
        var postTimeProxy = sbn.postTime
        if (text == "Incoming voice call" && sbn.packageName == "com.whatsapp") {
            postTimeProxy = sbn.postTime/100000
        }

        val contentKey = "${sbn.packageName}|${sbn.id}|${title}|${postTimeProxy}".sha256() // Create unique key
        cancelNotification(sbn.key)
        activeNotifications.remove(contentKey)

    }

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "FORCE_REFRESH") {
                forceRefreshNotifications()
            }
        }
    }

    private fun forceRefreshNotifications() {
        try {
            // Get current system notifications
            val currentSystemNotifications = getActiveNotifications()?.toList() ?: emptyList()

            // 1. Remove local notifications not in system
            activeNotifications.keys.toList().forEach { key ->
                if (currentSystemNotifications.none { generateKey(it) == key }) {
                    LocalBroadcastManager.getInstance(this).sendBroadcast(
                        Intent("REMOVE_NOTIFICATION").apply {
                            putExtra("key", key)
                        }
                    )
                }
            }

            // 2. Add/update existing system notifications
            currentSystemNotifications.forEach { sbn ->
                if (sbn.packageName in allowedPackages) {
                    onNotificationPosted(sbn) // Re-process valid notifications
                } else {
                    cancelNotification(sbn.key)
                }
            }
        } catch (e: Exception) {
            Log.e("NotificationService", "Force refresh failed", e)
        }
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(cancelReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(refreshReceiver)
        super.onDestroy()
    }

    fun String.sha256(): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(this.toByteArray())
            .fold("") { str, byte -> str + "%02x".format(byte) }
    }

    private fun generateKey(sbn: StatusBarNotification): String {
        return "${sbn.packageName}|${sbn.id}|${sbn.notification.extras.getString("android.title")}|${sbn.postTime}".sha256()
    }

}
