package com.example.notifier

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CalendarWebViewActivity : AppCompatActivity() {
    
    private lateinit var webView: WebView
    private var calendarUrl: String? = null
    
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        calendarUrl = intent.getStringExtra("calendar_url")
        if (calendarUrl.isNullOrEmpty()) {
            Toast.makeText(this, "No calendar URL provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Create WebView programmatically for better control
        webView = WebView(this).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
            
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Page loaded successfully
                }
                
                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    Toast.makeText(this@CalendarWebViewActivity, "Error loading calendar: $description", Toast.LENGTH_LONG).show()
                }
            }
        }
        
        setContentView(webView)
        
        // Load the calendar URL
        webView.loadUrl(calendarUrl!!)
        
        // Set title
        title = "Calendar"
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        
        // If the activity is already running and a new intent comes in,
        // just reload the URL instead of creating a new instance
        val newUrl = intent?.getStringExtra("calendar_url")
        if (!newUrl.isNullOrEmpty() && newUrl != calendarUrl) {
            calendarUrl = newUrl
            webView.loadUrl(calendarUrl!!)
        }
    }
}