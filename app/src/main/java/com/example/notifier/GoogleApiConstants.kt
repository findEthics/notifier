package com.example.notifier

import android.content.Context
import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

object GoogleApiConstants {


    val PROJECT_ID: String = "setup0204"
    val CLIENT_ID: String = BuildConfig.GOOGLE_WEB_CLIENT_ID
    val CLIENT_SECRET: String = BuildConfig.GOOGLE_WEB_CLIENT_SECRET
    const val REDIRECT_URI_BASE = "http://127.0.0.1"
    val AUTH_URI: String = "https://accounts.google.com/o/oauth2/auth"
    val TOKEN_URI_OAUTH: String = "https://oauth2.googleapis.com/token"
    val AUTH_PROVIDER_X509_CERT_URL: String = "https://www.googleapis.com/oauth2/v1/certs"

    // --- Other Constants ---
    // This must match exactly what you configured in Google Cloud Console AND
    // the scheme and host in your AndroidManifest.xml intent-filter for the redirect.
    val REDIRECT_URI: String = "com.example.notifier://oauth2redirect"

    // const val AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth" // This will be taken from AUTH_URI
    const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"

    const val CALENDAR_API_SCOPE = "https://www.googleapis.com/auth/calendar.events.readonly"
    const val CALENDAR_EVENTS_ENDPOINT = "https://www.googleapis.com/calendar/v3/calendars/primary/events"

    const val PREFS_NAME = "GoogleCalendarAuthPrefs"
    const val KEY_ACCESS_TOKEN = "access_token"
    const val KEY_REFRESH_TOKEN = "refresh_token"
    const val KEY_EXPIRES_IN = "expires_in"
    const val KEY_TOKEN_TYPE = "token_type"

}
