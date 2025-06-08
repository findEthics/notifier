// In SetupCalendar.kt
package com.example.notifier.Calendar

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import android.widget.ImageButton
import androidx.browser.customtabs.CustomTabsIntent
//import com.example.notifier.Calendar.GoogleApiConstants
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

    fun setupCalendar(btnCalendar: ImageButton) {
        btnCalendar.setOnClickListener {
            Log.d("SetupCalendar", "Calendar Button Clicked")
            CoroutineScope(Dispatchers.Main).launch {
                val accessToken = getValidAccessToken()
                println("getValidAccessToken executed")
                if (accessToken != null) {
                    Log.d("SetupCalendar", "Access token available. Fetching events...")
                    fetchCalendarEvents(accessToken)
                } else {
                    Log.d("SetupCalendar","No valid access token. Initiating OAuth flow.")
                    try {
                        // This new function replaces initiateOAuthFlow
                        val authCode = startAuthFlowAndAwaitCode()
                        Log.d("SetupCalendar", "Successfully received auth code via loopback.")
                        exchangeCodeForTokens(authCode.first, authCode.second)
                    } catch (e: Exception) {
                        Log.e("SetupCalendar", "Auth flow failed", e)
                    }
                }
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
                    .build()

                val customTabsIntent = CustomTabsIntent.Builder().build()
                activity.runOnUiThread {
                    customTabsIntent.launchUrl(activity, authUri)
                }

                val clientSocket = serverSocket.accept()
                val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
                val requestLine = reader.readLine()
                Log.d("SetupCalendar", "Received request: $requestLine")

                val response = "HTTP/1.1 200 OK\r\n\r\n<html><body>You can close this tab.</body></html>"
                clientSocket.getOutputStream().write(response.toByteArray())
                clientSocket.close()

                val redirectPath = requestLine.split(" ")[1]
                Log.d("SetupCalendar", "Received redirect path: $redirectPath")
                val fullUriString = "http://localhost$redirectPath"
                val receivedUri = Uri.parse(fullUriString)
                val code = receivedUri.getQueryParameter("code")
                val error = receivedUri.getQueryParameter("error")
                Log.d("SetupCalendar", "Received code: $code")
                Log.d("SetupCalendar", "Received error: $error")

                if (code != null) {
                    // THE FIX: Explicitly pass null for the optional onCancellation parameter.
                    continuation.resume(Pair(code, redirectUri), null)
                } else {
                    val errorMessage = "Auth code not found in redirect. Error from server: $error"
                    Log.e("SetupCalendar", errorMessage)
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
        Log.d("SetupCalendar", "Tokens stored. Access Token: $accessToken")
    }

    private suspend fun getValidAccessToken(): String? {
        println("getValidAccessToken started")
        val accessToken = prefs.getString(GoogleApiConstants.KEY_ACCESS_TOKEN, null)
        val expiresIn = prefs.getLong(GoogleApiConstants.KEY_EXPIRES_IN, 0)

        println("${accessToken}")

        if (accessToken != null && System.currentTimeMillis() / 1000 < expiresIn) {
            println("SetupCalendar , Found valid access token in Prefs.")
            return accessToken
        } else {
            val refreshToken = prefs.getString(GoogleApiConstants.KEY_REFRESH_TOKEN, null)
            if (refreshToken != null) {
                println("SetupCalendar: Access token expired or missing. Attempting refresh...")
                return refreshAccessToken(refreshToken)
            }
        }
        println("SetupCalendar: No valid or refreshable token found.")
        return null
    }

    private suspend fun exchangeCodeForTokens(code: String, redirectUri: String) {
        Log.d("SetupCalendar", "Exchanging authorization code for tokens...")
        Log.d("SetupCalendar", "REDIRECT_URI: $GoogleApiConstants.REDIRECT_URI")
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
                val accessToken = responseJson.getString("access_token")
                val refreshToken = responseJson.optString("refresh_token", null)
                val expiresIn = responseJson.getLong("expires_in")
                val tokenType = responseJson.getString("token_type")

                storeTokens(accessToken, refreshToken, expiresIn, tokenType)
                Log.i("SetupCalendar", "Tokens exchanged successfully. Fetching events now.")
                fetchCalendarEvents(accessToken)
            } else {
                Log.e("SetupCalendar", "Failed to exchange code for tokens: No response JSON")
            }
        } catch (e: Exception) {
            Log.e("SetupCalendar", "Error exchanging code for tokens", e)
        }
    }

    private suspend fun refreshAccessToken(refreshToken: String): String? {
        Log.d("SetupCalendar", "Refreshing access token...")
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
                Log.i("SetupCalendar", "Access token refreshed successfully.")
                return newAccessToken
            } else {
                Log.e("SetupCalendar", "Failed to refresh token: No response JSON")
                prefs.edit().remove(GoogleApiConstants.KEY_REFRESH_TOKEN).apply()
            }
        } catch (e: Exception) {
            Log.e("SetupCalendar", "Error refreshing access token", e)
        }
        return null
    }

    private suspend fun fetchCalendarEvents(accessToken: String) {
        Log.d("SetupCalendar", "Fetching calendar events with token: $accessToken")
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

        Log.d("SetupCalendar", "Fetching events from URL: $eventsUrl")

        try {
            val responseJson = makeKtorGetRequest(eventsUrl, accessToken)
            if (responseJson != null) {
                val items = responseJson.optJSONArray("items")
                val eventsList = ArrayList<CalendarEvent>() // Create a list for our data class

                if (items != null) {
                    for (i in 0 until items.length()) {
                        val event = items.getJSONObject(i)
                        val summary = event.optString("summary", "No Title")
                        val startObj = event.optJSONObject("start")
                        var startTime = "No Start Time"
                        startObj?.let {
                            startTime = it.optString("dateTime", it.optString("date", "No Start Date"))
                        }
                        // Add the parsed event to our list
                        eventsList.add(CalendarEvent(summary, startTime))
                    }
                }

                // Launch the new activity on the Main thread
                withContext(Dispatchers.Main) {
                    val intent = Intent(activity, CalendarActivity::class.java).apply {
                        putParcelableArrayListExtra("EVENTS_LIST", eventsList)
                    }
                    activity.startActivity(intent)
                }

            } else {
                Log.e("SetupCalendar", "Failed to fetch calendar events: No response JSON")
            }
        } catch (e: Exception) {
            Log.e("SetupCalendar", "Error fetching calendar events", e)
            println("Error fetching events: ${e.message}")
        }
    }

    // Helper to make Ktor POST requests and parse to JSONObject
    private suspend fun makeKtorPostRequest(urlString: String, formParameters: Parameters): JSONObject? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("SetupCalendar", "Ktor POST Request to URL: $urlString with params: ${formParameters.entries()}")
                val response: HttpResponse = httpClient.submitForm(
                    url = urlString,
                    formParameters = formParameters
                )
                logKtorResponse(response)

                if (response.status != HttpStatusCode.OK) {
                    handleKtorErrorResponse(response, urlString)
                    return@withContext null
                }
                val responseBody = response.bodyAsText()
                if (responseBody.isEmpty()) {
                    Log.w("SetupCalendar", "Ktor POST Response body is empty.")
                    return@withContext null
                }
                Log.d("SetupCalendar", "Ktor POST Response: $responseBody")
                JSONObject(responseBody) // Still using org.json for parsing for now
            } catch (e: Exception) { // Catch more specific Ktor exceptions if needed
                Log.e("SetupCalendar", "Exception during Ktor POST request to $urlString", e)
                null
            }
        }
    }

    // Helper to make Ktor GET requests and parse to JSONObject
    private suspend fun makeKtorGetRequest(urlString: String, bearerToken: String? = null): JSONObject? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("SetupCalendar", "Ktor GET Request to URL: $urlString")
                val response: HttpResponse = httpClient.get(urlString) {
                    bearerToken?.let { token ->
                        header("Authorization", "Bearer $token")
                    }
                }
                logKtorResponse(response)

                if (response.status != HttpStatusCode.OK) {
                    handleKtorErrorResponse(response, urlString)
                    return@withContext null
                }
                val responseBody = response.bodyAsText()
                if (responseBody.isEmpty()) {
                    Log.w("SetupCalendar", "Ktor GET Response body is empty.")
                    return@withContext null
                }
                Log.d("SetupCalendar", "Ktor GET Response: $responseBody")
                JSONObject(responseBody) // Still using org.json for parsing for now
            } catch (e: Exception) { // Catch more specific Ktor exceptions if needed
                Log.e("SetupCalendar", "Exception during Ktor GET request to $urlString", e)
                null
            }
        }
    }

    private suspend fun logKtorResponse(response: HttpResponse) {
        Log.d("SetupCalendar", "Ktor Response Status: ${response.status}")
        Log.d("SetupCalendar", "Ktor Response Headers: ${response.headers.entries()}")
    }

    private suspend fun handleKtorErrorResponse(response: HttpResponse, urlString: String) {
        val errorBody = try { response.bodyAsText() } catch (e: Exception) { "Could not read error body." }
        Log.e(
            "SetupCalendar",
            "Ktor HTTP Request Failed to $urlString: ${response.status}\nBody: $errorBody"
        )
        if (response.status == HttpStatusCode.Unauthorized && !urlString.contains("oauth2.googleapis.com/token")) {
            Log.w("SetupCalendar", "Received 401, access token might be invalid.")
            prefs.edit().remove(GoogleApiConstants.KEY_ACCESS_TOKEN).apply()
        }
    }

    // Call this when your activity/app is being destroyed to clean up the Ktor client
    fun cleanup() {
        httpClient.close()
        Log.d("SetupCalendar", "Ktor HttpClient closed.")
    }
}