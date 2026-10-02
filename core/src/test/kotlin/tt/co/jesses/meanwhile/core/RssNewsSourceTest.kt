package tt.co.jesses.meanwhile.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RssNewsSourceTest {
    private val tonga = NewsPlace("TO", "TN", "Tonga")
    private val local = RssFeed("https://local.example/rss")
    private val regional = RssFeed("https://regional.example/rss", requirePlaceName = true)

    private fun item(title: String, link: String, date: String) =
        "<item><title>$title</title><link>$link</link><pubDate>$date</pubDate><description>$title</description></item>"

    private fun feed(vararg items: String) = "<rss><channel>${items.joinToString("")}</channel></rss>"

    private fun source(
        feeds: Map<String, List<RssFeed>>,
        responses: Map<String, Pair<HttpStatusCode, String>>,
        requested: MutableList<String> = mutableListOf(),
    ): RssNewsSource {
        val engine = MockEngine { request ->
            val url = request.url.toString()
            requested += url
            val (status, body) = responses[url] ?: (HttpStatusCode.NotFound to "")
            respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "application/rss+xml"))
        }
        return RssNewsSource(HttpClient(engine), feeds)
    }

    @Test
    fun mergesFeedsNewestFirstAndMarksThemRss() = runTest {
        val source = source(
            feeds = mapOf("TN" to listOf(local, regional)),
            responses = mapOf(
                local.url to (HttpStatusCode.OK to feed(item("Older local story", "https://local.example/1", "Wed, 30 Sep 2026 01:00:00 GMT"))),
                regional.url to (HttpStatusCode.OK to feed(item("Tonga newer regional story", "https://regional.example/2", "Fri, 02 Oct 2026 01:00:00 GMT"))),
            ),
        )

        val result = source.headlines(tonga)

        assertEquals(listOf("Tonga newer regional story", "Older local story"), result.articles.map { it.title })
        assertEquals(setOf("rss"), result.articles.map { it.via }.toSet())
        assertEquals("7d", result.window)
    }

    @Test
    fun aRegionalFeedKeepsOnlyItemsThatNameThePlace() = runTest {
        val source = source(
            feeds = mapOf("TN" to listOf(regional)),
            responses = mapOf(
                regional.url to (HttpStatusCode.OK to feed(
                    item("Fiji parliament debates bill", "https://regional.example/1", "Fri, 02 Oct 2026 01:00:00 GMT"),
                    item("Tongan rugby team named", "https://regional.example/2", "Fri, 02 Oct 2026 02:00:00 GMT"),
                    item("Samoa weather warning", "https://regional.example/3", "Fri, 02 Oct 2026 03:00:00 GMT"),
                )),
            ),
        )

        assertEquals(listOf("Tongan rugby team named"), source.headlines(tonga).articles.map { it.title })
    }

    @Test
    fun aPlaceWithNoFeedsHasNoArticlesAndMakesNoRequests() = runTest {
        val requested = mutableListOf<String>()
        val result = source(feeds = emptyMap(), responses = emptyMap(), requested = requested)
            .headlines(NewsPlace("NZ", "NZ", "New Zealand"))

        assertEquals(emptyList(), result.articles)
        assertEquals(emptyList(), requested)
    }

    @Test
    fun oneFailingFeedDoesNotLoseTheOthers() = runTest {
        val source = source(
            feeds = mapOf("TN" to listOf(local, regional)),
            responses = mapOf(
                local.url to (HttpStatusCode.InternalServerError to ""),
                regional.url to (HttpStatusCode.OK to feed(item("Tonga story", "https://regional.example/1", "Fri, 02 Oct 2026 01:00:00 GMT"))),
            ),
        )
        assertEquals(listOf("Tonga story"), source.headlines(tonga).articles.map { it.title })
    }

    @Test
    fun failsWhenEveryFeedFails() = runTest {
        val source = source(
            feeds = mapOf("TN" to listOf(local, regional)),
            responses = mapOf(local.url to (HttpStatusCode.Forbidden to ""), regional.url to (HttpStatusCode.NotFound to "")),
        )
        assertFailsWith<SourceFailedException> { source.headlines(tonga) }
    }

    @Test
    fun theCuratedTableCoversTongaAndTheRegionalFeedIsFiltered() {
        val tongaFeeds = CURATED_FEEDS.getValue("TN")
        assertTrue(tongaFeeds.any { "matangitonga.to" in it.url && !it.requirePlaceName })
        assertTrue(tongaFeeds.any { "rnz.co.nz" in it.url && it.requirePlaceName })
        assertTrue(CURATED_FEEDS.getValue("WS").single().requirePlaceName)
    }
}
