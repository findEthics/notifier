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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.notifier.Calendar.SetupCalendar
import com.example.notifier.Calendar.CalendarCacheManager
import com.example.notifier.Calendar.CalendarEvent
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
    private var calendarSetup: SetupCalendar? = null
    private var spotifyManager: SpotifyManager? = null
    
    // Calendar cache manager
    private lateinit var calendarCacheManager: CalendarCacheManager
    
    // Upcoming events handling
    private lateinit var upcomingEventsLayout: LinearLayout
    private lateinit var eventsContainer: LinearLayout
    private var upcomingEventsUpdateRunnable: Runnable? = null
    // Optimized for e-ink displays - longer intervals save battery
    private val UPCOMING_EVENTS_UPDATE_INTERVAL = 5 * 60 * 1000L // 5 minutes (was 1 minute)
    
    // E-ink display optimization
    private var isEinkOptimized = true // Assume e-ink for battery optimization
    private var lastEventsUpdate = 0L
    private val EINK_UPDATE_THROTTLE = 8 * 1000L // 8 seconds minimum between UI updates
    
    // Permission state caching
    private var postNotificationPermissionGranted: Boolean? = null
    private var exactAlarmPermissionGranted: Boolean? = null
    
    // Spotify timeout handling
    private val handler = Handler(Looper.getMainLooper())
    private var spotifyTimeoutRunnable: Runnable? = null
    
    // Date update handling
    private var dateUpdateRunnable: Runnable? = null
    private var lastDisplayedDate: String? = null

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
        setContentView(R.layout.activity_main)

        // Spotify will be setup lazily when user interacts with controls
        // Calendar permissions will be checked when calendar button is clicked

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        // Initialize SharedPreferences HERE
        sharedPrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        calendarCacheManager = CalendarCacheManager(this)

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
        // Setup action button listenerscommit the
        setupActionButtonListeners()

        if (!isNotificationServiceEnabled()) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        setupRecyclerView()
        setupSwipeToDelete()
        
        // Setup upcoming events
        setupUpcomingEvents()

        val filter = IntentFilter().apply {
            addAction("NEW_NOTIFICATION")
            addAction("REMOVE_NOTIFICATION")
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter)

    }

    private fun setupActionButtonListeners() {
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
                
                // Set up 10-second timeout to open Spotify app directly
                spotifyTimeoutRunnable?.let { handler.removeCallbacks(it) }
                spotifyTimeoutRunnable = Runnable {
                    if (spotifyManager?.isPlayerReady != true) {
                        Toast.makeText(this, "Opening Spotify app directly", Toast.LENGTH_SHORT).show()
                        openSpotifyAppDirectly()
                        // Hide controls if connection failed after timeout
                        hideSpotifyControls()
                    }
                }
                handler.postDelayed(spotifyTimeoutRunnable!!, 50000) // 10 seconds
            } else {
                // Cancel timeout if action was executed successfully
                spotifyTimeoutRunnable?.let { handler.removeCallbacks(it) }
            }
        }
        
        // Clear button setup
        val btnClear = findViewById<ImageButton>(R.id.btnClear)
        btnClear.setOnClickListener {
            notifications.clear()
            adapter.notifyDataSetChanged()
        }
        //WhatsApp button setup
        val openWhatsApp = findViewById<ImageButton>(R.id.btnWhatsApp)
        openWhatsApp.setOnClickListener {
            // Open WhatsApp
            try {
                val intent = packageManager.getLaunchIntentForPackage("com.whatsapp")
                if (intent == null) {
                    Toast.makeText(this, "WhatsApp not found", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "WhatsApp not found", Toast.LENGTH_SHORT).show()
            }
        }
        //Assistant button setup
        val openAssistant = findViewById<ImageButton>(R.id.btnAssistant)
        openAssistant.setOnClickListener {
            // Open Claude Assistant
            try {
                val claudeIntent = packageManager.getLaunchIntentForPackage("com.anthropic.claude")
                if (claudeIntent != null) {
                    startActivity(claudeIntent)
                } else {
                    Toast.makeText(this, "No assistant or browser app found", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error opening assistant", Toast.LENGTH_SHORT).show()
            }
        }
        //Maps button setup
        val openMaps = findViewById<ImageButton>(R.id.btnMaps)
        openMaps.setOnClickListener {
            try {
                val intentGmapsWV = packageManager.getLaunchIntentForPackage("com.google.android.apps.mapslite")
                if (intentGmapsWV != null) {
                    startActivity(intentGmapsWV)
                }
                    else {
                        Toast.makeText(this, "No Map apps or browser found", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: PackageManager.NameNotFoundException) {
                Toast.makeText(this, "No Map app found", Toast.LENGTH_SHORT).show()
            }
        }
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
        // Restart upcoming events updates if they were stopped
        if (upcomingEventsUpdateRunnable == null) {
            startUpcomingEventsUpdates()
        }
        // Spotify will connect when user interacts with controls
    }


    override fun onPause() {
        super.onPause()
        // Stop background updates to save battery when app not visible
        stopUpcomingEventsUpdates()
        // Clean up Spotify timeout to prevent background wake-ups
        spotifyTimeoutRunnable?.let { handler.removeCallbacks(it) }
        // Clean up date update handler to prevent background wake-ups
        dateUpdateRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onStop() {
        super.onStop()
//        spotifyManager?.disconnect()
    }

    // Battery optimization: Cache system state verification
    private var lastVerificationTime = 0L
    private val VERIFICATION_THROTTLE = 30000L // 30 seconds
    
    private fun verifySystemState() {
        // Throttle verification to save battery on frequent resumes
        val now = System.currentTimeMillis()
        if (now - lastVerificationTime < VERIFICATION_THROTTLE) return
        lastVerificationTime = now

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
        calendarSetup?.cleanup()
        spotifyManager?.disconnect()
        // Clean up Spotify timeout
        spotifyTimeoutRunnable?.let { handler.removeCallbacks(it) }
        // Clean up date update handler
        dateUpdateRunnable?.let { handler.removeCallbacks(it) }
        // Clean up upcoming events updates
        stopUpcomingEventsUpdates()
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
        // Remove automatic midnight scheduling to save battery
        // Date will update on app resume instead
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
    
    // Removed scheduleNextMidnightUpdate() to save battery
    // Date updates now happen only on app resume for e-ink optimization

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
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
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
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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

    private fun setupUpcomingEvents() {
        upcomingEventsLayout = findViewById(R.id.upcoming_events_layout)
        eventsContainer = findViewById(R.id.events_container)
        startUpcomingEventsUpdates()
    }
    
    private fun startUpcomingEventsUpdates() {
        upcomingEventsUpdateRunnable = Runnable {
            updateUpcomingEvents()
            // Only reschedule if still visible to user (not paused)
            if (upcomingEventsUpdateRunnable != null) {
                handler.postDelayed(upcomingEventsUpdateRunnable!!, UPCOMING_EVENTS_UPDATE_INTERVAL)
            }
        }
        handler.post(upcomingEventsUpdateRunnable!!)
    }
    
    private fun stopUpcomingEventsUpdates() {
        upcomingEventsUpdateRunnable?.let { handler.removeCallbacks(it) }
        upcomingEventsUpdateRunnable = null
    }
    
    private fun updateUpcomingEvents() {
        // E-ink optimization: throttle UI updates to save battery and screen life
        if (isEinkOptimized) {
            val now = System.currentTimeMillis()
            if (now - lastEventsUpdate < EINK_UPDATE_THROTTLE) {
                return // Skip update if too recent
            }
            lastEventsUpdate = now
        }
        
        val upcomingEvents = getUpcomingEventsFromCache()
        displayUpcomingEvents(upcomingEvents)
    }
    
    private fun getUpcomingEventsFromCache(): List<CalendarEvent> {
        val cachedEvents = calendarCacheManager.getCachedEvents()
        if (cachedEvents == null) {
            return emptyList()
        }
        
        val now = System.currentTimeMillis()
        val thirtyMinutesFromNow = now + (30 * 60 * 1000) // 30 minutes in milliseconds
        
        return cachedEvents.filter { event ->
            val eventStartTime = parseEventTime(event.startTime)
            val fiveMinutesAfterStart = eventStartTime + (5 * 60 * 1000) // 5 minutes after start
            
            // Show events that start within 30 minutes or are currently happening (up to 5 minutes after start)
            (eventStartTime <= thirtyMinutesFromNow && eventStartTime >= now) || 
            (eventStartTime <= now && now <= fiveMinutesAfterStart)
        }.sortedBy { parseEventTime(it.startTime) }
    }
    
    private fun parseEventTime(timeString: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            format.parse(timeString)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
    
    private fun formatEventTime(timeString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val date = inputFormat.parse(timeString)
            outputFormat.format(date ?: Date())
        } catch (_: Exception) {
            timeString
        }
    }
    
    private fun displayUpcomingEvents(events: List<CalendarEvent>) {
        eventsContainer.removeAllViews()
        
        if (events.isEmpty()) {
            upcomingEventsLayout.visibility = View.GONE
            return
        }
        
        upcomingEventsLayout.visibility = View.VISIBLE
        
        events.forEach { event ->
            val eventView = layoutInflater.inflate(R.layout.item_upcoming_event, eventsContainer, false)
            
            val tvEventTime = eventView.findViewById<TextView>(R.id.tvEventTime)
            val tvEventTitle = eventView.findViewById<TextView>(R.id.tvEventTitle)
            
            tvEventTime.text = formatEventTime(event.startTime)
            tvEventTitle.text = event.summary
            
            eventsContainer.addView(eventView)
        }
    }
    
    fun refreshUpcomingEvents() {
        // Force update regardless of throttling (user-initiated)
        lastEventsUpdate = 0L
        updateUpcomingEvents()
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

}
