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
import kotlin.test.assertTrue

class GdeltNewsSourceTest {
    private val madagascar = NewsPlace("MG", "MA", "Madagascar")
    private val nz = NewsPlace("NZ", "NZ", "New Zealand")

    private fun json(articles: List<Triple<String, String, String>>): String =
        articles.joinToString(prefix = """{"articles":[""", postfix = "]}", separator = ",") { (domain, language, title) ->
            """{"url":"https://$domain/$title","title":"$title","seendate":"20261001T000000Z","domain":"$domain","language":"$language","sourcecountry":"Madagascar"}"""
        }

    private fun chinese(n: Int, prefix: String) = List(n) { Triple("storm.mg", "Chinese", "$prefix-zh-$it") }
    private fun french(n: Int, prefix: String) = List(n) { Triple("newsmada.com", "French", "$prefix-fr-$it") }

    private fun source(vararg bodiesByTimespan: Pair<String, String>, calls: MutableList<String>): GdeltNewsSource {
        val bodies = bodiesByTimespan.toMap()
        val engine = MockEngine { request ->
            val timespan = request.url.parameters["timespan"].orEmpty()
            calls += "${request.url.parameters["query"]} $timespan"
            respond(
                content = bodies.getValue(timespan),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return GdeltNewsSource(HttpClient(engine), minIntervalMs = 0, retryDelayMs = 0)
    }

    @Test
    fun aDayPaddedWithMisfiledOutletsStillWidensToSevenDays() = runTest {
        // The Madagascar case: 20 articles that look like a healthy day, but 17 are a Taiwanese site filed under .mg.
        val calls = mutableListOf<String>()
        val source = source(
            "24h" to json(chinese(17, "d") + french(3, "d")),
            "7d" to json(chinese(60, "w") + french(9, "w")),
            calls = calls,
        )

        val result = source.headlines(madagascar)

        assertEquals(listOf("sourcecountry:MA 24h", "sourcecountry:MA 7d"), calls)
        assertEquals("7d", result.window)
        assertEquals(9, result.articles.size)
        assertTrue(result.articles.all { it.domain == "newsmada.com" })
    }

    @Test
    fun aHealthyDayDoesNotWiden() = runTest {
        val calls = mutableListOf<String>()
        val source = source("24h" to json(french(20, "d")), "7d" to json(french(50, "w")), calls = calls)

        val result = source.headlines(madagascar)

        assertEquals(listOf("sourcecountry:MA 24h"), calls)
        assertEquals("24h", result.window)
        assertEquals(20, result.articles.size)
    }

    @Test
    fun keepsRateLimitingTypedAndFreeOfUserFacingDetail() = runTest {
        val engine = MockEngine {
            respond(content = "Please limit requests", status = HttpStatusCode.TooManyRequests, headers = headersOf(HttpHeaders.ContentType, "text/plain"))
        }
        val source = GdeltNewsSource(HttpClient(engine), minIntervalMs = 0, retryDelayMs = 0)

        val error = kotlin.test.assertFailsWith<RateLimitedException> { source.headlines(madagascar) }

        assertEquals("GDELT", error.source)
    }

    @Test
    fun marksEveryArticleAsComingFromGdelt() = runTest {
        val source = source("24h" to json(french(20, "d")), "7d" to json(french(1, "w")), calls = mutableListOf())
        assertEquals(setOf("gdelt"), source.headlines(madagascar).articles.map { it.via }.toSet())
    }

    @Test
    fun countriesNotInTheLanguageTableKeepTheirForeignLanguageArticles() = runTest {
        val calls = mutableListOf<String>()
        val mixed = List(20) { Triple("nzherald.co.nz", if (it % 2 == 0) "English" else "Chinese", "t$it") }
        val source = source("24h" to json(mixed), "7d" to json(mixed), calls = calls)

        val result = source.headlines(nz)

        assertEquals(20, result.articles.size)
        assertEquals(listOf("sourcecountry:NZ 24h"), calls)
    }
}
