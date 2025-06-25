package com.example.notifier

interface NotificationServiceController {
    fun cancelNotificationByKey(key: String)
    fun cancelSystemNotification(systemKey: String)
}