package tt.co.jesses.meanwhile.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelemetryTest {
    private val everyEvent: List<TelemetryEvent> = listOf(
        TelemetryEvent.PermissionResult(true),
        TelemetryEvent.PlaceChosen(PlaceSource.Search),
        TelemetryEvent.LoadFinished(water = true, showingOcean = false, kind = LoadKindName.NewPlace, articles = 44, durationMs = 45_051, sources = setOf("rss", "gnews", "gdelt"), country = "MG"),
        TelemetryEvent.LoadFinished(water = true, showingOcean = true, kind = LoadKindName.NewPlace, articles = 0, durationMs = 900, sources = emptySet(), country = null),
        TelemetryEvent.LoadFailed("Busy", LoadKindName.Refresh, country = "TO"),
        TelemetryEvent.LoadFailed("NoLandNearby", LoadKindName.NewPlace, country = null),
        TelemetryEvent.LoadFailed("Unknown", LoadKindName.NewPlace, country = "Nuku'alofa, Tonga"),
        TelemetryEvent.ArticleOpened("rss"),
        TelemetryEvent.ModeSwitched(toOcean = true, via = ModeSwitchVia.Snackbar),
        TelemetryEvent.AboutOpened,
        TelemetryEvent.KofiOpened,
        TelemetryEvent.LicensesOpened,
    )

    @Test
    fun noEventParameterCanHoldFreeTextOrAPlace() {
        // Every parameter must be a boolean, an int, one of a short list of known words, or a country code.
        val knownWords = setOf(
            "device", "search", "new_place", "switch_mode", "refresh", "switch", "snackbar",
            "none", "gdelt", "rss", "unknown",
            "LocationUnavailable", "LocationOff", "NoLandNearby", "NoHeadlines", "Busy", "Offline", "Unknown",
            "0", "1-4", "5-19", "20+", "under_2s", "2-10s", "10-45s", "over_45s",
        )
        for (event in everyEvent) {
            for ((key, value) in event.params) {
                val ok = value is Boolean || value is Int ||
                    (value is String && (value in knownWords || value.split(",").all { it in KNOWN_SOURCES })) ||
                    (key == "country" && value in KNOWN_COUNTRIES)
                assertTrue(ok, "${event.name}.$key = $value is not from a fixed vocabulary")
            }
        }
    }

    @Test
    fun eventAndParameterNamesFitFirebasesLimits() {
        for (event in everyEvent) {
            assertTrue(event.name.length <= 40 && event.name.all { it.isLetterOrDigit() || it == '_' }, event.name)
            for ((key, value) in event.params) {
                assertTrue(key.length <= 40, "$key is too long")
                if (value is String) assertTrue(value.length <= 100, "$key value is too long")
            }
        }
    }

    @Test
    fun loadFinishedKeepsOnlyKnownSourcesAndNeverTheirDetails() {
        val event = TelemetryEvent.LoadFinished(true, true, LoadKindName.Refresh, 3, 500, setOf("gdelt", "https://evil.example/?lat=49.2", "rss"), null)
        assertEquals("gdelt,rss", event.params["sources"])
        assertEquals("none", TelemetryEvent.LoadFinished(true, true, LoadKindName.Refresh, 0, 500, emptySet(), null).params["sources"])
    }

    private fun finished(country: String?) =
        TelemetryEvent.LoadFinished(water = false, showingOcean = false, kind = LoadKindName.NewPlace, articles = 12, durationMs = 3_000, sources = setOf("gdelt"), country = country)

    @Test
    fun aLoadReportsItsCountryAsAnIsoCodeFromTheFixedList() {
        assertEquals("NZ", finished("NZ").params["country"])
        assertEquals("NZ", finished(" nz ").params["country"])
        assertEquals("MG", TelemetryEvent.LoadFailed("Busy", LoadKindName.Refresh, "MG").params["country"])
    }

    @Test
    fun aSearchedLoadSendsTheResultsCountryAndNeverTheQuery() {
        // There is nowhere to put the query: a searched and a device-located load build the same event from the
        // country the headlines came from, and anything that is not a country code is refused.
        assertEquals("NZ", finished("NZ").params["country"])
        for (query in listOf("Madrid", "madrid, spain", "10 Downing Street, London", "40.4168,-3.7038", "New Zealand", "NZL", "")) {
            assertEquals("unknown", finished(query).params["country"], query)
            assertEquals("unknown", TelemetryEvent.LoadFailed("Busy", LoadKindName.NewPlace, query).params["country"], query)
        }
    }

    @Test
    fun theOpenSeaHasNoCountry() {
        assertEquals("none", finished(null).params["country"])
        assertEquals("none", TelemetryEvent.LoadFailed("NoLandNearby", LoadKindName.NewPlace, null).params["country"])
    }

    @Test
    fun onlyLoadEventsCarryACountry() {
        for (event in everyEvent) {
            val isLoad = event is TelemetryEvent.LoadFinished || event is TelemetryEvent.LoadFailed
            assertEquals(isLoad, "country" in event.params, event.name)
        }
        assertFalse("country" in TelemetryEvent.ArticleOpened("gdelt").params)
    }

    @Test
    fun theCountryListHoldsOnlyTwoLetterCodesAndNoStandIns() {
        assertTrue(KNOWN_COUNTRIES.all { code -> code.length == 2 && code.all { it in 'A'..'Z' } })
        assertEquals(250, KNOWN_COUNTRIES.size)
        assertTrue(listOf("NZ", "MG", "TO", "US", "XK").all { it in KNOWN_COUNTRIES })
    }

    @Test
    fun anUnknownSourceOnAnArticleBecomesUnknown() {
        assertEquals("unknown", TelemetryEvent.ArticleOpened("storm.mg/some-article").params["via"])
        assertEquals("rss", TelemetryEvent.ArticleOpened("rss").params["via"])
    }

    @Test
    fun aFailureReportsOnlyAKnownErrorName() {
        assertEquals("Busy", TelemetryEvent.LoadFailed("Busy", LoadKindName.Refresh, null).params["error"])
        // Anything else, such as a message that might carry a URL or a coordinate, becomes "unknown".
        val sneaky = TelemetryEvent.LoadFailed("Timeout at https://marine-api.open-meteo.com/?latitude=49.2&longitude=-123.1", LoadKindName.NewPlace, null)
        assertEquals("unknown", sneaky.params["error"])
    }

    @Test
    fun bucketsAreCoarseAndMeetAtTheirEdges() {
        assertEquals(listOf("0", "1-4", "1-4", "5-19", "5-19", "20+"), listOf(0, 1, 4, 5, 19, 20).map(::bucketArticles))
        assertEquals(
            listOf("under_2s", "2-10s", "2-10s", "10-45s", "10-45s", "over_45s"),
            listOf(1_999L, 2_000L, 9_999L, 10_000L, 44_999L, 45_000L).map(::bucketDuration),
        )
    }

    @Test
    fun theNoOpImplementationDoesNothingAndDoesNotThrow() {
        everyEvent.forEach(NoTelemetry::log)
        NoTelemetry.recordError(IllegalStateException("https://example.com/?latitude=49.2&longitude=-123.1"), "load")
        NoTelemetry.setEnabled(false)
        NoTelemetry.clearData()
    }
}
