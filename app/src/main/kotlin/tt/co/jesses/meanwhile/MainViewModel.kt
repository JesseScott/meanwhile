package tt.co.jesses.meanwhile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tt.co.jesses.meanwhile.core.Article
import tt.co.jesses.meanwhile.core.CachingNewsSource
import tt.co.jesses.meanwhile.core.GdeltNewsSource
import tt.co.jesses.meanwhile.core.LatLon
import tt.co.jesses.meanwhile.core.NewsSource
import tt.co.jesses.meanwhile.core.antipode
import tt.co.jesses.meanwhile.core.capPerDomain
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

enum class Status { Idle, Loading, Ready, Error }

data class UiState(
    val status: Status = Status.Idle,
    /** Where the user is, as a label ("Your location" or a searched place). */
    val originLabel: String? = null,
    val antipode: LatLon? = null,
    val country: ResolvedCountry? = null,
    val articles: List<Article> = emptyList(),
    /** GDELT window the articles came from: "24h", or "7d" when the last day was thin. */
    val window: String = "24h",
    val searchResults: List<NamedPlace> = emptyList(),
    val message: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val geocoding = Geocoding(app)
    private val resolver = AntipodeResolver(geocoding)
    private val location = LocationSource(app)
    private val news: NewsSource = CachingNewsSource(
        delegate = GdeltNewsSource(HttpClient(OkHttp)),
        store = FileNewsCacheStore(File(app.cacheDir, "news")),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var origin: LatLon? = null
    private var originLabel: String? = null

    /** Caller must already hold the coarse location permission. */
    fun useDeviceLocation() {
        loadJob?.cancel()
        _state.update { it.copy(status = Status.Loading, message = null) }
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
        origin = here
        originLabel = label
        val target = here.antipode()
        _state.update {
            it.copy(status = Status.Loading, originLabel = label, antipode = target, country = null, articles = emptyList(), message = null)
        }
        try {
            val country = resolver.resolve(target)
            if (country == null) {
                _state.update { it.copy(status = Status.Error, message = "Couldn't find any land near your antipode.") }
                return
            }
            _state.update { it.copy(country = country) }
            val result = news.headlines(country.fips)
            _state.update {
                it.copy(
                    status = Status.Ready,
                    articles = result.articles.capPerDomain(MAX_PER_DOMAIN).take(MAX_ARTICLES),
                    window = result.window,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(status = Status.Error, message = e.message ?: "Something went wrong loading headlines.") }
        }
    }

    private companion object {
        const val MAX_PER_DOMAIN = 8
        const val MAX_ARTICLES = 100
    }
}
