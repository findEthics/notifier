package com.example.notifier

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
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

class SpotifyManager(private val activity: Activity) {

    // --- Spotify Constants and Properties ---
    private val CLIENT_ID = "b5954f6b7e1f44b68a9c170550ce3d10"
    private val REDIRECT_URI = "notifier://callback"
    private val AUTH_TOKEN_REQUEST_CODE = 0x10

    private var spotifyAppRemote: SpotifyAppRemote? = null
    private var currentTrackUri: String? = null
    private var currentContextUri: String? = null

    // --- Public Functions to be called from MainActivity ---

    fun start() {
        startSpotifyAuth()
    }

    fun connect() {
        val connectionParams = ConnectionParams.Builder(CLIENT_ID)
            .setRedirectUri(REDIRECT_URI)
            .showAuthView(true)
            .build()

        SpotifyAppRemote.connect(activity, connectionParams, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                spotifyAppRemote = appRemote
                subscribeToPlayerState()
            }
            override fun onFailure(throwable: Throwable) {
                Log.e("SpotifyManager", "Connection failed", throwable)
            }
        })
    }

    fun disconnect() {
        spotifyAppRemote?.let {
            SpotifyAppRemote.disconnect(it)
            spotifyAppRemote = null
        }
    }

    // --- Private Helper Functions moved from MainActivity ---

    private fun startSpotifyAuth() {
        val builder = AuthorizationRequest.Builder(
            CLIENT_ID,
            AuthorizationResponse.Type.TOKEN,
            REDIRECT_URI
        )
        builder.setScopes(arrayOf("app-remote-control", "user-modify-playback-state", "user-read-playback-state"))
        val request = builder.build()
        AuthorizationClient.openLoginActivity(activity, AUTH_TOKEN_REQUEST_CODE, request)
    }

    fun setupSpotifyPlayerControls() {
        val btnPlayPause = activity.findViewById<ImageButton>(R.id.btnPlayPause)
        val btnPrev = activity.findViewById<ImageButton>(R.id.btnPrev)
        val btnNext = activity.findViewById<ImageButton>(R.id.btnNext)

        btnPlayPause.setOnClickListener {
            spotifyAppRemote?.playerApi?.playerState?.setResultCallback { playerState ->
                if (playerState.isPaused) {
                    spotifyAppRemote?.playerApi?.resume()
                } else {
                    spotifyAppRemote?.playerApi?.pause()
                }
            }
        }

        btnPrev.setOnClickListener { spotifyAppRemote?.playerApi?.skipPrevious() }
        btnNext.setOnClickListener { spotifyAppRemote?.playerApi?.skipNext() }

        // Setup click listeners for album art/text to open Spotify
        val tvTrack = activity.findViewById<TextView>(R.id.tvTrack)
        val tvArtist = activity.findViewById<TextView>(R.id.tvArtist)
        val ivAlbum = activity.findViewById<ImageView>(R.id.ivAlbum)
        val clickableViews = listOf(ivAlbum, tvTrack, tvArtist)
        clickableViews.forEach { view ->
            view.setOnClickListener {
                openInSpotify()
            }
        }
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

            if (playerState.isPaused) {
                btnPlayPause.setImageResource(R.drawable.ic_play)
            } else {
                btnPlayPause.setImageResource(R.drawable.ic_pause)
            }
        }

        spotifyAppRemote?.playerApi?.subscribeToPlayerContext()?.setEventCallback { context ->
            currentContextUri = context.uri
        }
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
