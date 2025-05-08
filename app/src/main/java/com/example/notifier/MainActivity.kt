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
    private val seenNotificationKeys = mutableSetOf<String>() // For deduplication
    private val seenGroups = mutableMapOf<String, Int>() // Group ID → Position in list

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "NEW_NOTIFICATION" -> {
                    val key = intent.getStringExtra("key") ?: return
                    val title = intent.getStringExtra("title") ?: ""
                    val text = intent.getStringExtra("text") ?: ""
                    val packageName = intent.getStringExtra("package") ?: ""
                    val isGroupSummary = intent.getBooleanExtra("isGroupSummary", false)
                    val groupId = intent.getStringExtra("groupId") ?: ""
                    val appName = getAppName(packageName)



//                    // Remove previous notification from the same group
//                    seenGroups[groupId]?.let { position ->
//                        if (position in 0 until notifications.size) {
//                            notifications.removeAt(position)
//                        }
//                    }
//                    // Remove any notification with the same key (safety)
//                    notifications.removeAll { it.key == key }
//
//                    // Add new notification at top
//                    notifications.add(0, NotificationData(title, text, packageName, appName, key))
//                    seenGroups[groupId] = 0
//                    adapter.notifyDataSetChanged()
//                }
                    // Remove previous group summary if exists
                    if (isGroupSummary) {
                        notifications.removeAll { it.isGroupSummary && it.groupId == groupId }
                    }

                    // Add new notification at top
                    notifications.removeAll { it.key == key } // Prevent duplicates
                    notifications.add(0, NotificationData(
                        title = title,
                        text = text,
                        packageName = packageName,
                        appName = appName,
                        key = key,
                        isGroupSummary = isGroupSummary,
                        groupId = groupId
                    ))

                    adapter.notifyDataSetChanged()
                }
                "REMOVE_NOTIFICATION" -> {
                    val key = intent.getStringExtra("key") ?: return
                    notifications.removeAll { it.key == key }
                    adapter.notifyDataSetChanged()
                }
                "REMOVE_ALL_WHATSAPP_SUMMARIES" -> {
                    notifications.removeAll {
                        it.packageName == "com.whatsapp" && it.isGroupSummary
                    }
                    adapter.notifyDataSetChanged()
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

        val btnClear = findViewById<FloatingActionButton>(R.id.btnClear)
        btnClear.setOnClickListener {
            notifications.clear()
            seenNotificationKeys.clear()
            seenGroups.clear()
            adapter.notifyDataSetChanged()
        }

        if (!isNotificationServiceEnabled()) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        setupRecyclerView()
        val filter = IntentFilter().apply {
            addAction("NEW_NOTIFICATION")
            addAction("REMOVE_NOTIFICATION")
            addAction("REMOVE_ALL_WHATSAPP_SUMMARIES")
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
