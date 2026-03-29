package com.example.bruffwalkingtour

import org.junit.Test
import org.junit.Assert.*
import kotlin.math.cos
import kotlin.math.PI

/**
 * Tests for the rectangular geo-fence boundary used in LocationService.
 *
 * The boundary is centred on the Sean Wall Monument and sized to cover the
 * entire Bruff town walking tour area. isOutsideRectangularBoundary is
 * private in LocationService so we mirror the algorithm here exactly.
 *
 * Approximate calculated bounds (used as reference in comments):
 *   North: ~52.4914°    South: ~52.4643°
 *   East:  ~-8.5369°    West:  ~-8.5591°
 */
class BoundaryDetectionTest {

    companion object {
        private const val CENTER_LAT = 52.47785299293757
        private const val CENTER_LON = -8.54801677334652
        private const val WIDTH_KM   = 1.5
        private const val HEIGHT_KM  = 3.0
    }

    private fun isOutsideBoundary(lat: Double, lon: Double): Boolean {
        val halfWidthDeg  = (WIDTH_KM  / 2.0) / (111.0 * cos(Math.toRadians(CENTER_LAT)))
        val halfHeightDeg = (HEIGHT_KM / 2.0) / 111.0
        val north = CENTER_LAT + halfHeightDeg
        val south = CENTER_LAT - halfHeightDeg
        val east  = CENTER_LON + halfWidthDeg
        val west  = CENTER_LON - halfWidthDeg
        return lat > north || lat < south || lon > east || lon < west
    }

    // -----------------------------------------------------------------------
    // Centre and known-inside points
    // -----------------------------------------------------------------------

    @Test
    fun centreOfBruff_isInsideBoundary() {
        assertFalse("Centre of tour area should be inside boundary",
            isOutsideBoundary(CENTER_LAT, CENTER_LON))
    }

    @Test
    fun thomasFitzgeraldCentre_isInsideBoundary() {
        assertFalse("Thomas Fitzgerald Centre (52.478689, -8.548776) should be inside boundary",
            isOutsideBoundary(52.478689, -8.548776))
    }

    @Test
    fun bruffCatholicChurch_isInsideBoundary() {
        assertFalse("Bruff Catholic Church should be inside boundary",
            isOutsideBoundary(52.478558, -8.548009))
    }

    @Test
    fun seanWallMonument_isInsideBoundary() {
        assertFalse("Sean Wall Monument should be inside boundary",
            isOutsideBoundary(52.477636, -8.547905))
    }

    @Test
    fun bruffGAAGrounds_isInsideBoundary() {
        assertFalse("GAA Grounds should be inside boundary",
            isOutsideBoundary(52.476002, -8.541206))
    }

    // -----------------------------------------------------------------------
    // Outside — each cardinal direction
    // -----------------------------------------------------------------------

    @Test
    fun pointFarNorth_isOutsideBoundary() {
        // 5 km north of centre
        assertOutside(CENTER_LAT + 0.045, CENTER_LON, "5 km north")
    }

    @Test
    fun pointFarSouth_isOutsideBoundary() {
        assertOutside(CENTER_LAT - 0.045, CENTER_LON, "5 km south")
    }

    @Test
    fun pointFarEast_isOutsideBoundary() {
        assertOutside(CENTER_LAT, CENTER_LON + 0.05, "far east")
    }

    @Test
    fun pointFarWest_isOutsideBoundary() {
        assertOutside(CENTER_LAT, CENTER_LON - 0.05, "far west")
    }

    @Test
    fun limerick_city_isOutsideBoundary() {
        // Limerick city centre is ~16 km east of Bruff
        assertOutside(52.6638, -8.6238, "Limerick city")
    }

    @Test
    fun dublin_isOutsideBoundary() {
        assertOutside(53.3498, -6.2603, "Dublin")
    }

    @Test
    fun cork_isOutsideBoundary() {
        assertOutside(51.8985, -8.4756, "Cork")
    }

    // -----------------------------------------------------------------------
    // Boundary edge behaviour (just inside / just outside)
    // -----------------------------------------------------------------------

    @Test
    fun pointJustInsideNorthBound_isInsideBoundary() {
        val halfHeightDeg = (HEIGHT_KM / 2.0) / 111.0
        val northBound = CENTER_LAT + halfHeightDeg
        assertFalse("Point just inside north bound should be inside",
            isOutsideBoundary(northBound - 0.0001, CENTER_LON))
    }

    @Test
    fun pointJustOutsideNorthBound_isOutsideBoundary() {
        val halfHeightDeg = (HEIGHT_KM / 2.0) / 111.0
        val northBound = CENTER_LAT + halfHeightDeg
        assertTrue("Point just outside north bound should be outside",
            isOutsideBoundary(northBound + 0.0001, CENTER_LON))
    }

    @Test
    fun pointJustInsideSouthBound_isInsideBoundary() {
        val halfHeightDeg = (HEIGHT_KM / 2.0) / 111.0
        val southBound = CENTER_LAT - halfHeightDeg
        assertFalse("Point just inside south bound should be inside",
            isOutsideBoundary(southBound + 0.0001, CENTER_LON))
    }

    @Test
    fun pointJustOutsideSouthBound_isOutsideBoundary() {
        val halfHeightDeg = (HEIGHT_KM / 2.0) / 111.0
        val southBound = CENTER_LAT - halfHeightDeg
        assertTrue("Point just outside south bound should be outside",
            isOutsideBoundary(southBound - 0.0001, CENTER_LON))
    }

    @Test
    fun pointJustInsideEastBound_isInsideBoundary() {
        val halfWidthDeg = (WIDTH_KM / 2.0) / (111.0 * cos(Math.toRadians(CENTER_LAT)))
        val eastBound = CENTER_LON + halfWidthDeg
        assertFalse("Point just inside east bound should be inside",
            isOutsideBoundary(CENTER_LAT, eastBound - 0.0001))
    }

    @Test
    fun pointJustOutsideEastBound_isOutsideBoundary() {
        val halfWidthDeg = (WIDTH_KM / 2.0) / (111.0 * cos(Math.toRadians(CENTER_LAT)))
        val eastBound = CENTER_LON + halfWidthDeg
        assertTrue("Point just outside east bound should be outside",
            isOutsideBoundary(CENTER_LAT, eastBound + 0.0001))
    }

    @Test
    fun pointJustInsideWestBound_isInsideBoundary() {
        val halfWidthDeg = (WIDTH_KM / 2.0) / (111.0 * cos(Math.toRadians(CENTER_LAT)))
        val westBound = CENTER_LON - halfWidthDeg
        assertFalse("Point just inside west bound should be inside",
            isOutsideBoundary(CENTER_LAT, westBound + 0.0001))
    }

    @Test
    fun pointJustOutsideWestBound_isOutsideBoundary() {
        val halfWidthDeg = (WIDTH_KM / 2.0) / (111.0 * cos(Math.toRadians(CENTER_LAT)))
        val westBound = CENTER_LON - halfWidthDeg
        assertTrue("Point just outside west bound should be outside",
            isOutsideBoundary(CENTER_LAT, westBound - 0.0001))
    }

    // -----------------------------------------------------------------------
    // Boundary dimensions sanity checks
    // -----------------------------------------------------------------------

    @Test
    fun northSouthSpan_isApproximately3km() {
        val halfHeightDeg = (HEIGHT_KM / 2.0) / 111.0
        val northBound = CENTER_LAT + halfHeightDeg
        val southBound = CENTER_LAT - halfHeightDeg
        val spanKm = (northBound - southBound) * 111.0
        assertEquals("N/S span should be ~3 km", 3.0, spanKm, 0.05)
    }

    @Test
    fun eastWestSpan_isApproximately1point5km() {
        val halfWidthDeg  = (WIDTH_KM / 2.0) / (111.0 * cos(Math.toRadians(CENTER_LAT)))
        val eastBound = CENTER_LON + halfWidthDeg
        val westBound = CENTER_LON - halfWidthDeg
        val spanKm = (eastBound - westBound) * 111.0 * cos(Math.toRadians(CENTER_LAT))
        assertEquals("E/W span should be ~1.5 km", 1.5, spanKm, 0.05)
    }

    @Test
    fun boundary_isWiderNorthSouthThanEastWest() {
        val halfHeightDeg = (HEIGHT_KM / 2.0) / 111.0
        val halfWidthDeg  = (WIDTH_KM  / 2.0) / (111.0 * cos(Math.toRadians(CENTER_LAT)))
        assertTrue("Boundary should be taller (N/S) than wide (E/W) in degrees",
            halfHeightDeg > halfWidthDeg)
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private fun assertOutside(lat: Double, lon: Double, label: String) {
        assertTrue("$label ($lat, $lon) should be outside boundary",
            isOutsideBoundary(lat, lon))
    }
}
