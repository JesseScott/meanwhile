package tt.co.jesses.meanwhile.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NewsTest {
    private val nz = NewsPlace("NZ", "NZ", "New Zealand")

    private val sample = """
        {"articles": [
          {"url":"https://a.example/1","url_mobile":"","title":"One","seendate":"20260930T143000Z","socialimage":"","domain":"a.example","language":"English","sourcecountry":"New Zealand"},
          {"url":"https://a.example/2","title":"Two","seendate":"20260930T120000Z","domain":"a.example","language":"English","sourcecountry":"New Zealand"},
          {"url":"https://b.example/3","title":"Tres","seendate":"20260930T110000Z","domain":"b.example","language":"Spanish","sourcecountry":"Chile"}
        ]}
    """.trimIndent()

    @Test
    fun parsesArtList() {
        val articles = parseArtList(sample)
        assertEquals(3, articles.size)
        assertEquals("One", articles[0].title)
        assertEquals("Spanish", articles[2].language)
    }

    @Test
    fun parsesSeenDate() {
        assertEquals("2026-09-30T14:30:00Z", parseArtList(sample)[0].seenInstant().toString())
    }

    @Test
    fun emptyAndNonJsonBodiesMeanNoArticles() {
        assertEquals(emptyList(), parseArtList(""))
        assertEquals(emptyList(), parseArtList("{}"))
        assertEquals(emptyList(), parseArtList("Please limit requests to one every 5 seconds"))
    }

    @Test
    fun capsArticlesPerDomain() {
        val capped = parseArtList(sample).capPerDomain(1)
        assertEquals(listOf("One", "Tres"), capped.map { it.title })
    }

    @Test
    fun cacheServesFreshEntriesAndSurvivesUpstreamFailure() = runTest {
        var now = 0L
        var calls = 0
        var fail = false
        val upstream = object : NewsSource {
            override suspend fun headlines(place: NewsPlace): NewsResult {
                calls++
                if (fail) error("boom")
                return NewsResult(parseArtList(sample), "24h")
            }
        }
        val source = CachingNewsSource(upstream, InMemoryNewsCacheStore(), coolDownMs = 1000, clock = { now })

        source.headlines(nz)
        now = 500
        source.headlines(nz)
        assertEquals(1, calls)

        now = 5000
        fail = true
        assertEquals(3, source.headlines(nz).articles.size)
        assertEquals(2, calls)
    }
}
