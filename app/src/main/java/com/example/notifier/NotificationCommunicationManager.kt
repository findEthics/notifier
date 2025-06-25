package com.example.notifier

import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference

object NotificationCommunicationManager {
    private var activityCallbackRef: WeakReference<NotificationCallback>? = null
    private var serviceControllerRef: WeakReference<NotificationServiceController>? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    
    fun registerActivityCallback(callback: NotificationCallback) {
        activityCallbackRef = WeakReference(callback)
    }
    
    fun unregisterActivityCallback() {
        activityCallbackRef?.clear()
        activityCallbackRef = null
    }
    
    fun registerServiceController(controller: NotificationServiceController) {
        serviceControllerRef = WeakReference(controller)
    }
    
    fun unregisterServiceController() {
        serviceControllerRef?.clear()
        serviceControllerRef = null
    }
    
    fun notifyNewNotification(notification: NotificationUpdate) {
        mainHandler.post {
            activityCallbackRef?.get()?.onNewNotification(notification)
        }
    }
    
    fun notifyRemoveNotification(key: String) {
        mainHandler.post {
            activityCallbackRef?.get()?.onRemoveNotification(key)
        }
    }
    
    fun requestCancelNotification(key: String) {
        serviceControllerRef?.get()?.cancelNotificationByKey(key)
    }
    
    fun requestCancelSystemNotification(systemKey: String) {
        serviceControllerRef?.get()?.cancelSystemNotification(systemKey)
    }
}