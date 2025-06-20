package com.example.notifier

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class AppNotificationListenerService : NotificationListenerService() {
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
    private val batchDelay = 15*1000L // 15s batching window
    
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

        // Send all broadcasts in a single batch
        for (intent in broadcastIntents) {
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
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
                    LocalBroadcastManager.getInstance(this).sendBroadcast(
                        Intent("REMOVE_NOTIFICATION").apply {
                            putExtra("key", oldKey)
                        }
                    )
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
//        LocalBroadcastManager.getInstance(this)
//            .registerReceiver(refreshReceiver, IntentFilter("FORCE_REFRESH"))
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

        val contentKey = "${sbn.packageName}|${sbn.id}|${title}|${postTimeProxy}" // Create unique key

        cancelNotification(sbn.key)
        activeNotifications.remove(contentKey)

    }

    override fun onDestroy() {
        // Clean up batching handler
        handler.removeCallbacks(batchProcessor)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(cancelReceiver)
        super.onDestroy()
    }

}
