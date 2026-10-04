package tt.co.jesses.meanwhile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
import tt.co.jesses.meanwhile.core.LatLon
import tt.co.jesses.meanwhile.core.LoadKindName
import tt.co.jesses.meanwhile.core.MarineSource
import tt.co.jesses.meanwhile.core.ModeSwitchVia
import tt.co.jesses.meanwhile.core.NewsFailure
import tt.co.jesses.meanwhile.core.NewsPlace
import tt.co.jesses.meanwhile.core.NewsSource
import tt.co.jesses.meanwhile.core.NewsUnavailableException
import tt.co.jesses.meanwhile.core.OpenMeteoMarineSource
import tt.co.jesses.meanwhile.core.PlaceSource
import tt.co.jesses.meanwhile.core.RssNewsSource
import tt.co.jesses.meanwhile.core.Telemetry
import tt.co.jesses.meanwhile.core.TelemetryEvent
import tt.co.jesses.meanwhile.core.Trace
import tt.co.jesses.meanwhile.core.antipode
import tt.co.jesses.meanwhile.core.roughKm
import tt.co.jesses.meanwhile.core.stream
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

private fun LoadKind.telemetryName() = when (this) {
    LoadKind.NewPlace -> LoadKindName.NewPlace
    LoadKind.SwitchMode -> LoadKindName.SwitchMode
    LoadKind.Refresh -> LoadKindName.Refresh
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    init {
        Trace.sink = { AppLog.d(TAG, it) }
    }

    /** Usage and crash reporting, which can only be sent facts from a fixed list (see core's TelemetryEvent). */
    private val telemetry: Telemetry = (app as MeanwhileApplication).telemetry
    private val settings = (app as MeanwhileApplication).settings
    private var askScheduled = false

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
    // GDELT (published there), then curated outlet feeds.
    private val news: NewsSource = CachingNewsSource(
        delegate = FallbackNewsSource(listOf(GdeltNewsSource(http), RssNewsSource(http))),
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
            is UiEvent.SetMode -> setMode(event.mode, event.via)
            UiEvent.Refresh -> refresh()
            UiEvent.DismissPlaces -> _state.update { it.copy(searchResults = emptyList()) }
        }
    }

    private fun useDeviceLocation() {
        loadJob?.cancel()
        // Asking for the phone's location means leaving the place on screen, so it goes straight away. Otherwise a
        // failed lookup would leave the last place in the header under an error about something else.
        _state.update {
            it.copy(
                status = Status.Loading,
                error = null,
                notice = null,
                progress = Progress.Locating,
                originLabel = null,
                antipode = null,
                country = null,
                articles = emptyList(),
                marine = null,
                searchResults = emptyList(),
                isRefreshing = false,
                loadingMore = false,
            )
        }
        loadJob = viewModelScope.launch {
            if (!location.isEnabled()) {
                _state.update { it.copy(status = Status.Error, error = UiError.LocationOff) }
                telemetry.log(TelemetryEvent.LoadFailed(UiError.LocationOff.name, LoadKindName.NewPlace))
                return@launch
            }
            val here = location.current()
            if (here == null) {
                _state.update { it.copy(status = Status.Error, error = UiError.LocationUnavailable) }
            } else {
                telemetry.log(TelemetryEvent.PlaceChosen(PlaceSource.Device))
                loadFor(here, "Your location", LoadKind.NewPlace)
            }
        }
    }

    private fun usePlace(place: NamedPlace) {
        _state.update { it.copy(searchResults = emptyList()) }
        telemetry.log(TelemetryEvent.PlaceChosen(PlaceSource.Search))
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadFor(place.point, place.label, LoadKind.NewPlace) }
    }

    private fun refresh() {
        val here = origin ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadFor(here, originLabel, LoadKind.Refresh) }
    }

    private fun setMode(mode: ViewMode, via: ModeSwitchVia) {
        if (mode == _state.value.mode) return
        telemetry.log(TelemetryEvent.ModeSwitched(toOcean = mode == ViewMode.Ocean, via = via))
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
        AppLog.d(TAG, "load($kind): origin (${"%.3f".format(here.lat)}, ${"%.3f".format(here.lon)}) \"$label\" -> antipode (${"%.3f".format(target.lat)}, ${"%.3f".format(target.lon)})")

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
                // Nothing is cleared: the old content stays until the new content replaces it. But from an error
                // screen there is no content to keep, so show a load in progress (and no stale error or Try again)
                // instead of a pull-to-refresh spinner over a message that no longer applies.
                LoadKind.Refresh ->
                    if (it.status == Status.Error) {
                        it.copy(status = Status.Loading, error = null, notice = null, progress = Progress.FindingLand, isRefreshing = false)
                    } else {
                        it.copy(error = null, notice = null, isRefreshing = true)
                    }
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
                AppLog.d(TAG, "load: ${country.name} (iso=${country.iso}, fips=${country.fips}, ${country.distanceKm.toInt()} km) after ${(System.nanoTime() - loadStart) / 1_000_000} ms")

                // Open water opens on the sea; the user can ask for the nearest land (see OfferNearestLand).
                if (kind == LoadKind.NewPlace && attempt == 0) {
                    _state.update { it.copy(mode = if (water) ViewMode.Ocean else ViewMode.Land) }
                }

                if (_state.value.mode == ViewMode.Ocean && water) {
                    _state.update { it.copy(country = country, progress = Progress.ReadingSea) }
                    val conditions = marine.conditions(target)
                    AppLog.d(TAG, "load: sea conditions ${if (conditions == null) "unavailable" else "ok"}, ${(System.nanoTime() - loadStart) / 1_000_000} ms in")
                    _state.update { it.copy(status = Status.Ready, marine = conditions, isRefreshing = false, error = null) }
                    telemetry.log(
                        TelemetryEvent.LoadFinished(
                            water = true, showingOcean = true, kind = kind.telemetryName(), articles = 0,
                            durationMs = (System.nanoTime() - loadStart) / 1_000_000, sources = emptySet(),
                        ),
                    )
                    noteGoodLoad()
                    if (kind == LoadKind.NewPlace) _effects.send(UiEffect.OfferNearestLand(country.name, roughKm(country.distanceKm)))
                    return
                }

                _state.update { it.copy(country = country, progress = Progress.FetchingHeadlines(country.name)) }

                // Results show as each source answers; cleaning happens per snapshot, so cached results and newly
                // added rules are covered too.
                var shown = false
                news.stream(NewsPlace(country.iso, country.fips, country.name)).collect { progress ->
                    AppLog.d(TAG, "load: ${country.name} snapshot ${progress.result.articles.size} articles, ${progress.pending} sources pending, ${(System.nanoTime() - loadStart) / 1_000_000} ms in")
                    // The reducer hands back the same state when a snapshot has nothing worth showing.
                    val current = _state.value
                    val next = current.withNewsProgress(progress, country.fips, MAX_PER_DOMAIN, MAX_ARTICLES)
                    if (next !== current) {
                        shown = true
                        _state.value = next
                    }
                }
                _state.update { it.withNewsFinished() }
                if (shown) {
                    val articles = _state.value.articles
                    AppLog.d(TAG, "load: ${country.name} finished with ${articles.size} articles, ${(System.nanoTime() - loadStart) / 1_000_000} ms in")
                    telemetry.log(
                        TelemetryEvent.LoadFinished(
                            water = water, showingOcean = false, kind = kind.telemetryName(), articles = articles.size,
                            durationMs = (System.nanoTime() - loadStart) / 1_000_000, sources = articles.map { it.via }.toSet(),
                        ),
                    )
                    noteGoodLoad()
                    return
                }
                empty += country.iso
                _state.update { it.copy(progress = Progress.TryingNext(country.name)) }
            }
            fail(kind, UiError.NoHeadlines)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NewsUnavailableException) {
            AppLog.e(TAG, "load failed after ${(System.nanoTime() - loadStart) / 1_000_000} ms: ${e.reason}", e)
            fail(
                kind,
                when (e.reason) {
                    NewsFailure.RateLimited -> UiError.Busy
                    NewsFailure.Network -> UiError.Offline
                    NewsFailure.Unknown -> UiError.Unknown
                },
            )
        } catch (e: Exception) {
            AppLog.e(TAG, "load failed after ${(System.nanoTime() - loadStart) / 1_000_000} ms", e)
            // An unexpected failure: worth a crash report, which carries only the type and stack frames, not the message.
            telemetry.recordError(e, "load")
            fail(kind, if (e is IOException) UiError.Offline else UiError.Unknown)
        }
    }

    /**
     * A load gave the user something. Count it, and once the app has worked for them a few times, ask once about
     * sharing usage and crash reports, after a pause so it never competes with the content they came for.
     */
    private fun noteGoodLoad() {
        viewModelScope.launch {
            settings.recordGoodLoad()
            // Debug builds ask even without a Firebase project, so the prompt can be tried.
            if (!askScheduled && settings.shouldAskAboutAnalytics(canCollect = telemetry.available || AppLog.enabled)) {
                askScheduled = true
                delay(ASK_DELAY_MS)
                settings.recordPromptShown()
                _effects.send(UiEffect.AskToShareUsage)
                askScheduled = false
            }
        }
    }

    /** A failed refresh over content that is still there keeps the content; anything else becomes an error screen. */
    private fun fail(kind: LoadKind, error: UiError) {
        telemetry.log(TelemetryEvent.LoadFailed(error.name, kind.telemetryName()))
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
        const val ASK_DELAY_MS = 5_000L
    }
}
