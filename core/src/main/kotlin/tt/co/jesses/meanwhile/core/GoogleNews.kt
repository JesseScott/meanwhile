package tt.co.jesses.meanwhile.core

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

/**
 * Place names that mean something else in a news search (Tonga is also a wrestler), keyed by FIPS code.
 * Found by looking, so it is short; add to it when a fallback feed turns up the wrong thing.
 */
private val SEARCH_OVERRIDES: Map<String, String> = mapOf(
    "TN" to "Tonga -WWE -wrestler -\"Tama Tonga\"",
)

/** The Google News search query for [place], limited to the last [days] days. */
fun googleNewsQuery(place: NewsPlace, days: Int = 7): String =
    (SEARCH_OVERRIDES[place.fips] ?: "\"${place.name}\"") + " when:${days}d"

/**
 * Headlines **about** a place from Google News' RSS search, which has coverage when GDELT doesn't (100 recent
 * items for a country GDELT knows three articles from). Items come from English-language search, whatever
 * country the outlet is in.
 *
 * Google's feed carries a licence notice: it is "made available solely for the purpose of rendering Google News
 * results within a personal feed reader for personal, non-commercial use", and any other use is "expressly
 * prohibited". That makes this fine for a personal hobby build and a problem for a public release, so it is easy
 * to leave out of the source list.
 */
class GoogleNewsSource(
    private val client: HttpClient,
    private val baseUrl: String = "https://news.google.com/rss/search",
    private val days: Int = 7,
    private val maxArticles: Int = 40,
) : NewsSource {
    override suspend fun headlines(place: NewsPlace): NewsResult {
        val response = client.get(baseUrl) {
            parameter("q", googleNewsQuery(place, days))
            parameter("hl", "en-US")
            parameter("gl", "US")
            parameter("ceid", "US:en")
            header(HttpHeaders.UserAgent, NEWS_USER_AGENT)
        }
        if (response.status == HttpStatusCode.TooManyRequests) throw RateLimitedException("Google News")
        if (response.status != HttpStatusCode.OK) throw SourceFailedException("Google News", "HTTP ${response.status.value}")
        val articles = parseFeed(response.bodyAsText(), VIA).take(maxArticles)
        return NewsResult(articles, "${days}d")
    }

    private companion object {
        const val VIA = "gnews"
    }
}
