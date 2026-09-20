package com.example.bruffwalkingtour

import android.os.Bundle
import android.view.View
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
        const val EXTRA_WAYPOINT_LOCAL_IMAGE = "waypoint_local_image"
        const val EXTRA_IS_LAST_WAYPOINT = "is_last_waypoint"
        /** Opened just to read (a visited/upcoming stop) — no "Continue". */
        const val EXTRA_BROWSE_ONLY = "browse_only"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_waypoint_detail)
        NarrationPlayer.init(this)
        setupViews()
        loadWaypointData()
        setupNarration()
    }

    private fun setupViews() {
        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val browseOnly = intent.getBooleanExtra(EXTRA_BROWSE_ONLY, false)
        val isLastWaypoint = intent.getBooleanExtra(EXTRA_IS_LAST_WAYPOINT, false)
        val continueButton = findViewById<MaterialButton>(R.id.continue_tour_text)
        continueButton.setText(
            when {
                browseOnly -> R.string.detail_done
                isLastWaypoint -> R.string.detail_finish
                else -> R.string.detail_continue
            },
        )
        continueButton.setOnClickListener {
            NarrationPlayer.stop()
            // Browse-only: just close, don't advance the tour. Otherwise
            // MainActivity advances on RESULT_OK.
            if (!browseOnly) setResult(RESULT_OK)
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_exit, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == R.id.action_exit) {
            ExitHelper.confirmExit(this)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /** "Listen" button — device text-to-speech reads the stop's history aloud. */
    private fun setupNarration() {
        val button = findViewById<MaterialButton>(R.id.narration_button)
        val script = listOfNotNull(
            intent.getStringExtra(EXTRA_WAYPOINT_HISTORICAL_INFO),
            intent.getStringExtra(EXTRA_WAYPOINT_DESCRIPTION),
        ).firstOrNull { it.isNotBlank() }.orEmpty()

        NarrationPlayer.available.observe(this) { available ->
            button.visibility = if (available && script.isNotBlank()) View.VISIBLE else View.GONE
        }
        NarrationPlayer.speaking.observe(this) { speaking ->
            button.setText(if (speaking) R.string.narration_stop else R.string.narration_listen)
            button.setIconResource(
                if (speaking) R.drawable.ic_stop_small else R.drawable.ic_play_small,
            )
        }
        button.setOnClickListener { NarrationPlayer.toggle(script) }
    }

    private fun loadWaypointData() {
        val name = intent.getStringExtra(EXTRA_WAYPOINT_NAME) ?: ""
        val description = intent.getStringExtra(EXTRA_WAYPOINT_DESCRIPTION) ?: ""
        val historicalInfo = intent.getStringExtra(EXTRA_WAYPOINT_HISTORICAL_INFO) ?: ""

        findViewById<TextView>(R.id.waypoint_name).text = name
        findViewById<TextView>(R.id.waypoint_description).text = description
        findViewById<TextView>(R.id.waypoint_historical_info).text = historicalInfo

        loadWaypointImage(
            name,
            localImage = intent.getStringExtra(EXTRA_WAYPOINT_LOCAL_IMAGE),
            imageUrl = intent.getStringExtra(EXTRA_WAYPOINT_IMAGE_URL),
        )
    }

    /**
     * Bundled photo first (offline, reliable), then the remote URL, then the
     * branded placeholder. [localImage] is treated as a drawable resource name —
     * any folder path or extension is stripped.
     */
    private fun loadWaypointImage(name: String, localImage: String?, imageUrl: String?) {
        val imageView = findViewById<ImageView>(R.id.waypoint_image)

        val localResId = localImage
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.')
            ?.takeIf { it.isNotBlank() }
            ?.let { resources.getIdentifier(it, "drawable", packageName) }
            ?: 0
        if (localResId != 0) {
            imageView.setImageResource(localResId)
            return
        }

        if (imageUrl.isNullOrEmpty()) {
            imageView.setImageResource(R.drawable.img_waypoint_placeholder)
            return
        }

        if (imageUrl.startsWith("android.resource://")) {
            val resName = imageUrl.substringAfterLast('/')
            val resId = resources.getIdentifier(resName, "drawable", packageName)
            imageView.setImageResource(
                if (resId != 0) resId else R.drawable.img_waypoint_placeholder,
            )
            return
        }

        Picasso.get()
            .load(imageUrl)
            .placeholder(R.drawable.img_waypoint_placeholder)
            .error(R.drawable.img_waypoint_placeholder)
            .into(imageView, object : Callback {
                override fun onSuccess() {}
                override fun onError(e: Exception?) {
                    LogUtils.e("WaypointDetail", "Failed to load image for: $name")
                    imageView.setImageResource(R.drawable.img_waypoint_placeholder)
                }
            })
    }

    override fun onSupportNavigateUp(): Boolean {
        setResult(RESULT_CANCELED)
        finish()
        return true
    }
}
