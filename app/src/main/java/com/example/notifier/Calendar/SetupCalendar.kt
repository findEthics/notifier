// In SetupCalendar.kt
package com.example.notifier.Calendar

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import com.example.notifier.GoogleApiConstants
import io.ktor.client.HttpClient // Ktor HTTP Client
import io.ktor.client.engine.cio.CIO // Ktor CIO Engine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation // Ktor Content Negotiation
import io.ktor.client.request.forms.submitForm // Ktor form submission
import io.ktor.client.request.get // Ktor GET request
import io.ktor.client.request.header // Ktor header
import io.ktor.client.statement.HttpResponse // Ktor HTTP Response
import io.ktor.client.statement.bodyAsText // Ktor body as text
import io.ktor.http.HttpStatusCode // Ktor HTTP Status Code
import io.ktor.http.Parameters // Ktor Parameters

import io.ktor.serialization.kotlinx.json.json // Ktor Kotlinx JSON support
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json // Kotlinx Serialization JSON
import org.json.JSONObject // Still using org.json.JSONObject for simplicity here, could move to full Kotlinx Serialization data classes
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resumeWithException

class SetupCalendar(private val activity: Activity) {

    // Initialize Ktor HttpClient
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true // Important for handling varying API responses
            })
        }

    }
    private val prefs: SharedPreferences = activity.getSharedPreferences(GoogleApiConstants.PREFS_NAME, Context.MODE_PRIVATE)
    private val cacheManager = CalendarCacheManager(activity)

    fun handleCalendarButtonClick() {

        CoroutineScope(Dispatchers.Main).launch {
            val accessToken = getValidAccessToken()
            if (accessToken != null) {
                // If we have a token, just fetch events and show them
                val events = fetchCalendarEvents(accessToken)
                if (events != null) {
                    val intent = Intent(activity, CalendarActivity::class.java).apply {
                        putParcelableArrayListExtra("EVENTS_LIST", ArrayList(events))
                    }
                    activity.startActivity(intent)
                } else {
                    Toast.makeText(activity, "Failed to fetch events", Toast.LENGTH_SHORT).show()
                }
            } else {
                // If we don't have a token, first check cache before triggering auth flow
                val cachedEvents = cacheManager.getCachedEvents()
                if (cachedEvents != null) {
                    // Show cached events immediately
                    Toast.makeText(activity, "Using cached events", Toast.LENGTH_SHORT).show()
                    val intent = Intent(activity, CalendarActivity::class.java).apply {
                        putParcelableArrayListExtra("EVENTS_LIST", ArrayList(cachedEvents))
                    }
                    activity.startActivity(intent)
                } else {
                    // If no cache, start the full login flow
                    triggerAuthenticationFlow { events ->
                        if (events != null) {
                            val intent = Intent(activity, CalendarActivity::class.java).apply {
                                putParcelableArrayListExtra("EVENTS_LIST", ArrayList(events))
                            }
                            activity.startActivity(intent)
                        } else {
                            Toast.makeText(activity, "Authentication failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    fun triggerAuthenticationFlow(onResult: (events: List<CalendarEvent>?) -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val authResult = startAuthFlowAndAwaitCode()
                // Pass the callback down to the next function
                exchangeCodeForTokens(authResult.first, authResult.second, onResult)
            } catch (e: Exception) {
                onResult(null) // Signal failure
            }
        }
    }

    private suspend fun startAuthFlowAndAwaitCode(): Pair<String, String> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            var serverSocket: ServerSocket? = null
            try {
                // ... (Code to start the server and launch the browser is correct)
                serverSocket = ServerSocket(0)
                val chosenPort = serverSocket.localPort
                val redirectUri = "${GoogleApiConstants.REDIRECT_URI_BASE}:$chosenPort"

                val authUri = Uri.parse(GoogleApiConstants.AUTH_URI).buildUpon()
                    .appendQueryParameter("client_id", GoogleApiConstants.CLIENT_ID)
                    .appendQueryParameter("redirect_uri", redirectUri)
                    .appendQueryParameter("response_type", "code")
                    .appendQueryParameter("scope", GoogleApiConstants.CALENDAR_API_SCOPE)
                    .appendQueryParameter("access_type", "offline")
                    .appendQueryParameter("prompt", "select_account")
                    .build()

                val customTabsIntent = CustomTabsIntent.Builder().build()
                activity.runOnUiThread {
                    customTabsIntent.launchUrl(activity, authUri)
                }

                val clientSocket = serverSocket.accept()
                val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                val requestLine = reader.readLine()

                val response = "HTTP/1.1 200 OK\r\n\r\n<html><body>You can close this tab.</body></html>"
                clientSocket.getOutputStream().write(response.toByteArray())
                clientSocket.close()

                val redirectPath = requestLine.split(" ")[1]
                val fullUriString = "http://localhost$redirectPath"
                val receivedUri = Uri.parse(fullUriString)
                val code = receivedUri.getQueryParameter("code")

                if (code != null) {
                    // THE FIX: Explicitly pass null for the optional onCancellation parameter.
                    continuation.resume(Pair(code, redirectUri), null)
                } else {
                    continuation.resumeWithException(Exception("Auth code not found in redirect."))
                }

            } catch (e: Exception) {
                // Use resumeWithException to fail the coroutine correctly
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            } finally {
                serverSocket?.close()
            }
        }
    }

    private fun storeTokens(accessToken: String, refreshToken: String?, expiresIn: Long, tokenType: String) {
        with(prefs.edit()) {
            putString(GoogleApiConstants.KEY_ACCESS_TOKEN, accessToken)
            refreshToken?.let { putString(GoogleApiConstants.KEY_REFRESH_TOKEN, it) }
            putLong(GoogleApiConstants.KEY_EXPIRES_IN, System.currentTimeMillis() / 1000 + expiresIn)
            putString(GoogleApiConstants.KEY_TOKEN_TYPE, tokenType)
            apply()
        }
    }

    suspend fun getValidAccessToken(): String? {
        val accessToken = prefs.getString(GoogleApiConstants.KEY_ACCESS_TOKEN, null)
        val expiresIn = prefs.getLong(GoogleApiConstants.KEY_EXPIRES_IN, 0)

        if (accessToken != null && System.currentTimeMillis() / 1000 < expiresIn) {
            return accessToken
        } else {
            val refreshToken = prefs.getString(GoogleApiConstants.KEY_REFRESH_TOKEN, null)
            if (refreshToken != null) {
                return refreshAccessToken(refreshToken)
            }
        }
        return null
    }

    private suspend fun exchangeCodeForTokens(
        code: String,
        redirectUri: String,
        onResult: (events: List<CalendarEvent>?) -> Unit
    ) {
        val formParameters = Parameters.build {
            append("code", code)
            append("client_id", GoogleApiConstants.CLIENT_ID)
            append("client_secret", GoogleApiConstants.CLIENT_SECRET)// SECURITY RISK
            append("redirect_uri", redirectUri)
            append("grant_type", "authorization_code")
        }

        try {
            val responseJson = makeKtorPostRequest(GoogleApiConstants.TOKEN_ENDPOINT, formParameters)
            if (responseJson != null) {
                // (parsing and storing tokens)
                val accessToken = responseJson.getString("access_token")
                val refreshToken = responseJson.optString("refresh_token", null)
                val expiresIn = responseJson.getLong("expires_in")
                val tokenType = responseJson.getString("token_type")

                storeTokens(accessToken, refreshToken, expiresIn, tokenType)

                // After storing tokens, fetch the calendar events
                val events = fetchCalendarEvents(accessToken) // fetchCalendarEvents will now return a list
                onResult(events) // Pass the result to the callback
            } else {
                onResult(null) // Signal failure
            }
        } catch (e: Exception) {
            onResult(null) // Signal failure
        }
    }

    private suspend fun refreshAccessToken(refreshToken: String): String? {
        val formParameters = Parameters.build {
            append("client_id", GoogleApiConstants.CLIENT_ID)// SECURITY RISK
            append("refresh_token", refreshToken)
            append("grant_type", "refresh_token")
        }

        try {
            val responseJson = makeKtorPostRequest(GoogleApiConstants.TOKEN_ENDPOINT, formParameters)
            if (responseJson != null) {
                val newAccessToken = responseJson.getString("access_token")
                val newExpiresIn = responseJson.getLong("expires_in")
                val newTokenType = responseJson.getString("token_type")
                storeTokens(newAccessToken, refreshToken, newExpiresIn, newTokenType)
                return newAccessToken
            } else {
                prefs.edit().remove(GoogleApiConstants.KEY_REFRESH_TOKEN).apply()
            }
        } catch (e: Exception) {
        }
        return null
    }

    suspend fun fetchCalendarEvents(accessToken: String, forceRefresh: Boolean = false): List<CalendarEvent>? {
        // Check cache first unless force refresh is requested
        if (!forceRefresh) {
            val cachedEvents = cacheManager.getCachedEvents()
            if (cachedEvents != null) {
                activity.runOnUiThread {
                    Toast.makeText(activity, "Using cached events", Toast.LENGTH_SHORT).show()
                }
                return cachedEvents
            }
        }

        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        sdf.timeZone = TimeZone.getDefault()

        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
        val timeMin = sdf.format(calendar.time)

        calendar.set(Calendar.HOUR_OF_DAY, 23); calendar.set(Calendar.MINUTE, 59); calendar.set(Calendar.SECOND, 59); calendar.set(Calendar.MILLISECOND, 999)
        val timeMax = sdf.format(calendar.time)

        val eventsUrl = Uri.parse(GoogleApiConstants.CALENDAR_EVENTS_ENDPOINT).buildUpon()
            .appendQueryParameter("timeMin", timeMin)
            .appendQueryParameter("timeMax", timeMax)
            .appendQueryParameter("singleEvents", "true")
            .appendQueryParameter("orderBy", "startTime")
            .build().toString()

        try {
            val responseJson = makeKtorGetRequest(eventsUrl, accessToken)
            if (responseJson != null) {
                val items = responseJson.optJSONArray("items")
                val eventsList = ArrayList<CalendarEvent>()
                val now = Calendar.getInstance()
                val gracePeriodMillis = 15 * 60 * 1000 // 15 minutes
                val cutoffTime = now.timeInMillis - gracePeriodMillis
                
                if (items != null) {
                    for (i in 0 until items.length()) {
                        val event = items.getJSONObject(i)
                        val summary = event.optString("summary", "No Title")
                        val startObj = event.optJSONObject("start")
                        var startTime = "No Start Time"
                        startObj?.let {
                            startTime = it.optString("dateTime", it.optString("date", "No Start Date"))
                        }
                        
                        val calendarEvent = CalendarEvent(summary, startTime)
                        
                        // During force refresh, filter out past events (older than current time - 15 minutes)
                        if (forceRefresh) {
                            try {
                                val eventTime = parseEventTime(startTime)
                                // Only add events that are NOT in the past (>= cutoff time)
                                if (eventTime >= cutoffTime) {
                                    eventsList.add(calendarEvent)
                                }
                            } catch (e: Exception) {
                                // Keep events with unparseable times during force refresh
                                eventsList.add(calendarEvent)
                            }
                        } else {
                            // During normal fetch (cache), add all events (filtering happens in cache manager)
                            eventsList.add(calendarEvent)
                        }
                    }
                }

                // Get previous cached events before replacing cache
                val previousEvents = cacheManager.getPreviousCachedEvents() ?: emptyList()
                
                // Cache the fresh events
                cacheManager.cacheEvents(eventsList)
                
                activity.runOnUiThread {
                    Toast.makeText(activity, "Fetched fresh events", Toast.LENGTH_SHORT).show()
                }

                // REMINDER SYNCHRONIZATION LOGIC ---
                val reminderScheduler = CalendarEventReminder()
                
                // Find events that were removed (exist in previous but not in new)
                val removedEvents = previousEvents.filter { oldEvent ->
                    !eventsList.any { newEvent -> 
                        newEvent.summary == oldEvent.summary && newEvent.startTime == oldEvent.startTime 
                    }
                }
                
                // Cancel reminders for removed events
                if (removedEvents.isNotEmpty()) {
                    reminderScheduler.cancelAllRemindersForEvents(activity, removedEvents)
                }
                
                // Schedule reminders for all current events (new and existing)
                eventsList.forEach { event ->
                    reminderScheduler.scheduleReminderForEvent(activity, event)
                }
                return eventsList // Return the list
            }
        } catch (e: Exception) {
        }
        return null // Return null on failure
    }

    // Helper to make Ktor POST requests and parse to JSONObject
    private suspend fun makeKtorPostRequest(urlString: String, formParameters: Parameters): JSONObject? {
        return withContext(Dispatchers.IO) {
            try {
                val response: HttpResponse = httpClient.submitForm(
                    url = urlString,
                    formParameters = formParameters
                )

                if (response.status != HttpStatusCode.OK) {
                    handleKtorErrorResponse(response, urlString)
                    return@withContext null
                }
                val responseBody = response.bodyAsText()
                if (responseBody.isEmpty()) {
                    return@withContext null
                }
                JSONObject(responseBody) // Still using org.json for parsing for now
            } catch (e: Exception) { // Catch more specific Ktor exceptions if needed
                null
            }
        }
    }

    // Helper to make Ktor GET requests and parse to JSONObject
    private suspend fun makeKtorGetRequest(urlString: String, bearerToken: String? = null): JSONObject? {
        return withContext(Dispatchers.IO) {
            try {
                val response: HttpResponse = httpClient.get(urlString) {
                    bearerToken?.let { token ->
                        header("Authorization", "Bearer $token")
                    }
                }

                if (response.status != HttpStatusCode.OK) {
                    handleKtorErrorResponse(response, urlString)
                    return@withContext null
                }
                val responseBody = response.bodyAsText()
                if (responseBody.isEmpty()) {
                    return@withContext null
                }
                JSONObject(responseBody) // Still using org.json for parsing for now
            } catch (e: Exception) { // Catch more specific Ktor exceptions if needed
                null
            }
        }
    }

    private fun handleKtorErrorResponse(response: HttpResponse, urlString: String) {
        if (response.status == HttpStatusCode.Unauthorized && !urlString.contains("oauth2.googleapis.com/token")) {
            prefs.edit().remove(GoogleApiConstants.KEY_ACCESS_TOKEN).apply()
        }
    }

    // Call this when your activity/app is being destroyed to clean up the Ktor client
    fun cleanup() {
        httpClient.close()
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
                calendar.time = sdf.parse(startTime) ?: java.util.Date()
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.timeInMillis
            }
        } catch (e: Exception) {
            0L
        }
    }

}