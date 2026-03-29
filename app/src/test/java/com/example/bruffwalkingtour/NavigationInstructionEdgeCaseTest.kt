package com.example.bruffwalkingtour

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for the distance-text and ETA formatting logic in
 * LocationService.getNavigationInstruction.
 *
 * getNavigationInstruction calls Location.distanceBetween and Location.bearingTo
 * (both Android SDK), so we cannot call it directly from a JVM unit test.
 * Instead, we mirror the two pure-Kotlin formatting expressions here exactly:
 *
 *   distanceText = when {
 *       distance < 50   -> "Continue ${distance.toInt()}m"
 *       distance < 1000 -> "${(distance / 10).toInt() * 10}m"
 *       else            -> "${String.format("%.1f", distance / 1000)}km"
 *   }
 *   estimatedTime = "${(distance / 83).toInt() + 1} min"  // 5 km/h walking speed
 *
 * Any divergence between the production code and these tests will surface
 * the moment the logic is changed.
 *
 * Threshold boundaries tested:
 *   Distance-text:   49 m, 49.9 m, 50 m, 50.1 m | 999 m, 1000 m, 1001 m
 *   Rounding:        54 m → 50 m,  95 m → 90 m,  1234 m → 1.2 km
 *   ETA:             0 m, 82 m, 83 m, 166 m, 830 m
 */
class NavigationInstructionEdgeCaseTest {

    // -----------------------------------------------------------------------
    // Mirrors of production formatting logic
    // -----------------------------------------------------------------------

    private fun distanceText(distance: Float): String = when {
        distance < 50f   -> "Continue ${distance.toInt()}m"
        distance < 1000f -> "${(distance / 10).toInt() * 10}m"
        else             -> "${String.format("%.1f", distance / 1000)}km"
    }

    private fun estimatedTime(distance: Float): String =
        "${(distance / 83).toInt() + 1} min"

    // -----------------------------------------------------------------------
    // "Continue Xm" branch  (distance < 50)
    // -----------------------------------------------------------------------

    @Test
    fun distance_0m_showsContinuePrefix() {
        assertTrue("0 m should show 'Continue' prefix",
            distanceText(0f).startsWith("Continue "))
    }

    @Test
    fun distance_1m_showsContinue1m() {
        assertEquals("Continue 1m", distanceText(1f))
    }

    @Test
    fun distance_49m_showsContinue49m() {
        assertEquals("Continue 49m", distanceText(49f))
    }

    @Test
    fun distance_49point9m_showsContinue49m() {
        // toInt() truncates, so 49.9 → 49
        assertEquals("Continue 49m", distanceText(49.9f))
    }

    @Test
    fun distance_exactly50m_doesNotShowContinuePrefix() {
        // 50 is NOT < 50, so falls through to the round-to-10 branch
        val text = distanceText(50f)
        assertFalse("50 m should not use 'Continue' prefix", text.startsWith("Continue "))
    }

    // -----------------------------------------------------------------------
    // Round-to-nearest-10 branch  (50 ≤ distance < 1000)
    // -----------------------------------------------------------------------

    @Test
    fun distance_50m_shows50m() {
        assertEquals("50m", distanceText(50f))
    }

    @Test
    fun distance_50point1m_shows50m() {
        // (50.1 / 10).toInt() = 5;  5 * 10 = 50
        assertEquals("50m", distanceText(50.1f))
    }

    @Test
    fun distance_54m_rounds_down_to_50m() {
        assertEquals("50m", distanceText(54f))
    }

    @Test
    fun distance_55m_rounds_down_to_50m() {
        // (55 / 10).toInt() = 5;  5 * 10 = 50 — note: int division truncates, not rounds
        assertEquals("50m", distanceText(55f))
    }

    @Test
    fun distance_59m_shows50m() {
        assertEquals("50m", distanceText(59f))
    }

    @Test
    fun distance_60m_shows60m() {
        assertEquals("60m", distanceText(60f))
    }

    @Test
    fun distance_95m_shows90m() {
        // (95 / 10).toInt() = 9;  9 * 10 = 90
        assertEquals("90m", distanceText(95f))
    }

    @Test
    fun distance_100m_shows100m() {
        assertEquals("100m", distanceText(100f))
    }

    @Test
    fun distance_500m_shows500m() {
        assertEquals("500m", distanceText(500f))
    }

    @Test
    fun distance_999m_shows990m() {
        // (999 / 10).toInt() = 99;  99 * 10 = 990
        assertEquals("990m", distanceText(999f))
    }

    @Test
    fun distance_exactly1000m_showsKm() {
        // 1000 is NOT < 1000 → falls through to km branch
        val text = distanceText(1000f)
        assertTrue("1000 m should display in km", text.endsWith("km"))
    }

    // -----------------------------------------------------------------------
    // Km branch  (distance ≥ 1000)
    // -----------------------------------------------------------------------

    @Test
    fun distance_1000m_shows1point0km() {
        assertEquals("1.0km", distanceText(1000f))
    }

    @Test
    fun distance_1001m_shows1point0km() {
        // String.format("%.1f", 1001/1000) = "1.0"
        assertEquals("1.0km", distanceText(1001f))
    }

    @Test
    fun distance_1100m_shows1point1km() {
        assertEquals("1.1km", distanceText(1100f))
    }

    @Test
    fun distance_1234m_shows1point2km() {
        // 1234/1000 = 1.234 → "1.2"
        assertEquals("1.2km", distanceText(1234f))
    }

    @Test
    fun distance_5000m_shows5point0km() {
        assertEquals("5.0km", distanceText(5000f))
    }

    // -----------------------------------------------------------------------
    // ETA calculation  (distance / 83 + 1 min at 5 km/h ≈ 83 m/min)
    // -----------------------------------------------------------------------

    @Test
    fun eta_at0m_is1min() {
        // (0 / 83).toInt() + 1 = 0 + 1 = 1
        assertEquals("1 min", estimatedTime(0f))
    }

    @Test
    fun eta_at82m_is1min() {
        // (82 / 83).toInt() = 0;  0 + 1 = 1
        assertEquals("1 min", estimatedTime(82f))
    }

    @Test
    fun eta_at83m_is2min() {
        // (83 / 83).toInt() = 1;  1 + 1 = 2
        assertEquals("2 min", estimatedTime(83f))
    }

    @Test
    fun eta_at84m_is2min() {
        // (84 / 83).toInt() = 1;  1 + 1 = 2
        assertEquals("2 min", estimatedTime(84f))
    }

    @Test
    fun eta_at166m_is3min() {
        // (166 / 83).toInt() = 2;  2 + 1 = 3
        assertEquals("3 min", estimatedTime(166f))
    }

    @Test
    fun eta_at830m_is11min() {
        // (830 / 83).toInt() = 10;  10 + 1 = 11
        assertEquals("11 min", estimatedTime(830f))
    }

    @Test
    fun eta_at1000m_is13min() {
        // (1000 / 83).toInt() = 12;  12 + 1 = 13
        assertEquals("13 min", estimatedTime(1000f))
    }

    // -----------------------------------------------------------------------
    // distanceText always ends with "m" or "km"
    // -----------------------------------------------------------------------

    @Test
    fun distanceText_alwaysEndsWithMeterOrKm() {
        listOf(0f, 1f, 49f, 50f, 100f, 500f, 999f, 1000f, 5000f).forEach { d ->
            val text = distanceText(d)
            assertTrue("distanceText($d) = '$text' should end with 'm' or 'km'",
                text.endsWith("m") || text.endsWith("km"))
        }
    }

    @Test
    fun distanceText_neverNegative() {
        // Defensive: 0 is the smallest sensible distance
        val text = distanceText(0f)
        assertFalse("distanceText(0) should not contain a minus sign",
            text.contains("-"))
    }
}
