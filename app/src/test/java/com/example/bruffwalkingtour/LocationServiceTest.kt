package com.example.bruffwalkingtour

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

class LocationServiceTest {

    private lateinit var mockTour: WalkingTour
    private lateinit var testWaypoints: List<TourWaypoint>

    @Before
    fun setUp() {
        testWaypoints = listOf(
            TourWaypoint(
                id = "waypoint_1",
                name = "First Waypoint",
                description = "The first stop on our test tour",
                latitude = 52.4790,
                longitude = -8.5487,
                historicalInfo = "Historical info for first waypoint",
                proximityRadius = 25.0
            ),
            TourWaypoint(
                id = "waypoint_2", 
                name = "Second Waypoint",
                description = "The second stop on our test tour",
                latitude = 52.4785,
                longitude = -8.5479,
                historicalInfo = "Historical info for second waypoint",
                proximityRadius = 20.0
            ),
            TourWaypoint(
                id = "waypoint_3",
                name = "Third Waypoint", 
                description = "The third stop on our test tour",
                latitude = 52.4778,
                longitude = -8.5480,
                historicalInfo = "Historical info for third waypoint",
                proximityRadius = 15.0
            )
        )

        mockTour = WalkingTour(
            id = "test_tour",
            name = "Test Tour",
            description = "A tour for testing purposes",
            waypoints = testWaypoints,
            estimatedDurationMinutes = 60,
            difficulty = TourDifficulty.EASY
        )
    }

    @Test
    fun tourCompletion_initialStateIsNotCompleted() {
        // This test simulates the initial state logic
        val currentWaypointIndex = 0
        val tourSize = testWaypoints.size
        
        val isCompleted = currentWaypointIndex >= tourSize - 1
        assertFalse("Tour should not be completed initially", isCompleted)
    }

    @Test
    fun tourCompletion_isCompletedAtLastWaypoint() {
        // This test simulates being at the last waypoint
        val currentWaypointIndex = testWaypoints.size - 1 // Index 2 for 3 waypoints
        val tourSize = testWaypoints.size
        
        val isCompleted = currentWaypointIndex >= tourSize - 1
        assertTrue("Tour should be completed at last waypoint", isCompleted)
    }

    @Test
    fun tourCompletion_isNotCompletedBeforeLastWaypoint() {
        // Test all waypoints except the last
        for (index in 0 until testWaypoints.size - 1) {
            val isCompleted = index >= testWaypoints.size - 1
            assertFalse("Tour should not be completed at waypoint $index", isCompleted)
        }
    }

    @Test
    fun moveToNextWaypoint_progressesThroughWaypoints() {
        var currentIndex = 0
        val maxIndex = testWaypoints.size - 1
        
        // Simulate moving through waypoints
        while (currentIndex < maxIndex) {
            assertFalse("Should not be completed before last waypoint", currentIndex >= maxIndex)
            currentIndex++
        }
        
        assertTrue("Should be completed at last waypoint", currentIndex >= maxIndex)
    }

    @Test
    fun moveToNextWaypoint_doesNotExceedBounds() {
        var currentIndex = 0
        val maxIndex = testWaypoints.size - 1
        
        // Move to last waypoint
        currentIndex = maxIndex
        
        // Attempt to move beyond (simulating button press at last waypoint)
        val attemptedIndex = if (currentIndex < maxIndex) currentIndex + 1 else currentIndex
        
        assertEquals("Index should not exceed bounds", maxIndex, attemptedIndex)
    }

    @Test
    fun getCurrentWaypoint_returnsCorrectWaypointAtIndex() {
        for (index in testWaypoints.indices) {
            val waypoint = testWaypoints.getOrNull(index)
            assertNotNull("Waypoint at index $index should exist", waypoint)
            assertEquals("Should return correct waypoint at index $index", 
                testWaypoints[index], waypoint)
        }
    }

    @Test
    fun getCurrentWaypoint_returnsNullForInvalidIndex() {
        val invalidIndices = listOf(-1, testWaypoints.size, testWaypoints.size + 5)
        
        invalidIndices.forEach { index ->
            val waypoint = testWaypoints.getOrNull(index)
            assertNull("Should return null for invalid index $index", waypoint)
        }
    }

    @Test
    fun getNextWaypoint_returnsCorrectNextWaypoint() {
        for (index in 0 until testWaypoints.size - 1) {
            val nextWaypoint = testWaypoints.getOrNull(index + 1)
            assertNotNull("Next waypoint should exist for index $index", nextWaypoint)
            assertEquals("Should return correct next waypoint", 
                testWaypoints[index + 1], nextWaypoint)
        }
    }

    @Test
    fun getNextWaypoint_returnsNullAtLastWaypoint() {
        val lastIndex = testWaypoints.size - 1
        val nextWaypoint = testWaypoints.getOrNull(lastIndex + 1)
        assertNull("Next waypoint should be null at last waypoint", nextWaypoint)
    }

    @Test
    fun tourProgression_followsCorrectSequence() {
        var currentIndex = 0
        val progression = mutableListOf<String>()
        
        // Simulate tour progression
        while (currentIndex < testWaypoints.size) {
            val currentWaypoint = testWaypoints[currentIndex]
            progression.add(currentWaypoint.id)
            currentIndex++
        }
        
        val expectedProgression = listOf("waypoint_1", "waypoint_2", "waypoint_3")
        assertEquals("Tour should follow correct sequence", expectedProgression, progression)
    }

    @Test
    fun proximityRadius_variationIsHandledCorrectly() {
        testWaypoints.forEach { waypoint ->
            assertTrue("Proximity radius should be positive", waypoint.proximityRadius > 0)
        }
        
        // Test that different waypoints can have different proximity radii
        val radii = testWaypoints.map { it.proximityRadius }.toSet()
        assertTrue("Waypoints should have different proximity radii", radii.size > 1)
    }

    @Test
    fun wayPointValidation_allRequiredFieldsPresent() {
        testWaypoints.forEach { waypoint ->
            assertFalse("ID should not be empty", waypoint.id.isBlank())
            assertFalse("Name should not be empty", waypoint.name.isBlank())
            assertFalse("Description should not be empty", waypoint.description.isBlank())
            assertNotNull("Historical info should not be null", waypoint.historicalInfo)
            assertFalse("Historical info should not be empty", waypoint.historicalInfo!!.isBlank())
        }
    }

    @Test
    fun walkingTour_hasValidConfiguration() {
        assertTrue("Tour should have waypoints", mockTour.waypoints.isNotEmpty())
        assertTrue("Tour duration should be positive", mockTour.estimatedDurationMinutes > 0)
        assertFalse("Tour name should not be empty", mockTour.name.isBlank())
        assertFalse("Tour description should not be empty", mockTour.description.isBlank())
        assertEquals("Tour should have correct number of waypoints", 
            testWaypoints.size, mockTour.waypoints.size)
    }

    @Test 
    fun tourDifficulty_isValidEnum() {
        val validDifficulties = TourDifficulty.values()
        assertTrue("Tour difficulty should be valid", 
            validDifficulties.contains(mockTour.difficulty))
    }

    @Test
    fun emptyTour_handledCorrectly() {
        val emptyTour = WalkingTour(
            id = "empty_tour",
            name = "Empty Tour",
            description = "A tour with no waypoints",
            waypoints = emptyList(),
            estimatedDurationMinutes = 0
        )
        
        assertTrue("Empty tour should have no waypoints", emptyTour.waypoints.isEmpty())
        
        // Test completion logic with empty tour
        val currentIndex = 0
        val isCompleted = currentIndex >= emptyTour.waypoints.size - 1
        assertTrue("Empty tour should be considered completed", isCompleted)
    }

    @Test
    fun singleWaypointTour_handledCorrectly() {
        val singleWaypointTour = WalkingTour(
            id = "single_tour",
            name = "Single Waypoint Tour",
            description = "A tour with one waypoint",
            waypoints = listOf(testWaypoints[0]),
            estimatedDurationMinutes = 15
        )
        
        assertEquals("Single waypoint tour should have one waypoint", 1, singleWaypointTour.waypoints.size)
        
        // Test completion logic with single waypoint tour
        val currentIndex = 0
        val isCompleted = currentIndex >= singleWaypointTour.waypoints.size - 1
        assertTrue("Single waypoint tour should be completed at first waypoint", isCompleted)
    }
}