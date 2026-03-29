package com.example.bruffwalkingtour

import org.junit.Test
import org.junit.Assert.*
import kotlin.math.*

/**
 * Deep-validation of BruffTourData content.
 *
 * The existing BruffTourDataTest checks that waypoints exist and have
 * valid coordinates. This file goes further and validates:
 *  - Exact proximity radius values for each waypoint
 *  - Image URLs are present and well-formed
 *  - Key historical terms appear in descriptions / historicalInfo
 *  - Waypoints are geographically ordered south along the tour
 *  - Every waypoint lies inside the rectangular tour boundary
 *  - Consecutive waypoints are within a sensible walking distance
 */
class BruffTourDataValidationTest {

    private val tour = BruffTourData.getDefaultTour()
    private val waypoints get() = tour.waypoints

    // Haversine for pure-Kotlin distance (no Android SDK needed)
    private fun distanceMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    // Mirror of LocationService.isOutsideRectangularBoundary
    private fun isOutsideBoundary(lat: Double, lon: Double): Boolean {
        val centreLat = 52.47785299293757
        val centreLon = -8.54801677334652
        val halfW = (1.5 / 2.0) / (111.0 * cos(Math.toRadians(centreLat)))
        val halfH = (3.0 / 2.0) / 111.0
        return lat > centreLat + halfH || lat < centreLat - halfH ||
               lon > centreLon + halfW || lon < centreLon - halfW
    }

    // -----------------------------------------------------------------------
    // Exact proximity radii
    // -----------------------------------------------------------------------

    @Test
    fun thomasFitzgeraldCentre_proximityRadiusIs25m() {
        val wp = waypoints.first { it.id == "thomas_fitzgerald_centre" }
        assertEquals("TF Centre proximity radius should be 25 m", 25.0, wp.proximityRadius, 0.001)
    }

    @Test
    fun bruffCatholicChurch_proximityRadiusIs20m() {
        val wp = waypoints.first { it.id == "bruff_catholic_church" }
        assertEquals("Church proximity radius should be 20 m", 20.0, wp.proximityRadius, 0.001)
    }

    @Test
    fun seanWallMonument_proximityRadiusIs15m() {
        val wp = waypoints.first { it.id == "sean_wall_monument" }
        assertEquals("Sean Wall radius should be 15 m", 15.0, wp.proximityRadius, 0.001)
    }

    @Test
    fun bruffGAAGrounds_proximityRadiusIs30m() {
        val wp = waypoints.first { it.id == "bruff_gaa_grounds" }
        assertEquals("GAA Grounds radius should be 30 m", 30.0, wp.proximityRadius, 0.001)
    }

    // -----------------------------------------------------------------------
    // Image URLs
    // -----------------------------------------------------------------------

    @Test
    fun allWaypoints_haveImageUrls() {
        waypoints.forEach { wp ->
            assertNotNull("${wp.name} should have an imageUrl", wp.imageUrl)
            assertFalse("${wp.name} imageUrl should not be blank", wp.imageUrl!!.isBlank())
        }
    }

    @Test
    fun allWaypoints_imageUrlsStartWithHttps() {
        waypoints.forEach { wp ->
            assertTrue("${wp.name} imageUrl should start with https://",
                wp.imageUrl!!.startsWith("https://"))
        }
    }

    @Test
    fun thomasFitzgeraldCentre_imageUrlPointsToTripAdvisor() {
        val wp = waypoints.first { it.id == "thomas_fitzgerald_centre" }
        assertTrue("TF Centre image should come from TripAdvisor CDN",
            wp.imageUrl!!.contains("tripadvisor"))
    }

    // -----------------------------------------------------------------------
    // Historical info content
    // -----------------------------------------------------------------------

    @Test
    fun thomasFitzgeraldCentre_historicalInfoMentionsKennedy() {
        val wp = waypoints.first { it.id == "thomas_fitzgerald_centre" }
        assertTrue("TF Centre history should mention Kennedy",
            wp.historicalInfo!!.contains("Kennedy"))
    }

    @Test
    fun thomasFitzgeraldCentre_historicalInfoMentions1852Emigration() {
        val wp = waypoints.first { it.id == "thomas_fitzgerald_centre" }
        assertTrue("TF Centre history should mention 1852 emigration date",
            wp.historicalInfo!!.contains("1852"))
    }

    @Test
    fun thomasFitzgeraldCentre_historicalInfoMentions2013Dedication() {
        val wp = waypoints.first { it.id == "thomas_fitzgerald_centre" }
        assertTrue("TF Centre history should mention 2013 dedication",
            wp.historicalInfo!!.contains("2013"))
    }

    @Test
    fun bruffCatholicChurch_historicalInfoMentions1828Start() {
        val wp = waypoints.first { it.id == "bruff_catholic_church" }
        assertTrue("Church history should mention 1828 construction start",
            wp.historicalInfo!!.contains("1828"))
    }

    @Test
    fun bruffCatholicChurch_historicalInfoMentions1833Completion() {
        val wp = waypoints.first { it.id == "bruff_catholic_church" }
        assertTrue("Church history should mention 1833 completion",
            wp.historicalInfo!!.contains("1833"))
    }

    @Test
    fun bruffCatholicChurch_descriptionMentionsGothicRevival() {
        val wp = waypoints.first { it.id == "bruff_catholic_church" }
        assertTrue("Church description should mention Gothic Revival",
            wp.description.contains("Gothic"))
    }

    @Test
    fun seanWallMonument_historicalInfoMentions1952Unveiling() {
        val wp = waypoints.first { it.id == "sean_wall_monument" }
        assertTrue("Sean Wall history should mention 1952 unveiling",
            wp.historicalInfo!!.contains("1952"))
    }

    @Test
    fun seanWallMonument_historicalInfoMentions1921Death() {
        val wp = waypoints.first { it.id == "sean_wall_monument" }
        assertTrue("Sean Wall history should mention 1921 death date",
            wp.historicalInfo!!.contains("1921"))
    }

    @Test
    fun seanWallMonument_descriptionMentionsAlbertPower() {
        val wp = waypoints.first { it.id == "sean_wall_monument" }
        assertTrue("Sean Wall description should credit sculptor Albert Power",
            wp.description.contains("Albert Power"))
    }

    @Test
    fun bruffGAAGrounds_historicalInfoMentions1887Founding() {
        val wp = waypoints.first { it.id == "bruff_gaa_grounds" }
        assertTrue("GAA history should mention 1887 founding",
            wp.historicalInfo!!.contains("1887"))
    }

    @Test
    fun bruffGAAGrounds_historicalInfoMentionsLiamMcCarthy() {
        val wp = waypoints.first { it.id == "bruff_gaa_grounds" }
        assertTrue("GAA history should mention Liam MacCarthy trophy connection",
            wp.historicalInfo!!.contains("MacCarthy") || wp.historicalInfo!!.contains("McCarthy"))
    }

    // -----------------------------------------------------------------------
    // Geographic ordering — tour walks broadly south
    // -----------------------------------------------------------------------

    @Test
    fun waypointLatitudes_decreaseFromFirstToLast() {
        // Every successive waypoint should be at a lower or equal latitude
        for (i in 0 until waypoints.size - 1) {
            assertTrue(
                "Waypoint ${waypoints[i].name} (${waypoints[i].latitude}) should be " +
                "north of or equal to ${waypoints[i+1].name} (${waypoints[i+1].latitude})",
                waypoints[i].latitude >= waypoints[i+1].latitude
            )
        }
    }

    @Test
    fun thomasFitzgeraldCentre_isNorthernmostWaypoint() {
        val tfLat = waypoints.first { it.id == "thomas_fitzgerald_centre" }.latitude
        waypoints.filter { it.id != "thomas_fitzgerald_centre" }.forEach { wp ->
            assertTrue("TF Centre should be further north than ${wp.name}",
                tfLat >= wp.latitude)
        }
    }

    @Test
    fun bruffGAAGrounds_isSouthernmostWaypoint() {
        val gaaLat = waypoints.first { it.id == "bruff_gaa_grounds" }.latitude
        waypoints.filter { it.id != "bruff_gaa_grounds" }.forEach { wp ->
            assertTrue("GAA Grounds should be further south than ${wp.name}",
                gaaLat <= wp.latitude)
        }
    }

    @Test
    fun bruffGAAGrounds_isEasternmostWaypoint() {
        // GAA Grounds is noticeably east of the other three (-8.541 vs -8.548)
        val gaaLon = waypoints.first { it.id == "bruff_gaa_grounds" }.longitude
        waypoints.filter { it.id != "bruff_gaa_grounds" }.forEach { wp ->
            assertTrue("GAA Grounds should be further east than ${wp.name}",
                gaaLon > wp.longitude)
        }
    }

    // -----------------------------------------------------------------------
    // All waypoints lie inside the tour boundary
    // -----------------------------------------------------------------------

    @Test
    fun allWaypoints_areInsideTourBoundary() {
        waypoints.forEach { wp ->
            assertFalse("${wp.name} should be inside the tour boundary",
                isOutsideBoundary(wp.latitude, wp.longitude))
        }
    }

    // -----------------------------------------------------------------------
    // Consecutive waypoints are within reasonable walking distance
    // -----------------------------------------------------------------------

    @Test
    fun consecutiveWaypoints_areWithin800mOfEachOther() {
        for (i in 0 until waypoints.size - 1) {
            val a = waypoints[i]
            val b = waypoints[i + 1]
            val dist = distanceMetres(a.latitude, a.longitude, b.latitude, b.longitude)
            assertTrue(
                "${a.name} → ${b.name} is ${dist.toInt()} m — should be within 800 m",
                dist <= 800.0
            )
        }
    }

    @Test
    fun consecutiveWaypoints_areAtLeast50mApart() {
        // They should be distinct locations, not duplicates
        for (i in 0 until waypoints.size - 1) {
            val a = waypoints[i]
            val b = waypoints[i + 1]
            val dist = distanceMetres(a.latitude, a.longitude, b.latitude, b.longitude)
            assertTrue(
                "${a.name} → ${b.name} is only ${dist.toInt()} m — they should be distinct locations",
                dist >= 50.0
            )
        }
    }

    @Test
    fun tfToChurch_distanceIsUnder200m() {
        val tf     = waypoints.first { it.id == "thomas_fitzgerald_centre" }
        val church = waypoints.first { it.id == "bruff_catholic_church" }
        val dist = distanceMetres(tf.latitude, tf.longitude, church.latitude, church.longitude)
        assertTrue("TF Centre to Church should be under 200 m (actual: ${dist.toInt()} m)", dist < 200)
    }

    @Test
    fun gaaGrounds_isMoreThanHalfKilometreFromTfCentre() {
        val tf  = waypoints.first { it.id == "thomas_fitzgerald_centre" }
        val gaa = waypoints.first { it.id == "bruff_gaa_grounds" }
        val dist = distanceMetres(tf.latitude, tf.longitude, gaa.latitude, gaa.longitude)
        assertTrue("TF Centre to GAA should be > 500 m (actual: ${dist.toInt()} m)", dist > 500)
    }
}
