package com.example.bruffwalkingtour

import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for the proximity detection logic mirrored from LocationService.checkProximityToWaypoints.
 *
 * LocationService depends on Android Context and FusedLocationProviderClient, so we replicate
 * the pure proximity state machine in a plain Kotlin inner class.
 *
 * Key behaviours exercised:
 *  - Entering the proximity radius fires exactly one arrival notification
 *  - The notification is not repeated while still inside the radius
 *  - Moving outside the radius clears the arrival state
 *  - Re-entering the radius fires a new notification
 *  - Moving to the next waypoint resets notification state
 *  - Each waypoint's individual proximityRadius is respected (15 / 20 / 25 / 30 m)
 */
class ProximityLogicTest {

    // -----------------------------------------------------------------------
    // Mirror of LocationService proximity state machine
    // -----------------------------------------------------------------------

    private inner class ProximityState(val waypoints: List<TourWaypoint>) {
        var currentWaypointIndex = 0
        var nearbyWaypoint: TourWaypoint? = null
        var lastNotifiedWaypoint: TourWaypoint? = null

        fun getCurrentWaypoint(): TourWaypoint? =
            waypoints.getOrNull(currentWaypointIndex)

        /** Mirrors LocationService.checkProximityToWaypoints */
        fun checkProximity(distance: Float) {
            val currentWaypoint = getCurrentWaypoint() ?: return
            if (distance <= currentWaypoint.proximityRadius) {
                if (lastNotifiedWaypoint != currentWaypoint && nearbyWaypoint == null) {
                    nearbyWaypoint = currentWaypoint
                    lastNotifiedWaypoint = currentWaypoint
                }
            } else {
                if (nearbyWaypoint == currentWaypoint) {
                    nearbyWaypoint = null
                    lastNotifiedWaypoint = null
                }
            }
        }

        /** Mirrors LocationService.moveToNextWaypoint (the advancing branch) */
        fun moveToNext() {
            if (currentWaypointIndex < waypoints.size - 1) {
                currentWaypointIndex++
                lastNotifiedWaypoint = null
                nearbyWaypoint = null
            }
        }
    }

    private lateinit var waypoints: List<TourWaypoint>
    private lateinit var proximity: ProximityState

    @Before
    fun setUp() {
        waypoints = BruffTourData.getDefaultTour().waypoints
        proximity = ProximityState(waypoints)
    }

    // -----------------------------------------------------------------------
    // Initial state
    // -----------------------------------------------------------------------

    @Test
    fun initialState_noNearbyWaypoint() {
        assertNull(proximity.nearbyWaypoint)
    }

    @Test
    fun initialState_noLastNotified() {
        assertNull(proximity.lastNotifiedWaypoint)
    }

    // -----------------------------------------------------------------------
    // Entering radius fires one notification
    // -----------------------------------------------------------------------

    @Test
    fun withinRadius_setsNearbyWaypoint() {
        val wp = proximity.getCurrentWaypoint()!!
        proximity.checkProximity((wp.proximityRadius - 1).toFloat())
        assertEquals("Should report arrival when inside radius", wp, proximity.nearbyWaypoint)
    }

    @Test
    fun withinRadius_setsLastNotifiedWaypoint() {
        val wp = proximity.getCurrentWaypoint()!!
        proximity.checkProximity((wp.proximityRadius - 1).toFloat())
        assertEquals(wp, proximity.lastNotifiedWaypoint)
    }

    @Test
    fun atExactRadius_triggersArrival() {
        val wp = proximity.getCurrentWaypoint()!!
        proximity.checkProximity(wp.proximityRadius.toFloat())
        assertNotNull("Arrival should trigger at exactly the proximity radius", proximity.nearbyWaypoint)
    }

    @Test
    fun outsideRadius_doesNotTriggerArrival() {
        val wp = proximity.getCurrentWaypoint()!!
        proximity.checkProximity((wp.proximityRadius + 1).toFloat())
        assertNull("No arrival should be reported when outside radius", proximity.nearbyWaypoint)
    }

    // -----------------------------------------------------------------------
    // One-shot: notification not repeated while still inside
    // -----------------------------------------------------------------------

    @Test
    fun repeatedPositionInsideRadius_doesNotDuplicateNotification() {
        val wp = proximity.getCurrentWaypoint()!!
        proximity.checkProximity((wp.proximityRadius - 1).toFloat()) // first arrival
        proximity.nearbyWaypoint = null // simulate app consuming the event (e.g. showing banner)
        // The lastNotifiedWaypoint is still set, so a second call should NOT re-fire
        proximity.checkProximity((wp.proximityRadius - 1).toFloat())
        assertNull("Arrival should not fire twice for the same waypoint", proximity.nearbyWaypoint)
    }

    // -----------------------------------------------------------------------
    // Moving away clears state
    // -----------------------------------------------------------------------

    @Test
    fun movingAway_clearsNearbyWaypoint() {
        val wp = proximity.getCurrentWaypoint()!!
        proximity.checkProximity((wp.proximityRadius - 1).toFloat()) // arrive
        proximity.checkProximity((wp.proximityRadius + 5).toFloat()) // leave
        assertNull("nearbyWaypoint should clear when moving outside radius", proximity.nearbyWaypoint)
    }

    @Test
    fun movingAway_clearsLastNotifiedWaypoint() {
        val wp = proximity.getCurrentWaypoint()!!
        proximity.checkProximity((wp.proximityRadius - 1).toFloat())
        proximity.checkProximity((wp.proximityRadius + 5).toFloat())
        assertNull("lastNotifiedWaypoint should clear when moving away", proximity.lastNotifiedWaypoint)
    }

    @Test
    fun afterMovingAway_reenteringRadius_firesNewArrival() {
        val wp = proximity.getCurrentWaypoint()!!
        proximity.checkProximity((wp.proximityRadius - 1).toFloat()) // arrive
        proximity.checkProximity((wp.proximityRadius + 5).toFloat()) // leave — clears state
        proximity.checkProximity((wp.proximityRadius - 1).toFloat()) // arrive again
        assertNotNull("Re-entry into radius should fire a new arrival", proximity.nearbyWaypoint)
    }

    // -----------------------------------------------------------------------
    // Moving outside radius clears ONLY when nearbyWaypoint matches current
    // -----------------------------------------------------------------------

    @Test
    fun movingAway_withoutPriorArrival_doesNotChangeNearbyWaypoint() {
        // If we never arrived, moving away from the radius does nothing
        proximity.checkProximity(500f) // far away — no arrival
        proximity.checkProximity(600f) // still far away
        assertNull("nearbyWaypoint should remain null", proximity.nearbyWaypoint)
    }

    // -----------------------------------------------------------------------
    // Per-waypoint radius values are respected
    // -----------------------------------------------------------------------

    @Test
    fun thomasFitzgeraldCentre_radius25m_insideAt24m() {
        // TF Centre has proximityRadius = 25.0
        proximity.checkProximity(24f)
        assertNotNull(proximity.nearbyWaypoint)
        assertEquals("thomas_fitzgerald_centre", proximity.nearbyWaypoint!!.id)
    }

    @Test
    fun thomasFitzgeraldCentre_radius25m_outsideAt26m() {
        proximity.checkProximity(26f)
        assertNull(proximity.nearbyWaypoint)
    }

    @Test
    fun bruffCatholicChurch_radius20m_insideAt19m() {
        proximity.moveToNext() // advance to Church
        assertEquals("bruff_catholic_church", proximity.getCurrentWaypoint()?.id)
        proximity.checkProximity(19f)
        assertNotNull(proximity.nearbyWaypoint)
        assertEquals("bruff_catholic_church", proximity.nearbyWaypoint!!.id)
    }

    @Test
    fun bruffCatholicChurch_radius20m_outsideAt21m() {
        proximity.moveToNext()
        proximity.checkProximity(21f)
        assertNull(proximity.nearbyWaypoint)
    }

    @Test
    fun seanWallMonument_radius15m_insideAt14m() {
        repeat(2) { proximity.moveToNext() }
        assertEquals("sean_wall_monument", proximity.getCurrentWaypoint()?.id)
        proximity.checkProximity(14f)
        assertNotNull(proximity.nearbyWaypoint)
        assertEquals("sean_wall_monument", proximity.nearbyWaypoint!!.id)
    }

    @Test
    fun seanWallMonument_radius15m_outsideAt16m() {
        repeat(2) { proximity.moveToNext() }
        proximity.checkProximity(16f)
        assertNull(proximity.nearbyWaypoint)
    }

    @Test
    fun bruffGAAGrounds_radius30m_insideAt29m() {
        repeat(3) { proximity.moveToNext() }
        assertEquals("bruff_gaa_grounds", proximity.getCurrentWaypoint()?.id)
        proximity.checkProximity(29f)
        assertNotNull(proximity.nearbyWaypoint)
        assertEquals("bruff_gaa_grounds", proximity.nearbyWaypoint!!.id)
    }

    @Test
    fun bruffGAAGrounds_radius30m_outsideAt31m() {
        repeat(3) { proximity.moveToNext() }
        proximity.checkProximity(31f)
        assertNull(proximity.nearbyWaypoint)
    }

    // -----------------------------------------------------------------------
    // Advancing waypoint resets notification tracking
    // -----------------------------------------------------------------------

    @Test
    fun moveToNext_clearsNearbyWaypoint() {
        val wp = proximity.getCurrentWaypoint()!!
        proximity.checkProximity((wp.proximityRadius - 1).toFloat())
        assertNotNull(proximity.nearbyWaypoint)
        proximity.moveToNext()
        assertNull("nearbyWaypoint should be null after advancing to next waypoint",
            proximity.nearbyWaypoint)
    }

    @Test
    fun moveToNext_clearsLastNotifiedWaypoint() {
        val wp = proximity.getCurrentWaypoint()!!
        proximity.checkProximity((wp.proximityRadius - 1).toFloat())
        proximity.moveToNext()
        assertNull("lastNotifiedWaypoint should be cleared when advancing",
            proximity.lastNotifiedWaypoint)
    }

    @Test
    fun afterMoveToNext_arrivalAtNewWaypointFires() {
        proximity.moveToNext() // now on Church (radius=20)
        val church = proximity.getCurrentWaypoint()!!
        assertEquals("bruff_catholic_church", church.id)
        proximity.checkProximity(15f) // inside 20m radius
        assertNotNull("Arrival should fire at the new current waypoint", proximity.nearbyWaypoint)
        assertEquals("bruff_catholic_church", proximity.nearbyWaypoint!!.id)
    }

    // -----------------------------------------------------------------------
    // No waypoint (null tour) — should not crash
    // -----------------------------------------------------------------------

    @Test
    fun emptyWaypointList_checkProximityDoesNotCrash() {
        val empty = ProximityState(emptyList())
        empty.checkProximity(5f) // should return early without throwing
        assertNull(empty.nearbyWaypoint)
    }
}
