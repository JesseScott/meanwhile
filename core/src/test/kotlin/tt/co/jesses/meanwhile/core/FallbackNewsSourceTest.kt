package tt.co.jesses.meanwhile.core

import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class FallbackNewsSourceTest {
    private val place = NewsPlace("TO", "TN", "Tonga")

    private fun articles(prefix: String, count: Int, via: String) =
        List(count) { Article(url = "https://$via.example/$prefix$it", title = "$prefix story $it", domain = "$via.example", via = via) }

    private class Fake(private val result: NewsResult? = null, private val error: Throwable? = null) : NewsSource {
        var calls = 0
        override suspend fun headlines(place: NewsPlace): NewsResult {
            calls++
            error?.let { throw it }
            return result!!
        }
    }

    private fun answer(prefix: String, count: Int, via: String, window: String = "24h") =
        Fake(NewsResult(articles(prefix, count, via), window))

    @Test
    fun stopsAsSoonAsThereAreEnoughArticles() = runTest {
        val first = answer("a", 5, "gdelt")
        val second = answer("b", 5, "rss")

        val result = FallbackNewsSource(listOf(first, second), minArticles = 5).headlines(place)

        assertEquals(5, result.articles.size)
        assertEquals(1, first.calls)
        assertEquals(0, second.calls)
    }

    @Test
    fun topsUpAThinResultFromTheNextSourceInsteadOfDiscardingIt() = runTest {
        val sources = listOf(answer("a", 3, "gdelt"), answer("b", 4, "rss", window = "7d"))

        val result = FallbackNewsSource(sources, minArticles = 5).headlines(place)

        assertEquals(listOf("a story 0", "a story 1", "a story 2", "b story 0", "b story 1", "b story 2", "b story 3"), result.articles.map { it.title })
        assertEquals(listOf("gdelt", "gdelt", "gdelt", "rss", "rss", "rss", "rss"), result.articles.map { it.via })
        assertEquals("7d", result.window)
    }

    @Test
    fun keepsTheNarrowWindowWhenOnlyTheDaySourceContributed() = runTest {
        val sources = listOf(answer("a", 6, "gdelt", window = "24h"), answer("b", 6, "gnews", window = "7d"))
        assertEquals("24h", FallbackNewsSource(sources, minArticles = 5).headlines(place).window)
    }

    @Test
    fun dropsRepeatsThatDifferOnlyInCaseOrPunctuation() = runTest {
        val first = Fake(NewsResult(listOf(Article(url = "https://a.example/1", title = "Cyclone hits Tonga!", via = "gdelt")), "24h"))
        val second = Fake(NewsResult(listOf(Article(url = "https://b.example/1", title = "cyclone hits tonga"), Article(url = "https://b.example/2", title = "A new story")), "7d"))

        val result = FallbackNewsSource(listOf(first, second), minArticles = 5).headlines(place)

        assertEquals(listOf("Cyclone hits Tonga!", "A new story"), result.articles.map { it.title })
        assertEquals("gdelt", result.articles[0].via)
    }

    @Test
    fun nonNewsFromTheFirstSourceDoesNotCountTowardsTheMinimum() = runTest {
        // Two GDELT items for Tonga were job ads. They must not make the place look covered, or RSS never gets asked.
        val ads = NewsResult(
            listOf(
                Article(url = "https://matangitonga.to/a", title = "9529 Ministry of Finance - TASP vacancy procurement officer 1", domain = "matangitonga.to", via = "gdelt"),
                Article(url = "https://matangitonga.to/b", title = "9526 SPC vacancy communications 1", domain = "matangitonga.to", via = "gdelt"),
            ),
            "24h",
        )
        val first = Fake(ads)
        val second = answer("news", 2, "rss", window = "7d")

        val result = FallbackNewsSource(listOf(first, second), minArticles = 2).headlines(place)

        assertEquals(1, second.calls)
        assertEquals(listOf("news story 0", "news story 1"), result.articles.map { it.title })
    }

    @Test
    fun skipsASourceThatFailsAndCarriesOn() = runTest {
        val sources = listOf(Fake(error = RateLimitedException("GDELT")), answer("b", 6, "rss"))

        val result = FallbackNewsSource(sources, minArticles = 5).headlines(place)

        assertEquals(6, result.articles.size)
    }

    @Test
    fun whenEverySourceAnswersWithNothingThePlaceIsQuietNotAnOutage() = runTest {
        val sources = listOf(answer("a", 0, "gdelt"), answer("b", 0, "rss"))

        val result = FallbackNewsSource(sources, minArticles = 5).headlines(place)

        assertEquals(emptyList(), result.articles)
    }

    @Test
    fun nothingFoundWhileASourceFailedIsAnOutageBecauseTheFailedOneMightHaveHadHeadlines() = runTest {
        // GDELT was rate limited and the other source simply has no feed for this place. Reporting "no headlines"
        // would send the app off to try another country, which hits the same limit.
        val sources = listOf(Fake(error = RateLimitedException("GDELT")), answer("b", 0, "rss"))

        val error = assertFailsWith<NewsUnavailableException> { FallbackNewsSource(sources).headlines(place) }

        assertEquals(NewsFailure.RateLimited, error.reason)
    }

    @Test
    fun aFailedSourceDoesNotMatterWhenAnotherFoundHeadlines() = runTest {
        val sources = listOf(Fake(error = IOException("offline")), answer("b", 3, "rss"))

        val result = FallbackNewsSource(sources, minArticles = 5).headlines(place)

        assertEquals(3, result.articles.size)
    }

    @Test
    fun aSourceThatTimesOutCountsAsBusy() {
        assertEquals(NewsFailure.RateLimited, NewsUnavailableException("Tonga", listOf(SourceTimedOutException("GdeltNewsSource", 45_000))).reason)
    }

    @Test
    fun whenEverySourceFailsItThrowsAGenericErrorThatNamesNoSource() = runTest {
        val sources = listOf(Fake(error = RateLimitedException("GDELT")), Fake(error = SourceFailedException("Google News", "HTTP 500")))

        val error = assertFailsWith<NewsUnavailableException> { FallbackNewsSource(sources).headlines(place) }

        assertEquals("Headlines are unavailable right now", error.message)
        assertFalse("GDELT" in error.message.orEmpty() || "Google" in error.message.orEmpty())
        assertEquals(NewsFailure.RateLimited, error.reason)
        assertEquals(2, error.causes.size) // the detail is still there for logs
    }

    @Test
    fun classifiesWhyNothingLoaded() = runTest {
        suspend fun reasonFor(vararg errors: Throwable) = assertFailsWith<NewsUnavailableException> {
            FallbackNewsSource(errors.map { Fake(error = it) }).headlines(place)
        }.reason

        assertEquals(NewsFailure.Network, reasonFor(IOException("offline"), SourceFailedException("RSS", "HTTP 500")))
        assertEquals(NewsFailure.RateLimited, reasonFor(IOException("offline"), RateLimitedException("GDELT")))
        assertEquals(NewsFailure.Unknown, reasonFor(IllegalStateException("surprise")))
    }

    @Test
    fun triesSourcesInTheOrderGiven() = runTest {
        val order = mutableListOf<String>()
        fun named(name: String) = object : NewsSource {
            override suspend fun headlines(place: NewsPlace): NewsResult {
                order += name
                return NewsResult(emptyList(), "24h")
            }
        }

        FallbackNewsSource(listOf(named("gdelt"), named("rss"), named("gnews"))).headlines(place)

        assertEquals(listOf("gdelt", "rss", "gnews"), order)
    }
}
