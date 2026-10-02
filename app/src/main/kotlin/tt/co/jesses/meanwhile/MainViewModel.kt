package tt.co.jesses.meanwhile

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tt.co.jesses.meanwhile.core.CachingNewsSource
import tt.co.jesses.meanwhile.core.FallbackNewsSource
import tt.co.jesses.meanwhile.core.GdeltNewsSource
import tt.co.jesses.meanwhile.core.GoogleNewsSource
import tt.co.jesses.meanwhile.core.LatLon
import tt.co.jesses.meanwhile.core.MarineSource
import tt.co.jesses.meanwhile.core.NewsFailure
import tt.co.jesses.meanwhile.core.NewsPlace
import tt.co.jesses.meanwhile.core.NewsSource
import tt.co.jesses.meanwhile.core.NewsUnavailableException
import tt.co.jesses.meanwhile.core.OpenMeteoMarineSource
import tt.co.jesses.meanwhile.core.RssNewsSource
import tt.co.jesses.meanwhile.core.Trace
import tt.co.jesses.meanwhile.core.antipode
import tt.co.jesses.meanwhile.core.capPerDomain
import tt.co.jesses.meanwhile.core.cleaned
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

private const val TAG = "Meanwhile"

/** How a load relates to what is already on screen, which decides what may be cleared. */
private enum class LoadKind {
    /** A different place: clear everything and start over. */
    NewPlace,

    /** Same place, other view: keep the header, replace the results. */
    SwitchMode,

    /** Same place, again: keep everything on screen and swap it out when the new data lands. */
    Refresh,
}

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

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects: Flow<UiEffect> = _effects.receiveAsFlow()

    private var loadJob: Job? = null
    private var origin: LatLon? = null
    private var originLabel: String? = null

    fun onEvent(event: UiEvent) {
        when (event) {
            UiEvent.UseMyLocation -> useDeviceLocation()
            UiEvent.LocationDenied -> _state.update { it.copy(notice = UiNotice.LocationDenied) }
            is UiEvent.Search -> search(event.query)
            is UiEvent.PickPlace -> usePlace(event.place)
            is UiEvent.SetMode -> setMode(event.mode)
            UiEvent.Refresh -> refresh()
        }
    }

    private fun useDeviceLocation() {
        loadJob?.cancel()
        _state.update { it.copy(status = Status.Loading, error = null, notice = null, progress = Progress.Locating) }
        loadJob = viewModelScope.launch {
            val here = location.current()
            if (here == null) {
                _state.update { it.copy(status = Status.Error, error = UiError.LocationUnavailable) }
            } else {
                loadFor(here, "Your location", LoadKind.NewPlace)
            }
        }
    }

    private fun usePlace(place: NamedPlace) {
        _state.update { it.copy(searchResults = emptyList()) }
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadFor(place.point, place.label, LoadKind.NewPlace) }
    }

    private fun refresh() {
        val here = origin ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadFor(here, originLabel, LoadKind.Refresh) }
    }

    private fun setMode(mode: ViewMode) {
        if (mode == _state.value.mode) return
        val here = origin
        if (here == null) {
            _state.update { it.copy(mode = mode) }
            return
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadFor(here, originLabel, LoadKind.SwitchMode, requestedMode = mode) }
    }

    private fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            val results = geocoding.search(query.trim())
            _state.update {
                it.copy(
                    searchResults = results,
                    notice = if (results.isEmpty()) UiNotice.NoPlaces(query.trim()) else null,
                )
            }
        }
    }

    private suspend fun loadFor(here: LatLon, label: String?, kind: LoadKind, requestedMode: ViewMode? = null) {
        val loadStart = System.nanoTime()
        origin = here
        originLabel = label
        val target = here.antipode()
        Log.d(TAG, "load($kind): origin (${"%.3f".format(here.lat)}, ${"%.3f".format(here.lon)}) \"$label\" -> antipode (${"%.3f".format(target.lat)}, ${"%.3f".format(target.lon)})")

        val before = _state.value
        _state.update {
            when (kind) {
                LoadKind.NewPlace -> it.copy(
                    status = Status.Loading,
                    mode = ViewMode.Land,
                    originLabel = label,
                    antipode = target,
                    country = null,
                    articles = emptyList(),
                    marine = null,
                    error = null,
                    notice = null,
                    progress = Progress.FindingLand,
                    isRefreshing = false,
                )
                // The header stays; only the results area starts over.
                LoadKind.SwitchMode -> it.copy(
                    status = Status.Loading,
                    mode = requestedMode ?: it.mode,
                    articles = emptyList(),
                    marine = null,
                    error = null,
                    notice = null,
                    progress = Progress.FindingLand,
                    isRefreshing = false,
                )
                // Nothing is cleared: the old content stays until the new content replaces it.
                LoadKind.Refresh -> it.copy(error = null, notice = null, isRefreshing = true)
            }
        }
        // Same place again, so the country is already known; skip looking it up (and the flicker that goes with it).
        val known = if (kind == LoadKind.NewPlace) null else before.country

        try {
            val empty = mutableSetOf<String>()
            repeat(MAX_COUNTRY_ATTEMPTS) { attempt ->
                val country = (if (attempt == 0) known else null) ?: resolver.resolve(target, empty)
                if (country == null) {
                    fail(kind, UiError.NoLandNearby)
                    return
                }
                val water = country.distanceKm > 0
                Log.d(TAG, "load: ${country.name} (iso=${country.iso}, fips=${country.fips}, ${country.distanceKm.toInt()} km) after ${(System.nanoTime() - loadStart) / 1_000_000} ms")

                // Open water opens on the sea; the user can ask for the nearest land (see OfferNearestLand).
                if (kind == LoadKind.NewPlace && attempt == 0) {
                    _state.update { it.copy(mode = if (water) ViewMode.Ocean else ViewMode.Land) }
                }

                if (_state.value.mode == ViewMode.Ocean && water) {
                    _state.update { it.copy(country = country, progress = Progress.ReadingSea) }
                    val conditions = marine.conditions(target)
                    Log.d(TAG, "load: sea conditions ${if (conditions == null) "unavailable" else "ok"}, ${(System.nanoTime() - loadStart) / 1_000_000} ms in")
                    _state.update { it.copy(status = Status.Ready, marine = conditions, isRefreshing = false, error = null) }
                    if (kind == LoadKind.NewPlace) _effects.send(UiEffect.OfferNearestLand(country.name, country.distanceKm.roundToInt()))
                    return
                }

                _state.update { it.copy(country = country, progress = Progress.FetchingHeadlines(country.name)) }
                val result = news.headlines(NewsPlace(country.iso, country.fips, country.name))
                // Cleaned here, not at fetch time, so cached results and newly added rules are covered too.
                val articles = result.articles.cleaned(country.fips)
                Log.d(TAG, "load: ${country.name} gave ${result.articles.size} articles (${articles.size} after cleaning, ${result.window}), ${(System.nanoTime() - loadStart) / 1_000_000} ms in")
                if (articles.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            status = Status.Ready,
                            articles = articles.capPerDomain(MAX_PER_DOMAIN).take(MAX_ARTICLES),
                            window = result.window,
                            isRefreshing = false,
                            error = null,
                        )
                    }
                    return
                }
                empty += country.iso
                _state.update { it.copy(progress = Progress.TryingNext(country.name)) }
            }
            fail(kind, UiError.NoHeadlines)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NewsUnavailableException) {
            Log.e(TAG, "load failed after ${(System.nanoTime() - loadStart) / 1_000_000} ms: ${e.reason}", e)
            fail(
                kind,
                when (e.reason) {
                    NewsFailure.RateLimited -> UiError.Busy
                    NewsFailure.Network -> UiError.Offline
                    NewsFailure.Unknown -> UiError.Unknown
                },
            )
        } catch (e: Exception) {
            Log.e(TAG, "load failed after ${(System.nanoTime() - loadStart) / 1_000_000} ms", e)
            fail(kind, if (e is IOException) UiError.Offline else UiError.Unknown)
        }
    }

    /** A failed refresh over content that is still there keeps the content; anything else becomes an error screen. */
    private fun fail(kind: LoadKind, error: UiError) {
        _state.update {
            if (kind == LoadKind.Refresh && it.status == Status.Ready) {
                it.copy(isRefreshing = false, notice = UiNotice.RefreshFailed)
            } else {
                it.copy(status = Status.Error, error = error, isRefreshing = false)
            }
        }
    }

    private companion object {
        const val MAX_PER_DOMAIN = 8
        const val MAX_ARTICLES = 100
        const val MAX_COUNTRY_ATTEMPTS = 3
    }
}
