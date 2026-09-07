package com.example.bruffwalkingtour

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
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
        setContentView(R.layout.activity_waypoint_detail)
        setupViews()
        loadWaypointData()
    }

    private fun setupViews() {
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val isLastWaypoint = intent.getBooleanExtra(EXTRA_IS_LAST_WAYPOINT, false)
        val continueButton = findViewById<MaterialButton>(R.id.continue_tour_text)
        continueButton.setText(
            if (isLastWaypoint) R.string.detail_finish else R.string.detail_continue,
        )
        continueButton.setOnClickListener {
            // MainActivity advances the tour on RESULT_OK and decides — from its
            // own waypoint index — whether that was the final stop.
            setResult(RESULT_OK)
            finish()
        }
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
                try {
                    val resourceName = imageUrl.substringAfterLast("/")
                    val resourceId = resources.getIdentifier(resourceName, "drawable", packageName)
                    if (resourceId != 0) {
                        imageView.setImageResource(resourceId)
                    } else {
                        LogUtils.e("WaypointDetail", "Resource not found: $resourceName")
                        imageView.setImageResource(R.drawable.ic_launcher_foreground)
                    }
                } catch (e: Exception) {
                    LogUtils.e("WaypointDetail", "Error loading local resource for: $name", e)
                    imageView.setImageResource(R.drawable.ic_launcher_foreground)
                }
            } else {
                Picasso.get()
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .error(R.drawable.ic_launcher_foreground)
                    .into(imageView, object : Callback {
                        override fun onSuccess() {}

                        override fun onError(e: Exception?) {
                            LogUtils.e("WaypointDetail", "Failed to load image for: $name")
                            imageView.setImageResource(R.drawable.ic_launcher_foreground)
                        }
                    })
            }
        } else {
            imageView.setImageResource(R.drawable.ic_launcher_foreground)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        setResult(RESULT_CANCELED)
        finish()
        return true
    }
}
