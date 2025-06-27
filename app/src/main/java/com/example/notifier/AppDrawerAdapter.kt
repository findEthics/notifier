package com.example.notifier

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppDrawerAdapter(
    private val allApps: List<Triple<String, String, Drawable>>, // packageName, appName, icon
    private val onAppClick: (String) -> Unit
) : RecyclerView.Adapter<AppDrawerAdapter.AppViewHolder>() {

    private var filteredApps = allApps.toList() // Currently displayed apps

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        val appName: TextView = itemView.findViewById(R.id.appName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_drawer, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val (packageName, appName, appIcon) = filteredApps[position]
        
        holder.appIcon.setImageDrawable(appIcon)
        holder.appName.text = appName
        
        // Set click listener to launch the app
        holder.itemView.setOnClickListener {
            onAppClick(packageName)
        }
    }

    override fun getItemCount(): Int = filteredApps.size
    
    fun filter(query: String) {
        filteredApps = if (query.isEmpty()) {
            allApps
        } else {
            allApps.filter { (_, appName, _) ->
                appName.contains(query, ignoreCase = true)
            }
        }
        notifyDataSetChanged()
    }
}