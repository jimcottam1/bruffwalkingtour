package com.example.bruffwalkingtour

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import android.graphics.drawable.Drawable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.views.overlay.Overlay
import android.graphics.Point
import android.view.MotionEvent
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.FrameLayout
import com.squareup.picasso.Picasso
import android.view.Gravity

class MainActivity : AppCompatActivity() {
    
    companion object {
        // Sean Wall Monument centered boundary constants
        private const val SEAN_WALL_CENTER_LAT = 52.47785299293757
        private const val SEAN_WALL_CENTER_LON = -8.54801677334652
        private const val BOUNDARY_WIDTH_KM = 1.5  // 1.5km wide (east-west)
        private const val BOUNDARY_HEIGHT_KM = 3.0 // 3km long (north-south)

        // The "outside Bruff" gate bounces the user back to the intro screen so
        // they aren't stranded on it — but only after several consecutive
        // outside fixes (GPS near the boundary edge is noisy) and a generous
        // delay, and never without offering a "start here anyway" escape.
        private const val GATE_OUTSIDE_RETURN_DELAY_MS = 12000L
        private const val GATE_OUTSIDE_FIXES_BEFORE_RETURN = 3

        // Heads-up notification shown the moment the user reaches a stop, so the
        // arrival isn't missed while the phone is pocketed or pointed at the site.
        private const val ARRIVAL_CHANNEL_ID = "stop_arrivals"
        private const val ARRIVAL_NOTIFICATION_ID = 1001

        // A saved tour is resumed silently only if the app was used this
        // recently — covers "the OS killed the screen while I read a stop".
        // A longer gap (came back next day, deliberately relaunched) asks
        // whether to resume or start over instead of silently dropping the
        // user back mid-tour.
        private const val RESUME_SILENT_WINDOW_MS = 45L * 60 * 1000

        // First time a walk starts, pull the whole (fixed, small) tour area into
        // osmdroid's disk cache in the background so the map keeps working if
        // signal drops mid-walk and a returning visitor makes ~no repeat tile
        // requests. z15–18 over the ~1.5×3 km boundary is a few hundred tiles.
        private const val PREWARM_ZOOM_MIN = 15
        private const val PREWARM_ZOOM_MAX = 18

        // Basemap history:
        //  - tile.openstreetmap.org blocked this app's traffic on real devices,
        //    seemingly fingerprinting the HTTP client rather than the User-Agent.
        //  - CARTO's keyless basemap (basemaps.cartocdn.com) then started serving
        //    an "API key required" watermark tile once an IP crossed its
        //    anonymous quota — so the map degraded to that image mid-use.
        //
        // OpenStreetMap France's Humanitarian (HOT) style: keyless, standard
        // z/x/y 256px scheme, a separate community deployment (not the main OSM
        // tile infra that blocked us), a clean pedestrian-oriented style, and
        // explicitly provided for this kind of light embedded use.
        // https://wiki.openstreetmap.org/wiki/OpenStreetMap_France
        private val OSM_HOT = org.osmdroid.tileprovider.tilesource.XYTileSource(
            "OSMFranceHOT",
            0, 20, 256, ".png",
            arrayOf(
                "https://a.tile.openstreetmap.fr/hot/",
                "https://b.tile.openstreetmap.fr/hot/",
                "https://c.tile.openstreetmap.fr/hot/"
            ),
            "© OpenStreetMap contributors — tiles courtesy of OpenStreetMap France"
        )
    }
    
    private lateinit var mapView: MapView
    private lateinit var locationService: LocationService
    private lateinit var routeService: RouteService
    private lateinit var navigationInstructionText: TextView
    private lateinit var distanceInfoText: TextView
    private lateinit var gpsAccuracyText: TextView
    private lateinit var boundaryGateOverlay: View
    private lateinit var gateStatusText: TextView
    private lateinit var gateDistanceText: TextView
    private lateinit var startTourButton: Button
    private lateinit var gateOverrideButton: Button
    private lateinit var recenterButton: Button
    private lateinit var arrivalCard: View
    private lateinit var arrivalCardTitle: TextView
    // True while the map auto-centres on GPS fixes. Suspended as soon as the
    // user touches the map (drag/pinch), so their gesture isn't fought by the
    // next location update; restored by tapping recenterButton.
    private var followMode = true
    private var tourStarted = false
    // Set when the user chose "continue anyway" past a denied location
    // permission — the map still loads but live navigation can't work.
    private var locationTrackingUnavailable = false
    // Consecutive gate updates reporting "outside the area", so a couple of
    // noisy edge-of-boundary fixes don't bounce the user back to the intro.
    private var consecutiveOutsideGateFixes = 0
    // True when the open waypoint detail screen was launched from an arrival
    // (vs. peeking at a stop mid-walk) — so backing out of it still advances.
    private var detailOpenedOnArrival = false
    // Waypoint index we've already shown the "you've moved on — advance?" prompt
    // for, so it isn't shown repeatedly for the same stop.
    private var movedOnPromptHandledForIndex = -1
    private var returnToIntroJob: Job? = null
    private var currentTour: WalkingTour? = null
    private var waypointMarkers = mutableListOf<Marker>()
    private var routePolylines = mutableListOf<Polyline>()
    private var routeArrowMarkers = mutableListOf<Marker>()
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var tourBoundaryOverlay: Polygon? = null
    private var currentLocation: Location? = null
    private var nearbyWaypoint: TourWaypoint? = null
    private val markerBitmaps = mutableListOf<Bitmap>()
    private var imagePreviewOverlay: android.view.View? = null
    private val markerAnimators = mutableMapOf<Marker, android.animation.ValueAnimator>()
    
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true -> {
                setupLocationTracking()
            }
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                setupLocationTracking()
            }
            else -> {
                handleLocationPermissionDenied()
            }
        }
    }
    
    private val waypointDetailsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Advancing to the next stop happens when the user taps Continue
        // (RESULT_OK) OR when they simply back out of a screen they opened *on
        // arrival* — "reached it and read it" is enough. Peeking at a stop's
        // page mid-walk (not arrived) still needs the explicit Continue tap.
        val advance = result.resultCode == RESULT_OK || detailOpenedOnArrival
        detailOpenedOnArrival = false
        if (!advance) return@registerForActivityResult

        val wasLastWaypoint = locationService.isTourCompleted()
        locationService.moveToNextWaypoint()
        hideArrivalCard()
        if (!wasLastWaypoint) {
            currentLocation?.let { location -> updateNavigationInstructions(location) }
            updateAllWaypointMarkers()
        }
        // Completion is driven solely by the tourCompleted observer, which
        // moveToNextWaypoint() triggers when stepping past the final stop.
    }

    // Arrival notifications are best-effort: if the user declines this we still
    // show the on-screen arrival card and vibrate.
    private val notificationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* nothing to do — arrival still surfaces on-screen */ }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogUtils.d("BruffTour", "=== MAIN ACTIVITY ONCREATE STARTED ===")
        
        // Enable edge-to-edge display and hide system navigation
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setupSystemUI()
        
        // Check network connectivity on startup
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, getString(R.string.no_network_connection), Toast.LENGTH_LONG).show()
        }
        
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        // OSM's tile usage policy requires a distinct, contactable User-Agent —
        // osmdroid's generic default (or a bare package name) gets blocked as
        // "not identifiable". https://operations.osmfoundation.org/policies/tiles/
        Configuration.getInstance().userAgentValue =
            "BruffWalkingTour/${BuildConfig.VERSION_NAME} (+https://github.com/jimcottam1/bruffwalkingtour)"
        // The tour is one fixed ~1.5×3 km area — once its tiles are cached they
        // should effectively never expire or be trimmed, so a returning visitor
        // (or one who loses signal mid-walk) keeps a working map and the app
        // makes ~no repeat tile requests.
        Configuration.getInstance().expirationOverrideDuration =
            1000L * 60 * 60 * 24 * 365 * 5 // ~5 years
        Configuration.getInstance().tileFileSystemCacheMaxBytes = 200L * 1024 * 1024
        Configuration.getInstance().tileFileSystemCacheTrimBytes = 160L * 1024 * 1024
        purgeStaleBlockedTileCache()

        setContentView(R.layout.activity_main)

        // Simple startup confirmation
        LogUtils.d("BruffTour", "MainActivity started successfully")

        LogUtils.d("MainActivity", "Setting up views, map, and services...")
        setupViews()
        setupMap()
        setupLocationService()
        setupRouteService()
        createArrivalNotificationChannel()
        requestNotificationPermissionIfNeeded()

        // Normally the map/tour is loaded lazily by startTour() once the user
        // clears the boundary gate. If a tour was already in progress, either
        // resume it silently (recent interruption) or ask (long gap / deliberate
        // relaunch) rather than always dropping the user back in mid-tour.
        if (locationService.hasActiveSession()) {
            if (locationService.millisSinceLastActive() < RESUME_SILENT_WINDOW_MS) {
                LogUtils.d("MainActivity", "Recent tour session — resuming, skipping gate")
                startTour()
            } else {
                LogUtils.d("MainActivity", "Stale tour session — asking resume vs start over")
                promptResumeOrRestart()
            }
        }
        requestLocationPermissions()
        LogUtils.d("MainActivity", "onCreate completed")
    }

    /**
     * A previous tour is saved but hasn't been touched for a while — ask the
     * user whether to carry on from where they were or start the tour over,
     * rather than silently resuming (which is surprising on a deliberate
     * relaunch). Shown over the still-visible boundary gate.
     */
    private fun promptResumeOrRestart() {
        val savedIndex = locationService.savedWaypointIndex()
        val total = BruffTourData.getDefaultTour().waypoints.size
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.resume_title))
            .setMessage(getString(R.string.resume_message, savedIndex + 1, total))
            .setPositiveButton(getString(R.string.resume_continue)) { _, _ -> startTour() }
            .setNegativeButton(getString(R.string.resume_start_over)) { _, _ ->
                // Fall through to the normal gate — it's already on screen and
                // driven by location updates.
                locationService.clearSavedProgress()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Dismisses the boundary gate and loads the live map/tour. Called either when
     * the user taps "Start Tour" while inside the boundary, or as a degraded-mode
     * fallback if location permission is denied (see handleLocationPermissionDenied).
     */
    private fun startTour() {
        if (tourStarted) return
        tourStarted = true
        returnToIntroJob?.cancel()
        returnToIntroJob = null
        boundaryGateOverlay.visibility = View.GONE
        mapView.visibility = View.VISIBLE
        // Keep the screen awake only for the live tour view — turn-by-turn
        // guidance is useless if the screen locks mid-walk. Not set on the
        // gate/intro/completion screens, where it would just waste battery.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        LogUtils.d("MainActivity", "Starting tour — loading map and route data")
        loadTour()
        // From here on an interrupted session resumes instead of re-gating.
        locationService.beginTourSession()
        addTourBoundaryToMap()
        prewarmTourTilesOnce()
    }

    /**
     * First time a walk starts, quietly pull the whole tour area's tiles into
     * osmdroid's disk cache in the background via osmdroid's own CacheManager
     * (which throttles politely). After this the install makes essentially no
     * further basemap requests for the tour, and the map survives a mid-walk
     * signal drop. Runs once per install; a partial result still counts.
     */
    private fun prewarmTourTilesOnce() {
        val prefs = getSharedPreferences("bruff_tour_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("tiles_prewarmed_v1", false)) return
        if (!NetworkUtils.isNetworkAvailable(this)) return
        try {
            val cacheManager = org.osmdroid.tileprovider.cachemanager.CacheManager(mapView)
            cacheManager.downloadAreaAsyncNoUI(
                this,
                calculateTourBoundary(),
                PREWARM_ZOOM_MIN,
                PREWARM_ZOOM_MAX,
                object : org.osmdroid.tileprovider.cachemanager.CacheManager.CacheManagerCallback {
                    override fun onTaskComplete() {
                        prefs.edit().putBoolean("tiles_prewarmed_v1", true).apply()
                        LogUtils.d("MainActivity", "Tour tile pre-warm complete")
                    }
                    override fun onTaskFailed(errors: Int) {
                        // A partial cache is still useful — don't retry every walk.
                        prefs.edit().putBoolean("tiles_prewarmed_v1", true).apply()
                        LogUtils.w("MainActivity", "Tour tile pre-warm finished with $errors error(s)")
                    }
                    override fun updateProgress(progress: Int, currentZoom: Int, zoomMin: Int, zoomMax: Int) {}
                    override fun downloadStarted() {}
                    override fun setPossibleTilesInArea(total: Int) {
                        LogUtils.d("MainActivity", "Pre-warming ~$total tour tiles (z$PREWARM_ZOOM_MIN–$PREWARM_ZOOM_MAX)")
                    }
                },
            )
        } catch (e: Exception) {
            LogUtils.w("MainActivity", "Could not start tour tile pre-warm", e)
        }
    }

    /**
     * One-time cleanup after a basemap provider change. A blocked/"API key"
     * response is a real 200 OK image, so osmdroid caches it to disk like any
     * other tile and keeps serving it forever. Bump the key whenever the tile
     * source changes so existing installs self-heal without clearing app storage.
     */
    private fun purgeStaleBlockedTileCache() {
        val prefs = getSharedPreferences("bruff_tour_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("tile_cache_purged_v2", false)) return
        try {
            Configuration.getInstance().getOsmdroidTileCache(this)?.deleteRecursively()
            LogUtils.d("MainActivity", "Purged stale osmdroid tile cache")
        } catch (e: Exception) {
            LogUtils.w("MainActivity", "Failed to purge tile cache", e)
        }
        prefs.edit().putBoolean("tile_cache_purged_v2", true).apply()
    }

    private fun setupSystemUI() {
        // Hide system navigation bar for full map experience
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.let { controller ->
            controller.hide(WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    
    private fun setupViews() {
        // The tour screen is a full-bleed map — no action bar (its only job was
        // hosting Help and a distance subtitle that duplicated the bottom bar).
        // Matches IntroActivity / WaypointDetailActivity / TourCompletionActivity.
        supportActionBar?.hide()

        navigationInstructionText = findViewById(R.id.navigation_instruction)
        distanceInfoText = findViewById(R.id.distance_info)
        gpsAccuracyText = findViewById(R.id.gps_accuracy)
        boundaryGateOverlay = findViewById(R.id.boundary_gate_overlay)
        gateStatusText = findViewById(R.id.gate_status_text)
        gateDistanceText = findViewById(R.id.gate_distance_text)
        startTourButton = findViewById(R.id.start_tour_button)
        gateOverrideButton = findViewById(R.id.gate_override_button)
        recenterButton = findViewById(R.id.recenter_button)
        arrivalCard = findViewById(R.id.arrival_card)
        arrivalCardTitle = findViewById(R.id.arrival_card_title)

        // Enable clickable links in navigation text
        navigationInstructionText.movementMethod = LinkMovementMethod.getInstance()

        startTourButton.setOnClickListener { startTour() }
        gateOverrideButton.setOnClickListener { startTour() }
        findViewById<Button>(R.id.help_button).setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }
        findViewById<Button>(R.id.arrival_card_button).setOnClickListener {
            nearbyWaypoint?.let { showWaypointDetails(it) }
        }
        findViewById<Button>(R.id.arrival_card_dismiss).setOnClickListener {
            arrivalCard.visibility = View.GONE
        }
        recenterButton.setOnClickListener {
            followMode = true
            recenterButton.visibility = View.GONE
            currentLocation?.let { location ->
                mapView.controller.animateTo(GeoPoint(location.latitude, location.longitude), 17.5, 1000L)
            }
        }
    }
    
    private fun setupMap() {
        mapView = findViewById(R.id.mapview)
        // Kept hidden (and no tour/route data loaded) until startTour() runs,
        // so nothing is drawn or fetched while the user is outside the boundary.
        mapView.visibility = View.GONE
        mapView.setTileSource(OSM_HOT)
        mapView.setMultiTouchControls(true)

        // OSM's tile usage policy requires visible attribution on the map.
        mapView.overlays.add(org.osmdroid.views.overlay.CopyrightOverlay(this))
        
        // Disable double-tap zoom to prevent unwanted screen closing behavior
        mapView.setUseDataConnection(false) // Temporarily disable to access the gesture detector
        mapView.setUseDataConnection(true)  // Re-enable
        
        // Configure map to be more stable for mobile interaction
        mapView.isFlingEnabled = true  // Allow flinging but controlled
        mapView.isHorizontalMapRepetitionEnabled = false
        mapView.isVerticalMapRepetitionEnabled = false
        
        // Allow full zoom range for better usability
        mapView.setMinZoomLevel(10.0)  // Wide area view
        mapView.setMaxZoomLevel(20.0)  // Very detailed street view
        
        // Clamp panning to the tour area (the same box drawn by
        // addTourBoundaryToMap()) — the map can be dragged right up to its
        // edges but no further.
        mapView.setScrollableAreaLimitDouble(calculateTourBoundary())

        val mapController = mapView.controller
        mapController.setZoom(17.5) // Closer street level zoom
        
        val seanWallCenter = GeoPoint(SEAN_WALL_CENTER_LAT, SEAN_WALL_CENTER_LON)
        mapController.setCenter(seanWallCenter)
        
        // Enable zoom controls and proper touch handling
        mapView.setMultiTouchControls(true)   // Enable pinch-to-zoom
        mapView.setUseDataConnection(true)    // Allow downloading map tiles

        // Any user touch (drag or pinch) suspends auto-follow so the next GPS
        // fix's animateTo() doesn't fight the gesture or snap the map back.
        mapView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && followMode) {
                followMode = false
                recenterButton.visibility = View.VISIBLE
            }
            false // never consume — let osmdroid's own gesture handling run
        }

        // Add map listener to handle events properly and prevent unwanted actions
        mapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                // Allow normal scrolling
                return false
            }
            
            override fun onZoom(event: ZoomEvent?): Boolean {
                // Allow normal zooming but don't let it interfere with activity lifecycle
                return false
            }
        })
        
    }
    
    
    private fun setupRouteService() {
        routeService = RouteService(this)
    }
    
    private fun setupLocationService() {
        locationService = LocationService(this)
        
        locationService.currentLocation.observe(this, Observer { location ->
            currentLocation = location
            LogUtils.d("MainActivity", "Location received: ${location.latitude}, ${location.longitude} (±${location.accuracy.toInt()}m)")
            updateMapCenter(location)
            updateNavigationInstructions(location)
            updateAllWaypointMarkers()
        })

        locationService.locationAccuracy.observe(this, Observer { accuracy ->
            if (accuracy == null) {
                gpsAccuracyText.text = "GPS: locating…"
            } else {
                val label = when {
                    accuracy <= 10f -> "GPS: ±${accuracy.toInt()}m  ●"
                    accuracy <= 30f -> "GPS: ±${accuracy.toInt()}m  ◑"
                    accuracy <= 50f -> "GPS: ±${accuracy.toInt()}m  ○"
                    else            -> "GPS: low accuracy (±${accuracy.toInt()}m)"
                }
                gpsAccuracyText.text = label
            }
        })
        
        locationService.nearbyWaypoint.observe(this, Observer { waypoint ->
            nearbyWaypoint = waypoint
            if (waypoint != null) {
                // Reaching a stop is the payoff moment — make it hard to miss:
                // haptic buzz, a heads-up notification, and a persistent on-screen
                // card with an Explore button (not just a 3-second toast).
                vibrate()
                showArrivalCard(waypoint)
                postArrivalNotification(waypoint)
            } else {
                hideArrivalCard()
            }
            currentLocation?.let { location -> updateNavigationInstructions(location) }
        })

        locationService.tourCompleted.observe(this, Observer { completed ->
            if (completed) {
                Toast.makeText(this, getString(R.string.tour_complete_celebration), Toast.LENGTH_LONG).show()
                // Let the celebration toast breathe, then fade into the summary.
                navigationInstructionText.postDelayed({
                    launchTourCompletionActivityWithAnimation()
                }, 1500)
            }
        })

        locationService.suggestAdvance.observe(this, Observer { fromWaypoint ->
            maybePromptToAdvance(fromWaypoint)
        })

        locationService.outsideTourArea.observe(this, Observer { guidanceMessage ->
            updateGateState(guidanceMessage)
        })

    }

    /**
     * The user reached a stop earlier but walked on without tapping Continue and
     * is now closer to the next stop — offer to advance so navigation stops
     * pointing back at a stop they've already seen. Shown once per stop.
     */
    private fun maybePromptToAdvance(fromWaypoint: TourWaypoint?) {
        if (fromWaypoint == null || !tourStarted) return
        val fromIndex = locationService.getCurrentWaypointIndex()
        if (movedOnPromptHandledForIndex == fromIndex) return
        movedOnPromptHandledForIndex = fromIndex
        val next = locationService.getNextWaypoint() ?: return

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setMessage(getString(R.string.moved_on_prompt, fromWaypoint.name, next.name))
            .setPositiveButton(getString(R.string.moved_on_advance)) { _, _ ->
                locationService.moveToNextWaypoint()
                hideArrivalCard()
                currentLocation?.let { location -> updateNavigationInstructions(location) }
                updateAllWaypointMarkers()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
    
    private fun loadTour() {
        LogUtils.d("MainActivity", "Loading default tour...")
        currentTour = BruffTourData.getDefaultTour()
        currentTour?.let { tour ->
            LogUtils.d("MainActivity", "Tour loaded: ${tour.name} with ${tour.waypoints.size} waypoints")
            locationService.setCurrentTour(tour)
            addWaypointMarkersToMap(tour.waypoints)
            // Draw L-shaped placeholder routes immediately, then upgrade to OSRM road routes
            drawRouteOnMap(tour.waypoints)
            drawRoutesAsync(tour.waypoints)
            currentLocation?.let { location -> updateNavigationInstructions(location) }
        } ?: run {
            LogUtils.e("MainActivity", "Failed to load default tour!")
        }
    }

    private fun drawRoutesAsync(waypoints: List<TourWaypoint>) {
        lifecycleScope.launch {
            // Accumulates the resolved OSRM points per leg so a later leg's fetch
            // doesn't overwrite earlier legs that already resolved with a straight-line fallback.
            val resolvedLegs = arrayOfNulls<List<GeoPoint>>(waypoints.size - 1)
            for (i in 0 until waypoints.size - 1) {
                val start = GeoPoint(waypoints[i].latitude, waypoints[i].longitude)
                val end   = GeoPoint(waypoints[i + 1].latitude, waypoints[i + 1].longitude)
                try {
                    resolvedLegs[i] = withContext(Dispatchers.IO) {
                        routeService.getRoadBasedRoute(start, end)
                    }
                } catch (e: Exception) {
                    LogUtils.w("MainActivity", "OSRM route fetch failed for leg $i: ${e.message}")
                }

                // Replace placeholder polylines (and their arrows) with whatever
                // legs have resolved so far
                routePolylines.forEach { mapView.overlays.remove(it) }
                routePolylines.clear()
                routeArrowMarkers.forEach { mapView.overlays.remove(it) }
                routeArrowMarkers.clear()
                waypoints.indices.drop(1).forEachIndexed { idx, _ ->
                    val legPoints = resolvedLegs[idx] ?: listOf(
                        GeoPoint(waypoints[idx].latitude,     waypoints[idx].longitude),
                        GeoPoint(waypoints[idx + 1].latitude, waypoints[idx + 1].longitude)
                    )
                    val poly = Polyline().apply {
                        setPoints(legPoints)
                        getOutlinePaint().color = Color.argb(200, 200, 146, 42)
                        getOutlinePaint().strokeWidth = 8.0f
                        getOutlinePaint().strokeCap  = android.graphics.Paint.Cap.ROUND
                        getOutlinePaint().strokeJoin = android.graphics.Paint.Join.ROUND
                    }
                    mapView.overlays.add(poly)
                    routePolylines.add(poly)
                    addDirectionArrow(legPoints)
                }
                mapView.invalidate()
            }
        }
    }
    
    private fun addTourBoundaryToMap() {
        try {
            // Create a rectangle showing the tour area boundary
            val rectangle = Polygon()
            rectangle.points = createRectangularBoundaryPoints()
            
            // Style the boundary rectangle
            rectangle.getFillPaint().color = Color.argb(30, 33, 150, 243) // Light blue fill (30% transparency)
            rectangle.getOutlinePaint().color = Color.argb(150, 33, 150, 243) // Blue border (more opaque)
            rectangle.getOutlinePaint().strokeWidth = 3.0f
            
            // Add title for the boundary
            rectangle.title = "Bruff Tour Area"
            rectangle.snippet = "Walking tour coverage area (${BOUNDARY_WIDTH_KM}km x ${BOUNDARY_HEIGHT_KM}km)"
            
            mapView.overlays.add(rectangle)
            tourBoundaryOverlay = rectangle
            
            mapView.invalidate()
        } catch (e: Exception) {
            LogUtils.w("MainActivity", "Error adding tour boundary to map", e)
        }
    }
    
    private fun createCirclePoints(centerLat: Double, centerLon: Double, radiusKm: Double): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        val numPoints = 64 // Number of points to create a smooth circle
        
        // Convert radius from kilometers to degrees (rough approximation)
        val radiusLat = radiusKm / 111.0 // 1 degree ≈ 111 km
        val radiusLon = radiusKm / (111.0 * kotlin.math.cos(Math.toRadians(centerLat)))
        
        for (i in 0 until numPoints) {
            val angle = 2.0 * kotlin.math.PI * i / numPoints
            val lat = centerLat + radiusLat * kotlin.math.sin(angle)
            val lon = centerLon + radiusLon * kotlin.math.cos(angle)
            points.add(GeoPoint(lat, lon))
        }
        
        // Close the circle by adding the first point at the end
        points.add(points[0])
        
        return points
    }
    
    private fun calculateTourBoundary(): org.osmdroid.util.BoundingBox {
        // Convert half-width and half-height from kilometers to degrees
        val halfWidthDegrees = (BOUNDARY_WIDTH_KM / 2.0) / (111.0 * kotlin.math.cos(Math.toRadians(SEAN_WALL_CENTER_LAT)))
        val halfHeightDegrees = (BOUNDARY_HEIGHT_KM / 2.0) / 111.0
        
        return org.osmdroid.util.BoundingBox(
            SEAN_WALL_CENTER_LAT + halfHeightDegrees, // North
            SEAN_WALL_CENTER_LON + halfWidthDegrees,  // East
            SEAN_WALL_CENTER_LAT - halfHeightDegrees, // South
            SEAN_WALL_CENTER_LON - halfWidthDegrees   // West
        )
    }
    
    private fun createRectangularBoundaryPoints(): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        
        // Convert half-width and half-height from kilometers to degrees
        val halfWidthDegrees = (BOUNDARY_WIDTH_KM / 2.0) / (111.0 * kotlin.math.cos(Math.toRadians(SEAN_WALL_CENTER_LAT)))
        val halfHeightDegrees = (BOUNDARY_HEIGHT_KM / 2.0) / 111.0
        
        // Create rectangle corners (clockwise from top-left)
        points.add(GeoPoint(SEAN_WALL_CENTER_LAT + halfHeightDegrees, SEAN_WALL_CENTER_LON - halfWidthDegrees)) // Top-left
        points.add(GeoPoint(SEAN_WALL_CENTER_LAT + halfHeightDegrees, SEAN_WALL_CENTER_LON + halfWidthDegrees)) // Top-right
        points.add(GeoPoint(SEAN_WALL_CENTER_LAT - halfHeightDegrees, SEAN_WALL_CENTER_LON + halfWidthDegrees)) // Bottom-right
        points.add(GeoPoint(SEAN_WALL_CENTER_LAT - halfHeightDegrees, SEAN_WALL_CENTER_LON - halfWidthDegrees)) // Bottom-left
        
        // Close the rectangle by adding the first point at the end
        points.add(points[0])
        
        return points
    }
    
    private fun requestLocationPermissions() {
        LogUtils.d("MainActivity", "Checking location permissions...")
        
        val fineLocationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        LogUtils.d("MainActivity", "Fine location granted: $fineLocationGranted")
        LogUtils.d("MainActivity", "Coarse location granted: $coarseLocationGranted")
        
        when {
            fineLocationGranted -> {
                LogUtils.d("MainActivity", "Location permissions granted - setting up location tracking")
                setupLocationTracking()
            }
            else -> {
                LogUtils.d("MainActivity", "Location permissions not granted - requesting permissions")
                Toast.makeText(this, "Location permission needed for your walking tour", Toast.LENGTH_LONG).show()
                locationPermissionRequest.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }
    }
    
    private fun setupLocationTracking() {
        try {
            LogUtils.d("MainActivity", "Setting up location tracking...")
            
            myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView)
            LogUtils.d("MainActivity", "MyLocationOverlay created")
            
            myLocationOverlay?.enableMyLocation()
            // Do NOT call enableFollowLocation() — updateMapCenter() already animates
            // the map on every fix; enabling both causes the two systems to fight.
            LogUtils.d("MainActivity", "My-location overlay enabled")
            
            // Set custom person icon for user location
            val personDrawable = ContextCompat.getDrawable(this, R.drawable.ic_person_location)
            val personBitmap = drawableToBitmap(personDrawable)
            if (personBitmap != null) {
                myLocationOverlay?.setPersonIcon(personBitmap)
                myLocationOverlay?.setDirectionIcon(personBitmap)
                LogUtils.d("MainActivity", "Custom person icon set")
            } else {
                LogUtils.w("MainActivity", "Failed to create person icon bitmap")
            }
            
            myLocationOverlay?.let { overlay ->
                mapView.overlays.add(overlay)
                LogUtils.d("MainActivity", "Location overlay added to map")
            }
            
            locationService.startLocationUpdates()
            LogUtils.d("MainActivity", "Location service started")
            
            Toast.makeText(this, "Location tracking active", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            LogUtils.e("MainActivity", "Error setting up location tracking", e)
            Toast.makeText(this, "Location tracking setup failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun addWaypointMarkersToMap(waypoints: List<TourWaypoint>) {
        LogUtils.d("MainActivity", "Adding ${waypoints.size} waypoint markers to map")
        waypointMarkers.clear()
        
        waypoints.forEachIndexed { index, waypoint ->
            LogUtils.d("BruffTour", "Creating marker ${index + 1} for waypoint: ${waypoint.name}")
            LogUtils.d("BruffTour", "Marker position: ${waypoint.latitude}, ${waypoint.longitude}")
            val marker = Marker(mapView)
            marker.position = GeoPoint(waypoint.latitude, waypoint.longitude)
            LogUtils.d("BruffTour", "Marker GeoPoint set: ${marker.position.latitude}, ${marker.position.longitude}")
            
            // Set context-aware hover help
            updateMarkerHoverHelp(marker, waypoint, index)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            
            // Set appropriate icon based on waypoint status
            updateWaypointMarkerIcon(marker, index)
            
            // Note: Built-in marker click listeners are unreliable in OSMDroid
            // Using touch overlay instead for reliable click detection
            
            // Enable touch events for this marker
            marker.setInfoWindow(null) // Disable info window to prevent conflicts
            marker.isDraggable = false // Ensure it's not draggable unless intended
            
            mapView.overlays.add(marker)
            waypointMarkers.add(marker)
            LogUtils.d("MainActivity", "Marker ${index + 1} added with single-click listener")
        }
        
        LogUtils.d("MainActivity", "All ${waypoints.size} markers added with single-click listeners")
        
        // Add smart touch overlay for marker image previews
        addSmartTouchOverlay(waypoints)
        
        // Force map refresh
        mapView.invalidate()
        
        LogUtils.d("MainActivity", "Map ready for marker clicks")
    }
    
    private fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        return try {
            if (drawable == null) return null
            
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                return drawable.bitmap
            }
            
            // Ensure drawable has valid dimensions
            val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 32
            val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 32
            
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        } catch (e: Exception) {
            LogUtils.w("MainActivity", "Error converting drawable to bitmap", e)
            null
        }
    }
    
    private fun updateWaypointMarkerIcon(marker: Marker, waypointIndex: Int) {
        try {
            val markerNumber = waypointIndex + 1
            val currentWaypointIndex = locationService.getCurrentWaypointIndex()
            
            val numberedIcon = when {
                waypointIndex < currentWaypointIndex -> {
                    // Completed waypoint - gray with checkmark
                    createCompletedMarkerIcon(markerNumber)
                }
                waypointIndex == currentWaypointIndex -> {
                    // Current target waypoint - bright blue and will flash
                    createCurrentWaypointMarkerIcon(markerNumber)
                }
                else -> {
                    // Future waypoint - green
                    createFutureWaypointMarkerIcon(markerNumber)
                }
            }
            
            marker.icon = numberedIcon
            
            // Add flashing animation to current waypoint
            if (waypointIndex == currentWaypointIndex) {
                startMarkerFlashAnimation(marker)
            } else {
                stopMarkerFlashAnimation(marker)
            }
            
            LogUtils.d("MainActivity", "Set icon for marker $markerNumber (status: ${when {
                waypointIndex < currentWaypointIndex -> "completed"
                waypointIndex == currentWaypointIndex -> "current"
                else -> "future"
            }})")
        } catch (e: Exception) {
            LogUtils.w("MainActivity", "Error updating waypoint marker icon", e)
        }
    }
    
    private fun updateMarkerHoverHelp(marker: Marker, waypoint: TourWaypoint, waypointIndex: Int) {
        val position = waypointIndex + 1
        // Show just the name on hover, description will be shown in info window
        marker.title = "${position}. ${waypoint.name}"
        marker.snippet = waypoint.description
    }
    
    private fun updateAllWaypointMarkers() {
        currentTour?.waypoints?.let { waypoints ->
            waypointMarkers.forEachIndexed { index, marker ->
                updateWaypointMarkerIcon(marker, index)
                updateMarkerHoverHelp(marker, waypoints[index], index)
            }
        }
        mapView.invalidate()
    }
    
    private fun drawRouteOnMap(waypoints: List<TourWaypoint>) {
        // Clear existing route polylines and their direction arrows
        routePolylines.forEach { mapView.overlays.remove(it) }
        routePolylines.clear()
        routeArrowMarkers.forEach { mapView.overlays.remove(it) }
        routeArrowMarkers.clear()

        // Create road-following routes between consecutive waypoints
        for (i in 0 until waypoints.size - 1) {
            val startPoint = GeoPoint(waypoints[i].latitude, waypoints[i].longitude)
            val endPoint = GeoPoint(waypoints[i + 1].latitude, waypoints[i + 1].longitude)

            // Use enhanced road-based routing with fallback
            val routePoints = try {
                routeService.getRoadBasedRouteSync(startPoint, endPoint)
            } catch (e: Exception) {
                // Fallback to direct line if routing fails
                listOf(startPoint, endPoint)
            }

            val polyline = Polyline().apply {
                setPoints(routePoints)
                // Amber — the tour's own waypoint-to-waypoint path, the single
                // route line shown on the map.
                getOutlinePaint().color = Color.argb(230, 200, 146, 42)
                getOutlinePaint().strokeWidth = 8.0f
                getOutlinePaint().strokeCap = android.graphics.Paint.Cap.ROUND
                getOutlinePaint().strokeJoin = android.graphics.Paint.Join.ROUND
            }

            mapView.overlays.add(polyline)
            routePolylines.add(polyline)

            addDirectionArrow(routePoints)
        }

        mapView.invalidate()
    }

    /**
     * Places a small chevron at the midpoint of a tour route segment, rotated
     * to the bearing of travel — makes the walking direction (waypoint order)
     * obvious at a glance, distinct from the live route-to-you line.
     */
    private fun addDirectionArrow(routePoints: List<GeoPoint>) {
        if (routePoints.size < 2) return
        val midIndex = routePoints.size / 2
        // Bearing is taken from the leg's overall start/end points, not the
        // points immediately either side of the midpoint — real OSRM road
        // geometry can double back briefly at a junction, and using only
        // adjacent points picked up that local wiggle instead of the leg's
        // actual direction of travel (arrow pointed backwards on some legs).
        val from = routePoints.first()
        val to = routePoints.last()
        val bearing = Location("").apply {
            latitude = from.latitude
            longitude = from.longitude
        }.bearingTo(Location("").apply {
            latitude = to.latitude
            longitude = to.longitude
        })

        val arrow = Marker(mapView).apply {
            position = routePoints[midIndex]
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_route_arrow)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            // osmdroid's Marker.rotation turns out to be counter-clockwise,
            // opposite to Location.bearingTo()'s clockwise-from-north degrees
            // (confirmed on-device: east/west legs pointed backwards while
            // north/south legs looked right, since south == -south mod 360).
            rotation = -bearing
            isFlat = true
            setInfoWindow(null)
        }
        mapView.overlays.add(arrow)
        routeArrowMarkers.add(arrow)
    }
    
    private fun updateMapCenter(location: Location) {
        if (!followMode) return // user is manually panning/zooming — don't fight them
        val geoPoint = GeoPoint(location.latitude, location.longitude)
        // Center the map on current location with smooth animation
        mapView.controller.animateTo(geoPoint, 17.5, 1000L)
    }
    
    /**
     * Drives the boundary gate overlay: shown until the user confirms they're
     * inside the tour area and taps Start. Called on every accepted GPS fix.
     */
    private fun updateGateState(outsideMessage: String?) {
        if (tourStarted) return // gate already dismissed

        if (outsideMessage == null) {
            consecutiveOutsideGateFixes = 0
            returnToIntroJob?.cancel()
            returnToIntroJob = null
            gateStatusText.text = getString(R.string.gate_ready)
            gateDistanceText.text = ""
            startTourButton.visibility = View.VISIBLE
            gateOverrideButton.visibility = View.GONE
        } else {
            consecutiveOutsideGateFixes++
            gateStatusText.text = getString(R.string.outside_bruff_message)
            startTourButton.visibility = View.GONE
            // Always give the user a way through — GPS may just be wrong.
            gateOverrideButton.visibility = View.VISIBLE
            currentLocation?.let { location ->
                val distance = locationService.calculateDistance(
                    location.latitude, location.longitude,
                    SEAN_WALL_CENTER_LAT, SEAN_WALL_CENTER_LON
                )
                val distanceText = if (distance < 1000) {
                    "${distance.toInt()}m"
                } else {
                    "${String.format("%.1f", distance / 1000)}km"
                }
                gateDistanceText.text = getString(R.string.gate_distance_away, distanceText)
            }

            // Bounce back to the intro screen only after several consecutive
            // outside fixes (not one noisy reading) and a generous delay.
            if (consecutiveOutsideGateFixes >= GATE_OUTSIDE_FIXES_BEFORE_RETURN &&
                returnToIntroJob == null
            ) {
                returnToIntroJob = lifecycleScope.launch {
                    delay(GATE_OUTSIDE_RETURN_DELAY_MS)
                    returnToIntro()
                }
            }
        }
    }

    private fun returnToIntro() {
        startActivity(Intent(this, IntroActivity::class.java))
        finish()
    }
    
    private fun showWaypointDetails(waypoint: TourWaypoint) {
        // Remember whether this was opened because the user just arrived — if so,
        // backing out of the detail screen still counts as "done with this stop".
        detailOpenedOnArrival = nearbyWaypoint?.id == waypoint.id
        val intent = Intent(this, WaypointDetailActivity::class.java).apply {
            putExtra(WaypointDetailActivity.EXTRA_WAYPOINT_NAME, waypoint.name)
            putExtra(WaypointDetailActivity.EXTRA_WAYPOINT_DESCRIPTION, waypoint.description)
            putExtra(WaypointDetailActivity.EXTRA_WAYPOINT_HISTORICAL_INFO, waypoint.historicalInfo)
            putExtra(WaypointDetailActivity.EXTRA_WAYPOINT_IMAGE_URL, waypoint.imageUrl)
            putExtra(WaypointDetailActivity.EXTRA_IS_LAST_WAYPOINT, locationService.isTourCompleted())
        }
        waypointDetailsLauncher.launch(intent)
    }
    
    private fun handleLocationPermissionDenied() {
        Toast.makeText(
            this, 
            getString(R.string.location_permission_required), 
            Toast.LENGTH_LONG
        ).show()
        
        // Show dialog explaining why location is needed
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.location_permission_title))
            .setMessage(getString(R.string.location_permission_explanation))
            .setPositiveButton(getString(R.string.settings)) { _, _ ->
                openAppSettings()
            }
            .setNegativeButton(getString(R.string.continue_anyway)) { _, _ ->
                // Without location we can't confirm the user is in the boundary —
                // bypass the gate directly rather than leaving them stuck on it.
                // Live navigation can't work, so say so plainly instead of
                // leaving the bar stuck on "Loading navigation…".
                locationTrackingUnavailable = true
                startTour()
                navigationInstructionText.text =
                    getString(R.string.navigation_unavailable_no_location)
                distanceInfoText.text = ""
                gpsAccuracyText.text = getString(R.string.gps_permission_denied)
            }
            .show()
    }
    
    private fun openAppSettings() {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = android.net.Uri.fromParts("package", packageName, null)
        startActivity(intent)
    }

    // ---- Arrival cue (card + notification) -----------------------------------

    private fun showArrivalCard(waypoint: TourWaypoint) {
        arrivalCardTitle.text = getString(R.string.arrival_card_title, waypoint.name)
        arrivalCard.visibility = View.VISIBLE
    }

    private fun hideArrivalCard() {
        if (::arrivalCard.isInitialized) arrivalCard.visibility = View.GONE
    }

    private fun createArrivalNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = android.app.NotificationChannel(
            ARRIVAL_CHANNEL_ID,
            getString(R.string.arrival_channel_name),
            android.app.NotificationManager.IMPORTANCE_HIGH
        ).apply { description = getString(R.string.arrival_channel_desc) }
        (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun postArrivalNotification(waypoint: TourWaypoint) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return // user declined notifications — the on-screen card still shows
        }

        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = android.app.PendingIntent.getActivity(
            this, 0, tapIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(this, ARRIVAL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.arrival_notification_title, waypoint.name))
            .setContentText(getString(R.string.arrival_notification_text))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        androidx.core.app.NotificationManagerCompat.from(this)
            .notify(ARRIVAL_NOTIFICATION_ID, notification)
    }
    
    private fun launchTourCompletionActivityWithAnimation() {
        val intent = Intent(this, TourCompletionActivity::class.java)
        
        // Use modern transition API
        val options = android.app.ActivityOptions.makeCustomAnimation(
            this,
            android.R.anim.fade_in,
            android.R.anim.fade_out
        )
        
        startActivity(intent, options.toBundle())
        finish()
    }
    
    
    
    private fun updateNavigationInstructions(location: Location) {
        // Map-only degraded mode after a denied location permission — the nav bar
        // shows a fixed explanation set in handleLocationPermissionDenied().
        if (locationTrackingUnavailable) return

        val currentWaypoint = locationService.getCurrentWaypoint()

        // Check if user is currently near a waypoint (has arrived)
        nearbyWaypoint?.let { arrivedWaypoint ->
            setNavigationTextWithClickableLink("Arrived at ${arrivedWaypoint.name}", arrivedWaypoint.name, arrivedWaypoint)
            distanceInfoText.text = "Tap location name above to view details"
            return
        }
        
        // Normal navigation instructions when not at a waypoint
        currentWaypoint?.let { waypoint ->
            val instruction = locationService.getNavigationInstruction(location, waypoint)
            val bearing = locationService.getBearing(location, waypoint)
            val arrow = getDirectionalArrow(bearing)
            setNavigationTextWithClickableLink("$arrow ${instruction.direction} to ${waypoint.name}", waypoint.name, waypoint)
            distanceInfoText.text = "${instruction.distance} • ${instruction.estimatedTime}"
        } ?: run {
            navigationInstructionText.text = getString(R.string.tour_complete)
            distanceInfoText.text = getString(R.string.well_done)
        }
    }
    
    private fun setNavigationTextWithClickableLink(fullText: String, linkText: String, waypoint: TourWaypoint) {
        val spannableString = SpannableString(fullText)
        val startIndex = fullText.indexOf(linkText)
        val endIndex = startIndex + linkText.length
        
        if (startIndex >= 0) {
            val clickableSpan = object : ClickableSpan() {
                override fun onClick(widget: View) {
                    showWaypointDetails(waypoint)
                }
            }
            
            spannableString.setSpan(clickableSpan, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        
        navigationInstructionText.text = spannableString
    }
    
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        // Refresh map display to ensure proper state after returning from other activities
        mapView.invalidate()
        
        // Restart location updates when resuming
        LogUtils.d("MainActivity", "onResume: restarting location updates")
        locationService.startLocationUpdates()
        
        // Force a navigation refresh when resuming
        currentLocation?.let { location ->
            LogUtils.d("MainActivity", "onResume: forcing location update")
            updateNavigationInstructions(location)
        }
    }
    
    override fun onPause() {
        super.onPause()
        mapView.onPause()
        locationService.stopLocationUpdates()
        // Record when the tour was last in front of the user, so a quick return
        // resumes silently but a long absence prompts (see promptResumeOrRestart).
        if (tourStarted) locationService.touchSession()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        locationService.stopLocationUpdates()
        
        // Stop all marker animations
        stopAllMarkerAnimations()
        
        
        // Clean up boundary overlay
        tourBoundaryOverlay?.let { overlay ->
            mapView.overlays.remove(overlay)
            tourBoundaryOverlay = null
        }
        
        // Recycle all marker bitmaps to prevent memory leaks
        markerBitmaps.forEach { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        markerBitmaps.clear()
        
        // Clean up location overlay
        myLocationOverlay?.let { overlay ->
            overlay.disableMyLocation()
            overlay.disableFollowLocation()
            mapView.overlays.remove(overlay)
            myLocationOverlay = null
        }
    }
    
    private fun createNumberedMarkerIcon(number: Int, backgroundColor: Int = Color.argb(255, 76, 175, 80)): Drawable? {
        return try {
            val size = 80
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            // Draw colored circle
            val paint = android.graphics.Paint().apply {
                color = backgroundColor
                isAntiAlias = true
                style = android.graphics.Paint.Style.FILL
            }
            canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, paint)
            
            // Draw white border
            paint.apply {
                color = Color.WHITE
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 4f
            }
            canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, paint)
            
            // Draw number text
            paint.apply {
                color = Color.WHITE
                style = android.graphics.Paint.Style.FILL
                textSize = 32f
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            
            val textBounds = android.graphics.Rect()
            paint.getTextBounds(number.toString(), 0, number.toString().length, textBounds)
            val textY = size / 2f + textBounds.height() / 2f
            canvas.drawText(number.toString(), size / 2f, textY, paint)
            
            markerBitmaps.add(bitmap)
            BitmapDrawable(resources, bitmap)
        } catch (e: Exception) {
            LogUtils.w("MainActivity", "Error creating numbered marker", e)
            null
        }
    }
    
    private fun createCurrentWaypointMarkerIcon(number: Int): Drawable? {
        // Bright blue for current waypoint
        return createNumberedMarkerIcon(number, Color.argb(255, 33, 150, 243))
    }
    
    private fun createFutureWaypointMarkerIcon(number: Int): Drawable? {
        // Green for future waypoints
        return createNumberedMarkerIcon(number, Color.argb(255, 76, 175, 80))
    }
    
    private fun createCompletedMarkerIcon(number: Int): Drawable? {
        return try {
            val size = 80
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            // Draw gray circle for completed
            val paint = android.graphics.Paint().apply {
                color = Color.argb(255, 158, 158, 158) // Gray
                isAntiAlias = true
                style = android.graphics.Paint.Style.FILL
            }
            canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, paint)
            
            // Draw white border
            paint.apply {
                color = Color.WHITE
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 4f
            }
            canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, paint)
            
            // Draw checkmark instead of number
            paint.apply {
                color = Color.WHITE
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 6f
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
            }
            
            // Draw checkmark path
            val checkPath = android.graphics.Path()
            checkPath.moveTo(size * 0.3f, size * 0.5f)
            checkPath.lineTo(size * 0.45f, size * 0.65f)
            checkPath.lineTo(size * 0.7f, size * 0.35f)
            canvas.drawPath(checkPath, paint)
            
            markerBitmaps.add(bitmap)
            BitmapDrawable(resources, bitmap)
        } catch (e: Exception) {
            LogUtils.w("MainActivity", "Error creating completed marker", e)
            createNumberedMarkerIcon(number, Color.argb(255, 158, 158, 158)) // Fallback
        }
    }
    
    
    private fun showImagePreview(waypoint: TourWaypoint) {
        // Remove any existing preview
        hideImagePreview()
        
        // Inflate the preview layout
        val layoutInflater = LayoutInflater.from(this)
        val previewView = layoutInflater.inflate(R.layout.image_preview_overlay, null)
        
        // Set up the preview content
        val previewImage = previewView.findViewById<ImageView>(R.id.preview_image)
        val previewTitle = previewView.findViewById<TextView>(R.id.preview_title)
        val previewDescription = previewView.findViewById<TextView>(R.id.preview_description)
        val closeButton = previewView.findViewById<Button>(R.id.preview_close_button)
        
        // Set title and description
        previewTitle.text = waypoint.name
        previewDescription.text = waypoint.description
        
        // Load image
        waypoint.imageUrl?.let { imageUrl ->
            Picasso.get()
                .load(imageUrl)
                .placeholder(R.drawable.ic_launcher_foreground)
                .error(R.drawable.ic_launcher_foreground)
                .into(previewImage)
        } ?: run {
            previewImage.setImageResource(R.drawable.ic_launcher_foreground)
        }
        
        // Set up close button
        closeButton.setOnClickListener {
            hideImagePreview()
        }
        
        // Add to main layout with click outside to close
        val mainLayout = findViewById<FrameLayout>(android.R.id.content)
        
        // Create container that fills the screen to catch outside clicks
        val containerView = FrameLayout(this)
        containerView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        containerView.setOnClickListener {
            hideImagePreview()
        }
        
        // Set preview layout params
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.gravity = Gravity.CENTER
        previewView.layoutParams = layoutParams
        
        containerView.addView(previewView)
        mainLayout.addView(containerView)
        
        imagePreviewOverlay = containerView
        
        LogUtils.d("BruffTour", "Image preview shown for: ${waypoint.name}")
    }
    
    private fun hideImagePreview() {
        imagePreviewOverlay?.let { overlay ->
            val mainLayout = findViewById<FrameLayout>(android.R.id.content)
            mainLayout.removeView(overlay)
            imagePreviewOverlay = null
        }
    }
    
    private fun startMarkerFlashAnimation(marker: Marker) {
        // Stop any existing animation for this marker
        stopMarkerFlashAnimation(marker)
        
        // Create a pulsing alpha animation
        val animator = android.animation.ValueAnimator.ofFloat(1.0f, 0.3f, 1.0f)
        animator.duration = 1500 // 1.5 seconds per pulse
        animator.repeatCount = android.animation.ValueAnimator.INFINITE
        animator.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        
        animator.addUpdateListener { animation ->
            val alpha = animation.animatedValue as Float
            try {
                // Update marker transparency
                marker.alpha = alpha
                mapView.invalidate()
            } catch (e: Exception) {
                // Stop animation if marker is no longer valid
                stopMarkerFlashAnimation(marker)
            }
        }
        
        animator.start()
        markerAnimators[marker] = animator
        
        LogUtils.d("MainActivity", "Started flashing animation for current waypoint marker")
    }
    
    private fun stopMarkerFlashAnimation(marker: Marker) {
        markerAnimators[marker]?.let { animator ->
            animator.cancel()
            markerAnimators.remove(marker)
            // Reset marker to full opacity
            marker.alpha = 1.0f
        }
    }
    
    private fun stopAllMarkerAnimations() {
        markerAnimators.values.forEach { animator ->
            animator.cancel()
        }
        markerAnimators.clear()
        // Reset all markers to full opacity
        waypointMarkers.forEach { marker ->
            marker.alpha = 1.0f
        }
    }
    
    private fun addSmartTouchOverlay(waypoints: List<TourWaypoint>) {
        val smartTouchOverlay = object : Overlay() {
            private var downX = 0f
            private var downY = 0f
            private var hasMoved = false
            
            override fun onTouchEvent(e: MotionEvent?, mapView: MapView?): Boolean {
                if (e == null || mapView == null) return false
                
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.x
                        downY = e.y
                        hasMoved = false
                        return false // Never consume DOWN events
                    }
                    
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = e.x - downX
                        val deltaY = e.y - downY
                        val distance = kotlin.math.sqrt((deltaX * deltaX + deltaY * deltaY).toDouble())
                        
                        if (distance > 20) { // If moved more than 20 pixels
                            hasMoved = true
                        }
                        return false // Never consume MOVE events
                    }
                    
                    MotionEvent.ACTION_UP -> {
                        // Only check for marker taps if user didn't move much
                        if (!hasMoved) {
                            LogUtils.d("BruffTour", "Static tap detected at ${e.x}, ${e.y}")
                            
                            val projection = mapView.projection
                            
                            // Find if tap is on any marker
                            waypoints.forEachIndexed { index, waypoint ->
                                val waypointGeo = GeoPoint(waypoint.latitude, waypoint.longitude)
                                val waypointPixel = Point()
                                projection.toPixels(waypointGeo, waypointPixel)
                                
                                val distance = kotlin.math.sqrt(
                                    ((e.x - waypointPixel.x) * (e.x - waypointPixel.x) + 
                                     (e.y - waypointPixel.y) * (e.y - waypointPixel.y)).toDouble()
                                )
                                
                                // If tap is close to a marker
                                if (distance < 80) {
                                    LogUtils.d("BruffTour", "Marker tapped: ${waypoint.name}")
                                    showImagePreview(waypoint)
                                    
                                    vibrate()
                                    
                                    return true // Only consume when we actually handle a marker tap
                                }
                            }
                        }
                        
                        return false // Let map handle all other touches
                    }
                }
                
                return false
            }
        }
        
        // Add overlay to handle taps
        mapView.overlays.add(smartTouchOverlay)
        LogUtils.d("BruffTour", "Smart touch overlay added for marker previews")
    }
    
    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(android.os.VibratorManager::class.java)
                vm?.defaultVibrator?.vibrate(
                    android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                (getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator)?.vibrate(
                    android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                (getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator)?.vibrate(50)
            }
        } catch (_: Exception) {}
    }

    private fun getDirectionalArrow(bearing: Float): String {
        val normalizedBearing = (bearing + 360) % 360
        return when {
            normalizedBearing < 22.5 || normalizedBearing >= 337.5 -> "⬆️" // North
            normalizedBearing < 67.5 -> "↗️" // Northeast  
            normalizedBearing < 112.5 -> "➡️" // East
            normalizedBearing < 157.5 -> "↘️" // Southeast
            normalizedBearing < 202.5 -> "⬇️" // South
            normalizedBearing < 247.5 -> "↙️" // Southwest
            normalizedBearing < 292.5 -> "⬅️" // West
            else -> "↖️" // Northwest
        }
    }
    
}