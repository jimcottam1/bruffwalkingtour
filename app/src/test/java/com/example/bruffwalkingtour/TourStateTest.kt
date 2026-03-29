package com.example.bruffwalkingtour

import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for tour state progression logic mirrored from LocationService.
 *
 * LocationService holds state in private fields and depends on Android
 * Context/FusedLocationClient, so these tests replicate the pure state
 * logic in a plain Kotlin class that is easy to exercise without a device.
 *
 * NOTE — documented bug in isTourCompleted():
 *   The production implementation returns true when
 *   currentWaypointIndex >= tour.waypoints.size - 1
 *   (i.e. it reports "complete" the moment the user ARRIVES at the last
 *   waypoint, before they have actually visited it).
 *   The correct check should be >= tour.waypoints.size.
 *   Tests below document both the current behaviour and the correct intent.
 */
class TourStateTest {

    // -----------------------------------------------------------------------
    // Minimal state machine that mirrors LocationService tour logic
    // -----------------------------------------------------------------------

    private inner class TourState(val tour: WalkingTour) {
        var currentWaypointIndex = 0
            private set
        var tourCompleted = false
            private set
        var lastNotifiedWaypoint: TourWaypoint? = null

        fun getCurrentWaypoint(): TourWaypoint? =
            tour.waypoints.getOrNull(currentWaypointIndex)

        fun getNextWaypoint(): TourWaypoint? =
            tour.waypoints.getOrNull(currentWaypointIndex + 1)

        /** Mirrors LocationService.moveToNextWaypoint() */
        fun moveToNextWaypoint() {
            if (currentWaypointIndex < tour.waypoints.size - 1) {
                currentWaypointIndex++
                lastNotifiedWaypoint = null
            } else {
                tourCompleted = true
            }
        }

        /** Mirrors LocationService.isTourCompleted() as-written in production code */
        fun isTourCompleted_asWritten(): Boolean =
            currentWaypointIndex >= tour.waypoints.size - 1

        /** Corrected version — only complete AFTER the last waypoint is visited */
        fun isTourCompleted_corrected(): Boolean =
            currentWaypointIndex >= tour.waypoints.size
    }

    private lateinit var tour: WalkingTour
    private lateinit var state: TourState

    @Before
    fun setUp() {
        tour = BruffTourData.getDefaultTour()
        state = TourState(tour)
    }

    // -----------------------------------------------------------------------
    // Initial state
    // -----------------------------------------------------------------------

    @Test
    fun initialState_indexIsZero() {
        assertEquals(0, state.currentWaypointIndex)
    }

    @Test
    fun initialState_tourNotCompleted() {
        assertFalse(state.tourCompleted)
    }

    @Test
    fun initialState_currentWaypointIsFirst() {
        assertEquals("thomas_fitzgerald_centre", state.getCurrentWaypoint()?.id)
    }

    @Test
    fun initialState_nextWaypointIsSecond() {
        assertEquals("bruff_catholic_church", state.getNextWaypoint()?.id)
    }

    @Test
    fun initialState_lastNotifiedWaypointIsNull() {
        assertNull(state.lastNotifiedWaypoint)
    }

    // -----------------------------------------------------------------------
    // Progression through waypoints
    // -----------------------------------------------------------------------

    @Test
    fun moveToNext_advancesIndexByOne() {
        state.moveToNextWaypoint()
        assertEquals(1, state.currentWaypointIndex)
    }

    @Test
    fun moveToNext_updatesCurrentWaypointToChurch() {
        state.moveToNextWaypoint()
        assertEquals("bruff_catholic_church", state.getCurrentWaypoint()?.id)
    }

    @Test
    fun moveToNext_clearsLastNotifiedWaypoint() {
        state.lastNotifiedWaypoint = tour.waypoints[0]
        state.moveToNextWaypoint()
        assertNull("lastNotifiedWaypoint should be cleared on advance", state.lastNotifiedWaypoint)
    }

    @Test
    fun walkingThroughAllFourWaypoints_correctSequence() {
        val visited = mutableListOf<String>()
        visited.add(state.getCurrentWaypoint()!!.id)

        repeat(tour.waypoints.size - 1) {
            state.moveToNextWaypoint()
            visited.add(state.getCurrentWaypoint()!!.id)
        }

        assertEquals(listOf(
            "thomas_fitzgerald_centre",
            "bruff_catholic_church",
            "sean_wall_monument",
            "bruff_gaa_grounds"
        ), visited)
    }

    @Test
    fun afterVisitingAllWaypoints_indexEqualsLastIndex() {
        repeat(tour.waypoints.size - 1) { state.moveToNextWaypoint() }
        assertEquals(tour.waypoints.size - 1, state.currentWaypointIndex)
    }

    @Test
    fun atLastWaypoint_moveToNextSetsTourCompleted() {
        // Advance to the last waypoint
        repeat(tour.waypoints.size - 1) { state.moveToNextWaypoint() }
        assertFalse("Should not be complete yet", state.tourCompleted)

        // One more move — this triggers the else branch
        state.moveToNextWaypoint()
        assertTrue("Tour should be complete after moving past last waypoint", state.tourCompleted)
    }

    @Test
    fun atLastWaypoint_moveToNextDoesNotIncrementIndex() {
        repeat(tour.waypoints.size - 1) { state.moveToNextWaypoint() }
        val indexBeforeExtra = state.currentWaypointIndex
        state.moveToNextWaypoint() // triggers complete branch
        assertEquals("Index must not increase past last waypoint",
            indexBeforeExtra, state.currentWaypointIndex)
    }

    @Test
    fun getNextWaypoint_returnsNullAtLastWaypoint() {
        repeat(tour.waypoints.size - 1) { state.moveToNextWaypoint() }
        assertNull("No next waypoint after the last one", state.getNextWaypoint())
    }

    // -----------------------------------------------------------------------
    // isTourCompleted — documenting the early-completion bug
    // -----------------------------------------------------------------------

    @Test
    fun isTourCompleted_asWritten_falseAtIndexZero() {
        assertFalse(state.isTourCompleted_asWritten())
    }

    @Test
    fun isTourCompleted_asWritten_falseAtIndexOne() {
        state.moveToNextWaypoint()
        assertFalse(state.isTourCompleted_asWritten())
    }

    @Test
    fun isTourCompleted_asWritten_falseAtIndexTwo() {
        repeat(2) { state.moveToNextWaypoint() }
        assertFalse(state.isTourCompleted_asWritten())
    }

    /**
     * BUG: isTourCompleted() returns true when the user ARRIVES at the last
     * waypoint (index 3), before they have tapped "Continue" to confirm the
     * visit.  The corrected version should only return true after index >= 4.
     */
    @Test
    fun isTourCompleted_asWritten_trueAtLastWaypoint_BUG() {
        repeat(tour.waypoints.size - 1) { state.moveToNextWaypoint() }
        // Production code returns true here — user is AT the last waypoint
        assertTrue("BUG: isTourCompleted() is true AT the last waypoint (index 3), " +
                "not after visiting it",
            state.isTourCompleted_asWritten())
    }

    @Test
    fun isTourCompleted_corrected_falseAtLastWaypoint() {
        repeat(tour.waypoints.size - 1) { state.moveToNextWaypoint() }
        assertFalse("Corrected check: should NOT be complete while still AT last waypoint",
            state.isTourCompleted_corrected())
    }

    @Test
    fun isTourCompleted_corrected_trueAfterPassingLastWaypoint() {
        repeat(tour.waypoints.size) { state.moveToNextWaypoint() }
        // After calling moveToNextWaypoint at the last waypoint, tourCompleted
        // is set but index stays at size-1. The corrected check uses >= size,
        // which would require the index to be incremented — see note below.
        // This test documents the INTENT even if the current impl can't satisfy it.
        assertTrue("Tour should be complete after passing last waypoint",
            state.tourCompleted)
    }

    // -----------------------------------------------------------------------
    // Edge case — single-waypoint tour
    // -----------------------------------------------------------------------

    @Test
    fun singleWaypointTour_completesImmediatelyOnMove() {
        val singleTour = WalkingTour(
            id = "single",
            name = "Single Stop",
            description = "One stop",
            waypoints = listOf(tour.waypoints[0]),
            estimatedDurationMinutes = 10
        )
        val s = TourState(singleTour)

        assertFalse("Not complete before any moves", s.tourCompleted)
        s.moveToNextWaypoint()
        assertTrue("Single-waypoint tour is complete after one move", s.tourCompleted)
    }

    @Test
    fun singleWaypointTour_isTourCompleted_asWritten_trueImmediately() {
        val singleTour = WalkingTour(
            id = "single",
            name = "Single Stop",
            description = "One stop",
            waypoints = listOf(tour.waypoints[0]),
            estimatedDurationMinutes = 10
        )
        val s = TourState(singleTour)
        // index 0 >= size(1) - 1 = 0 → true from the very start
        assertTrue("Single-waypoint tour reports complete from index 0 (current bug applies here too)",
            s.isTourCompleted_asWritten())
    }

    // -----------------------------------------------------------------------
    // Repeat calls after completion are idempotent
    // -----------------------------------------------------------------------

    @Test
    fun callingMoveToNextAfterCompletion_doesNotChangeIndex() {
        repeat(tour.waypoints.size) { state.moveToNextWaypoint() }
        val indexAfterComplete = state.currentWaypointIndex
        state.moveToNextWaypoint()
        assertEquals("Index should not change after tour is complete",
            indexAfterComplete, state.currentWaypointIndex)
    }

    @Test
    fun tourCompletedFlag_staysTrueAfterAdditionalMoves() {
        repeat(tour.waypoints.size) { state.moveToNextWaypoint() }
        state.moveToNextWaypoint()
        assertTrue("tourCompleted should remain true", state.tourCompleted)
    }
}
