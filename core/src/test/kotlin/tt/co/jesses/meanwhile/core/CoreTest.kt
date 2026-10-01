package tt.co.jesses.meanwhile.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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

class CountriesTest {
    @Test
    fun mapsIsoToFipsForCodesThatDiffer() {
        assertEquals("AS", fipsFor("AU"))
        assertEquals("CI", fipsFor("CL"))
        assertEquals("GM", fipsFor("DE"))
        assertEquals("UK", fipsFor("GB"))
        assertEquals("MP", fipsFor("MU"))
        assertEquals("SP", fipsFor("ES"))
    }

    @Test
    fun identicalCodesPassThrough() {
        assertEquals("NZ", fipsFor("nz"))
        assertEquals("AR", fipsFor("AR"))
        assertEquals("FJ", fipsFor("FJ"))
    }

    @Test
    fun rejectsMalformedCodes() {
        assertNull(fipsFor(""))
        assertNull(fipsFor("N"))
        assertNull(fipsFor("N1"))
    }

    @Test
    fun flagEmojiUsesRegionalIndicators() {
        assertEquals("🇳🇿", flagEmoji("NZ"))
        assertEquals("", flagEmoji("??"))
    }
}

class NewsTest {
    private val sample = """
        {"articles": [
          {"url":"https://a.example/1","url_mobile":"","title":"One","seendate":"20260930T143000Z","socialimage":"","domain":"a.example","language":"English","sourcecountry":"New Zealand"},
          {"url":"https://a.example/2","title":"Two","seendate":"20260930T120000Z","domain":"a.example","language":"English","sourcecountry":"New Zealand"},
          {"url":"https://b.example/3","title":"Tres","seendate":"20260930T110000Z","domain":"b.example","language":"Spanish","sourcecountry":"Chile"}
        ]}
    """.trimIndent()

    @Test
    fun parsesArtList() {
        val articles = parseArtList(sample)
        assertEquals(3, articles.size)
        assertEquals("One", articles[0].title)
        assertEquals("Spanish", articles[2].language)
    }

    @Test
    fun parsesSeenDate() {
        assertEquals("2026-09-30T14:30:00Z", parseArtList(sample)[0].seenInstant().toString())
    }

    @Test
    fun emptyAndNonJsonBodiesMeanNoArticles() {
        assertEquals(emptyList(), parseArtList(""))
        assertEquals(emptyList(), parseArtList("{}"))
        assertEquals(emptyList(), parseArtList("Please limit requests to one every 5 seconds"))
    }

    @Test
    fun capsArticlesPerDomain() {
        val capped = parseArtList(sample).capPerDomain(1)
        assertEquals(listOf("One", "Tres"), capped.map { it.title })
    }

    @Test
    fun cacheServesFreshEntriesAndSurvivesUpstreamFailure() = runTest {
        var now = 0L
        var calls = 0
        var fail = false
        val upstream = object : NewsSource {
            override suspend fun headlines(fips: String): NewsResult {
                calls++
                if (fail) error("boom")
                return NewsResult(parseArtList(sample), "24h")
            }
        }
        val source = CachingNewsSource(upstream, InMemoryNewsCacheStore(), ttlMs = 1000, clock = { now })

        source.headlines("NZ")
        now = 500
        source.headlines("NZ")
        assertEquals(1, calls)

        now = 5000
        fail = true
        assertEquals(3, source.headlines("NZ").articles.size)
        assertEquals(2, calls)
    }
}
