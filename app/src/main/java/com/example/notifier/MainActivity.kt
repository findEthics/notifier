package com.example.notifier

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.Track

import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse

class MainActivity : AppCompatActivity() {
    private val notifications = mutableListOf<NotificationData>()
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NotificationAdapter
    private val CLIENT_ID = "b5954f6b7e1f44b68a9c170550ce3d10"
    private val REDIRECT_URI = "notifier://callback"
    private var spotifyAppRemote: SpotifyAppRemote? = null
    private val AUTH_TOKEN_REQUEST_CODE = 0x10
    private var isMuted = false
    private var previousVolume = 0
    private var previousRingerVolume = 0
    private var isVibrateMode = false
    private lateinit var audioManager: AudioManager
    private lateinit var sharedPrefs: SharedPreferences
    private val PREFS_NAME = "AppSettings"
    private val KEY_VIBRATE_MODE = "vibrate_mode"
    private val KEY_MUTE_STATE = "mute_state"

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "NEW_NOTIFICATION" -> {
                    val key = intent.getStringExtra("key") ?: return
                    val title = intent.getStringExtra("title") ?: ""
                    val text = intent.getStringExtra("text") ?: ""
                    val packageName = intent.getStringExtra("package") ?: ""
//                    val isGroupSummary = intent.getBooleanExtra("isGroupSummary", false)
                    val appName = getAppName(packageName)

                    notifications.removeAll { it.key == key } // Prevent duplicates

                    // Add new notification at top
                    notifications.add(0, NotificationData(
                        title = title,
                        text = text,
                        packageName = packageName,
                        appName = appName,
                        key = key,
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // Initialize SharedPreferences HERE
        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Restore saved states
//        isVibrateMode = sharedPrefs.getBoolean(KEY_VIBRATE_MODE, audioManager.ringerMode == AudioManager.RINGER_MODE_VIBRATE)
        isVibrateMode = AudioManager.RINGER_MODE_VIBRATE == 1
        isMuted = sharedPrefs.getBoolean(KEY_MUTE_STATE, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0)

        setupSpotifyControls()

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
        setupSwipeToDelete()

        val filter = IntentFilter().apply {
            addAction("NEW_NOTIFICATION")
            addAction("REMOVE_NOTIFICATION")
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, filter)

    }

    override fun onStart() {
        super.onStart()
        startSpotifyAuth()
        val connectionParams = ConnectionParams.Builder(CLIENT_ID)
            .setRedirectUri(REDIRECT_URI)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(this, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                setupSpotifyControls()
            }
            override fun onFailure(throwable: Throwable) {
                throwable.printStackTrace()
                Log.e("Spotify", "Connection failed", throwable)
            }
        })
    }

    override fun onStop() {
        super.onStop()
        spotifyAppRemote?.let {
            SpotifyAppRemote.disconnect(it)
            spotifyAppRemote = null
        }
    }

    // Setup Spotify, Volume and Ring Controls
    private fun setupSpotifyControls() {
        val btnPlayPause = findViewById<ImageButton>(R.id.btnPlayPause)
        val btnPrev = findViewById<ImageButton>(R.id.btnPrev)
        val btnNext = findViewById<ImageButton>(R.id.btnNext)
        val tvTrack = findViewById<TextView>(R.id.tvTrack)
        val tvArtist = findViewById<TextView>(R.id.tvArtist)
        val ivAlbum = findViewById<ImageView>(R.id.ivAlbum)

        val btnMute = findViewById<ImageButton>(R.id.btnMute)
        val btnRingVibrate = findViewById<ImageButton>(R.id.btnRingVibrate)

        // Set Initial state of mute and Vibrate Buttons
        fun updateRingVibrateButton() {
            val imageRes = if (isVibrateMode) R.drawable.ic_vibrate else R.drawable.ic_ring
            btnRingVibrate.setImageResource(imageRes)
        }
        updateRingVibrateButton()

        fun updateMuteButton() {
            btnMute.setImageResource(
                if (isMuted) R.drawable.ic_mute
                else R.drawable.ic_unmute
            )
        }
        updateMuteButton()

        btnRingVibrate.setOnClickListener {
            if (isVibrateMode) {
                // Switch back to normal ring mode
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                audioManager.setStreamVolume(
                    AudioManager.STREAM_RING,
                    if (previousRingerVolume == 0) 3 else previousRingerVolume,
                    AudioManager.FLAG_PLAY_SOUND
                )
            } else {
                // Switch to vibrate mode
                previousRingerVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            }
            isVibrateMode = !isVibrateMode
            updateRingVibrateButton()

            sharedPrefs.edit().putBoolean(KEY_VIBRATE_MODE, isVibrateMode).apply() // Add persistence
        }

        btnMute.setOnClickListener {
            if (!isMuted) {
                // Save current volume and mute
                previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            } else {
                // Restore previous volume
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    if (previousVolume == 0) 3 else previousVolume,
                    0)
            }
            isMuted = !isMuted
            updateMuteButton()
            sharedPrefs.edit().putBoolean(KEY_MUTE_STATE, isMuted).apply() // Add persistence
        }


        // 1. Set button listeners ONCE
        btnPlayPause.setOnClickListener {
            spotifyAppRemote?.playerApi?.playerState?.setResultCallback { playerState ->
                if (playerState.isPaused) {
                    spotifyAppRemote?.playerApi?.resume()
                } else {
                    spotifyAppRemote?.playerApi?.pause()
                }
            }
        }

        btnPrev.setOnClickListener {
            spotifyAppRemote?.playerApi?.skipPrevious()
        }

        btnNext.setOnClickListener {
            spotifyAppRemote?.playerApi?.skipNext()
        }

        // 2. Subscribe to player state for UI updates
        spotifyAppRemote?.playerApi?.subscribeToPlayerState()?.setEventCallback { playerState: PlayerState ->
            val track: Track? = playerState.track
            if (track != null) {
                tvTrack.text = track.name
                tvArtist.text = track.artist.name
                spotifyAppRemote?.imagesApi?.getImage(track.imageUri)?.setResultCallback {
                    ivAlbum.setImageBitmap(it)
                }
            }

            // Update play/pause button icon
            if (playerState.isPaused) {
                btnPlayPause.setImageResource(R.drawable.ic_play)
            } else {
                btnPlayPause.setImageResource(R.drawable.ic_pause)
            }
        }
    }

    private fun verifySystemState() {

        val btnMute = findViewById<ImageButton>(R.id.btnMute)
        val btnRingVibrate = findViewById<ImageButton>(R.id.btnRingVibrate)

        fun updateRingVibrateButton() {
            val imageRes = if (isVibrateMode) R.drawable.ic_vibrate else R.drawable.ic_ring
            btnRingVibrate.setImageResource(imageRes)
        }

        // For vibrate mode
        val actualVibrate = audioManager.ringerMode == AudioManager.RINGER_MODE_VIBRATE
        if (isVibrateMode != actualVibrate) {
            isVibrateMode = actualVibrate
            sharedPrefs.edit().putBoolean(KEY_VIBRATE_MODE, actualVibrate).apply()
            updateRingVibrateButton()
        }

        // For mute state
        val actualMute = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
        if (isMuted != actualMute) {
            isMuted = actualMute
            sharedPrefs.edit().putBoolean(KEY_MUTE_STATE, actualMute).apply()
            btnMute.setImageResource(if (actualMute) R.drawable.ic_mute else R.drawable.ic_unmute)
        }
    }

    override fun onResume() {
        super.onResume()
        verifySystemState()
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

    private fun startSpotifyAuth() {
        val builder = AuthorizationRequest.Builder(
            CLIENT_ID,
            AuthorizationResponse.Type.TOKEN,
            REDIRECT_URI
        )
        builder.setScopes(arrayOf("app-remote-control", "user-modify-playback-state", "user-read-playback-state"))
        val request = builder.build()
        AuthorizationClient.openLoginActivity(this, AUTH_TOKEN_REQUEST_CODE, request)
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
}
