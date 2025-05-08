package com.example.notifier

import android.app.Notification
import android.content.Context
import android.content.Intent
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


    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in allowedPackages) return  // Ignore notifications not in the list
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val isGroupSummary = extras.getBoolean("android.support.isGroupSummary", false)
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        if (title.isEmpty() && text.isEmpty()) return // Skip empty notifications

        val contentKey = "${sbn.packageName}|${sbn.id}|${sbn.tag}|${title}|${System.currentTimeMillis()}" // Create unique key

        if (!isGroupSummary && contentKey in seenKeys) return // Skip already seen individual notifications (avoid duplicates)

        if (!isGroupSummary) { seenKeys.add(contentKey) } // Track individual notifications

        val isWhatsAppSummary = title == "WhatsApp"// Title is just the app name

        // Handle group summaries
        println("Outside the IF %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%")
        if (isGroupSummary || isWhatsAppSummary) {
            // Check if we already have a summary for this package
            println("INSIDE SUMMARY HANDLING %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%")
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
            val summaryKey = "SUMMARY|${sbn.packageName}|$groupId"

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
            println("DISPLAY NOTIFICATION %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%-")
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

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val isGroupSummary = extras.getBoolean("android.support.isGroupSummary", false)
        val isWhatsAppSummary = title == "WhatsApp"  // Use your detection logic here

        // Generate the same key format used in onNotificationPosted
        val key = if (isGroupSummary || isWhatsAppSummary) {
            // Match the summary key format from onNotificationPosted
            "SUMMARY|${sbn.packageName}|${sbn.notification.group ?: "default_group"}"
        } else {
            val contentKey = "${sbn.packageName}|${sbn.id}|${sbn.tag}|${title}|${text}"
            "INDIVIDUAL|${sbn.packageName}|$contentKey"
        }

        // Clean up tracking
        if (isGroupSummary || isWhatsAppSummary) {
            summaryKeys.remove(sbn.notification.group ?: "default_group")
        } else {
            seenKeys.remove("${sbn.packageName}|${sbn.id}|${sbn.tag}|${title}|${text}")
        }

        // Broadcast removal to MainActivity
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            Intent("REMOVE_NOTIFICATION").apply {
                putExtra("key", key)
            }
        )
    }

}
