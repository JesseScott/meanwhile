package tt.co.jesses.meanwhile.core

import kotlin.test.Test
import kotlin.test.assertEquals

class RecentPlacesTest {
    private fun place(label: String, lat: Double = 0.0, lon: Double = 0.0) = RecentPlace(label, LatLon(lat, lon))

    @Test
    fun putsTheNewestFirstAndKeepsAtMostThree() {
        val list = listOf(place("a"), place("b"), place("c")).withNewest(place("d"))
        assertEquals(listOf("d", "a", "b"), list.map { it.label })
    }

    @Test
    fun movesARepeatedPlaceToTheFrontInsteadOfDuplicatingIt() {
        val list = listOf(place("a"), place("b"), place("c")).withNewest(place("c"))
        assertEquals(listOf("c", "a", "b"), list.map { it.label })
    }

    @Test
    fun roundTripsThroughTheStoredText() {
        val places = listOf(place("Tamanrasset, Algeria", 22.79, 5.52), place("Reykjavík, Iceland", 64.15, -21.94))
        assertEquals(places, decodeRecentPlaces(places.encode()))
    }

    @Test
    fun aLabelWithTabsOrLineBreaksCannotBreakTheFormat() {
        val decoded = decodeRecentPlaces(listOf(place("Odd\tName\nHere", 1.0, 2.0)).encode())
        assertEquals(listOf(place("Odd Name Here", 1.0, 2.0)), decoded)
    }

    @Test
    fun skipsDamagedLinesAndOutOfRangeCoordinatesWithoutCrashing() {
        val raw = "garbage\n91.0\t0.0\tToo far north\n10.0\t200.0\tToo far east\n\t\t\nx\ty\tNot numbers\n5.0\t6.0\tFine\n5.0\t6.0\t"
        assertEquals(listOf(place("Fine", 5.0, 6.0)), decodeRecentPlaces(raw))
        assertEquals(emptyList(), decodeRecentPlaces(null))
        assertEquals(emptyList(), decodeRecentPlaces(""))
    }

    @Test
    fun readsNoMoreThanTheLimitEvenFromALongerStoredList() {
        val raw = (1..6).map { place("p$it") }.encode()
        assertEquals(listOf("p1", "p2", "p3"), decodeRecentPlaces(raw).map { it.label })
    }
}
