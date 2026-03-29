package com.example.bruffwalkingtour

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
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
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
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
import org.osmdroid.views.overlay.infowindow.InfoWindow
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.Distance
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
    }
    
    private lateinit var mapView: MapView
    private lateinit var locationService: LocationService
    private lateinit var routeService: RouteService
    private lateinit var navigationInstructionText: TextView
    private lateinit var distanceInfoText: TextView
    private var currentTour: WalkingTour? = null
    private var waypointMarkers = mutableListOf<Marker>()
    private var routePolylines = mutableListOf<Polyline>()
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
        if (result.resultCode == RESULT_OK) {
            locationService.moveToNextWaypoint()
            
            val shouldShowCompletion = result.data?.getBooleanExtra("show_completion", false) ?: false
            if (shouldShowCompletion) {
                Toast.makeText(this, getString(R.string.tour_complete_celebration), Toast.LENGTH_LONG).show()
                
                navigationInstructionText.postDelayed({
                    launchTourCompletionActivityWithAnimation()
                }, 1500)
            } else {
                currentLocation?.let { location ->
                    updateNavigationInstructions(location)
                }
                updateAllWaypointMarkers()
            }
        }
    }
    
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
        
        // Reset location message preference for new session
        getSharedPreferences("bruff_tour_prefs", MODE_PRIVATE)
            .edit().putBoolean("outside_tour_area_message_shown", false).apply()
        
        setContentView(R.layout.activity_main)
        
        // Simple startup confirmation
        LogUtils.d("BruffTour", "MainActivity started successfully")
        
        LogUtils.d("MainActivity", "Setting up views, map, and services...")
        setupViews()
        setupMap()
        setupLocationService()
        setupRouteService()
        LogUtils.d("MainActivity", "About to load tour...")
        loadTour()
        addTourBoundaryToMap()
        requestLocationPermissions()
        LogUtils.d("MainActivity", "onCreate completed")
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
        navigationInstructionText = findViewById(R.id.navigation_instruction)
        distanceInfoText = findViewById(R.id.distance_info)
        
        // Enable clickable links in navigation text
        navigationInstructionText.movementMethod = LinkMovementMethod.getInstance()
    }
    
    private fun setupMap() {
        mapView = findViewById(R.id.mapview)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        
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
        
        // Scroll boundaries removed - they were blocking map interaction
        
        val mapController = mapView.controller
        mapController.setZoom(17.5) // Closer street level zoom
        
        val seanWallCenter = GeoPoint(SEAN_WALL_CENTER_LAT, SEAN_WALL_CENTER_LON)
        mapController.setCenter(seanWallCenter)
        
        // Enable zoom controls and proper touch handling
        mapView.setMultiTouchControls(true)   // Enable pinch-to-zoom
        mapView.setUseDataConnection(true)    // Allow downloading map tiles
        
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
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            LogUtils.d("MainActivity", "[$timestamp] Location received in MainActivity: ${location.latitude}, ${location.longitude}")
            updateMapCenter(location)
            updateNavigationInstructions(location)
            // Update all marker hover help with current location context
            updateAllWaypointMarkers()
            
            
            // Visual feedback that location is updating
            findViewById<TextView>(R.id.distance_info)?.let { textView ->
                val currentText = textView.text.toString()
                if (!currentText.contains("•")) {
                    textView.text = "$currentText • Updated $timestamp"
                }
            }
        })
        
        locationService.nearbyWaypoint.observe(this, Observer { waypoint ->
            nearbyWaypoint = waypoint
            waypoint?.let {
                Toast.makeText(this, getString(R.string.arrived_at_waypoint, it.name), Toast.LENGTH_LONG).show()
                // Update navigation text to show arrival
                currentLocation?.let { location ->
                    updateNavigationInstructions(location)
                }
            } ?: run {
                // Update navigation text back to normal
                currentLocation?.let { location ->
                    updateNavigationInstructions(location)
                }
            }
        })
        
        locationService.distanceToNext.observe(this, Observer { distance ->
            updateNavigationInfo(distance)
        })
        
        locationService.tourCompleted.observe(this, Observer { completed ->
            if (completed) {
                launchTourCompletionActivity()
            }
        })
        
        locationService.outsideTourArea.observe(this, Observer { guidanceMessage ->
            guidanceMessage?.let { message ->
                showTourAreaGuidanceMessage(message)
            }
        })
        
    }
    
    private fun loadTour() {
        LogUtils.d("MainActivity", "Loading default tour...")
        currentTour = BruffTourData.getDefaultTour()
        currentTour?.let { tour ->
            LogUtils.d("MainActivity", "Tour loaded: ${tour.name} with ${tour.waypoints.size} waypoints")
            locationService.setCurrentTour(tour)
            LogUtils.d("MainActivity", "About to add waypoint markers to map")
            addWaypointMarkersToMap(tour.waypoints)
            LogUtils.d("MainActivity", "Waypoint markers added to map")
            // Update navigation instructions when current location is available
            currentLocation?.let { location ->
                updateNavigationInstructions(location)
            }
            // Blue lines removed - apparently they were offensive to the eyes
        } ?: run {
            android.util.Log.e("MainActivity", "Failed to load default tour!")
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
            android.util.Log.w("MainActivity", "Error adding tour boundary to map", e)
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
            myLocationOverlay?.enableFollowLocation()
            LogUtils.d("MainActivity", "Location and follow location enabled")
            
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
        android.util.Log.d("MainActivity", "Adding ${waypoints.size} waypoint markers to map")
        waypointMarkers.clear()
        
        waypoints.forEachIndexed { index, waypoint ->
            LogUtils.e("BruffTour", "Creating marker ${index + 1} for waypoint: ${waypoint.name}")
            LogUtils.e("BruffTour", "Marker position: ${waypoint.latitude}, ${waypoint.longitude}")
            val marker = Marker(mapView)
            marker.position = GeoPoint(waypoint.latitude, waypoint.longitude)
            LogUtils.e("BruffTour", "Marker GeoPoint set: ${marker.position.latitude}, ${marker.position.longitude}")
            
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
            android.util.Log.w("MainActivity", "Error converting drawable to bitmap", e)
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
        // Clear existing route polylines
        routePolylines.forEach { mapView.overlays.remove(it) }
        routePolylines.clear()
        
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
                getOutlinePaint().color = Color.BLUE
                getOutlinePaint().strokeWidth = 8.0f
            }
            
            mapView.overlays.add(polyline)
            routePolylines.add(polyline)
        }
        
        mapView.invalidate()
    }
    
    private fun updateMapCenter(location: Location) {
        val geoPoint = GeoPoint(location.latitude, location.longitude)
        // Center the map on current location with smooth animation
        mapView.controller.animateTo(geoPoint, 17.5, 1000L)
    }
    
    private fun showTourAreaGuidanceMessage(message: String) {
        // Only show the message once per session to avoid spam
        val sharedPrefs = getSharedPreferences("bruff_tour_prefs", MODE_PRIVATE)
        val messageShown = sharedPrefs.getBoolean("outside_tour_area_message_shown", false)
        
        if (!messageShown) {
            Toast.makeText(this, "📍 $message", Toast.LENGTH_LONG).show()
            
            // Mark message as shown for this session
            sharedPrefs.edit().putBoolean("outside_tour_area_message_shown", true).apply()
        }
    }
    
    private fun showWaypointDetails(waypoint: TourWaypoint) {
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
                // Continue without location
            }
            .show()
    }
    
    private fun openAppSettings() {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = android.net.Uri.fromParts("package", packageName, null)
        startActivity(intent)
    }
    
    private fun launchTourCompletionActivity() {
        val intent = Intent(this, TourCompletionActivity::class.java)
        startActivity(intent)
        finish()
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
    
    private fun updateNavigationInfo(distance: Float) {
        val currentWaypoint = locationService.getCurrentWaypoint()
        currentWaypoint?.let { waypoint ->
            val distanceText = if (distance < 1000) {
                "${distance.toInt()}m to ${waypoint.name}"
            } else {
                "${String.format("%.1f", distance / 1000)}km to ${waypoint.name}"
            }
            
            supportActionBar?.subtitle = distanceText
        }
    }
    
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        // Refresh map display to ensure proper state after returning from other activities
        mapView.invalidate()
        
        // Restart location updates when resuming
        LogUtils.d("MainActivity", "onResume: restarting location updates")
        locationService.startLocationUpdates()
        
        // Force a location and distance update when resuming
        currentLocation?.let { location ->
            LogUtils.d("MainActivity", "onResume: forcing location update")
            updateNavigationInstructions(location)
            locationService.getCurrentWaypoint()?.let { waypoint ->
                val distance = locationService.calculateDistance(
                    location.latitude, location.longitude,
                    waypoint.latitude, waypoint.longitude
                )
                updateNavigationInfo(distance)
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        mapView.onPause()
        locationService.stopLocationUpdates()
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
                                    
                                    // Haptic feedback
                                    try {
                                        @Suppress("DEPRECATION")
                                        (getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator)?.vibrate(50)
                                    } catch (e: Exception) {
                                        // Ignore if vibration not available
                                    }
                                    
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