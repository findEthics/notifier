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

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val key = it.getStringExtra("key") ?: return@let
                val title = it.getStringExtra("title") ?: ""
                val text = it.getStringExtra("text") ?: ""
                val packageName = it.getStringExtra("package") ?: ""

                if (key !in seenNotificationKeys) {
                    seenNotificationKeys.add(key)
                    notifications.add(0, NotificationData(title, text, packageName))
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnClear = findViewById<FloatingActionButton>(R.id.btnClear)
        btnClear.setOnClickListener {
            notifications.clear()
            seenNotificationKeys.clear()
            adapter.notifyDataSetChanged()
        }

        if (!isNotificationServiceEnabled()) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        setupRecyclerView()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(receiver, IntentFilter("NEW_NOTIFICATION"))
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
