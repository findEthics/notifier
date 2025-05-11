package com.example.notifier

data class NotificationData(
    val title: String,
    val text: String,
    val packageName: String,
    val appName: String,
    val key: String,
    val timestamp: Long = System.currentTimeMillis(),
    val systemKey: String
)
