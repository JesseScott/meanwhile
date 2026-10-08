package tt.co.jesses.meanwhile.core

/*
 * What the app may tell its analytics and crash reporting, and how.
 *
 * The rule: an event can only carry a boolean, a number, or a value from a fixed list. There is no free-text
 * parameter, so a place name, a search query or a coordinate has no way into an event. Firebase itself lives in
 * the app module; everything that decides what is *sent* is here, where a test can check it.
 *
 * The one fact about a place that may be sent is the country whose headlines were loaded, as a code from
 * [KNOWN_COUNTRIES], on `load_finished` and `load_failed` only. It is the same for a device-located and a searched
 * load. It is never attached to an opened article, which would tie a country to reading.
 */

interface Telemetry {
    /** False when no analytics project is configured, so there is nothing to opt in to. */
    val available: Boolean

    fun log(event: TelemetryEvent)

    /**
     * Reports a handled failure. Only its type and stack frames are sent, never its message: some messages carry
     * the request URL, and Open-Meteo's URL contains the antipode's coordinates.
     */
    fun recordError(error: Throwable, where: String)

    /** Turns collection on or off. It is off until the user opts in, with the switch in About. */
    fun setEnabled(enabled: Boolean)

    /** Called when the user opts out: forget the random installation ID and drop anything not yet sent. */
    fun clearData()
}

/** Used when no analytics project is configured, and in tests. */
object NoTelemetry : Telemetry {
    override val available = false
    override fun log(event: TelemetryEvent) = Unit
    override fun recordError(error: Throwable, where: String) = Unit
    override fun setEnabled(enabled: Boolean) = Unit
    override fun clearData() = Unit
}

enum class PlaceSource(val value: String) { Device("device"), Search("search") }
enum class LoadKindName(val value: String) { NewPlace("new_place"), SwitchMode("switch_mode"), Refresh("refresh") }
enum class ModeSwitchVia(val value: String) { Switch("switch"), Snackbar("snackbar") }

/** Parameters are limited to what [TelemetryEvent.params] allows: booleans, ints, and the fixed vocabularies here. */
sealed class TelemetryEvent(val name: String, val params: Map<String, Any> = emptyMap()) {
    class PermissionResult(granted: Boolean) : TelemetryEvent("permission_result", mapOf("granted" to granted))

    class PlaceChosen(source: PlaceSource) : TelemetryEvent("place_chosen", mapOf("source" to source.value))

    /**
     * One load finished. [sources] lists which news sources contributed, for example "gdelt,rss". [country] is the
     * ISO code of the country whose headlines were loaded, or null when the load ended on the open sea.
     */
    class LoadFinished(
        water: Boolean,
        showingOcean: Boolean,
        kind: LoadKindName,
        articles: Int,
        durationMs: Long,
        sources: Set<String>,
        country: String?,
    ) : TelemetryEvent(
        "load_finished",
        mapOf(
            "water" to water,
            "ocean" to showingOcean,
            "kind" to kind.value,
            "articles" to bucketArticles(articles),
            "duration" to bucketDuration(durationMs),
            "sources" to sources.filter { it in KNOWN_SOURCES }.sorted().joinToString(",").ifEmpty { "none" },
            "country" to countryParam(country),
        ),
    )

    /**
     * [error] is the name of one of the app's fixed error types ([KNOWN_ERRORS]); anything else is reported as "unknown".
     * [country] is the ISO code of the country whose headlines were being loaded, or null when there was none yet.
     */
    class LoadFailed(error: String, kind: LoadKindName, country: String?) : TelemetryEvent(
        "load_failed",
        mapOf(
            "error" to (error.takeIf { it in KNOWN_ERRORS } ?: "unknown"),
            "kind" to kind.value,
            "country" to countryParam(country),
        ),
    )

    class ModeSwitched(toOcean: Boolean, via: ModeSwitchVia) : TelemetryEvent("mode_switched", mapOf("ocean" to toOcean, "via" to via.value))

    /** [via] is the news source that supplied the article, not the article or its outlet. */
    class ArticleOpened(via: String) : TelemetryEvent("article_opened", mapOf("via" to (via.takeIf { it in KNOWN_SOURCES } ?: "unknown")))

    data object AboutOpened : TelemetryEvent("about_opened")
    data object KofiOpened : TelemetryEvent("kofi_opened")
    data object LicensesOpened : TelemetryEvent("licenses_opened")
}

val KNOWN_SOURCES = setOf("gdelt", "rss")

/** The names of the screen's error types. Kept here as words so that nothing free-form can be sent in their place. */
val KNOWN_ERRORS = setOf("LocationUnavailable", "LocationOff", "NoLandNearby", "NoHeadlines", "Busy", "Offline", "Unknown")

/**
 * What an event says about the country: its ISO 3166 alpha-2 code if it is one of [KNOWN_COUNTRIES], "none" when
 * there is no country (the open sea), and "unknown" for anything else, so no name, query or coordinate can pass.
 */
fun countryParam(iso: String?): String = when (iso) {
    null -> "none"
    else -> iso.trim().uppercase().takeIf { it in KNOWN_COUNTRIES } ?: "unknown"
}

/** Coarse on purpose: enough to see whether places are empty, thin or full. */
fun bucketArticles(count: Int): String = when {
    count <= 0 -> "0"
    count < 5 -> "1-4"
    count < 20 -> "5-19"
    else -> "20+"
}

fun bucketDuration(ms: Long): String = when {
    ms < 2_000 -> "under_2s"
    ms < 10_000 -> "2-10s"
    ms < 45_000 -> "10-45s"
    else -> "over_45s"
}
