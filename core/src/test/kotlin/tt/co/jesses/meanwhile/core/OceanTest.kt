package tt.co.jesses.meanwhile.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OceanTest {
    @Test
    fun namesTheBasin() {
        assertEquals("Indian Ocean", oceanNameAt(LatLon(-49.26, 56.95)))
        assertEquals("Pacific Ocean", oceanNameAt(LatLon(0.0, -150.0)))
        assertEquals("Pacific Ocean", oceanNameAt(LatLon(-30.0, 170.0)))
        assertEquals("Atlantic Ocean", oceanNameAt(LatLon(30.0, -40.0)))
        assertEquals("Southern Ocean", oceanNameAt(LatLon(-70.0, 0.0)))
        assertEquals("Arctic Ocean", oceanNameAt(LatLon(80.0, 0.0)))
    }

    @Test
    fun sunIsOverheadAtTheEquatorAroundTheMarchEquinoxNoon() {
        val equinoxNoon = Instant.parse("2026-03-20T12:00:00Z")
        val overhead = sunAltitudeDeg(LatLon(0.0, 0.0), equinoxNoon)
        assertTrue(overhead > 85.0, "expected the sun nearly overhead, got $overhead")

        val antipode = sunAltitudeDeg(LatLon(0.0, 180.0), equinoxNoon)
        assertTrue(antipode < -85.0, "expected the sun far below the horizon, got $antipode")
    }

    @Test
    fun sunIsNearTheHorizonAtDawnAndDusk() {
        val equinoxNoon = Instant.parse("2026-03-20T12:00:00Z")
        // 90 degrees east is six hours ahead, so it is about sunset there.
        val altitude = sunAltitudeDeg(LatLon(0.0, 90.0), equinoxNoon)
        assertTrue(altitude in -8.0..3.0, "expected near the horizon, got $altitude")
    }

    @Test
    fun classifiesDayPhase() {
        assertEquals(DayPhase.Day, dayPhaseOf(10.0))
        assertEquals(DayPhase.Twilight, dayPhaseOf(-3.0))
        assertEquals(DayPhase.Night, dayPhaseOf(-20.0))
    }

    @Test
    fun compassPointsAndSeaState() {
        assertEquals("N", compassPoint(0.0))
        assertEquals("N", compassPoint(359.0))
        assertEquals("SW", compassPoint(225.0))
        assertEquals("SW", compassPoint(234.0))
        assertEquals("Very rough", seaState(5.36 + 1.0))
        assertEquals("Rough", seaState(5.36))
        assertEquals("Calm", seaState(0.0))
    }

    @Test
    fun parsesMarineResponse() {
        val body = """{"latitude":-49.29,"longitude":56.95,"current":{"time":"2026-10-01T09:45","interval":900,
            "wave_height":5.36,"wave_period":10.1,"wave_direction":234,"swell_wave_height":3.84,
            "sea_surface_temperature":2.7,"ocean_current_velocity":1.7}}"""
        val marine = parseMarine(body)!!
        assertEquals(5.36, marine.waveHeightM)
        assertEquals(234.0, marine.waveDirectionDeg)
        assertEquals(2.7, marine.seaTempC)
        assertTrue(marine.hasAnyData)
    }

    @Test
    fun marineWithOnlyNullsOrGarbageHasNoData() {
        assertNull(parseMarine("not json"))
        assertNull(parseMarine("{}"))
        assertTrue(parseMarine("""{"current":{"wave_height":null}}""")?.hasAnyData != true)
    }
}
