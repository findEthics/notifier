package com.example.notifier

interface NotificationServiceController {
    fun cancelNotification(key: String)
    fun cancelSystemNotification(systemKey: String)
}