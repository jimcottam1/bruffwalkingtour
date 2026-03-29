package com.example.bruffwalkingtour

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import com.google.gson.annotations.SerializedName

// OSRM API - Completely FREE, no API key needed
data class OSRMResponse(
    @SerializedName("routes") val routes: List<OSRMRoute>
)

data class OSRMRoute(
    @SerializedName("geometry") val geometry: OSRMGeometry
)

data class OSRMGeometry(
    @SerializedName("coordinates") val coordinates: List<List<Double>>
)

interface OSRMApi {
    @GET("route/v1/foot/{coordinates}")
    suspend fun getRoute(
        @retrofit2.http.Path("coordinates") coordinates: String,
        @Query("geometries") geometries: String = "geojson",
        @Query("overview") overview: String = "full"
    ): OSRMResponse
}

class RouteService(private val context: Context) {
    
    // OSRM - Completely FREE, no API key needed!
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://router.project-osrm.org/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    private val osrmApi = retrofit.create(OSRMApi::class.java)
    
    suspend fun getRoadBasedRoute(start: GeoPoint, end: GeoPoint): List<GeoPoint> {
        return withContext(Dispatchers.IO) {
            try {
                val coordinates = "${start.longitude},${start.latitude};${end.longitude},${end.latitude}"
                val response = osrmApi.getRoute(coordinates)
                
                if (response.routes.isNotEmpty()) {
                    val routeCoordinates = response.routes[0].geometry.coordinates
                    routeCoordinates.map { coord ->
                        GeoPoint(coord[1], coord[0]) // OSRM returns [lng, lat]
                    }
                } else {
                    Log.w("RouteService", "OSRM returned no routes, using fallback")
                    getSimpleRoute(start, end)
                }
                
            } catch (e: Exception) {
                Log.e("RouteService", "OSRM routing failed: ${e.message}")
                getSimpleRoute(start, end)
            }
        }
    }
    
    // Enhanced fallback routing that creates more realistic paths
    private fun getSimpleRoute(start: GeoPoint, end: GeoPoint): List<GeoPoint> {
        val routePoints = mutableListOf<GeoPoint>()
        routePoints.add(start)
        
        val latDiff = end.latitude - start.latitude
        val lonDiff = end.longitude - start.longitude
        val totalDistance = kotlin.math.sqrt(latDiff * latDiff + lonDiff * lonDiff)
        
        // Create more realistic walking route with turns at intersections
        when {
            totalDistance < 0.0005 -> {
                // Very short distance - direct route
                routePoints.add(end)
            }
            kotlin.math.abs(latDiff) > kotlin.math.abs(lonDiff) -> {
                // Primarily north-south movement
                val midPoint = GeoPoint(start.latitude + latDiff * 0.7, start.longitude)
                routePoints.add(midPoint)
                routePoints.add(GeoPoint(midPoint.latitude, end.longitude))
                routePoints.add(end)
            }
            else -> {
                // Primarily east-west movement
                val midPoint = GeoPoint(start.latitude, start.longitude + lonDiff * 0.7)
                routePoints.add(midPoint)
                routePoints.add(GeoPoint(end.latitude, midPoint.longitude))
                routePoints.add(end)
            }
        }
        
        return routePoints
    }
    
    // Synchronous version for immediate use
    fun getRoadBasedRouteSync(start: GeoPoint, end: GeoPoint): List<GeoPoint> {
        return getSimpleRoute(start, end)
    }
}