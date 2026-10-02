package tt.co.jesses.meanwhile.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TelemetryTest {
    private val everyEvent: List<TelemetryEvent> = listOf(
        TelemetryEvent.PermissionResult(true),
        TelemetryEvent.PlaceChosen(PlaceSource.Search),
        TelemetryEvent.LoadFinished(water = true, showingOcean = false, kind = LoadKindName.NewPlace, articles = 44, durationMs = 45_051, sources = setOf("rss", "gnews", "gdelt")),
        TelemetryEvent.LoadFailed("Busy", LoadKindName.Refresh),
        TelemetryEvent.ModeSwitched(toOcean = true, via = ModeSwitchVia.Snackbar),
        TelemetryEvent.ArticleOpened("gnews"),
        TelemetryEvent.AboutOpened,
        TelemetryEvent.KofiOpened,
        TelemetryEvent.LicensesOpened,
    )

    @Test
    fun noEventParameterCanHoldFreeTextOrAPlace() {
        // Every parameter must be a boolean, an int, or one of a short list of known words.
        val knownWords = setOf(
            "device", "search", "new_place", "switch_mode", "refresh", "switch", "snackbar",
            "none", "gdelt", "rss", "gnews", "unknown",
            "LocationUnavailable", "NoLandNearby", "NoHeadlines", "Busy", "Offline", "Unknown",
            "0", "1-4", "5-19", "20+", "under_2s", "2-10s", "10-45s", "over_45s",
        )
        for (event in everyEvent) {
            for ((key, value) in event.params) {
                val ok = value is Boolean || value is Int ||
                    (value is String && (value in knownWords || value.split(",").all { it in KNOWN_SOURCES }))
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
        val event = TelemetryEvent.LoadFinished(true, true, LoadKindName.Refresh, 3, 500, setOf("gdelt", "https://evil.example/?lat=49.2", "rss"))
        assertEquals("gdelt,rss", event.params["sources"])
        assertEquals("none", TelemetryEvent.LoadFinished(true, true, LoadKindName.Refresh, 0, 500, emptySet()).params["sources"])
    }

    @Test
    fun anUnknownSourceOnAnArticleBecomesUnknown() {
        assertEquals("unknown", TelemetryEvent.ArticleOpened("storm.mg/some-article").params["via"])
        assertEquals("rss", TelemetryEvent.ArticleOpened("rss").params["via"])
    }

    @Test
    fun aFailureReportsOnlyAKnownErrorName() {
        assertEquals("Busy", TelemetryEvent.LoadFailed("Busy", LoadKindName.Refresh).params["error"])
        // Anything else, such as a message that might carry a URL or a coordinate, becomes "unknown".
        val sneaky = TelemetryEvent.LoadFailed("Timeout at https://marine-api.open-meteo.com/?latitude=49.2&longitude=-123.1", LoadKindName.NewPlace)
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
