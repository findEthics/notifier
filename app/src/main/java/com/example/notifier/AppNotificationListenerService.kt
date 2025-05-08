package com.example.notifier

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class AppNotificationListenerService : NotificationListenerService() {
    private val allowedPackages = setOf(
        "com.whatsapp"
        // Only listen for notifications from these apps
    )
    private val seenKeys = mutableSetOf<String>()
    private val seenGroups = mutableMapOf<String, String>() // Group ID → Latest Key

    private val groupSummaries = mutableMapOf<String, String>() // groupId → summaryKey


    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in allowedPackages) return  // Ignore notifications not in the list

        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""

        val isGroupSummary = extras.getBoolean("android.support.isGroupSummary", false)
        val groupId = sbn.notification.group ?: "" // Use group ID to track notifications from the same group
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val notificationKey = "${sbn.packageName}|${sbn.id}|${sbn.tag}|${title}|${text}"
        // Create unique key
        val key = if (isGroupSummary) {
            "SUMMARY_${sbn.packageName}|${groupId}"
        } else {
            "INDIVIDUAL_${sbn.packageName}|${sbn.id}|${sbn.tag}|${title}|${text}"
        }

        // Skip group summaries, empty content, or duplicates
//        if ((title.isEmpty() && text.isEmpty()) || notificationKey in seenKeys) return
        // Handle group summaries
        if (sbn.packageName == "com.whatsapp" && isGroupSummary) {
//            // Remove previous summary for this group
//            groupSummaries[groupId]?.let { oldKey ->
//                LocalBroadcastManager.getInstance(this).sendBroadcast(
//                    Intent("REMOVE_NOTIFICATION").apply {
//                        putExtra("key", oldKey)
//                    }
//                )
//            }
//            groupSummaries[groupId] = key

            LocalBroadcastManager.getInstance(this).sendBroadcast(
                Intent("REMOVE_ALL_WHATSAPP_SUMMARIES")
            )
        }

//        // Generate a unique key for this notification (group ID + timestamp)
//        val groupKey = "${groupId}_${System.currentTimeMillis()}"
//        // Track the latest key for this group
//        seenGroups[groupId] = groupKey
//
//        seenKeys.add(notificationKey)

        LocalBroadcastManager.getInstance(this).sendBroadcast(
            Intent("NEW_NOTIFICATION").apply {
                putExtra("key", notificationKey)
                putExtra("title", title)
                putExtra("text", text)
                putExtra("package", sbn.packageName)
                putExtra("isGroupSummary", isGroupSummary)
                putExtra("groupId", groupId)
            }
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
//        val key = "${sbn.packageName}|${sbn.id}|${sbn.tag}"
//        seenKeys.remove(key)
        val groupId = sbn.notification.group ?: ""
        groupSummaries.remove(groupId)

    }
}
