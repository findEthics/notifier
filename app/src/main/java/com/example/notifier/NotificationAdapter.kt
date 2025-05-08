package com.example.notifier

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class NotificationAdapter(
    private val items: List<NotificationData>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvTitle)
        val text: TextView = view.findViewById(R.id.tvText)
        val appName: TextView = view.findViewById(R.id.tvAppName)
        val groupIndicator: TextView = view.findViewById(R.id.tvGroupIndicator)
        val groupId: TextView = view.findViewById(R.id.tvGroupId)

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.notification_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.text.text = item.text
        holder.appName.text = item.appName
        holder.groupId.text = "Group ID: ${item.groupId}"


        // Show/hide group summary indicator
        holder.groupIndicator.visibility =
            if (item.isGroupSummary) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener {
            onClick(item.packageName)
        }
    }

    override fun getItemCount() = items.size
}


