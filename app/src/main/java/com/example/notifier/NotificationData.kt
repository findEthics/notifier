package com.example.notifier

data class NotificationData(
    val title: String,
    val text: String,
    val packageName: String,
    val appName: String,
    val key: String,
    val isGroupSummary: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
