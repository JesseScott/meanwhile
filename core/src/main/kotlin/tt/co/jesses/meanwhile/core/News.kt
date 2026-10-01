package tt.co.jesses.meanwhile.core

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
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

interface NewsSource {
    /** Headlines published in the country with this FIPS code. */
    suspend fun headlines(fips: String): NewsResult
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

    override suspend fun headlines(fips: String): NewsResult {
        val day = fetch(fips, "24h")
        if (day.size >= minArticles) return NewsResult(day, "24h")
        val week = fetch(fips, "7d")
        return if (week.size > day.size) NewsResult(week, "7d") else NewsResult(day, "24h")
    }

    private suspend fun fetch(fips: String, timespan: String): List<Article> {
        repeat(MAX_ATTEMPTS) {
            val (status, body) = throttled {
                val response = client.get(baseUrl) {
                    parameter("query", "sourcecountry:$fips")
                    parameter("mode", "artlist")
                    parameter("format", "json")
                    parameter("maxrecords", 250)
                    parameter("timespan", timespan)
                }
                response.status to response.bodyAsText()
            }
            when (status) {
                HttpStatusCode.OK ->
                    return parseArtList(body).filter { it.title.isNotBlank() }.distinctBy { it.title }
                HttpStatusCode.TooManyRequests -> delay(retryDelayMs)
                else -> error("GDELT returned ${status.value}")
            }
        }
        error("GDELT is rate limiting requests, try again shortly")
    }

    private suspend fun <T> throttled(block: suspend () -> T): T = gate.withLock {
        val wait = lastCallAt + minIntervalMs - clock()
        if (wait > 0) delay(wait)
        try {
            block()
        } finally {
            lastCallAt = clock()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
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

/** Serves fresh cache hits, and falls back to a stale entry when the upstream call fails. */
class CachingNewsSource(
    private val delegate: NewsSource,
    private val store: NewsCacheStore,
    private val ttlMs: Long = 20 * 60 * 1000,
    private val clock: () -> Long = System::currentTimeMillis,
) : NewsSource {
    override suspend fun headlines(fips: String): NewsResult {
        val cached = store.get(fips)
        if (cached != null && clock() - cached.savedAt < ttlMs) return cached.toResult()
        return try {
            delegate.headlines(fips).also { store.put(fips, CacheEntry(clock(), it.window, it.articles)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cached?.toResult() ?: throw e
        }
    }

    private fun CacheEntry.toResult() = NewsResult(articles, window)
}
