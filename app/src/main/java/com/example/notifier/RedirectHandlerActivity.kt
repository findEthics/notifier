package com.example.notifier

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class RedirectHandlerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Just pass result back to your OAuth library
        val redirectIntent = Intent(this, MainActivity::class.java).apply {
            data = intent?.data
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(redirectIntent)
        finish()
    }
}
