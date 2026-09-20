package com.example.bruffwalkingtour

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class AdminSettingsActivity : AppCompatActivity() {
    
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display and hide system navigation
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setupSystemUI()
        
        setContentView(R.layout.activity_admin_settings)
        
        setupViews()
    }
    
    private fun setupSystemUI() {
        // Hide system navigation bar for full screen experience
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.let { controller ->
            controller.hide(WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    
    private fun setupViews() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Admin Settings"
        
        // Set up clickable links
        setupClickableLinks()
    }
    
    private fun setupClickableLinks() {
        // View Logs link
        val viewLogsTextView = findViewById<TextView>(R.id.view_logs_text)
        viewLogsTextView.movementMethod = LinkMovementMethod.getInstance()
        
        val logsText = "📋 View Application Logs"
        val logsSpannable = SpannableString(logsText)
        val logsSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                val intent = Intent(this@AdminSettingsActivity, LogViewerActivity::class.java)
                startActivity(intent)
            }
        }
        logsSpannable.setSpan(logsSpan, 0, logsText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        viewLogsTextView.text = logsSpannable
        
        // Back to app link
        val backTextView = findViewById<TextView>(R.id.back_to_app_text)
        backTextView.movementMethod = LinkMovementMethod.getInstance()
        
        val backText = "🔙 Back to App"
        val backSpannable = SpannableString(backText)
        val backSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                finish()
            }
        }
        backSpannable.setSpan(backSpan, 0, backText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        backTextView.text = backSpannable
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}