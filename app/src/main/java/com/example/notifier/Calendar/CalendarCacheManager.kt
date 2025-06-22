package com.example.notifier.Calendar

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

class CalendarCacheManager(context: Context) {
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences("calendar_cache", Context.MODE_PRIVATE)
    private val gson: Gson = Gson()
    
    companion object {
        private const val KEY_CACHED_EVENTS = "cached_events"
        private const val KEY_CACHE_TIMESTAMP = "cache_timestamp"
        private const val KEY_CACHE_DATE = "cache_date"
    }
    
    fun cacheEvents(events: List<CalendarEvent>) {
        val filteredEvents = filterPastEvents(events)
        val eventsJson = gson.toJson(filteredEvents)
        val currentDate = getCurrentDateString()
        
        sharedPreferences.edit().apply {
            putString(KEY_CACHED_EVENTS, eventsJson)
            putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
            putString(KEY_CACHE_DATE, currentDate)
            apply()
        }
    }
    
    fun getCachedEvents(): List<CalendarEvent>? {
        if (!isCacheValid()) {
            return null
        }
        
        val eventsJson = sharedPreferences.getString(KEY_CACHED_EVENTS, null) ?: return null
        return try {
            val type = object : TypeToken<List<CalendarEvent>>() {}.type
            val cachedEvents: List<CalendarEvent> = gson.fromJson(eventsJson, type) ?: return null
            filterPastEvents(cachedEvents)
        } catch (e: Exception) {
            null
        }
    }
    
    fun isCacheValid(): Boolean {
        val cachedDate = sharedPreferences.getString(KEY_CACHE_DATE, null)
        val currentDate = getCurrentDateString()
        return cachedDate == currentDate
    }
    
    fun clearCache() {
        sharedPreferences.edit().clear().apply()
    }
    
    private fun getCurrentDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(Date())
    }
    
    private fun filterPastEvents(events: List<CalendarEvent>): List<CalendarEvent> {
        val now = Calendar.getInstance()
        val gracePeriodMillis = 15 * 60 * 1000 // 15 minutes
        val cutoffTime = now.timeInMillis - gracePeriodMillis
        
        return events.filter { event ->
            try {
                val eventTime = parseEventTime(event.startTime)
                eventTime >= cutoffTime
            } catch (e: Exception) {
                true // Keep events with unparseable times
            }
        }
    }
    
    private fun parseEventTime(startTime: String): Long {
        return try {
            if (startTime.contains("T")) {
                // DateTime format: 2024-06-22T14:30:00-07:00
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                sdf.timeZone = TimeZone.getDefault()
                sdf.parse(startTime)?.time ?: 0L
            } else {
                // Date format: 2024-06-22 (all-day event)
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                sdf.timeZone = TimeZone.getDefault()
                val calendar = Calendar.getInstance()
                calendar.time = sdf.parse(startTime) ?: Date()
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.timeInMillis
            }
        } catch (e: Exception) {
            0L
        }
    }
}