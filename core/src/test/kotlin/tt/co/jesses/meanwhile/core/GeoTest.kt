package tt.co.jesses.meanwhile.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeoTest {
    @Test
    fun antipodeOfMadridIsInTheSouthPacificNearNewZealand() {
        val a = LatLon(40.4168, -3.7038).antipode()
        assertEquals(-40.4168, a.lat, 1e-9)
        assertEquals(176.2962, a.lon, 1e-9)
    }

    @Test
    fun antipodeIsHalfTheEarthAway() {
        val points = listOf(
            LatLon(0.0, 0.0),
            LatLon(49.28, -123.12),
            LatLon(-33.9, 151.2),
            LatLon(90.0, 0.0),
            LatLon(10.0, 180.0),
        )
        for (p in points) {
            assertEquals(20015.1, haversineKm(p, p.antipode()), 5.0, "for $p")
        }
    }

    @Test
    fun antipodeOfAntipodeIsOriginal() {
        val p = LatLon(49.28, -123.12)
        val back = p.antipode().antipode()
        assertEquals(p.lat, back.lat, 1e-9)
        assertEquals(p.lon, back.lon, 1e-9)
    }

    @Test
    fun destinationIsTheRequestedDistanceAway() {
        val p = LatLon(10.0, 20.0)
        for (bearing in listOf(0.0, 45.0, 90.0, 225.0)) {
            assertEquals(400.0, haversineKm(p, p.destination(bearing, 400.0)), 0.5)
        }
    }

    @Test
    fun searchRingsGrowOutward() {
        val rings = expandingSearchRings(LatLon(-40.0, -176.0))
        assertEquals(DEFAULT_SEARCH_RADII_KM.size, rings.size)
        assertTrue(rings.all { it.points.size == 16 })
        assertTrue(rings.zipWithNext().all { (a, b) -> a.radiusKm < b.radiusKm })
    }

    @Test
    fun approxOffsetFollowsLongitude() {
        assertEquals(0, approxUtcOffsetHours(0.0))
        assertEquals(12, approxUtcOffsetHours(175.0))
        assertEquals(-8, approxUtcOffsetHours(-123.0))
    }
}
