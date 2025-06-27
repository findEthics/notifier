package com.example.notifier.Calendar
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventSummary = intent.getStringExtra("EVENT_SUMMARY") ?: "Event"
        val eventStartTime = intent.getStringExtra("EVENT_START_TIME") ?: ""
        
        // Play notification sound instead of posting visual notification
        playNotificationSound(context)
        
        // Optional: Add vibration for additional feedback
        vibrate(context)
    }
    
    private fun playNotificationSound(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            // Check if device is not in silent mode
            if (audioManager.ringerMode != AudioManager.RINGER_MODE_SILENT) {
                // Play notification sound using ToneGenerator
                val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 500) // 500ms beep
                
                // Release resources after a short delay
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    toneGenerator.release()
                }, 600)
            }
        } catch (e: Exception) {
            // Gracefully handle any audio playback errors
        }
    }
    
    private fun vibrate(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ (API 31+)
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                // Pre-Android 12
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(200)
                }
            }
        } catch (e: Exception) {
            // Gracefully handle any vibration errors
        }
    }
}