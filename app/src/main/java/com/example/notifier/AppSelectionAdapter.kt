package com.example.notifier

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppSelectionAdapter(
    private val allApps: List<Triple<String, String, Drawable>>, // packageName, appName, icon
    private val initialSelectedApps: Set<String>,
    private val onSelectionChanged: (String, Boolean) -> Unit
) : RecyclerView.Adapter<AppSelectionAdapter.AppViewHolder>() {

    private val selectedApps = initialSelectedApps.toMutableSet()
    private var filteredApps = allApps.toList() // Currently displayed apps

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        val appName: TextView = itemView.findViewById(R.id.appName)
        val appCheckBox: CheckBox = itemView.findViewById(R.id.appCheckBox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_selection, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val (packageName, appName, appIcon) = filteredApps[position]
        
        holder.appIcon.setImageDrawable(appIcon)
        holder.appName.text = appName
        
        // Clear previous listener to avoid unwanted triggers
        holder.appCheckBox.setOnCheckedChangeListener(null)
        
        // Set the checkbox state
        holder.appCheckBox.isChecked = selectedApps.contains(packageName)
        
        // Set up the listener
        holder.appCheckBox.setOnCheckedChangeListener { _, isChecked ->
            android.util.Log.d("AppSelectionAdapter", "Checkbox changed for $appName ($packageName): $isChecked")
            if (isChecked) {
                selectedApps.add(packageName)
            } else {
                selectedApps.remove(packageName)
            }
            android.util.Log.d("AppSelectionAdapter", "Selected apps now: ${selectedApps.size} total")
            onSelectionChanged(packageName, isChecked)
        }
        
        // Make the whole item clickable to toggle checkbox
        holder.itemView.setOnClickListener {
            holder.appCheckBox.isChecked = !holder.appCheckBox.isChecked
        }
    }

    override fun getItemCount(): Int = filteredApps.size

    fun getSelectedApps(): Set<String> {
        android.util.Log.d("AppSelectionAdapter", "getSelectedApps() returning: ${selectedApps.size} apps: $selectedApps")
        return selectedApps.toSet()
    }
    
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