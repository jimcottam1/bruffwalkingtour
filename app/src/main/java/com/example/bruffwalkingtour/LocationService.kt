package com.example.bruffwalkingtour

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.*
import kotlin.math.*

class LocationService(private val context: Context) {

    companion object {
        // Sean Wall Monument centered rectangular boundary - matching MainActivity
        private const val SEAN_WALL_CENTER_LAT = 52.47785299293757
        private const val SEAN_WALL_CENTER_LON = -8.54801677334652
        private const val BOUNDARY_WIDTH_KM = 1.5  // 1.5km wide (east-west)
        private const val BOUNDARY_HEIGHT_KM = 3.0 // 3km long (north-south)

        // Only accept GPS fixes with accuracy better than this threshold.
        // After ACCURACY_TIMEOUT_MS without a good fix, accept whatever is available.
        private const val ACCURACY_THRESHOLD_M = 50f
        private const val ACCURACY_TIMEOUT_MS = 15_000L

        // Extra distance beyond proximityRadius the user must move before an arrival
        // is cleared. Prevents GPS jitter right at the boundary from re-firing the
        // "arrived" toast repeatedly.
        private const val EXIT_HYSTERESIS_M = 5f

        // Average walking speed used to estimate time-to-waypoint, in metres/minute
        // (~5 km/h). Must match WALKING_SPEED_MPM in web/js/data.js.
        private const val WALKING_SPEED_MPM = 83f

        // Tour progress is persisted so an interrupted session (activity
        // recreation, process death while the user reads a stop's page) resumes
        // where it left off instead of restarting from stop 1. Mirrors the web
        // app's bruff_tour_state / bruff_gate_passed in sessionStorage.
        private const val PROGRESS_PREFS = "bruff_tour_progress"
        private const val KEY_SESSION_ACTIVE = "session_active"
        private const val KEY_WAYPOINT_INDEX = "waypoint_index"
        private const val KEY_LAST_ACTIVE = "last_active_at"
    }

    private val progressPrefs =
        context.getSharedPreferences(PROGRESS_PREFS, Context.MODE_PRIVATE)

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _currentLocation = MutableLiveData<Location>()
    val currentLocation: LiveData<Location> = _currentLocation

    private val _nearbyWaypoint = MutableLiveData<TourWaypoint?>()
    val nearbyWaypoint: LiveData<TourWaypoint?> = _nearbyWaypoint

    private val _distanceToNext = MutableLiveData<Float>()
    val distanceToNext: LiveData<Float> = _distanceToNext

    private val _tourCompleted = MutableLiveData<Boolean>()
    val tourCompleted: LiveData<Boolean> = _tourCompleted

    private val _outsideTourArea = MutableLiveData<String?>()
    val outsideTourArea: LiveData<String?> = _outsideTourArea

    /**
     * Set to the current waypoint when the user reached it earlier (an arrival
     * was notified) but never tapped Continue, and has since walked clearly past
     * it toward the next stop. Lets the UI offer to advance rather than leaving
     * navigation pointing backwards at a stop the user has already seen.
     */
    private val _suggestAdvance = MutableLiveData<TourWaypoint?>()
    val suggestAdvance: LiveData<TourWaypoint?> = _suggestAdvance

    /** Accuracy of the last accepted GPS fix in metres, or null if no fix yet. */
    private val _locationAccuracy = MutableLiveData<Float?>()
    val locationAccuracy: LiveData<Float?> = _locationAccuracy

    private var currentTour: WalkingTour? = null
    private var currentWaypointIndex = 0
    private var lastNotifiedWaypoint: TourWaypoint? = null
    // Waypoint index for which an arrival has been announced this session — used
    // to detect "arrived but walked on without continuing" (see checkProximity).
    private var arrivalNotifiedForIndex = -1
    private var locationStartTime = 0L

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        3000L
    ).apply {
        setMinUpdateIntervalMillis(1000L)
        setMaxUpdateDelayMillis(5000L)
        setWaitForAccurateLocation(false)
    }.build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                LogUtils.d("LocationService", "[$timestamp] Raw fix: ${location.latitude}, ${location.longitude} (accuracy: ${location.accuracy}m)")

                val elapsed = System.currentTimeMillis() - locationStartTime
                val accuracyOk = location.accuracy <= ACCURACY_THRESHOLD_M
                val timedOut = elapsed >= ACCURACY_TIMEOUT_MS

                if (!accuracyOk && !timedOut) {
                    LogUtils.d("LocationService", "Skipping inaccurate fix (${location.accuracy}m > ${ACCURACY_THRESHOLD_M}m, waited ${elapsed}ms)")
                    _locationAccuracy.value = location.accuracy
                    return
                }

                _locationAccuracy.value = location.accuracy
                _currentLocation.value = location
                checkIfInTourArea(location)
                checkProximityToWaypoints(location)
                updateDistanceToNextWaypoint(location)
            } ?: LogUtils.w("LocationService", "Location update received but location was null")
        }
        
        override fun onLocationAvailability(locationAvailability: LocationAvailability) {
            super.onLocationAvailability(locationAvailability)
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            LogUtils.d("LocationService", "[$timestamp] Location availability: ${locationAvailability.isLocationAvailable}")
        }
    }
    
    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        // Check if location services are enabled by admin
        if (!AdminSettingsActivity.isLocationEnabled(context)) {
            LogUtils.d("LocationService", "Location updates disabled by admin - using mock location")
            // Use mock location data for testing
            useMockLocation()
            return
        }
        
        // Check if device GPS is enabled
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        
        LogUtils.d("LocationService", "GPS enabled: $isGpsEnabled, Network enabled: $isNetworkEnabled")
        
        if (!isGpsEnabled && !isNetworkEnabled) {
            LogUtils.w("LocationService", "No location providers enabled - cannot get location")
            // Still try to request updates in case user enables GPS later
        }
        
        locationStartTime = System.currentTimeMillis()
        _locationAccuracy.value = null
        LogUtils.d("LocationService", "Starting real location updates")
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                context.mainLooper
            )
            LogUtils.d("LocationService", "Location updates requested successfully")
        } catch (e: Exception) {
            LogUtils.e("LocationService", "Failed to request location updates", e)
        }
    }
    
    fun stopLocationUpdates() {
        LogUtils.d("LocationService", "Stopping location updates")
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
    
    private fun useMockLocation() {
        // Use mock location at Sean Wall Monument for testing when location is disabled
        val mockLocation = android.location.Location("mock").apply {
            latitude = SEAN_WALL_CENTER_LAT
            longitude = SEAN_WALL_CENTER_LON
            accuracy = 10f
            time = System.currentTimeMillis()
        }
        
        LogUtils.d("LocationService", "Using mock location: $SEAN_WALL_CENTER_LAT, $SEAN_WALL_CENTER_LON")
        _currentLocation.value = mockLocation
        checkIfInTourArea(mockLocation)
        checkProximityToWaypoints(mockLocation)
        updateDistanceToNextWaypoint(mockLocation)
    }
    
    fun setCurrentTour(tour: WalkingTour) {
        currentTour = tour
        // Resume mid-tour if a previous session was interrupted (activity
        // recreation / process death). A fresh start or a completed tour has no
        // active session, so it begins at the first stop.
        val savedIndex = progressPrefs.getInt(KEY_WAYPOINT_INDEX, 0)
        currentWaypointIndex =
            if (hasActiveSession() && savedIndex in tour.waypoints.indices) savedIndex else 0
        _tourCompleted.value = false
        _nearbyWaypoint.value = null // Clear any stuck "arrived" state
        _suggestAdvance.value = null
        lastNotifiedWaypoint = null // Reset notification tracking
        arrivalNotifiedForIndex = -1
        persistProgress()
    }

    /**
     * Marks that the user has passed the boundary gate and is now on the walk.
     * From this point an interrupted session resumes instead of re-gating.
     */
    fun beginTourSession() {
        progressPrefs.edit()
            .putBoolean(KEY_SESSION_ACTIVE, true)
            .putLong(KEY_LAST_ACTIVE, System.currentTimeMillis())
            .apply()
    }

    /** True when a tour is part-way through and could be resumed. */
    fun hasActiveSession(): Boolean =
        progressPrefs.getBoolean(KEY_SESSION_ACTIVE, false)

    /** Waypoint index a resumed session would start at (0-based). */
    fun savedWaypointIndex(): Int = progressPrefs.getInt(KEY_WAYPOINT_INDEX, 0)

    /**
     * Milliseconds since the tour was last actively used. A short gap means an
     * interruption to resume through silently; a long gap means the user should
     * be asked whether to resume or start over.
     */
    fun millisSinceLastActive(): Long {
        val t = progressPrefs.getLong(KEY_LAST_ACTIVE, 0L)
        return if (t == 0L) Long.MAX_VALUE else System.currentTimeMillis() - t
    }

    /** Refresh the "last active" stamp — call while the tour screen is in use. */
    fun touchSession() {
        if (hasActiveSession()) {
            progressPrefs.edit().putLong(KEY_LAST_ACTIVE, System.currentTimeMillis()).apply()
        }
    }

    /** Clears persisted progress — call on completion and on an explicit restart. */
    fun clearSavedProgress() {
        progressPrefs.edit()
            .putBoolean(KEY_SESSION_ACTIVE, false)
            .remove(KEY_WAYPOINT_INDEX)
            .remove(KEY_LAST_ACTIVE)
            .apply()
    }

    private fun persistProgress() {
        progressPrefs.edit()
            .putInt(KEY_WAYPOINT_INDEX, currentWaypointIndex)
            .putLong(KEY_LAST_ACTIVE, System.currentTimeMillis())
            .apply()
    }
    
    fun getCurrentWaypoint(): TourWaypoint? {
        return currentTour?.waypoints?.getOrNull(currentWaypointIndex)
    }
    
    fun getCurrentWaypointIndex(): Int {
        return currentWaypointIndex
    }
    
    fun getNextWaypoint(): TourWaypoint? {
        return currentTour?.waypoints?.getOrNull(currentWaypointIndex + 1)
    }
    
    fun moveToNextWaypoint() {
        currentTour?.let { tour ->
            if (currentWaypointIndex < tour.waypoints.size - 1) {
                currentWaypointIndex++
                lastNotifiedWaypoint = null // Reset for new waypoint
                arrivalNotifiedForIndex = -1
                _nearbyWaypoint.value = null // Clear arrival state immediately
                _suggestAdvance.value = null
                persistProgress()

                LogUtils.d("LocationService", "Moved to waypoint ${currentWaypointIndex + 1}: ${getCurrentWaypoint()?.name}")

                // Force immediate recalculation with current location
                _currentLocation.value?.let { location ->
                    LogUtils.d("LocationService", "Recalculating distances for new waypoint")
                    updateDistanceToNextWaypoint(location)
                    checkProximityToWaypoints(location)
                }
            } else {
                LogUtils.d("LocationService", "Tour completed!")
                clearSavedProgress()
                _tourCompleted.value = true
            }
        }
    }
    
    fun isTourCompleted(): Boolean {
        return currentTour?.let { tour ->
            currentWaypointIndex >= tour.waypoints.size - 1
        } ?: false
    }
    
    private fun checkIfInTourArea(currentLocation: Location) {
        val isOutside = isOutsideRectangularBoundary(currentLocation.latitude, currentLocation.longitude)
        
        if (isOutside) {
            _outsideTourArea.value = "Head back to Bruff town centre"
        } else {
            _outsideTourArea.value = null
        }
    }
    
    private fun isOutsideRectangularBoundary(lat: Double, lon: Double): Boolean {
        // Convert half-width and half-height from kilometers to degrees
        val halfWidthDegrees = (BOUNDARY_WIDTH_KM / 2.0) / (111.0 * kotlin.math.cos(Math.toRadians(SEAN_WALL_CENTER_LAT)))
        val halfHeightDegrees = (BOUNDARY_HEIGHT_KM / 2.0) / 111.0
        
        val northBound = SEAN_WALL_CENTER_LAT + halfHeightDegrees
        val southBound = SEAN_WALL_CENTER_LAT - halfHeightDegrees
        val eastBound = SEAN_WALL_CENTER_LON + halfWidthDegrees
        val westBound = SEAN_WALL_CENTER_LON - halfWidthDegrees
        
        return lat > northBound || lat < southBound || lon > eastBound || lon < westBound
    }
    
    
    private fun checkProximityToWaypoints(currentLocation: Location) {
        val currentWaypoint = getCurrentWaypoint() ?: return
        
        val distance = calculateDistance(
            currentLocation.latitude,
            currentLocation.longitude,
            currentWaypoint.latitude,
            currentWaypoint.longitude
        )
        
        if (distance <= currentWaypoint.proximityRadius) {
            // Only notify if this is a new arrival AND we're checking the correct waypoint
            if (lastNotifiedWaypoint != currentWaypoint && _nearbyWaypoint.value == null) {
                _nearbyWaypoint.value = currentWaypoint
                lastNotifiedWaypoint = currentWaypoint
                arrivalNotifiedForIndex = currentWaypointIndex
            }
            _suggestAdvance.value = null
        } else if (distance > currentWaypoint.proximityRadius + EXIT_HYSTERESIS_M) {
            // Clear only once the user has moved clearly outside the radius (with a
            // hysteresis margin) - avoids re-firing the arrival toast from GPS jitter
            // right at the boundary. Only clear if we were actually at this waypoint.
            if (_nearbyWaypoint.value == currentWaypoint) {
                _nearbyWaypoint.value = null
                lastNotifiedWaypoint = null
            }

            // "Arrived here but never tapped Continue, and now walking on toward
            // the next stop" — surface a one-shot advance suggestion so the user
            // isn't left with navigation pointing back at a visited stop.
            val next = getNextWaypoint()
            if (arrivalNotifiedForIndex == currentWaypointIndex &&
                next != null &&
                distance > currentWaypoint.proximityRadius * 2
            ) {
                val distToNext = calculateDistance(
                    currentLocation.latitude, currentLocation.longitude,
                    next.latitude, next.longitude
                )
                if (distToNext < distance && _suggestAdvance.value != currentWaypoint) {
                    _suggestAdvance.value = currentWaypoint
                }
            }
        }
    }
    
    private fun updateDistanceToNextWaypoint(currentLocation: Location) {
        val nextWaypoint = getCurrentWaypoint() ?: return
        
        val distance = calculateDistance(
            currentLocation.latitude,
            currentLocation.longitude,
            nextWaypoint.latitude,
            nextWaypoint.longitude
        )
        
        LogUtils.d("LocationService", "Distance to ${nextWaypoint.name}: ${distance.toInt()}m")
        _distanceToNext.value = distance
    }
    
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }
    
    fun getBearing(currentLocation: Location, waypoint: TourWaypoint): Float {
        return currentLocation.bearingTo(Location("").apply {
            latitude = waypoint.latitude
            longitude = waypoint.longitude
        })
    }
    
    fun getNavigationInstruction(currentLocation: Location, waypoint: TourWaypoint): NavigationInstruction {
        val distance = calculateDistance(
            currentLocation.latitude,
            currentLocation.longitude,
            waypoint.latitude,
            waypoint.longitude
        )
        
        val bearing = getBearing(currentLocation, waypoint)
        val direction = getDirectionFromBearing(bearing)
        
        val distanceText = when {
            distance < 50 -> "Continue ${distance.toInt()}m"
            distance < 1000 -> "${(distance / 10).toInt() * 10}m"
            else -> "${String.format("%.1f", distance / 1000)}km"
        }
        
        val estimatedTime = "${(distance / WALKING_SPEED_MPM).toInt() + 1} min" // Assuming 5 km/h walking speed
        
        return NavigationInstruction(
            direction = direction,
            distance = distanceText,
            estimatedTime = estimatedTime
        )
    }
    
    private fun getDirectionFromBearing(bearing: Float): String {
        val normalizedBearing = (bearing + 360) % 360
        return when {
            normalizedBearing < 22.5 || normalizedBearing >= 337.5 -> "Head North"
            normalizedBearing < 67.5 -> "Head Northeast"
            normalizedBearing < 112.5 -> "Head East"
            normalizedBearing < 157.5 -> "Head Southeast"
            normalizedBearing < 202.5 -> "Head South"
            normalizedBearing < 247.5 -> "Head Southwest"
            normalizedBearing < 292.5 -> "Head West"
            else -> "Head Northwest"
        }
    }
}