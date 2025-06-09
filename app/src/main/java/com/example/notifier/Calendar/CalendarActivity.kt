package com.example.notifier.Calendar

import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.notifier.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CalendarActivity : AppCompatActivity() {

    private lateinit var calendarSetup: SetupCalendar
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CalendarAdapter
    private lateinit var refreshButton: ImageButton
    private lateinit var loginButton: ImageButton


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar)

        // Initialize SetupCalendar for this activity
        calendarSetup = SetupCalendar(this)

        val events =
            intent.getParcelableArrayListExtra<CalendarEvent>("EVENTS_LIST") ?: arrayListOf()

        recyclerView = findViewById(R.id.calendar_recyclerView)
        adapter = CalendarAdapter(events)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        loginButton = findViewById(R.id.idBtnLogin)
        loginButton.setOnClickListener {
            calendarSetup.triggerAuthenticationFlow { newEvents ->
                // This callback runs on the main thread
                if (newEvents != null) {
                    adapter.updateData(newEvents)
                    Log.i("CalendarActivity", "Events refreshed after login")
                } else {
                    Log.e("CalendarActivity", "Failed to refresh events after login")
                }
            }
        }

        refreshButton = findViewById(R.id.idBtnCalendarRefresh) // Make sure this ID exists in your layout
        refreshButton.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                val accessToken = calendarSetup.getValidAccessToken()
                if (accessToken != null) {
                    // If a token exists, just fetch the latest events
                    val newEvents = calendarSetup.fetchCalendarEvents(accessToken)
                    if (newEvents != null) {
                        adapter.updateData(newEvents)
                        Log.i("CalendarActivity", "Events refreshed successfully")
                    } else {
                        Log.e("CalendarActivity", "Failed to fetch events with existing token")
                    }
                } else {
                    // If no valid token, trigger the full login flow
                    calendarSetup.triggerAuthenticationFlow { newEvents ->
                        if (newEvents != null) {
                            adapter.updateData(newEvents)
                            Log.i("CalendarActivity", "Login successful, events updated")
                        } else {
                            Log.e("CalendarActivity", "Authentication failed during refresh")
                        }
                    }
                }
            }
        }
    }


//
//    // Handle back button press
//    override fun onSupportNavigateUp(): Boolean {
//        onBackPressedDispatcher.onBackPressed()
//        return true
//    }
}