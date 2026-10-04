package tt.co.jesses.meanwhile.core

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException

val NewsJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

@Serializable
data class Article(
    val url: String,
    val title: String = "",
    @SerialName("seendate") val seenDate: String = "",
    @SerialName("socialimage") val socialImage: String? = null,
    val domain: String = "",
    val language: String = "",
    @SerialName("sourcecountry") val sourceCountry: String = "",
    /** Which source produced this article ("gdelt", "rss"). Empty in older cache files. */
    val via: String = "",
)

private val SEEN_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

fun Article.seenInstant(): Instant? =
    runCatching { LocalDateTime.parse(seenDate, SEEN_FORMAT).toInstant(ZoneOffset.UTC) }.getOrNull()

@Serializable
private data class ArtListResponse(val articles: List<Article> = emptyList())

/** GDELT answers "no results" and some errors with an empty or non-JSON body; both mean no articles. */
fun parseArtList(body: String): List<Article> {
    val text = body.trim()
    if (!text.startsWith("{")) return emptyList()
    return try {
        NewsJson.decodeFromString<ArtListResponse>(text).articles
    } catch (e: SerializationException) {
        emptyList()
    }
}

/** At most [max] articles from any one outlet, preserving order, so one site can't dominate the feed. */
fun List<Article>.capPerDomain(max: Int): List<Article> {
    val seen = HashMap<String, Int>()
    return filter { seen.merge(it.domain, 1, Int::plus)!! <= max }
}

data class NewsResult(val articles: List<Article>, val window: String)

/** A country to fetch headlines for, as the nearest land to an antipode. Sources use whichever fields they need. */
data class NewsPlace(val iso: String, val fips: String, val name: String)

interface NewsSource {
    /** Headlines for [place]: published there or about it, depending on the source. */
    suspend fun headlines(place: NewsPlace): NewsResult
}

/**
 * GDELT DOC 2.0 `sourcecountry:` queries. GDELT allows one request per 5 seconds, so every call
 * goes through a shared gate, and a 429 is retried.
 */
class GdeltNewsSource(
    private val client: HttpClient,
    private val minIntervalMs: Long = 5_500,
    private val retryDelayMs: Long = 15_000,
    private val minArticles: Int = 15,
    private val baseUrl: String = "https://api.gdeltproject.org/api/v2/doc/doc",
    private val clock: () -> Long = System::currentTimeMillis,
) : NewsSource {
    private val gate = Mutex()
    private var lastCallAt = 0L

    override suspend fun headlines(place: NewsPlace): NewsResult {
        val fips = place.fips
        val day = fetch(fips, "24h")
        if (day.size >= minArticles) return NewsResult(day, "24h")
        // Widening is a bonus; if it fails (GDELT rate limits hard), keep what the day gave us.
        val week = try {
            fetch(fips, "7d")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Trace.log { "7d widening failed for $fips: ${e.message}; keeping ${day.size} articles from 24h" }
            return NewsResult(day, "24h")
        }
        return if (week.size > day.size) NewsResult(week, "7d") else NewsResult(day, "24h")
    }

    private suspend fun fetch(fips: String, timespan: String): List<Article> {
        repeat(MAX_ATTEMPTS) { attempt ->
            val label = "GDELT $fips/$timespan attempt ${attempt + 1}/$MAX_ATTEMPTS"
            val (status, body) = throttled(label) {
                Trace.timed("$label request") {
                    val response = client.get(baseUrl) {
                        parameter("query", "sourcecountry:$fips")
                        parameter("mode", "artlist")
                        parameter("format", "json")
                        parameter("maxrecords", 250)
                        parameter("timespan", timespan)
                    }
                    response.status to response.bodyAsText()
                }
            }
            Trace.log { "$label -> HTTP ${status.value}, ${body.length} chars" }
            when (status) {
                HttpStatusCode.OK -> {
                    // Drop misfiled outlets before counting, so a feed padded with them still falls back to 7d.
                    val articles = parseArtList(body)
                        .filter { it.title.isNotBlank() }
                        .distinctBy { it.title }
                        .cleaned(fips)
                        .map { it.copy(via = VIA) }
                    Trace.log { "$label parsed ${articles.size} articles" }
                    return articles
                }
                HttpStatusCode.TooManyRequests -> {
                    Trace.log { "$label rate limited, waiting ${retryDelayMs} ms" }
                    delay(retryDelayMs)
                }
                else -> throw SourceFailedException("GDELT", "HTTP ${status.value}")
            }
        }
        throw RateLimitedException("GDELT")
    }

    private suspend fun <T> throttled(label: String, block: suspend () -> T): T = gate.withLock {
        val wait = lastCallAt + minIntervalMs - clock()
        if (wait > 0) {
            Trace.log { "$label throttle wait ${wait} ms" }
            delay(wait)
        }
        try {
            block()
        } finally {
            lastCallAt = clock()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val VIA = "gdelt"
    }
}

@Serializable
data class CacheEntry(val savedAt: Long, val window: String, val articles: List<Article>)

interface NewsCacheStore {
    suspend fun get(key: String): CacheEntry?
    suspend fun put(key: String, entry: CacheEntry)
}

class InMemoryNewsCacheStore : NewsCacheStore {
    private val entries = HashMap<String, CacheEntry>()
    override suspend fun get(key: String) = entries[key]
    override suspend fun put(key: String, entry: CacheEntry) {
        entries[key] = entry
    }
}

/**
 * The cache is what you see while the real answer loads, not a promise that it is still right. An entry of any age
 * under [maxAgeMs] is shown straight away (marked as still refreshing) while the live sources answer, and the
 * live answer replaces it. Only two things stop a fetch: an entry younger than [coolDownMs], which is served as
 * is so that rotating the phone or flipping views doesn't hammer the sources, and a failure, which keeps the
 * old entry on screen. An entry older than [maxAgeMs] is ignored, since very old headlines would mislead.
 * The cache is written only once every source has finished.
 */
class CachingNewsSource(
    private val delegate: NewsSource,
    private val store: NewsCacheStore,
    private val coolDownMs: Long = 2 * 60 * 1000,
    private val maxAgeMs: Long = 3 * 24 * 60 * 60 * 1000,
    private val clock: () -> Long = System::currentTimeMillis,
) : StreamingNewsSource {
    /**
     * The stored entry for [key], unless it is too old to show or has nothing in it. An empty entry is never worth
     * keeping: it would be replayed as "no headlines" when a refresh fails, and a failed fetch isn't a quiet place.
     */
    private suspend fun usable(key: String): CacheEntry? =
        store.get(key)?.takeIf { clock() - it.savedAt < maxAgeMs && it.articles.isNotEmpty() }

    override fun headlinesFlow(place: NewsPlace): Flow<NewsProgress> = flow {
        val key = place.fips
        val cached = usable(key)
        if (cached != null && clock() - cached.savedAt < coolDownMs) {
            Trace.log { "cache HIT $key (${cached.articles.size} articles, ${(clock() - cached.savedAt) / 1000}s old)" }
            emit(NewsProgress(cached.toResult(), 0))
            return@flow
        }
        Trace.log { "cache ${if (cached == null) "MISS" else "REVALIDATE"} $key" }
        // An old entry beats a blank screen while the refresh runs.
        if (cached != null) emit(NewsProgress(cached.toResult(), 1))
        emitAll(
            delegate.stream(place)
                .onEach { if (it.done && it.result.articles.isNotEmpty()) store.put(key, CacheEntry(clock(), it.result.window, it.result.articles)) }
                .catch { e ->
                    Trace.log { "fetch failed for $key: ${e::class.simpleName}: ${e.message}; ${if (cached != null) "keeping cached entry" else "no cache to fall back on"}" }
                    if (cached != null) emit(NewsProgress(cached.toResult(), 0)) else throw e
                },
        )
    }

    override suspend fun headlines(place: NewsPlace): NewsResult {
        val key = place.fips
        val cached = usable(key)
        if (cached != null && clock() - cached.savedAt < coolDownMs) {
            Trace.log { "cache HIT $key (${cached.articles.size} articles, ${(clock() - cached.savedAt) / 1000}s old)" }
            return cached.toResult()
        }
        Trace.log { "cache ${if (cached == null) "MISS" else "REVALIDATE"} $key" }
        return try {
            delegate.headlines(place).also { if (it.articles.isNotEmpty()) store.put(key, CacheEntry(clock(), it.window, it.articles)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Trace.log { "fetch failed for $key: ${e::class.simpleName}: ${e.message}; ${if (cached != null) "serving cached entry" else "no cache to fall back on"}" }
            cached?.toResult() ?: throw e
        }
    }

    private fun CacheEntry.toResult() = NewsResult(articles, window)
}
