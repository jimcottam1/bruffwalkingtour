package com.example.bruffwalkingtour

data class TourWaypoint(
    val id: String,
    val name: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val imageUrl: String? = null,
    val audioUrl: String? = null,
    val historicalInfo: String? = null,
    val proximityRadius: Double = 20.0 // meters
)

data class WalkingTour(
    val id: String,
    val name: String,
    val description: String,
    val waypoints: List<TourWaypoint>,
    val estimatedDurationMinutes: Int,
    val difficulty: TourDifficulty = TourDifficulty.EASY
)

enum class TourDifficulty {
    EASY, MODERATE, CHALLENGING
}

data class NavigationInstruction(
    val direction: String,
    val distance: String,
    val estimatedTime: String
)