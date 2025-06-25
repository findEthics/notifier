package com.example.notifier

data class NotificationUpdate(
    val key: String,
    val title: String,
    val text: String,
    val packageName: String,
    val systemKey: String,
    val isGroupSummary: Boolean
)

interface NotificationCallback {
    fun onNewNotification(notification: NotificationUpdate)
    fun onRemoveNotification(key: String)
}