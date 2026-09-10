package com.example.bruffwalkingtour

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class IntroActivity : AppCompatActivity() {

    companion object {
        private const val SPLASH_MIN_MS = 550L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Keep the branded splash up for a brief, deliberate beat rather than a
        // sub-frame flash, then hand off to the intro screen.
        val splash = installSplashScreen()
        val shownAt = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition {
            SystemClock.uptimeMillis() - shownAt < SPLASH_MIN_MS
        }

        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display and hide system navigation
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setupSystemUI()

        setContentView(R.layout.activity_intro)

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
        // Hide action bar for cleaner intro experience
        supportActionBar?.hide()
        
        // The start CTA is a pinned bar at the bottom of the screen — the whole
        // bar is the tap target, so a plain click listener on the view.
        findViewById<TextView>(R.id.start_tour_text).setOnClickListener {
            startActivity(Intent(this@IntroActivity, MainActivity::class.java))
            finish() // Close intro so user can't go back to it
        }

        findViewById<TextView>(R.id.help_link).setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }

        populateHighlights()

        // Set up admin access via long press on the logo
        setupAdminAccess()
    }

    private fun populateHighlights() {
        val waypoints = BruffTourData.getDefaultTour().waypoints

        findViewById<TextView>(R.id.locations_count_text).text =
            getString(R.string.intro_stat_stops, waypoints.size)

        val container = findViewById<LinearLayout>(R.id.highlights_container)
        val inflater = LayoutInflater.from(this)
        waypoints.forEachIndexed { index, waypoint ->
            val item = inflater.inflate(R.layout.list_item_highlight, container, false)
            item.findViewById<TextView>(R.id.highlight_number).text = (index + 1).toString()
            item.findViewById<TextView>(R.id.highlight_name).text = waypoint.name
            item.findViewById<TextView>(R.id.highlight_desc).text = waypoint.description
            container.addView(item)
        }
    }
    
    private fun setupAdminAccess() {
        val logoImageView = findViewById<android.widget.ImageView>(R.id.app_logo)
        var longPressCount = 0
        
        logoImageView.setOnLongClickListener {
            longPressCount++
            LogUtils.d("IntroActivity", "Admin access attempt: $longPressCount")
            
            if (longPressCount >= 3) {
                // Reset counter and launch admin settings
                longPressCount = 0
                val intent = Intent(this, AdminSettingsActivity::class.java)
                startActivity(intent)
                true
            } else {
                // Show hint after first long press
                android.widget.Toast.makeText(this, "Long press ${3 - longPressCount} more times for admin access", android.widget.Toast.LENGTH_SHORT).show()
                true
            }
        }
    }
}