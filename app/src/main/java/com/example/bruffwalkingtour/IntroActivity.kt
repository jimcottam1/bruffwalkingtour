package com.example.bruffwalkingtour

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class IntroActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
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
        
        val startTourTextView = findViewById<TextView>(R.id.start_tour_text)
        startTourTextView.movementMethod = LinkMovementMethod.getInstance()
        
        val fullText = "🚀 Begin Your Bruff Heritage Adventure"
        val spannableString = SpannableString(fullText)
        
        // Make the entire text clickable
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                // Launch the main tour activity
                val intent = Intent(this@IntroActivity, MainActivity::class.java)
                startActivity(intent)
                finish() // Close intro so user can't go back to it
            }
        }
        
        spannableString.setSpan(clickableSpan, 0, fullText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        startTourTextView.text = spannableString

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
            "📍\n${waypoints.size} locations"

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