package tt.co.jesses.meanwhile.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GoogleNewsSourceTest {
    private val madagascar = NewsPlace("MG", "MA", "Madagascar")
    private val tonga = NewsPlace("TO", "TN", "Tonga")

    private fun rss(vararg titles: String) = titles.withIndex().joinToString(
        prefix = """<rss><channel>""", postfix = "</channel></rss>", separator = "",
    ) { (i, t) ->
        """<item><title>$t - Outlet $i</title><link>https://news.google.com/rss/articles/$i</link><pubDate>Fri, 02 Oct 2026 04:00:00 GMT</pubDate><source url="https://www.outlet$i.example">Outlet $i</source></item>"""
    }

    private fun source(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = rss("One", "Two", "Three"),
        seen: MutableList<HttpRequestData> = mutableListOf(),
        maxArticles: Int = 40,
    ): GoogleNewsSource {
        val engine = MockEngine { request ->
            seen += request
            respond(content = body, status = status, headers = headersOf(HttpHeaders.ContentType, "application/rss+xml"))
        }
        return GoogleNewsSource(HttpClient(engine), maxArticles = maxArticles)
    }

    @Test
    fun searchesForTheQuotedPlaceNameOverTheLastWeek() {
        assertEquals("\"Madagascar\" when:7d", googleNewsQuery(madagascar))
        assertEquals("\"Madagascar\" when:3d", googleNewsQuery(madagascar, days = 3))
    }

    @Test
    fun excludesTheWrestlerWhenSearchingForTonga() {
        val query = googleNewsQuery(tonga)
        assertTrue("-WWE" in query && "-\"Tama Tonga\"" in query, query)
        assertTrue(query.endsWith("when:7d"))
    }

    @Test
    fun sendsAnEnglishSearchAndIdentifiesTheApp() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        source(seen = seen).headlines(madagascar)

        val request = seen.single()
        assertEquals("\"Madagascar\" when:7d", request.url.parameters["q"])
        assertEquals("en-US", request.url.parameters["hl"])
        assertEquals("US", request.url.parameters["gl"])
        assertEquals("US:en", request.url.parameters["ceid"])
        assertEquals(NEWS_USER_AGENT, request.headers[HttpHeaders.UserAgent])
    }

    @Test
    fun returnsParsedArticlesMarkedAsGoogleNewsOverAWeek() = runTest {
        val result = source().headlines(madagascar)

        assertEquals("7d", result.window)
        assertEquals(listOf("One", "Two", "Three"), result.articles.map { it.title })
        assertEquals(setOf("gnews"), result.articles.map { it.via }.toSet())
        assertEquals("outlet0.example", result.articles[0].domain)
    }

    @Test
    fun capsTheNumberOfArticles() = runTest {
        val result = source(body = rss(*Array(10) { "Story $it" }), maxArticles = 4).headlines(madagascar)
        assertEquals(4, result.articles.size)
    }

    @Test
    fun anEmptyFeedMeansNoArticlesNotAFailure() = runTest {
        assertEquals(emptyList(), source(body = "<rss><channel></channel></rss>").headlines(madagascar).articles)
    }

    @Test
    fun rateLimitingAndOtherErrorsAreTypedAndGeneric() = runTest {
        assertFailsWith<RateLimitedException> { source(status = HttpStatusCode.TooManyRequests, body = "").headlines(madagascar) }
        assertFailsWith<SourceFailedException> { source(status = HttpStatusCode.InternalServerError, body = "").headlines(madagascar) }
    }
}
