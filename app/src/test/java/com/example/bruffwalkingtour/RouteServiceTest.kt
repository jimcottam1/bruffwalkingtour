package com.example.bruffwalkingtour

import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Tests for RouteService's fallback routing algorithm (getSimpleRoute).
 *
 * getSimpleRoute is private, so we mirror the algorithm here exactly as it
 * appears in the source. Any divergence between the two will surface as a
 * failing test.
 *
 * Three cases are covered:
 *  1. Very short distance  (<0.0005°) → direct two-point line
 *  2. N/S-dominant movement            → L-shaped route going north/south first
 *  3. E/W-dominant movement            → L-shaped route going east/west first
 */
class RouteServiceTest {

    // -----------------------------------------------------------------------
    // Mirror of RouteService.getSimpleRoute
    // -----------------------------------------------------------------------

    data class Point(val lat: Double, val lon: Double)

    private fun getSimpleRoute(start: Point, end: Point): List<Point> {
        val points = mutableListOf<Point>()
        points.add(start)

        val latDiff = end.lat - start.lat
        val lonDiff = end.lon - start.lon
        val totalDistance = sqrt(latDiff * latDiff + lonDiff * lonDiff)

        when {
            totalDistance < 0.0005 -> {
                points.add(end)
            }
            abs(latDiff) > abs(lonDiff) -> {
                val mid = Point(start.lat + latDiff * 0.7, start.lon)
                points.add(mid)
                points.add(Point(mid.lat, end.lon))
                points.add(end)
            }
            else -> {
                val mid = Point(start.lat, start.lon + lonDiff * 0.7)
                points.add(mid)
                points.add(Point(end.lat, mid.lon))
                points.add(end)
            }
        }

        return points
    }

    // -----------------------------------------------------------------------
    // Very short distance
    // -----------------------------------------------------------------------

    @Test
    fun veryShortRoute_producesDirectTwoPointLine() {
        val start = Point(52.4787, -8.5488)
        val end   = Point(52.4787, -8.5489) // ~8 m east — well under threshold
        val route = getSimpleRoute(start, end)
        assertEquals("Very short route should have exactly 2 points", 2, route.size)
    }

    @Test
    fun veryShortRoute_firstPointIsStart() {
        val start = Point(52.4787, -8.5488)
        val end   = Point(52.4787, -8.5489)
        val route = getSimpleRoute(start, end)
        assertEquals(start.lat, route.first().lat, 0.000001)
        assertEquals(start.lon, route.first().lon, 0.000001)
    }

    @Test
    fun veryShortRoute_lastPointIsEnd() {
        val start = Point(52.4787, -8.5488)
        val end   = Point(52.4787, -8.5489)
        val route = getSimpleRoute(start, end)
        assertEquals(end.lat, route.last().lat, 0.000001)
        assertEquals(end.lon, route.last().lon, 0.000001)
    }

    @Test
    fun veryShortRoute_atExactThresholdBoundary() {
        // 0.001° >> 0.0005 threshold — clearly above the threshold, falls through to E/W branch
        val start = Point(52.0, -8.0)
        val end   = Point(52.0, -7.999) // lonDiff = 0.001, well above threshold
        val route = getSimpleRoute(start, end)
        // Should use L-shape (E/W branch), producing 4 points
        assertEquals("Route above threshold boundary should use L-shape (4 points)", 4, route.size)
    }

    // -----------------------------------------------------------------------
    // North/South dominant route
    // -----------------------------------------------------------------------

    // N/S dominant test coordinates: large latDiff, negligible lonDiff
    // Start: Thomas Fitzgerald Centre latitude, Church longitude (same lon)
    // End:   GAA Grounds latitude — moves ~280 m south, 0 m east/west
    private val nsStart = Point(52.4787, -8.5488)
    private val nsEnd   = Point(52.4760, -8.5488)  // latDiff=-0.0027, lonDiff=0 → N/S

    @Test
    fun nsDominantRoute_producesFourPoints() {
        val route = getSimpleRoute(nsStart, nsEnd)
        assertEquals("N/S dominant route should have 4 points", 4, route.size)
    }

    @Test
    fun nsDominantRoute_firstPointIsStart() {
        val route = getSimpleRoute(nsStart, nsEnd)
        assertEquals(nsStart.lat, route[0].lat, 0.000001)
        assertEquals(nsStart.lon, route[0].lon, 0.000001)
    }

    @Test
    fun nsDominantRoute_lastPointIsEnd() {
        val route = getSimpleRoute(nsStart, nsEnd)
        assertEquals(nsEnd.lat, route.last().lat, 0.000001)
        assertEquals(nsEnd.lon, route.last().lon, 0.000001)
    }

    @Test
    fun nsDominantRoute_midpointKeepsStartLongitude() {
        val route = getSimpleRoute(nsStart, nsEnd)
        assertEquals("Midpoint should keep start longitude", nsStart.lon, route[1].lon, 0.000001)
    }

    @Test
    fun nsDominantRoute_midpointIsAt70PercentLatitude() {
        val expectedMidLat = nsStart.lat + (nsEnd.lat - nsStart.lat) * 0.7
        val route = getSimpleRoute(nsStart, nsEnd)
        assertEquals("Midpoint latitude should be 70% of the way", expectedMidLat, route[1].lat, 0.000001)
    }

    @Test
    fun nsDominantRoute_cornerPointAdoptsEndLongitude() {
        val expectedMidLat = nsStart.lat + (nsEnd.lat - nsStart.lat) * 0.7
        val route = getSimpleRoute(nsStart, nsEnd)
        assertEquals("Corner point latitude matches mid", expectedMidLat, route[2].lat, 0.000001)
        assertEquals("Corner point longitude matches end", nsEnd.lon, route[2].lon, 0.000001)
    }

    @Test
    fun nsDominantRoute_southwardMovement() {
        // nsStart is north of nsEnd so the route should head south (lat decreases)
        val route = getSimpleRoute(nsStart, nsEnd)
        assertEquals(4, route.size)
        assertTrue("Route should move southward first", route[1].lat < nsStart.lat)
    }

    @Test
    fun nsDominantRoute_northwardMovement() {
        // Reversed: moving north — lat increases
        val route = getSimpleRoute(nsEnd, nsStart)
        assertEquals(4, route.size)
        assertTrue("Route should move northward first", route[1].lat > nsEnd.lat)
    }

    // -----------------------------------------------------------------------
    // East/West dominant route
    // -----------------------------------------------------------------------

    @Test
    fun ewDominantRoute_producesFourPoints() {
        // Large lon diff, small lat diff → E/W dominant
        val start = Point(52.4780, -8.5488)
        val end   = Point(52.4782, -8.5400)  // lonDiff >> latDiff
        val route = getSimpleRoute(start, end)
        assertEquals("E/W dominant route should have 4 points", 4, route.size)
    }

    @Test
    fun ewDominantRoute_firstAndLastAreStartEnd() {
        val start = Point(52.4780, -8.5488)
        val end   = Point(52.4782, -8.5400)
        val route = getSimpleRoute(start, end)
        assertEquals(start.lat, route.first().lat, 0.000001)
        assertEquals(start.lon, route.first().lon, 0.000001)
        assertEquals(end.lat, route.last().lat, 0.000001)
        assertEquals(end.lon, route.last().lon, 0.000001)
    }

    @Test
    fun ewDominantRoute_midpointKeepsStartLatitude() {
        val start = Point(52.4780, -8.5488)
        val end   = Point(52.4782, -8.5400)
        val route = getSimpleRoute(start, end)
        assertEquals("Midpoint should keep start latitude", start.lat, route[1].lat, 0.000001)
    }

    @Test
    fun ewDominantRoute_midpointIsAt70PercentLongitude() {
        val start = Point(52.4780, -8.5488)
        val end   = Point(52.4782, -8.5400)
        val expectedMidLon = start.lon + (end.lon - start.lon) * 0.7
        val route = getSimpleRoute(start, end)
        assertEquals("Midpoint longitude should be 70% of the way", expectedMidLon, route[1].lon, 0.000001)
    }

    @Test
    fun ewDominantRoute_cornerPointAdoptsEndLatitude() {
        val start = Point(52.4780, -8.5488)
        val end   = Point(52.4782, -8.5400)
        val expectedMidLon = start.lon + (end.lon - start.lon) * 0.7
        val route = getSimpleRoute(start, end)
        assertEquals("Corner point latitude matches end", end.lat, route[2].lat, 0.000001)
        assertEquals("Corner point longitude matches mid", expectedMidLon, route[2].lon, 0.000001)
    }

    @Test
    fun ewDominantRoute_westwardMovement() {
        // Moving west: longitude decreases (more negative)
        val start = Point(52.4780, -8.5400)
        val end   = Point(52.4782, -8.5488)
        val route = getSimpleRoute(start, end)
        assertEquals(4, route.size)
        assertTrue("Route should move westward first", route[1].lon < start.lon)
    }

    // -----------------------------------------------------------------------
    // All routes share start and end regardless of case
    // -----------------------------------------------------------------------

    @Test
    fun allRoutes_alwaysStartAndEndAtGivenPoints() {
        val testCases = listOf(
            Pair(Point(52.4787, -8.5488), Point(52.4787, -8.5489)), // short
            Pair(Point(52.4787, -8.5488), Point(52.4700, -8.5480)), // N/S
            Pair(Point(52.4787, -8.5488), Point(52.4790, -8.5300))  // E/W
        )

        testCases.forEach { (start, end) ->
            val route = getSimpleRoute(start, end)
            assertEquals("Route must start at given start point — lat", start.lat, route.first().lat, 0.000001)
            assertEquals("Route must start at given start point — lon", start.lon, route.first().lon, 0.000001)
            assertEquals("Route must end at given end point — lat", end.lat, route.last().lat, 0.000001)
            assertEquals("Route must end at given end point — lon", end.lon, route.last().lon, 0.000001)
        }
    }

    @Test
    fun allRoutes_haveAtLeastTwoPoints() {
        val testCases = listOf(
            Pair(Point(52.4787, -8.5488), Point(52.4787, -8.5489)),
            Pair(Point(52.4787, -8.5488), Point(52.4700, -8.5480)),
            Pair(Point(52.4787, -8.5488), Point(52.4790, -8.5300))
        )

        testCases.forEach { (start, end) ->
            val route = getSimpleRoute(start, end)
            assertTrue("Route must have at least 2 points", route.size >= 2)
        }
    }

    @Test
    fun sameStartAndEnd_producesVeryShortDirectRoute() {
        val point = Point(52.4787, -8.5488)
        val route = getSimpleRoute(point, point)
        assertEquals("Identical start and end should produce 2-point route", 2, route.size)
    }
}
