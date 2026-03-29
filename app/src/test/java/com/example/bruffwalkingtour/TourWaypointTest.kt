package com.example.bruffwalkingtour

import org.junit.Test
import org.junit.Assert.*

class TourWaypointTest {

    @Test
    fun tourWaypoint_createsWithAllRequiredFields() {
        val waypoint = TourWaypoint(
            id = "test_waypoint",
            name = "Test Waypoint",
            description = "A test waypoint for unit testing",
            latitude = 52.4779,
            longitude = -8.5480,
            historicalInfo = "Historical information about the test location",
            proximityRadius = 25.0
        )

        assertEquals("test_waypoint", waypoint.id)
        assertEquals("Test Waypoint", waypoint.name)
        assertEquals("A test waypoint for unit testing", waypoint.description)
        assertEquals(52.4779, waypoint.latitude, 0.0001)
        assertEquals(-8.5480, waypoint.longitude, 0.0001)
        assertEquals("Historical information about the test location", waypoint.historicalInfo)
        assertEquals(25.0, waypoint.proximityRadius, 0.1)
    }

    @Test
    fun tourWaypoint_createsWithOptionalFields() {
        val waypoint = TourWaypoint(
            id = "test_waypoint",
            name = "Test Waypoint",
            description = "A test waypoint",
            latitude = 52.4779,
            longitude = -8.5480,
            historicalInfo = "Historical info",
            proximityRadius = 20.0,
            imageUrl = "https://example.com/image.jpg",
            audioUrl = "https://example.com/audio.mp3"
        )

        assertEquals("https://example.com/image.jpg", waypoint.imageUrl)
        assertEquals("https://example.com/audio.mp3", waypoint.audioUrl)
    }

    @Test
    fun tourWaypoint_defaultOptionalFieldsAreNull() {
        val waypoint = TourWaypoint(
            id = "test_waypoint",
            name = "Test Waypoint", 
            description = "A test waypoint",
            latitude = 52.4779,
            longitude = -8.5480,
            historicalInfo = "Historical info",
            proximityRadius = 20.0
        )

        assertNull("Default imageUrl should be null", waypoint.imageUrl)
        assertNull("Default audioUrl should be null", waypoint.audioUrl)
    }

    @Test
    fun tourWaypoint_allowsZeroProximityRadius() {
        val waypoint = TourWaypoint(
            id = "precise_waypoint",
            name = "Precise Waypoint",
            description = "A waypoint with zero proximity radius",
            latitude = 52.4779,
            longitude = -8.5480,
            historicalInfo = "Very precise location",
            proximityRadius = 0.0
        )

        assertEquals(0.0, waypoint.proximityRadius, 0.001)
    }

    @Test
    fun tourWaypoint_allowsNegativeCoordinates() {
        val waypoint = TourWaypoint(
            id = "negative_coords",
            name = "Negative Coordinates",
            description = "Testing negative coordinates",
            latitude = -52.4779,
            longitude = -8.5480,
            historicalInfo = "Location with negative latitude",
            proximityRadius = 10.0
        )

        assertEquals(-52.4779, waypoint.latitude, 0.0001)
        assertEquals(-8.5480, waypoint.longitude, 0.0001)
    }

    @Test
    fun tourWaypoint_handlesEmptyStrings() {
        val waypoint = TourWaypoint(
            id = "",
            name = "",
            description = "",
            latitude = 0.0,
            longitude = 0.0,
            historicalInfo = "",
            proximityRadius = 1.0
        )

        assertEquals("", waypoint.id)
        assertEquals("", waypoint.name)
        assertEquals("", waypoint.description)
        assertEquals("", waypoint.historicalInfo)
    }

    @Test
    fun tourWaypoint_handlesVeryLongProximityRadius() {
        val waypoint = TourWaypoint(
            id = "large_radius",
            name = "Large Radius Waypoint",
            description = "Testing very large proximity radius",
            latitude = 52.4779,
            longitude = -8.5480,
            historicalInfo = "Large area coverage",
            proximityRadius = 1000000.0
        )

        assertEquals(1000000.0, waypoint.proximityRadius, 0.1)
    }

    @Test
    fun tourWaypoint_handlesUnicodeCharacters() {
        val waypoint = TourWaypoint(
            id = "unicode_test",
            name = "Brú na Bóinne 🏛️",
            description = "Testing unicode characters: café, naïve, résumé",
            latitude = 52.4779,
            longitude = -8.5480,
            historicalInfo = "Historical info with émojis 📚 and accénts",
            proximityRadius = 15.0
        )

        assertEquals("Brú na Bóinne 🏛️", waypoint.name)
        assertTrue("Description should contain unicode", waypoint.description.contains("café"))
        assertTrue("Historical info should contain emojis", waypoint.historicalInfo?.contains("📚") == true)
    }

    @Test
    fun tourWaypoint_handlesExtremeCoordinates() {
        val waypoint = TourWaypoint(
            id = "extreme_coords",
            name = "Extreme Coordinates",
            description = "Testing extreme latitude/longitude values",
            latitude = 90.0,  // North Pole
            longitude = 180.0,  // International Date Line
            historicalInfo = "Extreme location test",
            proximityRadius = 50.0
        )

        assertEquals(90.0, waypoint.latitude, 0.001)
        assertEquals(180.0, waypoint.longitude, 0.001)
    }

    @Test
    fun tourWaypoint_dataClassEquality() {
        val waypoint1 = TourWaypoint(
            id = "equality_test",
            name = "Equality Test",
            description = "Testing data class equality",
            latitude = 52.4779,
            longitude = -8.5480,
            historicalInfo = "Same historical info",
            proximityRadius = 20.0
        )

        val waypoint2 = TourWaypoint(
            id = "equality_test",
            name = "Equality Test",
            description = "Testing data class equality", 
            latitude = 52.4779,
            longitude = -8.5480,
            historicalInfo = "Same historical info",
            proximityRadius = 20.0
        )

        val waypoint3 = TourWaypoint(
            id = "different_test",
            name = "Different Test",
            description = "Testing data class inequality",
            latitude = 52.4779,
            longitude = -8.5480,
            historicalInfo = "Different historical info",
            proximityRadius = 20.0
        )

        assertEquals("Identical waypoints should be equal", waypoint1, waypoint2)
        assertNotEquals("Different waypoints should not be equal", waypoint1, waypoint3)
    }
}