package com.example.bruffwalkingtour

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso

class WaypointDetailActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_WAYPOINT_NAME = "waypoint_name"
        const val EXTRA_WAYPOINT_DESCRIPTION = "waypoint_description"
        const val EXTRA_WAYPOINT_HISTORICAL_INFO = "waypoint_historical_info"
        const val EXTRA_WAYPOINT_IMAGE_URL = "waypoint_image_url"
        const val EXTRA_IS_LAST_WAYPOINT = "is_last_waypoint"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContentView(R.layout.activity_waypoint_detail)
        
        setupSystemUI()
        setupViews()
        loadWaypointData()
    }
    
    private fun setupSystemUI() {
        // Hide system navigation bar but keep status bar visible
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.let { controller ->
            controller.hide(WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        
        // Handle window insets to avoid overlap with action bar
        window.decorView.setOnApplyWindowInsetsListener { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val actionBarHeight = (56 * resources.displayMetrics.density).toInt() // Convert 56dp to pixels
            val scrollView = findViewById<android.widget.ScrollView>(R.id.waypoint_scroll_view)
            scrollView?.setPadding(0, systemBars.top + actionBarHeight, 0, 0)
            insets
        }
    }
    
    private fun setupViews() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Location Details"
        
        val isLastWaypoint = intent.getBooleanExtra(EXTRA_IS_LAST_WAYPOINT, false)
        val continueTextView = findViewById<TextView>(R.id.continue_tour_text)
        continueTextView.movementMethod = LinkMovementMethod.getInstance()
        
        val fullText = if (isLastWaypoint) {
            "🎉 Complete Your Bruff Adventure!"
        } else {
            "➡️ Continue Your Bruff Journey"
        }
        
        val spannableString = SpannableString(fullText)
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                if (isLastWaypoint) {
                    // Return to MainActivity with completion flag
                    val resultIntent = Intent().apply {
                        putExtra("show_completion", true)
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()
                } else {
                    // Return to MainActivity to continue
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }
        
        spannableString.setSpan(clickableSpan, 0, fullText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        continueTextView.text = spannableString
    }
    
    private fun loadWaypointData() {
        val name = intent.getStringExtra(EXTRA_WAYPOINT_NAME) ?: ""
        val description = intent.getStringExtra(EXTRA_WAYPOINT_DESCRIPTION) ?: ""
        val historicalInfo = intent.getStringExtra(EXTRA_WAYPOINT_HISTORICAL_INFO) ?: ""
        val imageUrl = intent.getStringExtra(EXTRA_WAYPOINT_IMAGE_URL)
        
        findViewById<TextView>(R.id.waypoint_name).text = name
        findViewById<TextView>(R.id.waypoint_description).text = description
        findViewById<TextView>(R.id.waypoint_historical_info).text = historicalInfo
        
        val imageView = findViewById<ImageView>(R.id.waypoint_image)
        
        LogUtils.d("WaypointDetail", "Loading image for waypoint: $name, URL: $imageUrl")
        
        if (!imageUrl.isNullOrEmpty()) {
            if (imageUrl.startsWith("android.resource://")) {
                // Handle local resource
                try {
                    val resourceName = imageUrl.substringAfterLast("/")
                    val resourceId = resources.getIdentifier(resourceName, "drawable", packageName)
                    if (resourceId != 0) {
                        imageView.setImageResource(resourceId)
                        LogUtils.d("WaypointDetail", "Successfully loaded local image for: $name")
                    } else {
                        LogUtils.e("WaypointDetail", "Resource not found: $resourceName")
                        imageView.setImageResource(R.drawable.ic_launcher_foreground)
                    }
                } catch (e: Exception) {
                    LogUtils.e("WaypointDetail", "Error loading local resource for: $name", e)
                    imageView.setImageResource(R.drawable.ic_launcher_foreground)
                }
            } else {
                // Handle URL
                Picasso.get()
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .error(R.drawable.ic_launcher_foreground)
                    .into(imageView, object : Callback {
                        override fun onSuccess() {
                            LogUtils.d("WaypointDetail", "Successfully loaded image for: $name")
                        }
                        
                        override fun onError(e: Exception?) {
                            LogUtils.e("WaypointDetail", "Failed to load image for: $name")
                            imageView.setImageResource(R.drawable.ic_launcher_foreground)
                        }
                    })
            }
        } else {
            LogUtils.d("WaypointDetail", "No image URL provided for: $name")
            imageView.setImageResource(R.drawable.ic_launcher_foreground)
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        setResult(RESULT_CANCELED)
        finish()
        return true
    }
}