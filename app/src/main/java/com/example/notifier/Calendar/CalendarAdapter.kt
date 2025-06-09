package com.example.notifier.Calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.notifier.R
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class CalendarAdapter(private var events: List<CalendarEvent>) :
    RecyclerView.Adapter<CalendarAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvEventTime: TextView = view.findViewById(R.id.tvEventTime)
        val tvEventSummary: TextView = view.findViewById(R.id.tvEventSummary)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_event, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val event = events[position]
        holder.tvEventSummary.text = event.summary
        holder.tvEventTime.text = formatDisplayTime(event.startTime)
    }

    override fun getItemCount() = events.size

    private fun formatDisplayTime(isoDateTime: String): String {
        return try {
            val odt = OffsetDateTime.parse(isoDateTime)
            val formatter = DateTimeFormatter.ofPattern("HH:mm")
            odt.format(formatter)
        } catch (e: Exception) {
            // Fallback for all-day events or different formats
            "All-day"
        }
    }

    fun updateData(newEvents: List<CalendarEvent>) {
        this.events = newEvents
        notifyDataSetChanged() // Refresh the list
    }
}