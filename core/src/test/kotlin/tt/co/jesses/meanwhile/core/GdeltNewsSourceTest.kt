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
        return GdeltNewsSource(HttpClient(engine), minIntervalMs = 0)
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
        val source = GdeltNewsSource(HttpClient(engine), minIntervalMs = 0)

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

    private fun scripted(vararg responses: Pair<String, HttpStatusCode>, bodiesByTimespan: Map<String, String>): GdeltNewsSource {
        val status = responses.toMap()
        val engine = MockEngine { request ->
            val timespan = request.url.parameters["timespan"].orEmpty()
            val code = status[timespan] ?: HttpStatusCode.OK
            respond(
                content = if (code == HttpStatusCode.OK) bodiesByTimespan.getValue(timespan) else "Please limit requests",
                status = code,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return GdeltNewsSource(HttpClient(engine), minIntervalMs = 0)
    }

    @Test
    fun anEmptyDayWhenWideningIsRateLimitedIsAFailureNotAQuietPlace() = runTest {
        // Madagascar's day is nearly all a misfiled Taiwanese site, so it comes out empty; the week is rate limited.
        // Saying "no headlines" here sent the app off to South Africa.
        val source = scripted(
            "7d" to HttpStatusCode.TooManyRequests,
            bodiesByTimespan = mapOf("24h" to json(chinese(20, "d"))),
        )

        kotlin.test.assertFailsWith<RateLimitedException> { source.headlines(madagascar) }
    }

    @Test
    fun aThinDayIsKeptWhenWideningFails() = runTest {
        val source = scripted(
            "7d" to HttpStatusCode.TooManyRequests,
            bodiesByTimespan = mapOf("24h" to json(chinese(10, "d") + french(3, "d"))),
        )

        val result = source.headlines(madagascar)

        assertEquals("24h", result.window)
        assertEquals(3, result.articles.size)
    }

    @Test
    fun aDayAndAWeekThatBothAnswerWithNothingIsAQuietPlace() = runTest {
        val source = scripted(bodiesByTimespan = mapOf("24h" to json(emptyList()), "7d" to json(emptyList())))

        val result = source.headlines(madagascar)

        assertEquals(emptyList(), result.articles)
    }

    /** A day with enough articles that the source does not go on to ask for the week. */
    private fun healthyDay() = json(french(15, "d"))

    /** Answers with the given statuses in turn (the last one repeats) and counts the requests that reach it. */
    private class Scripted(vararg val statuses: HttpStatusCode, private val okBody: String = "{}") {
        var calls = 0
        val engine = MockEngine {
            val status = statuses[minOf(calls, statuses.lastIndex)]
            calls++
            respond(
                content = if (status == HttpStatusCode.OK) okBody else "Please limit requests to one every 5 seconds",
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
    }

    @Test
    fun aRefusalMakesTheSourceStandDownWithoutTouchingTheNetwork() = runTest {
        var now = 1_000_000L
        val script = Scripted(HttpStatusCode.TooManyRequests, HttpStatusCode.OK, okBody = healthyDay())
        val source = GdeltNewsSource(HttpClient(script.engine), minIntervalMs = 0, clock = { now }, coolDownsMs = listOf(60_000))

        kotlin.test.assertFailsWith<RateLimitedException> { source.headlines(madagascar) }
        assertEquals(1, script.calls)

        // Still inside the stand-down: refused at once, and GDELT is not asked.
        now += 30_000
        kotlin.test.assertFailsWith<RateLimitedException> { source.headlines(madagascar) }
        assertEquals(1, script.calls)

        // After it, the source tries again.
        now += 31_000
        source.headlines(madagascar)
        assertEquals(2, script.calls)
    }

    @Test
    fun theStandDownGrowsWithEachRefusalInARowAndIsForgottenAfterASuccess() = runTest {
        var now = 1_000_000L
        val script = Scripted(
            HttpStatusCode.TooManyRequests, HttpStatusCode.TooManyRequests, HttpStatusCode.OK, HttpStatusCode.TooManyRequests,
            okBody = healthyDay(),
        )
        val source = GdeltNewsSource(HttpClient(script.engine), minIntervalMs = 0, clock = { now }, coolDownsMs = listOf(10_000, 20_000, 30_000))

        suspend fun tryOnce() = runCatching { source.headlines(madagascar) }

        tryOnce() // refusal #1: stand down 10 s
        now += 11_000
        tryOnce() // refusal #2: stand down 20 s
        assertEquals(2, script.calls)

        now += 15_000 // 15 s into a 20 s stand-down
        tryOnce()
        assertEquals(2, script.calls, "still standing down")

        now += 6_000 // past it
        tryOnce() // an answer: the count of refusals in a row is forgotten
        assertEquals(3, script.calls)

        tryOnce() // the source is not standing down after a success, so this one is asked and refused: stand down 10 s again
        assertEquals(4, script.calls)
        now += 11_000
        tryOnce()
        assertEquals(5, script.calls, "back to the shortest stand-down, not the third")
    }

    @Test
    fun aPlainTextPageSentWithA200IsARefusalNotAQuietPlace() = runTest {
        val script = Scripted(HttpStatusCode.OK, okBody = "Please limit requests to one every 5 seconds or contact us")
        val source = GdeltNewsSource(HttpClient(script.engine), minIntervalMs = 0)

        kotlin.test.assertFailsWith<RateLimitedException> { source.headlines(madagascar) }
    }

    @Test
    fun aValidEmptyAnswerIsStillAQuietPlace() = runTest {
        val script = Scripted(HttpStatusCode.OK, okBody = "{}")
        val source = GdeltNewsSource(HttpClient(script.engine), minIntervalMs = 0)

        val result = source.headlines(madagascar)

        assertEquals(emptyList(), result.articles)
        assertEquals(2, script.calls, "an empty day still tries the week")
    }
}
