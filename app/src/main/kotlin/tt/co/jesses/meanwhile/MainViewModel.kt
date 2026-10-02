package tt.co.jesses.meanwhile

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tt.co.jesses.meanwhile.core.Article
import tt.co.jesses.meanwhile.core.CachingNewsSource
import tt.co.jesses.meanwhile.core.FallbackNewsSource
import tt.co.jesses.meanwhile.core.GdeltNewsSource
import tt.co.jesses.meanwhile.core.GoogleNewsSource
import tt.co.jesses.meanwhile.core.LatLon
import tt.co.jesses.meanwhile.core.MarineConditions
import tt.co.jesses.meanwhile.core.MarineSource
import tt.co.jesses.meanwhile.core.NewsPlace
import tt.co.jesses.meanwhile.core.NewsSource
import tt.co.jesses.meanwhile.core.OpenMeteoMarineSource
import tt.co.jesses.meanwhile.core.RssNewsSource
import tt.co.jesses.meanwhile.core.Trace
import tt.co.jesses.meanwhile.core.antipode
import tt.co.jesses.meanwhile.core.capPerDomain
import tt.co.jesses.meanwhile.core.cleaned
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

enum class Status { Idle, Loading, Ready, Error }

/** What to show when the antipode is open water: headlines from the nearest land, or the sea itself. */
enum class ViewMode { Land, Ocean }

data class UiState(
    val status: Status = Status.Idle,
    val mode: ViewMode = ViewMode.Land,
    /** Where the user is, as a label ("Your location" or a searched place). */
    val originLabel: String? = null,
    val antipode: LatLon? = null,
    val country: ResolvedCountry? = null,
    val articles: List<Article> = emptyList(),
    /** Sea conditions at the antipode, set in [ViewMode.Ocean]; null if the source had none. */
    val marine: MarineConditions? = null,
    /** GDELT window the articles came from: "24h", or "7d" when the last day was thin. */
    val window: String = "24h",
    val searchResults: List<NamedPlace> = emptyList(),
    val message: String? = null,
    /** What the load is doing right now; only meaningful while [status] is [Status.Loading]. */
    val progress: String? = null,
)

private const val TAG = "Meanwhile"

class MainViewModel(app: Application) : AndroidViewModel(app) {
    init {
        Trace.sink = { Log.d(TAG, it) }
    }

    private val geocoding = Geocoding(app)
    private val resolver = AntipodeResolver(geocoding)
    private val location = LocationSource(app)
    private val http = HttpClient(OkHttp) {
        // GDELT can be slow; give it room instead of cutting it off mid-response.
        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
            requestTimeoutMillis = 90_000
        }
    }
    // Every source gets a turn at the nearest place before the app gives up and moves farther away:
    // GDELT (published there), curated outlet feeds, then Google News search (about there).
    private val news: NewsSource = CachingNewsSource(
        delegate = FallbackNewsSource(listOf(GdeltNewsSource(http), RssNewsSource(http), GoogleNewsSource(http))),
        store = FileNewsCacheStore(File(app.cacheDir, "news")),
    )
    private val marine: MarineSource = OpenMeteoMarineSource(http)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(UiState(mode = loadMode()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var origin: LatLon? = null
    private var originLabel: String? = null

    /** Caller must already hold the coarse location permission. */
    fun useDeviceLocation() {
        loadJob?.cancel()
        _state.update { it.copy(status = Status.Loading, message = null, progress = "Getting your location…") }
        loadJob = viewModelScope.launch {
            val here = location.current()
            if (here == null) {
                _state.update { it.copy(status = Status.Error, message = "Couldn't get your location. Search for a place instead.") }
            } else {
                loadFor(here, "Your location")
            }
        }
    }

    fun usePlace(place: NamedPlace) {
        _state.update { it.copy(searchResults = emptyList()) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadFor(place.point, place.label) }
    }

    fun refresh() {
        val here = origin ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadFor(here, originLabel) }
    }

    /** Remembered across launches; only matters when the antipode is open water. */
    fun setMode(mode: ViewMode) {
        if (mode == _state.value.mode) return
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        _state.update { it.copy(mode = mode) }
        refresh()
    }

    private fun loadMode(): ViewMode =
        runCatching { ViewMode.valueOf(prefs.getString(KEY_MODE, null) ?: ViewMode.Land.name) }.getOrDefault(ViewMode.Land)

    fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            val results = geocoding.search(query.trim())
            _state.update {
                it.copy(
                    searchResults = results,
                    message = if (results.isEmpty()) "No places found for \"${query.trim()}\"." else null,
                )
            }
        }
    }

    private suspend fun loadFor(here: LatLon, label: String?) {
        val loadStart = System.nanoTime()
        origin = here
        originLabel = label
        val target = here.antipode()
        Log.d(TAG, "load: origin (${"%.3f".format(here.lat)}, ${"%.3f".format(here.lon)}) \"$label\" -> antipode (${"%.3f".format(target.lat)}, ${"%.3f".format(target.lon)})")
        _state.update {
            it.copy(
                status = Status.Loading,
                originLabel = label,
                antipode = target,
                country = null,
                articles = emptyList(),
                marine = null,
                message = null,
                progress = "Looking for land on the other side of the world…",
            )
        }
        try {
            val empty = mutableSetOf<String>()
            repeat(MAX_COUNTRY_ATTEMPTS) {
                val country = resolver.resolve(target, empty)
                if (country == null) {
                    _state.update { it.copy(status = Status.Error, message = "Couldn't find any land near your antipode.") }
                    return
                }
                Log.d(TAG, "load: ${country.name} (iso=${country.iso}, fips=${country.fips}, ${country.distanceKm.toInt()} km) after ${(System.nanoTime() - loadStart) / 1_000_000} ms")
                if (_state.value.mode == ViewMode.Ocean && country.distanceKm > 0) {
                    // Open water, and the user wants the sea: no headlines needed, so no GDELT call.
                    _state.update { it.copy(country = country, progress = "Reading the sea…") }
                    val conditions = marine.conditions(target)
                    Log.d(TAG, "load: sea conditions ${if (conditions == null) "unavailable" else "ok"}, ${(System.nanoTime() - loadStart) / 1_000_000} ms in")
                    _state.update { it.copy(status = Status.Ready, marine = conditions) }
                    return
                }
                _state.update {
                    it.copy(country = country, progress = "Fetching headlines from ${country.name}… the first load can take up to 30 seconds.")
                }
                val result = news.headlines(NewsPlace(country.iso, country.fips, country.name))
                // Filtered here, not at fetch time, so cached results and newly added entries are covered too.
                val articles = result.articles.cleaned(country.fips)
                Log.d(TAG, "load: ${country.name} gave ${result.articles.size} articles (${articles.size} after cleaning, ${result.window}), ${(System.nanoTime() - loadStart) / 1_000_000} ms in")
                if (articles.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            status = Status.Ready,
                            articles = articles.capPerDomain(MAX_PER_DOMAIN).take(MAX_ARTICLES),
                            window = result.window,
                        )
                    }
                    return
                }
                empty += country.iso
                _state.update { it.copy(progress = "${country.name} had no headlines, trying the next closest country…") }
            }
            _state.update { it.copy(status = Status.Error, message = "No headlines found near your antipode. Try another place.") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "load failed after ${(System.nanoTime() - loadStart) / 1_000_000} ms", e)
            _state.update { it.copy(status = Status.Error, message = e.message ?: "Something went wrong loading headlines.") }
        }
    }

    private companion object {
        const val MAX_PER_DOMAIN = 8
        const val MAX_ARTICLES = 100
        const val MAX_COUNTRY_ATTEMPTS = 3
        const val KEY_MODE = "mode"
    }
}
