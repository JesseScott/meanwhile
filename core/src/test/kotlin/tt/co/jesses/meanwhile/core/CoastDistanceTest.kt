package tt.co.jesses.meanwhile.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoastDistanceTest {
    private val centre = LatLon(-49.0, 57.0)

    @Test
    fun probesSplitTheGapEvenlyAndLeaveBothEndsOut() {
        val probes = probesAlong(centre, 90.0, innerKm = 1600.0, outerKm = 3200.0, steps = 7)
        assertEquals(listOf(1800.0, 2000.0, 2200.0, 2400.0, 2600.0, 2800.0, 3000.0), probes.map { it.distanceKm })
    }

    @Test
    fun eachProbeReallyIsThatFarFromTheCentreOnTheBearing() {
        val probes = probesAlong(centre, 135.0, 200.0, 400.0)
        probes.forEach { assertTrue(kotlin.math.abs(haversineKm(centre, it.point) - it.distanceKm) < 1.0) }
    }

    @Test
    fun theCoastIsHalfwayBetweenTheLastWaterProbeAndTheFirstLandProbe() {
        val km = listOf(1800.0, 2000.0, 2200.0, 2400.0)
        assertEquals(2100.0, estimateCoastKm(1600.0, 3200.0, km, listOf(false, false, true, true)))
        assertEquals(1700.0, estimateCoastKm(1600.0, 3200.0, km, listOf(true, true, true, true)))
    }

    @Test
    fun withNoLandBetweenTheCoastIsNearTheOuterRing() {
        val km = listOf(1800.0, 2000.0)
        assertEquals(2600.0, estimateCoastKm(1600.0, 3200.0, km, listOf(false, false)))
    }

    @Test
    fun roundingKeepsToDistancesThatAreHonest() {
        assertEquals(0, roughKm(0.0))
        assertEquals(50, roughKm(10.0))
        assertEquals(350, roughKm(337.0))
        assertEquals(2100, roughKm(2134.0))
        assertEquals(3200, roughKm(3181.0))
    }
}
