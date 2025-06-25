package com.example.notifier

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class AppNotificationListenerService : NotificationListenerService(), NotificationServiceController {
    private val allowedPackages = setOf(
        "com.whatsapp","com.mudita.messages","com.mudita.calendar","com.example.notifier"
        // Only listen for notifications from these apps
    )
    private val ignoreWhatsappNotification = setOf(
        "Ringing…","Calling…", "Ongoing voice call"
    )
    private val seenKeys = mutableSetOf<String>()
    private val summaryKeys = mutableMapOf<String, String>() // Group ID → Latest Key
    private val activeNotifications = mutableMapOf<String, StatusBarNotification>()

    // Batching components
    private val handler = Handler(Looper.getMainLooper())
    private val pendingNotifications = mutableListOf<StatusBarNotification>()
    private val batchDelay = 10*1000L // 10s batching window
    
    private val batchProcessor = Runnable {
        processBatchedNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Immediate filtering and cancellation for unwanted notifications
        if (sbn.packageName !in allowedPackages) {
            cancelNotification(sbn.key)
            return
        }

        // Immediate cancellation for call notifications (critical for UX)
        val text = sbn.notification.extras.getCharSequence("android.text")?.toString() ?: ""
        if ((sbn.packageName == "com.whatsapp") && (text in ignoreWhatsappNotification)) {
            cancelNotification(sbn.key)
            return
        }

        // Add to batching queue for processing
        synchronized(pendingNotifications) {
            pendingNotifications.add(sbn)
        }

        // Schedule batch processing
        handler.removeCallbacks(batchProcessor)
        handler.postDelayed(batchProcessor, batchDelay)
    }

    private fun processBatchedNotifications() {
        val notificationsToProcess: List<StatusBarNotification>
        
        // Get all pending notifications
        synchronized(pendingNotifications) {
            notificationsToProcess = pendingNotifications.toList()
            pendingNotifications.clear()
        }

        if (notificationsToProcess.isEmpty()) return

        val broadcastIntents = mutableListOf<Intent>()
        val processedKeys = mutableSetOf<String>()

        for (sbn in notificationsToProcess) {
            val result = processIndividualNotification(sbn, processedKeys)
            result?.let { broadcastIntents.add(it) }
        }

        // Send all notifications through the communication manager
        for (intent in broadcastIntents) {
            when (intent.action) {
                "NEW_NOTIFICATION" -> {
                    val notification = NotificationUpdate(
                        key = intent.getStringExtra("key") ?: "",
                        title = intent.getStringExtra("title") ?: "",
                        text = intent.getStringExtra("text") ?: "",
                        packageName = intent.getStringExtra("package") ?: "",
                        systemKey = intent.getStringExtra("systemKey") ?: "",
                        isGroupSummary = intent.getBooleanExtra("isGroupSummary", false)
                    )
                    NotificationCommunicationManager.notifyNewNotification(notification)
                }
                "REMOVE_NOTIFICATION" -> {
                    val key = intent.getStringExtra("key") ?: ""
                    NotificationCommunicationManager.notifyRemoveNotification(key)
                }
            }
        }
    }

    private fun processIndividualNotification(sbn: StatusBarNotification, processedKeys: MutableSet<String>): Intent? {
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        if (sbn.packageName != "com.example.notifier") {
            if (title.isEmpty() || text.isEmpty() || !(extras.containsKey("android.template"))) {
                cancelNotification(sbn.key)
                return null
            }
        }

        val summaryText = extras.getString("android.summaryText") ?: ""
        val isWhatsAppSummary = (summaryText != "")
        if (isWhatsAppSummary && title != "WhatsApp") {
            cancelNotification(sbn.key)
            return null
        }

        val isGroupSummary = extras.getBoolean("android.support.isGroupSummary", false)
        var postTimeProxy = sbn.postTime
        if (text == "Incoming voice call" && sbn.packageName == "com.whatsapp") {
            postTimeProxy = sbn.postTime / 100000
        }
        val contentKey = "${sbn.packageName}|${sbn.id}|${title}|${postTimeProxy}"

        // Skip if already processed in this batch
        if (contentKey in processedKeys) return null
        processedKeys.add(contentKey)

        activeNotifications[contentKey] = sbn
        if (!isGroupSummary && contentKey in seenKeys) return null

        if (!isGroupSummary) {
            seenKeys.add(contentKey)
        }

        // Handle group summaries
        if (isGroupSummary || isWhatsAppSummary) {
            val groupId = sbn.notification.group ?: "default_group"
            val newSummaryKey = "SUMMARY|${sbn.packageName}|$groupId|${sbn.postTime}"

            // Remove previous summary for this group
            summaryKeys[sbn.packageName]?.let { oldKey ->
                if (oldKey != newSummaryKey) {
                    NotificationCommunicationManager.notifyRemoveNotification(oldKey)
                }
            }

            summaryKeys[sbn.packageName] = newSummaryKey

            return Intent("NEW_NOTIFICATION").apply {
                putExtra("key", newSummaryKey)
                putExtra("title", title)
                putExtra("text", text)
                putExtra("package", sbn.packageName)
                putExtra("isGroupSummary", isGroupSummary)
                putExtra("systemKey", sbn.key)
            }
        } else {
            val key = "INDIVIDUAL|${sbn.packageName}|$contentKey"
            return Intent("NEW_NOTIFICATION").apply {
                putExtra("key", key)
                putExtra("title", title)
                putExtra("text", text)
                putExtra("package", sbn.packageName)
                putExtra("isGroupSummary", isGroupSummary)
                putExtra("systemKey", sbn.key)
            }
        }
    }

    // Implementation of NotificationServiceController interface
    override fun cancelNotification(key: String) {
        activeNotifications[key]?.let { sbn ->
            cancelNotification(sbn.key) // Cancel the system notification
            activeNotifications.remove(key)
            seenKeys.remove(key.split("|").last()) // Remove individual notification key
            if (key.startsWith("SUMMARY")) { // Handle group summaries
                summaryKeys.values.remove(key)
            }
        }
    }

    override fun cancelSystemNotification(systemKey: String) {
        cancelNotification(systemKey)
    }

    override fun onCreate() {
        super.onCreate()
        // Register as the service controller
        NotificationCommunicationManager.registerServiceController(this)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getString("android.text") ?: ""
        var postTimeProxy = sbn.postTime
        if (text == "Incoming voice call" && sbn.packageName == "com.whatsapp") {
            postTimeProxy = sbn.postTime/100000
        }

        val contentKey = "${sbn.packageName}|${sbn.id}|${title}|${postTimeProxy}" // Create unique key

        cancelNotification(sbn.key)
        activeNotifications.remove(contentKey)

    }

    override fun onDestroy() {
        // Clean up batching handler
        handler.removeCallbacks(batchProcessor)
        // Unregister from communication manager
        NotificationCommunicationManager.unregisterServiceController()
        super.onDestroy()
    }

}
