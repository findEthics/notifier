package com.example.notifier

import android.Manifest
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.spotify.android.appremote.api.SpotifyAppRemote
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.notifier.Calendar.SetupCalendar

class MainActivity : AppCompatActivity() {
    private val notifications = mutableListOf<NotificationData>()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NotificationAdapter
    private var isMuted = false
    private var previousVolume = 0
    private var previousRingerVolume = 0
    private var isVibrateMode = false
    private lateinit var audioManager: AudioManager
    private lateinit var sharedPrefs: SharedPreferences
    private val PREFS_NAME = "AppSettings"
    private val KEY_VIBRATE_MODE = "vibrate_mode"
    private val KEY_MUTE_STATE = "mute_state"
    private lateinit var calendarSetup: SetupCalendar
    private lateinit var spotifyManager: SpotifyManager

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "NEW_NOTIFICATION" -> {
                    val key = intent.getStringExtra("key") ?: return
                    val title = intent.getStringExtra("title") ?: ""
                    val text = intent.getStringExtra("text") ?: ""
                    val packageName = intent.getStringExtra("package") ?: ""
                    val appName = getAppName(packageName)
                    val systemKey = intent.getStringExtra("systemKey") ?: return

                    notifications.removeAll { it.key == key } // Prevent duplicates

                    // Add new notification at top
                    notifications.add(0, NotificationData(
                        title = title,
                        text = text,
                        packageName = packageName,
                        appName = appName,
                        key = key,
                        systemKey = systemKey
                    ))
                    adapter.notifyDataSetChanged()
                    if (packageName == "com.whatsapp") {
                        try {
                            val notification =
                                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                            val r = RingtoneManager.getRingtone(context, notification)
                            r.play()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                "REMOVE_NOTIFICATION" -> {
                    val key = intent.getStringExtra("key") ?: return
                    val removed = notifications.removeAll { it.key == key }
                    if (removed) adapter.notifyDataSetChanged()
                }
            }
        }
    }

    // Activity Result Launcher for POST_NOTIFICATIONS permission
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted. You can now post notifications.
                Log.d("Permissions", "POST_NOTIFICATIONS permission granted.")
                checkAndRequestExactAlarmPermission() // Chain to the next permission check
            } else {
                // Explain to the user that the feature is unavailable
                Log.w("Permissions", "POST_NOTIFICATIONS permission denied.")
                Toast.makeText(this, "Notification permission denied. Reminders will not work.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Calendar and Spotify setup
        spotifyManager = SpotifyManager(this)
        calendarSetup = SetupCalendar(this)

        createNotificationChannel()
        checkAndRequestPermissions()

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        // Initialize SharedPreferences HERE
        sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Get current state of Vibrate and Mute
        isVibrateMode = audioManager.ringerMode == AudioManager.RINGER_MODE_VIBRATE
        isMuted = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0

        // Start Spotify Auth Flow
        spotifyManager.start()

        // Setup UI
        setupVolumeAndRingControls()
        spotifyManager.setupSpotifyPlayerControls()

        val btnCalendar = findViewById<ImageButton>(R.id.btnCalendar)
        calendarSetup.setupCalendar(btnCalendar)

        val tvBattery = findViewById<TextView>(R.id.tvBattery)
        tvBattery.text = getString(R.string.battery_status, getBatteryPercentage(this))

        // Clear button setup
        val btnClear = findViewById<FloatingActionButton>(R.id.btnClear)
        btnClear.setOnClickListener {
            notifications.clear()
            adapter.notifyDataSetChanged()
        }
        //WhatsApp button setup
        val openWhatsApp = findViewById<FloatingActionButton>(R.id.btnWhatsApp)
        openWhatsApp.setOnClickListener {
            // Open WhatsApp
            try {
                val intent = packageManager.getLaunchIntentForPackage("com.whatsapp")
                if (intent == null) {
                    // WhatsApp not found, open default messaging app
                    val defaultSmsIntent = Intent(Intent.ACTION_MAIN)
                    defaultSmsIntent.addCategory(Intent.CATEGORY_APP_MESSAGING)
                    startActivity(defaultSmsIntent)
                    Toast.makeText(this, "WhatsApp not found, opening default messaging app", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "WhatsApp not found", Toast.LENGTH_SHORT).show()
            }
        }

        val openAssistant = findViewById<FloatingActionButton>(R.id.btnAssistant)
        openAssistant.setOnClickListener {
            // Open Claude Assistant
            Log.d("MainActivity", "Attempting to open assistant...")
            try {
                val claudeIntent = packageManager.getLaunchIntentForPackage("com.anthropic.claude")
                if (claudeIntent != null) {
                    Log.d("MainActivity", "Claude app found. Launching...")
                    startActivity(claudeIntent)
                } else {
                    Log.d("MainActivity", "Claude app not found. Trying default assistant.")
                    // Try to open the default voice assistant
                    val defaultAssistantIntent = Intent(Intent.ACTION_VOICE_COMMAND)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (defaultAssistantIntent.resolveActivity(packageManager) != null) {
                        Log.d("MainActivity", "Default assistant found. Launching...")
                        startActivity(defaultAssistantIntent)
                    } else {
                        Log.d("MainActivity", "No default assistant found. Trying default browser.")
                        // Try to open the default browser as a last resort
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                            // Ensure the intent can be handled by a browser
                            addCategory(Intent.CATEGORY_BROWSABLE)
                        }
                        if (browserIntent.resolveActivity(packageManager) != null) {
                            Log.d("MainActivity", "Default browser found. Launching...")
                            startActivity(browserIntent)
                        } else {
                            Log.w("MainActivity", "No suitable app found to handle assistant request (Claude, Default Assistant, Browser).")
                            Toast.makeText(this, "No assistant or browser app found", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error opening assistant", e)
                Toast.makeText(this, "Error opening assistant", Toast.LENGTH_SHORT).show()
            }
        }
//
        val openMaps = findViewById<FloatingActionButton>(R.id.btnMaps)
        openMaps.setOnClickListener {
            try {
                val intentGmapsWV = packageManager.getLaunchIntentForPackage("us.spotco.maps")
                val intentMuditaMaps = packageManager.getLaunchIntentForPackage("com.mudita.maps")
                val intentGoogleMaps = packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
                if (intentGmapsWV != null) {
                    startActivity(intentGmapsWV)
                } else if (intentMuditaMaps != null) {
                    startActivity(intentMuditaMaps)
                } else if (intentGoogleMaps != null) {
                    startActivity(intentGoogleMaps)
                } else {
                    // Try to open Google Maps in the default browser
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com"))
                    if (browserIntent.resolveActivity(packageManager) != null) {
                        startActivity(browserIntent)
                    } else {
                        Toast.makeText(this, "No Map apps or browser found", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: PackageManager.NameNotFoundException) {
                // App not installed
                Toast.makeText(this, "No Map apps found", Toast.LENGTH_SHORT).show()
            }
        }

        if (!isNotificationServiceEnabled()) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }



        setupRecyclerView()
        setupSwipeToDelete()

        val filter = IntentFilter().apply {
            addAction("NEW_NOTIFICATION")
            addAction("REMOVE_NOTIFICATION")
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter)

    }

    override fun onStart() {
        super.onStart()

        spotifyManager.connect()

        val tvBattery = findViewById<TextView>(R.id.tvBattery)
        tvBattery.text = getString(R.string.battery_status, getBatteryPercentage(this))
    }

    override fun onResume() {
        super.onResume()
        verifySystemState()
    }

    override fun onStop() {
        super.onStop()
        spotifyManager.disconnect()
    }

    private fun verifySystemState() {

        val btnMute = findViewById<ImageButton>(R.id.btnMute)
        val btnRingVibrate = findViewById<ImageButton>(R.id.btnRingVibrate)

        // For vibrate mode
        val actualVibrate = audioManager.ringerMode == AudioManager.RINGER_MODE_VIBRATE
        if (isVibrateMode != actualVibrate) {
            isVibrateMode = actualVibrate
            sharedPrefs.edit().putBoolean(KEY_VIBRATE_MODE, actualVibrate).apply()
            btnRingVibrate.setImageResource(if (isVibrateMode) R.drawable.ic_vibrate else R.drawable.ic_ring)
        }

        // For mute state
        val actualMute = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
        if (isMuted != actualMute) {
            isMuted = actualMute
            sharedPrefs.edit().putBoolean(KEY_MUTE_STATE, actualMute).apply()
            btnMute.setImageResource(if (actualMute) R.drawable.ic_mute else R.drawable.ic_unmute)
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerView)
        adapter = NotificationAdapter(notifications) { notificationData ->
            try {
                packageManager.getLaunchIntentForPackage(notificationData.packageName)?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(it)
                } ?: run {
                    Toast.makeText(this, "App not found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error opening app", Toast.LENGTH_SHORT).show()
            }
            // Remove from local list
            notifications.removeAll { it.key == notificationData.key }
            adapter.notifyDataSetChanged()
            // Send cancellation command to service
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                Intent("CANCEL_NOTIFICATION").apply {
                    putExtra("key", notificationData.key)
                }
            )
        }

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            addItemDecoration(DividerItemDecoration(this@MainActivity, DividerItemDecoration.VERTICAL))
        }
    }

    private fun setupSwipeToDelete() {
        val swipeToDeleteCallback = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false // No drag-and-drop

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val notification = notifications[position]

                // Remove from RecyclerView
                notifications.removeAt(position)
                adapter.notifyItemRemoved(position)

                // Notify service to cancel the system notification
                LocalBroadcastManager.getInstance(this@MainActivity).sendBroadcast(
                    Intent("CANCEL_NOTIFICATION").apply {
                        putExtra("key", notification.key)
                    }
                )
                LocalBroadcastManager.getInstance(this@MainActivity).sendBroadcast(
                    Intent("CANCEL_SYSTEM_NOTIFICATION").apply {
                        putExtra("systemKey", notification.systemKey)
                    }
                )
            }
        }
        ItemTouchHelper(swipeToDeleteCallback).attachToRecyclerView(recyclerView)
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

    private fun getAppName(packageName: String): String {
        return try {
            val packageManager = applicationContext.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: Exception) {
            packageName // fallback if not found
        }
    }

    private fun getBatteryPercentage(context: Context): Int {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, ifilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else 0
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Calendar Event Reminders"
            val descriptionText = "Notifications for upcoming calendar events"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("CALENDAR_REMINDERS", name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun checkAndRequestPermissions() {
        // 1. Check for POST_NOTIFICATIONS (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission is already granted
                    Log.d("Permissions", "POST_NOTIFICATIONS permission already granted.")
                    checkAndRequestExactAlarmPermission() // Check next permission
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Show a dialog explaining why you need the permission
                    AlertDialog.Builder(this)
                        .setTitle("Permission Needed")
                        .setMessage("This app needs permission to post notifications to provide reminders for your calendar events.")
                        .setPositiveButton("OK") { _, _ ->
                            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        .setNegativeButton("Cancel", null)
                        .create()
                        .show()
                }
                else -> {
                    // Directly request the permission
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // For older versions, this permission is not needed, so check the next one
            checkAndRequestExactAlarmPermission()
        }
    }

    // 2. Check for SCHEDULE_EXACT_ALARM (Android 12+)
    private fun checkAndRequestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                // Show a dialog to explain and guide the user to settings
                AlertDialog.Builder(this)
                    .setTitle("Permission Needed for Reminders")
                    .setMessage("To set precise reminders for your events, this app needs special permission to schedule exact alarms. Please grant it in the next screen.")
                    .setPositiveButton("Go to Settings") { _, _ ->
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        startActivity(intent)
                    }
                    .setNegativeButton("Not now", null)
                    .create()
                    .show()
            }
        }
    }

    private fun setupVolumeAndRingControls() {
        val btnMute = findViewById<ImageButton>(R.id.btnMute)
        val btnRingVibrate = findViewById<ImageButton>(R.id.btnRingVibrate)

        fun updateRingVibrateButton() {
            val imageRes = if (isVibrateMode) R.drawable.ic_vibrate else R.drawable.ic_ring
            btnRingVibrate.setImageResource(imageRes)
        }
        updateRingVibrateButton()

        fun updateMuteButton() {
            btnMute.setImageResource(if (isMuted) R.drawable.ic_mute else R.drawable.ic_unmute)
        }
        updateMuteButton()

        btnRingVibrate.setOnClickListener {
            if (isVibrateMode) {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                audioManager.setStreamVolume(AudioManager.STREAM_RING, if (previousRingerVolume == 0) 5 else previousRingerVolume, AudioManager.FLAG_PLAY_SOUND)
            } else {
                previousRingerVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            }
            isVibrateMode = !isVibrateMode
            updateRingVibrateButton()
            sharedPrefs.edit().putBoolean(KEY_VIBRATE_MODE, isVibrateMode).apply()
        }

        btnMute.setOnClickListener {
            if (!isMuted) {
                previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            } else {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, if (previousVolume == 0) 3 else previousVolume, 0)
            }
            isMuted = !isMuted
            updateMuteButton()
            sharedPrefs.edit().putBoolean(KEY_MUTE_STATE, isMuted).apply()
        }
    }

}
