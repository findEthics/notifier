package com.example.notifier
import java.security.MessageDigest
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class AppNotificationListenerService : NotificationListenerService() {
    private val allowedPackages = setOf(
        "com.whatsapp","com.mudita.messages"
        // Only listen for notifications from these apps
    )
    private val seenKeys = mutableSetOf<String>()
    private val summaryKeys = mutableMapOf<String, String>() // Group ID → Latest Key
    private val activeNotifications = mutableMapOf<String, StatusBarNotification>()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in allowedPackages) return  // Ignore notifications not in the list
        val extras = sbn.notification.extras

        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        if ((title.isEmpty() && text.isEmpty()) || !(extras.containsKey("android.template"))) return // Skip empty and has no template notifications

        val summaryText = extras.getString("android.summaryText") ?: "" //
        val isGroupSummary = extras.getBoolean("android.support.isGroupSummary", false)

        val contentKey = "${sbn.packageName}|${sbn.id}|${title}|${sbn.postTime}".sha256() // Create unique key
        activeNotifications[contentKey] = sbn

        if (!isGroupSummary && contentKey in seenKeys) return // Skip already seen individual notifications (avoid duplicates)

        if (!isGroupSummary) { seenKeys.add(contentKey) } // Track individual notifications

        val isWhatsAppSummary = (summaryText != "")

        // Handle group summaries

        if (isGroupSummary || isWhatsAppSummary) {
            // Check if we already have a summary for this package
            summaryKeys[sbn.packageName]?.let { oldKey ->
                // Remove old summary
                LocalBroadcastManager.getInstance(this).sendBroadcast(
                    Intent("REMOVE_NOTIFICATION").apply {
                        putExtra("key", oldKey)
                    }
                )

            }
            // Track this new summary
            val groupId = sbn.notification.group ?: "default_group"
            val summaryKey = "SUMMARY|${sbn.packageName}|$groupId".sha256()

            summaryKeys[sbn.packageName] = summaryKey
            // Send the new summary
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                Intent("NEW_NOTIFICATION").apply {
                    putExtra("key", summaryKey)
                    putExtra("title", title)
                    putExtra("text", text)
                    putExtra("package", sbn.packageName)
                    putExtra("isGroupSummary", isGroupSummary)
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
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(cancelReceiver, IntentFilter("CANCEL_NOTIFICATION"))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""

        val contentKey = "${sbn.packageName}|${sbn.id}|${title}|${sbn.postTime}".sha256() // Create unique key
        activeNotifications.remove(contentKey)
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(cancelReceiver)
        super.onDestroy()
    }

    fun String.sha256(): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(this.toByteArray())
            .fold("") { str, byte -> str + "%02x".format(byte) }
    }

}
