package com.example.bruffwalkingtour

import org.junit.Test
import org.junit.Assert.*

class BruffTourDataTest {

    @Test
    fun getDefaultTour_returnsCorrectTourId() {
        val tour = BruffTourData.getDefaultTour()
        assertEquals("bruff_heritage_trail", tour.id)
    }

    @Test
    fun getDefaultTour_returnsCorrectTourName() {
        val tour = BruffTourData.getDefaultTour()
        assertEquals("Bruff Heritage Trail", tour.name)
    }

    @Test
    fun getDefaultTour_returnsCorrectDescription() {
        val tour = BruffTourData.getDefaultTour()
        assertEquals("Discover the rich history and beautiful architecture of Bruff town", tour.description)
    }

    @Test
    fun getDefaultTour_returnsCorrectDuration() {
        val tour = BruffTourData.getDefaultTour()
        assertEquals(90, tour.estimatedDurationMinutes)
    }

    @Test
    fun getDefaultTour_returnsEasyDifficulty() {
        val tour = BruffTourData.getDefaultTour()
        assertEquals(TourDifficulty.EASY, tour.difficulty)
    }

    @Test
    fun getDefaultTour_returnsFourWaypoints() {
        val tour = BruffTourData.getDefaultTour()
        assertEquals(4, tour.waypoints.size)
    }

    @Test
    fun getDefaultTour_waypointsHaveCorrectIds() {
        val tour = BruffTourData.getDefaultTour()
        val expectedIds = listOf(
            "thomas_fitzgerald_centre",
            "bruff_catholic_church", 
            "sean_wall_monument",
            "bruff_gaa_grounds"
        )
        
        tour.waypoints.forEachIndexed { index, waypoint ->
            assertEquals(expectedIds[index], waypoint.id)
        }
    }

    @Test
    fun getDefaultTour_waypointsHaveCorrectNames() {
        val tour = BruffTourData.getDefaultTour()
        val expectedNames = listOf(
            "Thomas Fitzgerald Centre",
            "Saints Peter and Paul Catholic Church",
            "Sean Wall Monument", 
            "Bruff GAA Grounds"
        )
        
        tour.waypoints.forEachIndexed { index, waypoint ->
            assertEquals(expectedNames[index], waypoint.name)
        }
    }

    @Test
    fun getDefaultTour_waypointsHaveValidCoordinates() {
        val tour = BruffTourData.getDefaultTour()
        
        tour.waypoints.forEach { waypoint ->
            // Verify latitude is within valid range for Ireland
            assertTrue("Latitude ${waypoint.latitude} should be between 51-54", 
                waypoint.latitude in 51.0..54.0)
            
            // Verify longitude is within valid range for Ireland  
            assertTrue("Longitude ${waypoint.longitude} should be between -11 to -6",
                waypoint.longitude in -11.0..-6.0)
        }
    }

    @Test
    fun getDefaultTour_waypointsHaveValidProximityRadius() {
        val tour = BruffTourData.getDefaultTour()
        
        tour.waypoints.forEach { waypoint ->
            assertTrue("Proximity radius should be positive", waypoint.proximityRadius > 0)
            assertTrue("Proximity radius should be reasonable (< 100m)", waypoint.proximityRadius < 100.0)
        }
    }

    @Test
    fun getDefaultTour_allWaypointsHaveDescriptions() {
        val tour = BruffTourData.getDefaultTour()
        
        tour.waypoints.forEach { waypoint ->
            assertFalse("Waypoint ${waypoint.name} should have description", 
                waypoint.description.isBlank())
        }
    }

    @Test
    fun getDefaultTour_allWaypointsHaveHistoricalInfo() {
        val tour = BruffTourData.getDefaultTour()
        
        tour.waypoints.forEach { waypoint ->
            assertFalse("Waypoint ${waypoint.name} should have historical info",
                waypoint.historicalInfo?.isBlank() ?: true)
        }
    }

    @Test
    fun getDefaultTour_thomasFitzgeraldCentreHasCorrectCoordinates() {
        val tour = BruffTourData.getDefaultTour()
        val thomasFitzgerald = tour.waypoints.find { it.id == "thomas_fitzgerald_centre" }
        
        assertNotNull("Thomas Fitzgerald Centre waypoint should exist", thomasFitzgerald)
        assertEquals(52.478689, thomasFitzgerald!!.latitude, 0.000001)
        assertEquals(-8.548776, thomasFitzgerald.longitude, 0.000001)
    }

    @Test
    fun getDefaultTour_bruffCatholicChurchHasCorrectCoordinates() {
        val tour = BruffTourData.getDefaultTour()
        val church = tour.waypoints.find { it.id == "bruff_catholic_church" }
        
        assertNotNull("Bruff Catholic Church waypoint should exist", church)
        assertEquals(52.478558, church!!.latitude, 0.000001)
        assertEquals(-8.548009, church.longitude, 0.000001)
    }

    @Test
    fun getDefaultTour_seanWallMonumentHasCorrectCoordinates() {
        val tour = BruffTourData.getDefaultTour()
        val monument = tour.waypoints.find { it.id == "sean_wall_monument" }
        
        assertNotNull("Sean Wall Monument waypoint should exist", monument)
        assertEquals(52.477636, monument!!.latitude, 0.000001)
        assertEquals(-8.547905, monument.longitude, 0.000001)
    }

    @Test
    fun getDefaultTour_bruffGAAGroundsHasCorrectCoordinates() {
        val tour = BruffTourData.getDefaultTour()
        val gaaGrounds = tour.waypoints.find { it.id == "bruff_gaa_grounds" }
        
        assertNotNull("Bruff GAA Grounds waypoint should exist", gaaGrounds)
        assertEquals(52.476002, gaaGrounds!!.latitude, 0.000001)
        assertEquals(-8.541206, gaaGrounds.longitude, 0.000001)
    }
}