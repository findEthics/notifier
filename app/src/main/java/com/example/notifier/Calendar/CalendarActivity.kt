package com.example.notifier.Calendar

import android.os.Bundle
import android.text.SpannableString
import android.text.style.StyleSpan
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.notifier.R
import com.google.android.material.appbar.MaterialToolbar

class CalendarActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar)

        // Get the events from the intent
        val events = intent.getParcelableArrayListExtra<CalendarEvent>("EVENTS_LIST")

        if (events != null) {
            val recyclerView = findViewById<RecyclerView>(R.id.calendar_recyclerView)
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = CalendarAdapter(events)
        }
    }

    // Handle back button press
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}