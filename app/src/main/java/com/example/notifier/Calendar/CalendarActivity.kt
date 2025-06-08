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

        // Setup Toolbar with back button
        val toolbar = findViewById<MaterialToolbar>(R.id.calendar_toolbar)
        setSupportActionBar(toolbar)
        val title = "Today's Events"
        val spannableTitle = SpannableString(title)
        spannableTitle.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, title.length, 0)
        supportActionBar?.title = spannableTitle
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

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