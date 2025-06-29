package com.example.notifier

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Browser
import android.widget.Toast

class PublicCalendarManager(private val context: Context) {
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_PUBLIC_CALENDAR_URL = "public_calendar_url"
    }
    
    fun savePublicCalendarUrl(url: String) {
        sharedPrefs.edit().putString(KEY_PUBLIC_CALENDAR_URL, url).apply()
    }
    
    fun getPublicCalendarUrl(): String? {
        return sharedPrefs.getString(KEY_PUBLIC_CALENDAR_URL, null)
    }
    
    fun hasPublicCalendarUrl(): Boolean {
        return !getPublicCalendarUrl().isNullOrEmpty()
    }
    
    fun openPublicCalendar(): Boolean {
        val url = getPublicCalendarUrl()
        return if (!url.isNullOrEmpty()) {
            try {
                val scheduleViewUrl = convertToScheduleView(url)
                val intent = Intent(context, CalendarWebViewActivity::class.java).apply {
                    putExtra("calendar_url", scheduleViewUrl)
                    // Use single task mode to reuse existing activity
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                Toast.makeText(context, "Error opening calendar: ${e.message}", Toast.LENGTH_SHORT).show()
                false
            }
        } else {
            Toast.makeText(context, "No public calendar URL configured", Toast.LENGTH_SHORT).show()
            false
        }
    }
    
    private fun convertToScheduleView(originalUrl: String): String {
        val uri = Uri.parse(originalUrl)
        val builder = uri.buildUpon()
        
        // Remove existing view parameters if they exist
        builder.clearQuery()
        
        // Add all original query parameters except view-related ones
        uri.queryParameterNames.forEach { paramName ->
            if (paramName !in listOf("mode", "view")) {
                uri.getQueryParameter(paramName)?.let { paramValue ->
                    builder.appendQueryParameter(paramName, paramValue)
                }
            }
        }
        
        // Force agenda/schedule view - this was working in WebView
        builder.appendQueryParameter("mode", "AGENDA")
        
        return builder.build().toString()
    }
    
    fun clearPublicCalendarUrl() {
        sharedPrefs.edit().remove(KEY_PUBLIC_CALENDAR_URL).apply()
    }
    
    fun isValidCalendarUrl(url: String): Boolean {
        return url.isNotEmpty() && 
               (url.startsWith("https://calendar.google.com/calendar/embed") ||
                url.startsWith("https://calendar.google.com/calendar/u/"))
    }
    
    // Open in browser with tab reuse using Browser.EXTRA_APPLICATION_ID
    fun openPublicCalendarInBrowser(): Boolean {
        val url = getPublicCalendarUrl()
        return if (!url.isNullOrEmpty()) {
            try {
                val scheduleViewUrl = convertToScheduleView(url)
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(scheduleViewUrl)).apply {
                    // Use Browser.EXTRA_APPLICATION_ID to reuse existing tabs
                    putExtra(Browser.EXTRA_APPLICATION_ID, context.packageName)
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                Toast.makeText(context, "Error opening calendar: ${e.message}", Toast.LENGTH_SHORT).show()
                false
            }
        } else {
            Toast.makeText(context, "No public calendar URL configured", Toast.LENGTH_SHORT).show()
            false
        }
    }
}