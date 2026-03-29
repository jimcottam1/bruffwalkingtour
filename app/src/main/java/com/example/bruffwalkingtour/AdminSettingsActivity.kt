package com.example.bruffwalkingtour

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class AdminSettingsActivity : AppCompatActivity() {
    
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var locationToggle: Switch
    
    companion object {
        const val PREF_LOCATION_ENABLED = "location_enabled"
        
        fun isLocationEnabled(context: android.content.Context): Boolean {
            val prefs = context.getSharedPreferences("bruff_admin_prefs", MODE_PRIVATE)
            // Ensure location is enabled by default and reset if disabled
            val isEnabled = prefs.getBoolean(PREF_LOCATION_ENABLED, true)
            if (!isEnabled) {
                // Re-enable location services
                prefs.edit().putBoolean(PREF_LOCATION_ENABLED, true).apply()
                return true
            }
            return isEnabled
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display and hide system navigation
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setupSystemUI()
        
        setContentView(R.layout.activity_admin_settings)
        
        sharedPreferences = getSharedPreferences("bruff_admin_prefs", MODE_PRIVATE)
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
        
        locationToggle = findViewById(R.id.location_toggle)
        
        // Load current location setting
        val locationEnabled = sharedPreferences.getBoolean(PREF_LOCATION_ENABLED, true)
        locationToggle.isChecked = locationEnabled
        
        // Set up location toggle listener
        locationToggle.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(PREF_LOCATION_ENABLED, isChecked).apply()
            LogUtils.d("AdminSettings", "Location services ${if (isChecked) "enabled" else "disabled"}")
        }
        
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