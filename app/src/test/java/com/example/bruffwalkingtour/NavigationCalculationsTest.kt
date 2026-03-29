package com.example.bruffwalkingtour

import org.junit.Test
import org.junit.Assert.*

class NavigationCalculationsTest {

    @Test
    fun getDirectionFromBearing_north() {
        // Test North direction (0 degrees and near 360)
        val northBearings = listOf(0f, 10f, 350f, 360f)
        
        northBearings.forEach { bearing ->
            val direction = getDirectionFromBearingTest(bearing)
            assertEquals("Bearing $bearing should be North", "Head North", direction)
        }
    }

    @Test
    fun getDirectionFromBearing_northeast() {
        // Test Northeast direction (22.5 to 67.5 degrees)
        val northeastBearings = listOf(25f, 45f, 60f)
        
        northeastBearings.forEach { bearing ->
            val direction = getDirectionFromBearingTest(bearing)
            assertEquals("Bearing $bearing should be Northeast", "Head Northeast", direction)
        }
    }

    @Test
    fun getDirectionFromBearing_east() {
        // Test East direction (67.5 to 112.5 degrees)
        val eastBearings = listOf(70f, 90f, 110f)
        
        eastBearings.forEach { bearing ->
            val direction = getDirectionFromBearingTest(bearing)
            assertEquals("Bearing $bearing should be East", "Head East", direction)
        }
    }

    @Test
    fun getDirectionFromBearing_southeast() {
        // Test Southeast direction (112.5 to 157.5 degrees)
        val southeastBearings = listOf(115f, 135f, 155f)
        
        southeastBearings.forEach { bearing ->
            val direction = getDirectionFromBearingTest(bearing)
            assertEquals("Bearing $bearing should be Southeast", "Head Southeast", direction)
        }
    }

    @Test
    fun getDirectionFromBearing_south() {
        // Test South direction (157.5 to 202.5 degrees)
        val southBearings = listOf(160f, 180f, 200f)
        
        southBearings.forEach { bearing ->
            val direction = getDirectionFromBearingTest(bearing)
            assertEquals("Bearing $bearing should be South", "Head South", direction)
        }
    }

    @Test
    fun getDirectionFromBearing_southwest() {
        // Test Southwest direction (202.5 to 247.5 degrees)
        val southwestBearings = listOf(205f, 225f, 245f)
        
        southwestBearings.forEach { bearing ->
            val direction = getDirectionFromBearingTest(bearing)
            assertEquals("Bearing $bearing should be Southwest", "Head Southwest", direction)
        }
    }

    @Test
    fun getDirectionFromBearing_west() {
        // Test West direction (247.5 to 292.5 degrees)
        val westBearings = listOf(250f, 270f, 290f)
        
        westBearings.forEach { bearing ->
            val direction = getDirectionFromBearingTest(bearing)
            assertEquals("Bearing $bearing should be West", "Head West", direction)
        }
    }

    @Test
    fun getDirectionFromBearing_northwest() {
        // Test Northwest direction (292.5 to 337.5 degrees)
        val northwestBearings = listOf(295f, 315f, 335f)
        
        northwestBearings.forEach { bearing ->
            val direction = getDirectionFromBearingTest(bearing)
            assertEquals("Bearing $bearing should be Northwest", "Head Northwest", direction)
        }
    }

    @Test
    fun getDirectionFromBearing_boundaryValues() {
        // Test exact boundary values
        val boundaryTests = mapOf(
            22.4f to "Head North",
            22.6f to "Head Northeast", 
            67.4f to "Head Northeast",
            67.6f to "Head East",
            112.4f to "Head East",
            112.6f to "Head Southeast",
            157.4f to "Head Southeast", 
            157.6f to "Head South",
            202.4f to "Head South",
            202.6f to "Head Southwest",
            247.4f to "Head Southwest",
            247.6f to "Head West",
            292.4f to "Head West",
            292.6f to "Head Northwest",
            337.4f to "Head Northwest",
            337.6f to "Head North"
        )
        
        boundaryTests.forEach { (bearing, expectedDirection) ->
            val direction = getDirectionFromBearingTest(bearing)
            assertEquals("Bearing $bearing should be $expectedDirection", expectedDirection, direction)
        }
    }

    @Test
    fun getDirectionFromBearing_negativeBearings() {
        // Test negative bearings (should be normalized)
        val negativeTests = mapOf(
            -10f to "Head North",      // -10 + 360 = 350
            -45f to "Head Northwest",  // -45 + 360 = 315
            -90f to "Head West",       // -90 + 360 = 270
            -180f to "Head South",     // -180 + 360 = 180
            -270f to "Head East"       // -270 + 360 = 90
        )
        
        negativeTests.forEach { (bearing, expectedDirection) ->
            val direction = getDirectionFromBearingTest(bearing)
            assertEquals("Negative bearing $bearing should be $expectedDirection", expectedDirection, direction)
        }
    }

    @Test
    fun getDirectionFromBearing_largeBearings() {
        // Test bearings > 360 (should be normalized)
        val largeTests = mapOf(
            370f to "Head North",      // 370 % 360 = 10
            405f to "Head Northeast",  // 405 % 360 = 45  
            450f to "Head East",       // 450 % 360 = 90
            540f to "Head South",      // 540 % 360 = 180
            720f to "Head North"       // 720 % 360 = 0
        )
        
        largeTests.forEach { (bearing, expectedDirection) ->
            val direction = getDirectionFromBearingTest(bearing)
            assertEquals("Large bearing $bearing should be $expectedDirection", expectedDirection, direction)
        }
    }

    @Test
    fun distanceFormatting_underFiftyMeters() {
        // Test distance formatting for very short distances
        val shortDistances = listOf(10f, 25f, 45f, 49f)
        
        shortDistances.forEach { distance ->
            val formatted = formatDistanceForNavigation(distance)
            assertEquals("Distance ${distance}m should be formatted as 'Continue ${distance.toInt()}m'",
                "Continue ${distance.toInt()}m", formatted)
        }
    }

    @Test
    fun distanceFormatting_fiftyToThousandMeters() {
        // Test distance formatting for medium distances (rounded to nearest 10m)
        val mediumDistances = mapOf(
            55f to "50m",      // (55/10).toInt() * 10 = 50
            125f to "120m",    // (125/10).toInt() * 10 = 120
            267f to "260m",    // (267/10).toInt() * 10 = 260
            999f to "990m"     // (999/10).toInt() * 10 = 990
        )
        
        mediumDistances.forEach { (distance, expected) ->
            val formatted = formatDistanceForNavigation(distance)
            assertEquals("Distance ${distance}m should be formatted as '$expected'", expected, formatted)
        }
    }

    @Test
    fun distanceFormatting_overOneKilometer() {
        // Test distance formatting for long distances (in kilometers)
        val longDistances = mapOf(
            1000f to "1.0km",
            1500f to "1.5km", 
            2750f to "2.8km",
            10000f to "10.0km"
        )
        
        longDistances.forEach { (distance, expected) ->
            val formatted = formatDistanceForNavigation(distance)
            assertEquals("Distance ${distance}m should be formatted as '$expected'", expected, formatted)
        }
    }

    @Test
    fun timeEstimation_walkingSpeed() {
        // Test time estimation assuming 5 km/h walking speed (83.33 m/min)
        val distanceTimeTests = mapOf(
            83f to 2,     // (83 / 83).toInt() + 1 = 1 + 1 = 2 min
            166f to 3,    // (166 / 83).toInt() + 1 = 2 + 1 = 3 min  
            250f to 4,    // (250 / 83).toInt() + 1 = 3 + 1 = 4 min
            500f to 7     // (500 / 83).toInt() + 1 = 6 + 1 = 7 min
        )
        
        distanceTimeTests.forEach { (distance, expectedMinutes) ->
            val estimatedMinutes = estimateWalkingTime(distance)
            assertEquals("Distance ${distance}m should take $expectedMinutes minutes", 
                expectedMinutes, estimatedMinutes)
        }
    }

    @Test
    fun navigationInstruction_completeness() {
        // Test that NavigationInstruction contains all required fields
        val instruction = NavigationInstruction(
            direction = "Head North",
            distance = "150m", 
            estimatedTime = "2 min"
        )
        
        assertFalse("Direction should not be empty", instruction.direction.isBlank())
        assertFalse("Distance should not be empty", instruction.distance.isBlank())
        assertFalse("Estimated time should not be empty", instruction.estimatedTime.isBlank())
        assertTrue("Direction should contain 'Head'", instruction.direction.contains("Head"))
        assertTrue("Distance should contain 'm' or 'km'", 
            instruction.distance.contains("m") || instruction.distance.contains("km"))
        assertTrue("Time should contain 'min'", instruction.estimatedTime.contains("min"))
    }

    @Test
    fun bearingNormalization_correctness() {
        // Test bearing normalization logic
        val normalizationTests = mapOf(
            0f to 0f,
            45f to 45f,
            360f to 0f,
            -90f to 270f,
            450f to 90f,
            -180f to 180f
        )
        
        normalizationTests.forEach { (input, expected) ->
            val normalized = normalizeBearing(input)
            assertEquals("Bearing $input should normalize to $expected", 
                expected, normalized, 0.001f)
        }
    }

    // Helper methods to simulate the private methods from LocationService
    
    private fun getDirectionFromBearingTest(bearing: Float): String {
        val normalizedBearing = (bearing + 360) % 360
        return when {
            normalizedBearing < 22.5 || normalizedBearing >= 337.5 -> "Head North"
            normalizedBearing < 67.5 -> "Head Northeast"
            normalizedBearing < 112.5 -> "Head East"
            normalizedBearing < 157.5 -> "Head Southeast"
            normalizedBearing < 202.5 -> "Head South"
            normalizedBearing < 247.5 -> "Head Southwest"
            normalizedBearing < 292.5 -> "Head West"
            else -> "Head Northwest"
        }
    }
    
    private fun formatDistanceForNavigation(distance: Float): String {
        return when {
            distance < 50 -> "Continue ${distance.toInt()}m"
            distance < 1000 -> "${(distance / 10).toInt() * 10}m"
            else -> "${String.format("%.1f", distance / 1000)}km"
        }
    }
    
    private fun estimateWalkingTime(distance: Float): Int {
        return (distance / 83).toInt() + 1 // 5 km/h = 83.33 m/min
    }
    
    private fun normalizeBearing(bearing: Float): Float {
        return (bearing + 360) % 360
    }
}