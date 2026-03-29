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
    }
    
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
    
    private var currentTour: WalkingTour? = null
    private var currentWaypointIndex = 0
    private var lastNotifiedWaypoint: TourWaypoint? = null
    
    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        3000L // 3 seconds for more responsive updates
    ).apply {
        setMinUpdateIntervalMillis(1000L) // 1 second minimum for testing
        setMaxUpdateDelayMillis(5000L) // 5 seconds maximum delay
        setWaitForAccurateLocation(false) // Don't wait for perfect accuracy
    }.build()
    
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                LogUtils.d("LocationService", "[$timestamp] Location update: ${location.latitude}, ${location.longitude} (accuracy: ${location.accuracy}m)")
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
        currentWaypointIndex = 0
        _tourCompleted.value = false
        _nearbyWaypoint.value = null // Clear any stuck "arrived" state
        lastNotifiedWaypoint = null // Reset notification tracking
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
                _nearbyWaypoint.value = null // Clear arrival state immediately
                
                LogUtils.d("LocationService", "Moved to waypoint ${currentWaypointIndex + 1}: ${getCurrentWaypoint()?.name}")
                
                // Force immediate recalculation with current location
                _currentLocation.value?.let { location ->
                    LogUtils.d("LocationService", "Recalculating distances for new waypoint")
                    updateDistanceToNextWaypoint(location)
                    checkProximityToWaypoints(location)
                }
            } else {
                LogUtils.d("LocationService", "Tour completed!")
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
            }
        } else {
            // Clear when moving away - but only if we were actually at this waypoint
            if (_nearbyWaypoint.value == currentWaypoint) {
                _nearbyWaypoint.value = null
                lastNotifiedWaypoint = null
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
        
        val estimatedTime = "${(distance / 83).toInt() + 1} min" // Assuming 5 km/h walking speed
        
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