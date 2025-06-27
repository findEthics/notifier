package com.example.notifier

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.Track
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse

class SpotifyManager(private val activity: Activity, private val onDisconnectCallback: (() -> Unit)? = null) {
    // --- Spotify Constants and Properties ---
    private val clientID = BuildConfig.SPOTIFY_CLIENT_ID
    private val redirectURI = "notifier://callback"
    private val authTokenRequestCode = 0x10

    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var currentTrackUri: String? = null
    private var currentContextUri: String? = null
    private var isInitialized = false
    var isPlayerReady = false
        private set
    
    // Auto-disconnect functionality
    private val handler = Handler(Looper.getMainLooper())
    private var autoDisconnectRunnable: Runnable? = null
    private var isPlaying = false
    private val autoDisconnectDelay = 2 * 60 * 1000L // Disconnect after 2 minutes of paused state

    // --- Public Functions to be called from MainActivity ---

    fun start() {
        startSpotifyAuth()
    }

    fun connect() {
        if (spotifyAppRemote != null) return // Already connected
        
        val connectionParams = ConnectionParams.Builder(clientID)
            .setRedirectUri(redirectURI)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(activity, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                subscribeToPlayerState()
                isPlayerReady = true
                // Cancel any pending timeout in MainActivity
                if (activity is MainActivity) {
                    (activity).cancelSpotifyTimeout()
                }
            }
            override fun onFailure(throwable: Throwable) {
                isPlayerReady = false
            }
        })
    }

    fun disconnect() {
        cancelAutoDisconnect()
        resetSpotifyUI()
        spotifyAppRemote?.let {
            SpotifyAppRemote.disconnect(it)
            spotifyAppRemote = null
            isPlayerReady = false
        }
        // Notify MainActivity to hide controls
        onDisconnectCallback?.invoke()
    }

    private fun startSpotifyAuth() {
        val builder = AuthorizationRequest.Builder(
            clientID,
            AuthorizationResponse.Type.TOKEN,
            redirectURI
        )
        builder.setScopes(arrayOf("app-remote-control", "user-modify-playback-state", "user-read-playback-state"))
        val request = builder.build()
        AuthorizationClient.openLoginActivity(activity, authTokenRequestCode, request)
    }



    fun handlePlayPauseClick(): Boolean {
        if (!isPlayerReady) return false
        
        spotifyAppRemote?.playerApi?.playerState?.setResultCallback { playerState ->
            if (playerState.isPaused) {
                spotifyAppRemote?.playerApi?.resume()
            } else {
                spotifyAppRemote?.playerApi?.pause()
            }
        }
        return true
    }

    fun handlePreviousClick(): Boolean {
        if (!isPlayerReady) return false
        
        spotifyAppRemote?.playerApi?.skipPrevious()
        return true
    }

    fun handleNextClick(): Boolean {
        if (!isPlayerReady) return false
        
        spotifyAppRemote?.playerApi?.skipNext()
        return true
    }

    fun handleSpotifyAppClick(): Boolean {
        if (!isPlayerReady) {
            // If not ready, set up the player
            if (!isInitialized) {
                start()
                isInitialized = true
            }
            connect()
            return false
        }
        
        openInSpotify()
        return true
    }

    private fun subscribeToPlayerState() {
        val tvTrack = activity.findViewById<TextView>(R.id.tvTrack)
        val tvArtist = activity.findViewById<TextView>(R.id.tvArtist)
        val ivAlbum = activity.findViewById<ImageView>(R.id.ivAlbum)
        val btnPlayPause = activity.findViewById<ImageButton>(R.id.btnPlayPause)

        spotifyAppRemote?.playerApi?.subscribeToPlayerState()?.setEventCallback { playerState: PlayerState ->
            val track: Track? = playerState.track
            currentTrackUri = track?.uri

            if (track != null) {
                tvTrack.text = track.name
                tvArtist.text = track.artist.name
                spotifyAppRemote?.imagesApi?.getImage(track.imageUri)?.setResultCallback {
                    ivAlbum.setImageBitmap(it)
                }
            }

            // Update play/pause state and manage auto-disconnect
            isPlaying = !playerState.isPaused
            if (playerState.isPaused) {
                btnPlayPause.setImageResource(R.drawable.ic_play)
                startAutoDisconnectTimer()
            } else {
                btnPlayPause.setImageResource(R.drawable.ic_pause)
                cancelAutoDisconnect()
            }
        }

        spotifyAppRemote?.playerApi?.subscribeToPlayerContext()?.setEventCallback { context ->
            currentContextUri = context.uri
        }
    }

    private fun startAutoDisconnectTimer() {
        cancelAutoDisconnect() // Cancel any existing timer
        
        autoDisconnectRunnable = Runnable {
            if (!isPlaying && spotifyAppRemote != null) {
                Toast.makeText(activity, "Spotify disconnected due to inactivity", Toast.LENGTH_SHORT).show()
                disconnect()
            }
        }
        
        handler.postDelayed(autoDisconnectRunnable!!, autoDisconnectDelay)
    }
    
    private fun cancelAutoDisconnect() {
        autoDisconnectRunnable?.let {
            handler.removeCallbacks(it)
            autoDisconnectRunnable = null
        }
    }

    private fun resetSpotifyUI() {
        val tvTrack = activity.findViewById<TextView>(R.id.tvTrack)
        val tvArtist = activity.findViewById<TextView>(R.id.tvArtist)
        val ivAlbum = activity.findViewById<ImageView>(R.id.ivAlbum)
        val btnPlayPause = activity.findViewById<ImageButton>(R.id.btnPlayPause)

        // Reset to original layout values
        tvTrack.text = "Track"
        tvArtist.text = "Artist"
        ivAlbum.setImageResource(R.drawable.ic_music_note)
        btnPlayPause.setImageResource(R.drawable.ic_play)
        
        // Clear internal state
        currentTrackUri = null
        currentContextUri = null
        isPlaying = false
    }

    private fun openInSpotify() {
        val spotifyUri = currentContextUri?.takeIf { it.startsWith("spotify:playlist:") }
            ?: currentTrackUri
            ?: "spotify:" // A fallback to just open the app

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(spotifyUri)
            setPackage("com.spotify.music")
            putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://${activity.packageName}"))
        }

        if (intent.resolveActivity(activity.packageManager) != null) {
            activity.startActivity(intent)
        } else {
            Toast.makeText(activity, "Spotify not installed", Toast.LENGTH_SHORT).show()
        }
    }
}