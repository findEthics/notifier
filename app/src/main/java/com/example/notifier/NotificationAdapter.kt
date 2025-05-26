package com.example.notifier

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationAdapter(
    private val items: List<NotificationData>,
    private val onClick: (NotificationData) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvTitle)
        val text: TextView = view.findViewById(R.id.tvText)
//        val appName: TextView = view.findViewById(R.id.tvAppName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.notification_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.text.text = item.text

        val timeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.timestamp))



        val titleText: String
        val boldEndIndex: Int

        if (item.appName == "Calendar") {
            titleText = "${item.appName}: ${item.title} $timeString"
            boldEndIndex = item.appName.length + 1 // Include the colon and space
        } else {
            titleText = "${item.title} $timeString"
            boldEndIndex = item.title.length // Nothing to bold specifically from appName
        }

        val spannableTitle = SpannableString(titleText)
        if (boldEndIndex > 0) { // Only apply span if there's something to bold
            spannableTitle.setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                boldEndIndex,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        holder.title.text = spannableTitle

//        // Combine appName (bold) and time (normal)
//        val combined = "${item.appName} $timeString"
//        val spannable = SpannableString(combined)
//        spannable.setSpan(
//            StyleSpan(Typeface.BOLD),
//            0,
//            item.appName.length,
//            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
//        )
//        holder.appName.text = spannable

        holder.itemView.setOnClickListener {
//            onClick(item.packageName)
            onClick(item)
        }
    }

    override fun getItemCount() = items.size
}


