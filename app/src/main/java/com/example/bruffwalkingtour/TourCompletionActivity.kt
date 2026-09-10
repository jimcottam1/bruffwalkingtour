package com.example.bruffwalkingtour

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class TourCompletionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setupSystemUI()

        setContentView(R.layout.activity_tour_completion)

        setupViews()
    }

    private fun setupSystemUI() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.let { controller ->
            controller.hide(WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun setupViews() {
        populateSummary()

        findViewById<Button>(R.id.btn_restart).setOnClickListener {
            val intent = Intent(this, IntroActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        }
        findViewById<Button>(R.id.btn_done).setOnClickListener { finishAffinity() }
    }

    private fun populateSummary() {
        val waypoints = BruffTourData.getDefaultTour().waypoints

        findViewById<TextView>(R.id.locations_visited_text).text =
            getString(R.string.complete_stat_stops, waypoints.size)

        val container = findViewById<LinearLayout>(R.id.completed_container)
        val inflater = LayoutInflater.from(this)
        waypoints.forEach { waypoint ->
            val item = inflater.inflate(R.layout.list_item_completed, container, false)
            item.findViewById<TextView>(R.id.completed_name).text = waypoint.name
            item.setOnClickListener { openStop(waypoint) }
            container.addView(item)
        }
    }

    /** Re-read a stop's story from the summary. */
    private fun openStop(waypoint: TourWaypoint) {
        startActivity(
            Intent(this, WaypointDetailActivity::class.java).apply {
                putExtra(WaypointDetailActivity.EXTRA_WAYPOINT_NAME, waypoint.name)
                putExtra(WaypointDetailActivity.EXTRA_WAYPOINT_DESCRIPTION, waypoint.description)
                putExtra(WaypointDetailActivity.EXTRA_WAYPOINT_HISTORICAL_INFO, waypoint.historicalInfo)
                putExtra(WaypointDetailActivity.EXTRA_WAYPOINT_IMAGE_URL, waypoint.imageUrl)
                putExtra(WaypointDetailActivity.EXTRA_WAYPOINT_LOCAL_IMAGE, waypoint.localImage)
                putExtra(WaypointDetailActivity.EXTRA_BROWSE_ONLY, true)
            },
        )
    }
}
