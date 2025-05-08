package com.example.notifier

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {
    private val notifications = mutableListOf<NotificationData>()
    private lateinit var adapter: NotificationAdapter

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "NEW_NOTIFICATION" -> {
                    val key = intent.getStringExtra("key") ?: return
                    val title = intent.getStringExtra("title") ?: ""
                    val text = intent.getStringExtra("text") ?: ""
                    val packageName = intent.getStringExtra("package") ?: ""
                    val isGroupSummary = intent.getBooleanExtra("isGroupSummary", false)
                    val appName = getAppName(packageName)

                    // Check for duplicates
                    val existingIndex = notifications.indexOfFirst { it.key == key }
                    if (existingIndex >= 0) {
                        // Update instead of adding duplicate
                        notifications.removeAt(existingIndex)
                    }

                    // Add new notification at top
                    notifications.removeAll { it.key == key } // Prevent duplicates
                    notifications.add(0, NotificationData(
                        title = title,
                        text = text,
                        packageName = packageName,
                        appName = appName,
                        key = key,
                        isGroupSummary = isGroupSummary
                    ))
                    adapter.notifyDataSetChanged()
                }
                "REMOVE_NOTIFICATION" -> {
                    val key = intent.getStringExtra("key") ?: return
                    val removed = notifications.removeAll { it.key == key }
                    if (removed) adapter.notifyDataSetChanged()
                }
            }
        }
    }




    private fun getAppName(packageName: String): String {
        return try {
            val packageManager = applicationContext.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: Exception) {
            packageName // fallback if not found
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Clear button setup
        val btnClear = findViewById<FloatingActionButton>(R.id.btnClear)
        btnClear.setOnClickListener {
            notifications.clear()
            adapter.notifyDataSetChanged()
        }

        if (!isNotificationServiceEnabled()) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        setupRecyclerView()
        val filter = IntentFilter().apply {
            addAction("NEW_NOTIFICATION")
            addAction("REMOVE_NOTIFICATION")
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter)

    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        adapter = NotificationAdapter(notifications) { packageName ->
            try {
                packageManager.getLaunchIntentForPackage(packageName)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(it)
                } ?: run {
                    Toast.makeText(this, "App not present", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Cannot open app", Toast.LENGTH_SHORT).show()
            }
        }

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            addItemDecoration(DividerItemDecoration(this@MainActivity, DividerItemDecoration.VERTICAL))
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        return enabledListeners?.contains(packageName) == true
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        super.onDestroy()
    }
}
