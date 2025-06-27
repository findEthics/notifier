package com.example.notifier

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.notifier.Calendar.SetupCalendar
import com.example.notifier.Calendar.CalendarCacheManager
import com.example.notifier.Calendar.CalendarEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MainActivity : AppCompatActivity(), NotificationCallback {
    private val notifications = mutableListOf<NotificationData>()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NotificationAdapter
    private var isMuted = false
    private var previousVolume = 0
    private var previousRingerVolume = 0
    private var isVibrateMode = false
    private lateinit var audioManager: AudioManager
    private lateinit var sharedPrefs: SharedPreferences
    private val prefsName = "AppSettings"
    private val keyVibrateMode = "vibrate_mode"
    private val keyMuteState = "mute_state"
    private val keyDarkMode = "dark_mode_enabled"
    
    // Calendar cache manager
    private lateinit var calendarCacheManager: CalendarCacheManager
    
    // Bottom layout button configuration keys
    private val keyBottomButton1 = "bottom_button_1_package" // Maps button
    private val keyBottomButton2 = "bottom_button_2_package" // Assistant button  
    private val keyBottomButton3 = "bottom_button_3_package" // WhatsApp button
    private var calendarSetup: SetupCalendar? = null
    private var spotifyManager: SpotifyManager? = null
    
    // Permission state caching
    private var postNotificationPermissionGranted: Boolean? = null
    private var exactAlarmPermissionGranted: Boolean? = null
    
    // Spotify timeout handling
    private val handler = Handler(Looper.getMainLooper())
    private var spotifyTimeoutRunnable: Runnable? = null
    
    // Date update handling
    private var dateUpdateRunnable: Runnable? = null
    private var lastDisplayedDate: String? = null
    
    // Bottom layout gesture handling
    private lateinit var gestureDetector: GestureDetector
    private lateinit var bottomButtonLayout: ConstraintLayout
    private var hideBottomLayoutRunnable: Runnable? = null
    private val BOTTOM_LAYOUT_HIDE_DELAY = 15000L // 15 seconds
    
    // App drawer handling
    private lateinit var appDrawerLayout: ConstraintLayout
    private var appDrawerAdapter: AppDrawerAdapter? = null
    
    // Upcoming events handling
    private lateinit var upcomingEventsLayout: LinearLayout
    private lateinit var eventsContainer: LinearLayout
    private var upcomingEventsUpdateRunnable: Runnable? = null
    private val UPCOMING_EVENTS_UPDATE_INTERVAL = 60000L // 1 minute

    // Implementation of NotificationCallback interface
    override fun onNewNotification(notification: NotificationUpdate) {
        val appName = getAppName(notification.packageName)

        notifications.removeAll { it.key == notification.key } // Prevent duplicates

        // Add new notification at top
        notifications.add(0, NotificationData(
            title = notification.title,
            text = notification.text,
            packageName = notification.packageName,
            appName = appName,
            key = notification.key,
            systemKey = notification.systemKey
        ))
        adapter.notifyDataSetChanged()
    }

    override fun onRemoveNotification(key: String) {
        val removed = notifications.removeAll { it.key == key }
        if (removed) adapter.notifyDataSetChanged()
    }

    // Activity Result Launcher for POST_NOTIFICATIONS permission
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            // Update cache
            postNotificationPermissionGranted = isGranted
            
            if (isGranted) {
                // Permission granted, continue with exact alarm check
                checkExactAlarmAndProceed()
            } else {
                // Permission denied
                showPermissionDeniedMessage()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize SharedPreferences FIRST
        sharedPrefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        calendarCacheManager = CalendarCacheManager(this)
        
        // Apply theme before setting content view
        applyTheme()
        
        setContentView(R.layout.activity_main)

        // Spotify will be setup lazily when user interacts with controls
        // Calendar permissions will be checked when calendar button is clicked

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        // Get current state of Vibrate and Mute
        isVibrateMode = audioManager.ringerMode == AudioManager.RINGER_MODE_VIBRATE
        isMuted = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0

        // Setup UI
        setupCurrentDate()
        setupVolumeAndRingControls()
        setupSpotifyControls()
        
        // Initialize Spotify controls as hidden
        hideSpotifyControls()

        // Calendar functionality moved to date display
        setupDateDisplayCalendar()
        // Setup action button listeners
        setupActionButtonListeners()

        if (!isNotificationServiceEnabled()) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        setupRecyclerView()
        setupSwipeToDelete()

        // Register as the activity callback
        NotificationCommunicationManager.registerActivityCallback(this)

        // Setup bottom layout gesture handling
        setupBottomLayoutGesture()
        
        // Setup app drawer
        setupAppDrawer()
        
        // Setup upcoming events
        setupUpcomingEvents()
    }

    private fun setupActionButtonListeners() {
        // App drawer button setup
        val btnAppDrawer = findViewById<ImageButton>(R.id.btnAppDrawer)
        btnAppDrawer.setOnClickListener {
            resetBottomLayoutTimer()
            showAppDrawer()
        }
        
        // Spotify button setup
        val btnOpenSpotify = findViewById<ImageButton>(R.id.btnOpenSpotify)
        btnOpenSpotify.setOnClickListener {
            // Show Spotify controls immediately when user clicks
            showSpotifyControls()
            
            // Lazy initialization - only create SpotifyManager when user interacts with controls
            if (spotifyManager == null) {
                spotifyManager = SpotifyManager(this, ::hideSpotifyControls)
            }
            val actionExecuted = spotifyManager!!.handleSpotifyAppClick()
            if (!actionExecuted) {
                Toast.makeText(this, "Setting up Spotify player", Toast.LENGTH_SHORT).show()
                
                // Set up 5-second timeout to open Spotify app directly
                spotifyTimeoutRunnable?.let { handler.removeCallbacks(it) }
                spotifyTimeoutRunnable = Runnable {
                    if (spotifyManager?.isPlayerReady != true) {
                        Toast.makeText(this, "Opening Spotify app directly", Toast.LENGTH_SHORT).show()
                        openSpotifyAppDirectly()
                        // Hide controls if connection failed after timeout
                        hideSpotifyControls()
                    }
                }
                handler.postDelayed(spotifyTimeoutRunnable!!, 50000) // 5 seconds
            } else {
                // Cancel timeout if action was executed successfully
                spotifyTimeoutRunnable?.let { handler.removeCallbacks(it) }
            }
        }
        
        // Clear button setup
        val btnClear = findViewById<ImageButton>(R.id.btnClear)
        btnClear.setOnClickListener {
            resetBottomLayoutTimer()
            notifications.clear()
            adapter.notifyDataSetChanged()
        }
        // Setup configurable bottom buttons
        setupBottomButtons()
    }

    override fun onStart() {
        super.onStart()
        // Spotify will connect only when user interacts with controls
    }

    override fun onResume() {
        super.onResume()
        verifySystemState()
        // Invalidate permission cache when returning from settings
        invalidatePermissionCache()
        // Check if date has changed while app was in background
        updateDateIfChanged()
        // Update bottom button configuration in case it changed
        setupBottomButtons()
    }


    override fun onStop() {
        super.onStop()
//        spotifyManager?.disconnect()
    }

    private fun verifySystemState() {

        val btnMute = findViewById<ImageButton>(R.id.btnMute)
        val btnRingVibrate = findViewById<ImageButton>(R.id.btnRingVibrate)

        // For vibrate mode
        val actualVibrate = audioManager.ringerMode == AudioManager.RINGER_MODE_VIBRATE
        if (isVibrateMode != actualVibrate) {
            isVibrateMode = actualVibrate
            sharedPrefs.edit().putBoolean(keyVibrateMode, actualVibrate).apply()
            btnRingVibrate.setImageResource(if (isVibrateMode) R.drawable.ic_vibrate else R.drawable.ic_ring)
        }

        // For mute state
        val actualMute = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
        if (isMuted != actualMute) {
            isMuted = actualMute
            sharedPrefs.edit().putBoolean(keyMuteState, actualMute).apply()
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
            // Send cancellation command to service through communication manager
            NotificationCommunicationManager.requestCancelNotification(notificationData.key)
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

                // Notify service to cancel the system notification through communication manager
                NotificationCommunicationManager.requestCancelNotification(notification.key)
                NotificationCommunicationManager.requestCancelSystemNotification(notification.systemKey)
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


    private fun getAppName(packageName: String): String {
        return try {
            val packageManager = applicationContext.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: Exception) {
            packageName // fallback if not found
        }
    }

    private fun openSpotifyAppDirectly() {
        try {
            val intent = packageManager.getLaunchIntentForPackage("com.spotify.music")
            if (intent != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "Spotify not installed", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening Spotify", Toast.LENGTH_SHORT).show()
        }
    }

    fun cancelSpotifyTimeout() {
        spotifyTimeoutRunnable?.let { 
            handler.removeCallbacks(it)
            spotifyTimeoutRunnable = null
        }
    }

    private fun showSpotifyControls() {
        val spotifyControls = findViewById<View>(R.id.spotify_controls)
        spotifyControls.visibility = View.VISIBLE
    }

    private fun hideSpotifyControls() {
        val spotifyControls = findViewById<View>(R.id.spotify_controls)
        spotifyControls.visibility = View.GONE
    }



    private fun setupCurrentDate() {
        updateDateIfChanged()
        scheduleNextMidnightUpdate()
    }
    
    private fun updateDateIfChanged() {
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("EEE, MMMM d", Locale.getDefault())
        val currentDate = dateFormat.format(calendar.time)
        
        // Only update UI if date actually changed
        if (currentDate != lastDisplayedDate) {
            val tvCurrentDate = findViewById<TextView>(R.id.tvCurrentDate)
            tvCurrentDate.text = currentDate
            lastDisplayedDate = currentDate
        }
    }
    
    private fun scheduleNextMidnightUpdate() {
        // Cancel any existing scheduled update
        dateUpdateRunnable?.let { handler.removeCallbacks(it) }
        
        // Calculate time until next midnight
        val now = Calendar.getInstance()
        val nextMidnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)  // Tomorrow
            set(Calendar.HOUR_OF_DAY, 0)   // 00:00
            set(Calendar.MINUTE, 0)        // 00:00
            set(Calendar.SECOND, 1)        // 00:01 (1 second after midnight)
            set(Calendar.MILLISECOND, 0)
        }
        
        val millisecondsUntilMidnight = nextMidnight.timeInMillis - now.timeInMillis
        
        // Create the update runnable
        dateUpdateRunnable = Runnable {
            updateDateIfChanged()           // Update the display
            scheduleNextMidnightUpdate()    // Schedule next day's update
        }
        
        // Schedule exactly at next midnight
        handler.postDelayed(dateUpdateRunnable!!, millisecondsUntilMidnight)
    }

    private fun setupDateDisplayCalendar() {
        val tvCurrentDate = findViewById<TextView>(R.id.tvCurrentDate)
        tvCurrentDate.setOnClickListener {
            // Use the same calendar permission flow as before
            checkCalendarPermissionsAndProceed()
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
            sharedPrefs.edit().putBoolean(keyVibrateMode, isVibrateMode).apply()
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
            sharedPrefs.edit().putBoolean(keyMuteState, isMuted).apply()
        }
    }

    // Permission cache management
    private fun invalidatePermissionCache() {
        postNotificationPermissionGranted = null
        exactAlarmPermissionGranted = null
    }

    // Calendar permission checking methods
    private fun needsPostNotificationPermission(): Boolean {
        if (postNotificationPermissionGranted == null) {
            postNotificationPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Not needed on older versions
            }
        }
        return postNotificationPermissionGranted == false
    }

    private fun needsExactAlarmPermission(): Boolean {
        if (exactAlarmPermissionGranted == null) {
            exactAlarmPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
                alarmManager.canScheduleExactAlarms()
            } else {
                true // Not needed on older versions
            }
        }
        return exactAlarmPermissionGranted == false
    }

    private fun checkCalendarPermissionsAndProceed() {
        if (needsPostNotificationPermission()) {
            requestPostNotificationPermission()
        } else {
            checkExactAlarmAndProceed()
        }
    }

    private fun checkExactAlarmAndProceed() {
        if (needsExactAlarmPermission()) {
            requestExactAlarmPermission()
        } else {
            proceedWithCalendarSetup()
        }
    }

    private fun requestPostNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    AlertDialog.Builder(this)
                        .setTitle("Calendar Notification Permission")
                        .setMessage("Calendar needs notification permission to remind you about upcoming events.")
                        .setPositiveButton("Grant Permission") { _, _ ->
                            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        .setNegativeButton("Cancel") { _, _ ->
                            showPermissionDeniedMessage()
                        }
                        .create()
                        .show()
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlertDialog.Builder(this)
                .setTitle("Calendar Reminder Permission")
                .setMessage("For precise calendar reminders, please grant exact alarm permission in the next screen.")
                .setPositiveButton("Go to Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    startActivity(intent)
                }
                .setNegativeButton("Continue without") { _, _ ->
                    showExactAlarmDeniedMessage()
                }
                .create()
                .show()
        }
    }

    private fun proceedWithCalendarSetup() {
        // Create notification channel when actually needed
        createCalendarNotificationChannel()
        
        // Lazy initialization - only create CalendarSetup when permissions are granted
        if (calendarSetup == null) {
            calendarSetup = SetupCalendar(this)
        }
        calendarSetup?.handleCalendarButtonClick()
    }

    private fun createCalendarNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Calendar Event Reminders"
            val descriptionText = "Notifications for upcoming calendar events"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("CALENDAR_REMINDERS", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showPermissionDeniedMessage() {
        Toast.makeText(this, 
            "Calendar features require notification permission for reminders", 
            Toast.LENGTH_LONG).show()
    }

    private fun showExactAlarmDeniedMessage() {
        Toast.makeText(this, 
            "Calendar reminders will work but may not be precisely timed", 
            Toast.LENGTH_LONG).show()
        // Still proceed with calendar but with limited functionality
        proceedWithCalendarSetup()
    }

    private fun setupSpotifyControls() {
        val btnPlayPause = findViewById<ImageButton>(R.id.btnPlayPause)
        val btnPrev = findViewById<ImageButton>(R.id.btnPrev)
        val btnNext = findViewById<ImageButton>(R.id.btnNext)
        val tvTrack = findViewById<TextView>(R.id.tvTrack)
        val tvArtist = findViewById<TextView>(R.id.tvArtist)
        val ivAlbum = findViewById<ImageView>(R.id.ivAlbum)


        btnPlayPause.setOnClickListener {
            if (spotifyManager != null) {
                val actionExecuted = spotifyManager!!.handlePlayPauseClick()
                if (!actionExecuted) {
                    Toast.makeText(this, "Connect Spotify first using the Spotify button", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Connect Spotify first using the Spotify button", Toast.LENGTH_SHORT).show()
            }
        }

        btnPrev.setOnClickListener {
            if (spotifyManager != null) {
                val actionExecuted = spotifyManager!!.handlePreviousClick()
                if (!actionExecuted) {
                    Toast.makeText(this, "Connect Spotify first using the Spotify button", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Connect Spotify first using the Spotify button", Toast.LENGTH_SHORT).show()
            }
        }

        btnNext.setOnClickListener {
            if (spotifyManager != null) {
                val actionExecuted = spotifyManager!!.handleNextClick()
                if (!actionExecuted) {
                    Toast.makeText(this, "Connect Spotify first using the Spotify button", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Connect Spotify first using the Spotify button", Toast.LENGTH_SHORT).show()
            }
        }

        // Setup click listeners for album art/text to open Spotify (only when connected)
        val clickableViews = listOf(ivAlbum, tvTrack, tvArtist)
        clickableViews.forEach { view ->
            view.setOnClickListener {
                if (spotifyManager != null) {
                    val actionExecuted = spotifyManager!!.handleSpotifyAppClick()
                    if (!actionExecuted) {
                        Toast.makeText(this, "Connect Spotify first using the Spotify button", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Connect Spotify first using the Spotify button", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun applyTheme() {
        val isDarkMode = sharedPrefs.getBoolean(keyDarkMode, false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES 
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun toggleDarkMode() {
        val isDarkMode = sharedPrefs.getBoolean(keyDarkMode, false)
        val newMode = !isDarkMode
        
        sharedPrefs.edit().putBoolean(keyDarkMode, newMode).apply()
        
        AppCompatDelegate.setDefaultNightMode(
            if (newMode) AppCompatDelegate.MODE_NIGHT_YES 
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        
        Toast.makeText(this, 
            if (newMode) "Dark mode enabled" else "Light mode enabled", 
            Toast.LENGTH_SHORT).show()
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        
        // Create the main settings dialog with explicit theme context
        val dialog = AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(dialogView)
            .setNegativeButton("Close", null)
            .create()
        
        // Dark mode toggle
        val btnToggleDarkMode = dialogView.findViewById<ImageButton>(R.id.btnToggleDarkMode)
        fun updateDarkModeIcon() {
            val isDarkMode = sharedPrefs.getBoolean(keyDarkMode, false)
            btnToggleDarkMode.setImageResource(
                if (isDarkMode) R.drawable.ic_light_mode else R.drawable.ic_dark_mode
            )
        }
        updateDarkModeIcon()
        
        btnToggleDarkMode.setOnClickListener {
            // Close dialog before toggling dark mode to prevent window leak
            dialog.dismiss()
            toggleDarkMode()
            // Reopen settings dialog after a longer delay to allow activity recreation and theme application
            Handler(Looper.getMainLooper()).postDelayed({
                // Ensure the new activity instance shows the dialog with updated theme
                showSettingsDialog()
            }, 300)
        }
        
        // App selection button
        val btnSelectApps = dialogView.findViewById<android.widget.Button>(R.id.btnSelectApps)
        btnSelectApps.setOnClickListener {
            showAppSelectionDialog()
        }
        
        // Bottom button configuration
        val btnConfigureButton1 = dialogView.findViewById<android.widget.Button>(R.id.btnConfigureButton1)
        val btnConfigureButton2 = dialogView.findViewById<android.widget.Button>(R.id.btnConfigureButton2)
        val btnConfigureButton3 = dialogView.findViewById<android.widget.Button>(R.id.btnConfigureButton3)
        
        btnConfigureButton1.setOnClickListener {
            showBottomButtonSelectionDialog(keyBottomButton1, "Left Button") { updateBottomButtonsLayout() }
        }
        btnConfigureButton2.setOnClickListener {
            showBottomButtonSelectionDialog(keyBottomButton2, "Middle Button") { updateBottomButtonsLayout() }
        }
        btnConfigureButton3.setOnClickListener {
            showBottomButtonSelectionDialog(keyBottomButton3, "Right Button") { updateBottomButtonsLayout() }
        }
        
        dialog.show()
    }
    
    private fun showAppSelectionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_app_selection, null)
        
        // Get all installed apps
        val packageManager = packageManager
        val currentPackageName = packageName
        val installedApps = packageManager.getInstalledApplications(0)
            .filter { appInfo ->
                // Only show apps that have a launch intent (launchable apps) and exclude this app
                appInfo.packageName != currentPackageName && 
                packageManager.getLaunchIntentForPackage(appInfo.packageName) != null
            }
            .map { appInfo ->
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                val appPackageName = appInfo.packageName
                Triple(appPackageName, appName, appInfo.loadIcon(packageManager))
            }
            .sortedBy { it.second.lowercase() } // Sort by app name
        
        // Add this app at the top
        val thisAppInfo = packageManager.getApplicationInfo(currentPackageName, 0)
        val thisAppName = packageManager.getApplicationLabel(thisAppInfo).toString()
        val thisAppIcon = thisAppInfo.loadIcon(packageManager)
        val allApps = listOf(Triple(currentPackageName, "$thisAppName (This App)", thisAppIcon)) + installedApps
        
        // Get current selected apps
        val selectedApps = getSelectedNotificationApps()
        
        // Setup RecyclerView for app selection
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.appsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        val appAdapter = AppSelectionAdapter(allApps, selectedApps) { packageName: String, isChecked: Boolean ->
            // Handle checkbox change
        }
        recyclerView.adapter = appAdapter
        
        // Setup search functionality
        val searchEditText = dialogView.findViewById<android.widget.EditText>(R.id.searchEditText)
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s?.toString() ?: ""
                appAdapter.filter(query)
            }
        })
        
        // Create and show the app selection dialog
        AlertDialog.Builder(this)
            .setTitle("Select Apps for Notifications")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                // Save selected apps
                val newSelectedApps = appAdapter.getSelectedApps()
                android.util.Log.d("MainActivity", "Saving ${newSelectedApps.size} selected apps: $newSelectedApps")
                saveSelectedNotificationApps(newSelectedApps)
                
                // Verify saved apps
                val savedApps = getSelectedNotificationApps()
                android.util.Log.d("MainActivity", "Verified saved apps: $savedApps")
                
                Toast.makeText(this, "Notification apps updated (${newSelectedApps.size} apps)", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getSelectedNotificationApps(): Set<String> {
        val defaultApps = setOf("com.whatsapp", "com.mudita.messages", "com.mudita.calendar", "com.example.notifier")
        return sharedPrefs.getStringSet("selected_notification_apps", defaultApps) ?: defaultApps
    }
    
    private fun saveSelectedNotificationApps(apps: Set<String>) {
        sharedPrefs.edit().putStringSet("selected_notification_apps", apps).apply()
    }
    
    // Bottom button configuration methods
    private fun getBottomButtonPackage(buttonKey: String, defaultPackage: String): String {
        return sharedPrefs.getString(buttonKey, defaultPackage) ?: defaultPackage
    }
    
    private fun saveBottomButtonPackage(buttonKey: String, packageName: String) {
        sharedPrefs.edit().putString(buttonKey, packageName).apply()
    }
    
    private fun getConfiguredBottomButtons(): Triple<String, String, String> {
        return Triple(
            getBottomButtonPackage(keyBottomButton1, "com.google.android.apps.mapslite"), // Maps
            getBottomButtonPackage(keyBottomButton2, "com.anthropic.claude"), // Assistant
            getBottomButtonPackage(keyBottomButton3, "com.whatsapp") // WhatsApp
        )
    }
    
    private fun showBottomButtonSelectionDialog(buttonKey: String, buttonName: String, onSelectionComplete: () -> Unit) {
        // Get all installed apps (reuse the same logic as app drawer)
        val packageManager = packageManager
        val installedApps = packageManager.getInstalledApplications(0)
            .filter { appInfo ->
                // Only show apps that have a launch intent (launchable apps)
                packageManager.getLaunchIntentForPackage(appInfo.packageName) != null
            }
            .map { appInfo ->
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                val appPackageName = appInfo.packageName
                Triple(appPackageName, appName, appInfo.loadIcon(packageManager))
            }
            .sortedBy { it.second.lowercase() }
        
        // Create selection dialog
        val appNames = installedApps.map { it.second }.toTypedArray()
        val appPackages = installedApps.map { it.first }.toTypedArray()
        
        val currentPackage = getBottomButtonPackage(buttonKey, "")
        val currentIndex = appPackages.indexOfFirst { it == currentPackage }
        
        AlertDialog.Builder(this)
            .setTitle("Select app for $buttonName")
            .setSingleChoiceItems(appNames, currentIndex) { dialog, which ->
                val selectedPackage = appPackages[which]
                saveBottomButtonPackage(buttonKey, selectedPackage)
                onSelectionComplete()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun updateBottomButtonsLayout() {
        val (package1, package2, package3) = getConfiguredBottomButtons()
        val packageManager = packageManager
        
        // Update button 1 (Maps/Left)
        try {
            val btnMaps = findViewById<ImageButton>(R.id.btnMaps)
            val appInfo1 = packageManager.getApplicationInfo(package1, 0)
            btnMaps.setImageDrawable(appInfo1.loadIcon(packageManager))
        } catch (e: Exception) {
            // Keep default icon if app not found
        }
        
        // Update button 2 (Assistant/Middle)
        try {
            val btnAssistant = findViewById<ImageButton>(R.id.btnAssistant)
            val appInfo2 = packageManager.getApplicationInfo(package2, 0)
            btnAssistant.setImageDrawable(appInfo2.loadIcon(packageManager))
        } catch (e: Exception) {
            // Keep default icon if app not found
        }
        
        // Update button 3 (WhatsApp/Right)
        try {
            val btnWhatsApp = findViewById<ImageButton>(R.id.btnWhatsApp)
            val appInfo3 = packageManager.getApplicationInfo(package3, 0)
            btnWhatsApp.setImageDrawable(appInfo3.loadIcon(packageManager))
        } catch (e: Exception) {
            // Keep default icon if app not found
        }
    }
    
    private fun setupBottomButtons() {
        val (package1, package2, package3) = getConfiguredBottomButtons()
        
        // Button 1 (Maps/Left)
        val btnMaps = findViewById<ImageButton>(R.id.btnMaps)
        btnMaps.setOnClickListener {
            resetBottomLayoutTimer()
            launchConfiguredApp(package1)
        }
        btnMaps.setOnLongClickListener {
            resetBottomLayoutTimer()
            showBottomButtonSelectionDialog(keyBottomButton1, "Left Button") { updateBottomButtonsLayout() }
            true
        }
        
        // Button 2 (Assistant/Middle)
        val btnAssistant = findViewById<ImageButton>(R.id.btnAssistant)
        btnAssistant.setOnClickListener {
            resetBottomLayoutTimer()
            launchConfiguredApp(package2)
        }
        btnAssistant.setOnLongClickListener {
            resetBottomLayoutTimer()
            showBottomButtonSelectionDialog(keyBottomButton2, "Middle Button") { updateBottomButtonsLayout() }
            true
        }
        
        // Button 3 (WhatsApp/Right)
        val btnWhatsApp = findViewById<ImageButton>(R.id.btnWhatsApp)
        btnWhatsApp.setOnClickListener {
            resetBottomLayoutTimer()
            launchConfiguredApp(package3)
        }
        btnWhatsApp.setOnLongClickListener {
            resetBottomLayoutTimer()
            showBottomButtonSelectionDialog(keyBottomButton3, "Right Button") { updateBottomButtonsLayout() }
            true
        }
        
        // Update icons to match configured apps
        updateBottomButtonsLayout()
    }
    
    private fun launchConfiguredApp(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                startActivity(intent)
            } else {
                val appName = try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    "App"
                }
                Toast.makeText(this, "$appName not found or cannot be launched", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error launching app", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupAppDrawer() {
        appDrawerLayout = findViewById(R.id.appDrawerFullscreen)
        
        // Get all installed apps
        val packageManager = packageManager
        val installedApps = packageManager.getInstalledApplications(0)
            .filter { appInfo ->
                // Only show apps that have a launch intent (launchable apps)
                packageManager.getLaunchIntentForPackage(appInfo.packageName) != null
            }
            .map { appInfo ->
                val appName = packageManager.getApplicationLabel(appInfo).toString()
                val appPackageName = appInfo.packageName
                Triple(appPackageName, appName, appInfo.loadIcon(packageManager))
            }
            .sortedBy { it.second.lowercase() } // Sort by app name
        
        // Setup RecyclerView for app list
        val recyclerView = appDrawerLayout.findViewById<RecyclerView>(R.id.appsDrawerRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        appDrawerAdapter = AppDrawerAdapter(installedApps) { packageName ->
            // Launch the selected app and close drawer
            launchApp(packageName)
            hideAppDrawer()
        }
        recyclerView.adapter = appDrawerAdapter
        
        // Setup search functionality
        val searchEditText = appDrawerLayout.findViewById<android.widget.EditText>(R.id.searchAppsEditText)
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val query = s?.toString() ?: ""
                appDrawerAdapter?.filter(query)
            }
        })
        
        // Setup settings button in app drawer
        val btnSettingsInDrawer = appDrawerLayout.findViewById<ImageButton>(R.id.btnSettingsInDrawer)
        btnSettingsInDrawer?.setOnClickListener {
            showSettingsDialog()
        }
    }
    
    private fun showAppDrawer() {
        // Hide bottom button layout when app drawer is shown
        hideBottomLayout()
        
        appDrawerLayout.visibility = View.VISIBLE
        appDrawerLayout.alpha = 0f
        appDrawerLayout.animate()
            .alpha(1f)
            .setDuration(200)
            .setListener(null)
            .start()
    }
    
    private fun hideAppDrawer() {
        appDrawerLayout.animate()
            .alpha(0f)
            .setDuration(200)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    appDrawerLayout.visibility = View.GONE
                }
            })
            .start()
    }
    
    private fun setupUpcomingEvents() {
        upcomingEventsLayout = findViewById(R.id.upcoming_events_layout)
        eventsContainer = findViewById(R.id.events_container)
        
        // Start periodic updates for upcoming events
        startUpcomingEventsUpdates()
    }
    
    private fun startUpcomingEventsUpdates() {
        stopUpcomingEventsUpdates() // Stop any existing updates
        
        upcomingEventsUpdateRunnable = Runnable {
            updateUpcomingEvents()
            // Schedule next update
            handler.postDelayed(upcomingEventsUpdateRunnable!!, UPCOMING_EVENTS_UPDATE_INTERVAL)
        }
        
        // Initial update
        updateUpcomingEvents()
        // Schedule periodic updates
        handler.postDelayed(upcomingEventsUpdateRunnable!!, UPCOMING_EVENTS_UPDATE_INTERVAL)
    }
    
    private fun stopUpcomingEventsUpdates() {
        upcomingEventsUpdateRunnable?.let { handler.removeCallbacks(it) }
        upcomingEventsUpdateRunnable = null
    }
    
    private fun updateUpcomingEvents() {
        // Get upcoming events from calendar cache
        val upcomingEvents = getUpcomingEventsFromCache()
        
        if (upcomingEvents.isNotEmpty()) {
            displayUpcomingEvents(upcomingEvents)
            upcomingEventsLayout.visibility = View.VISIBLE
        } else {
            upcomingEventsLayout.visibility = View.GONE
        }
    }
    
    // Public method to refresh upcoming events (called when calendar cache is updated)
    fun refreshUpcomingEvents() {
        updateUpcomingEvents()
    }
    
    private fun getUpcomingEventsFromCache(): List<Pair<String, String>> {
        try {
            val cachedEvents = calendarCacheManager.getCachedEvents() ?: return emptyList()
            val now = System.currentTimeMillis()
            val thirtyMinutesFromNow = now + (30 * 60 * 1000)
            val fiveMinutesAgo = now - (5 * 60 * 1000) // 5 minutes before current time
            
            val upcomingEvents = cachedEvents.filter { event ->
                try {
                    val eventTime = parseEventTime(event.startTime)
                    // Show events that start within next 30 minutes OR started within last 5 minutes
                    eventTime in fiveMinutesAgo..thirtyMinutesFromNow
                } catch (e: Exception) {
                    false
                }
            }.map { event ->
                val timeStr = formatEventTime(event.startTime)
                Pair(timeStr, event.summary)
            }
            
            return upcomingEvents
        } catch (e: Exception) {
            // Handle any calendar access errors gracefully
            return emptyList()
        }
    }
    
    private fun parseEventTime(startTime: String): Long {
        return try {
            if (startTime.contains("T")) {
                // DateTime format: 2024-06-22T14:30:00-07:00
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                sdf.parse(startTime)?.time ?: 0L
            } else {
                // Date format: 2024-06-22 (all-day event)
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val calendar = Calendar.getInstance()
                calendar.time = sdf.parse(startTime) ?: java.util.Date()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.timeInMillis
            }
        } catch (e: Exception) {
            0L
        }
    }
    
    private fun formatEventTime(startTime: String): String {
        return try {
            if (startTime.contains("T")) {
                // DateTime format: 2024-06-22T14:30:00-07:00
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                val time = sdf.parse(startTime)
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                timeFormat.format(time ?: java.util.Date())
            } else {
                // All-day event
                "All Day"
            }
        } catch (e: Exception) {
            "Time"
        }
    }
    
    private fun displayUpcomingEvents(events: List<Pair<String, String>>) {
        // Clear existing event views
        eventsContainer.removeAllViews()
        
        events.forEach { (time, title) ->
            val eventView = layoutInflater.inflate(R.layout.item_upcoming_event, eventsContainer, false)
            val tvEventTime = eventView.findViewById<TextView>(R.id.tvEventTime)
            val tvEventTitle = eventView.findViewById<TextView>(R.id.tvEventTitle)
            
            tvEventTime.text = time
            tvEventTitle.text = title
            
            eventsContainer.addView(eventView)
        }
    }
    
    private fun launchApp(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "Cannot launch this app", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error launching app", Toast.LENGTH_SHORT).show()
        }
    }


    private fun setupBottomLayoutGesture() {
        bottomButtonLayout = findViewById(R.id.bottomButtonLayout)
        
        // Create gesture detector for swipe up detection
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 != null) {
                    val deltaY = e1.y - e2.y
                    val deltaX = abs(e1.x - e2.x)
                    
                    // Check if it's a swipe up gesture
                    if (deltaY > 50 && deltaX < 200 && abs(velocityY) > 300) {
                        showBottomLayout()
                        return true
                    }
                }
                return false
            }
            
            override fun onDown(e: MotionEvent): Boolean {
                return true
            }
        })
    }
    
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // Only process swipe gestures if app drawer is not visible
        if (::gestureDetector.isInitialized && 
            !(::appDrawerLayout.isInitialized && appDrawerLayout.visibility == View.VISIBLE)) {
            gestureDetector.onTouchEvent(ev)
        }
        
        // Always pass the event to the super class for normal processing
        return super.dispatchTouchEvent(ev)
    }
    
    private fun showBottomLayout() {
        if (bottomButtonLayout.visibility != View.VISIBLE) {
            bottomButtonLayout.visibility = View.VISIBLE
            bottomButtonLayout.alpha = 0f
            bottomButtonLayout.translationY = bottomButtonLayout.height.toFloat()
            
            bottomButtonLayout.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setListener(null)
                .start()
        }
        
        // Reset the auto-hide timer
        scheduleBottomLayoutHide()
    }
    
    private fun hideBottomLayout() {
        if (bottomButtonLayout.visibility == View.VISIBLE) {
            bottomButtonLayout.animate()
                .alpha(0f)
                .translationY(bottomButtonLayout.height.toFloat())
                .setDuration(300)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        bottomButtonLayout.visibility = View.GONE
                    }
                })
                .start()
        }
    }
    
    private fun scheduleBottomLayoutHide() {
        // Cancel any existing hide timer
        hideBottomLayoutRunnable?.let { handler.removeCallbacks(it) }
        
        // Schedule new hide timer
        hideBottomLayoutRunnable = Runnable {
            hideBottomLayout()
        }
        handler.postDelayed(hideBottomLayoutRunnable!!, BOTTOM_LAYOUT_HIDE_DELAY)
    }
    
    private fun resetBottomLayoutTimer() {
        // Reset the auto-hide timer when user interacts with bottom buttons
        if (bottomButtonLayout.visibility == View.VISIBLE) {
            scheduleBottomLayoutHide()
        }
    }
    
    override fun onDestroy() {
        // Clean up timers
        hideBottomLayoutRunnable?.let { handler.removeCallbacks(it) }
        dateUpdateRunnable?.let { handler.removeCallbacks(it) }
        spotifyTimeoutRunnable?.let { handler.removeCallbacks(it) }
        stopUpcomingEventsUpdates()
        
        // Cleanup calendar
        calendarSetup?.cleanup()
        
        // Disconnect Spotify
        spotifyManager?.disconnect()
        
        // Unregister from communication manager
        NotificationCommunicationManager.unregisterActivityCallback()
        
        super.onDestroy()
    }
    
    override fun onBackPressed() {
        // If app drawer is visible, close it instead of exiting the app
        if (::appDrawerLayout.isInitialized && appDrawerLayout.visibility == View.VISIBLE) {
            hideAppDrawer()
        } else {
            super.onBackPressed()
        }
    }

}
