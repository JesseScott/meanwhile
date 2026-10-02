package tt.co.jesses.meanwhile.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class) // for currentTime, which these tests use to prove sources run side by side
class FallbackStreamTest {
    private val place = NewsPlace("TO", "TN", "Tonga")

    private fun stories(prefix: String, count: Int, via: String) =
        List(count) { Article(url = "https://$via.example/$prefix$it", title = "$prefix$it", domain = "$via.example", via = via) }

    /** Answers after [delayMs] of (virtual) time, and notes whether it was started, finished or cancelled. */
    private class Slow(
        private val delayMs: Long,
        private val articles: List<Article> = emptyList(),
        private val window: String = "24h",
    ) : NewsSource {
        var started = false
        var finished = false
        var cancelled = false

        override suspend fun headlines(place: NewsPlace): NewsResult {
            started = true
            try {
                delay(delayMs)
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
            finished = true
            return NewsResult(articles, window)
        }
    }

    @Test
    fun snapshotsArriveAsSourcesFinishAndKeepPriorityOrder() = runTest {
        val gdelt = Slow(5_000, stories("a", 1, "gdelt"))
        val rss = Slow(100, stories("b", 1, "rss"))

        val snapshots = FallbackNewsSource(listOf(gdelt, rss), minArticles = 5, hedgeDelayMs = 10).headlinesFlow(place).toList()

        // RSS answers first, so it is shown alone; when GDELT lands its article goes ahead of it, because it ranks higher.
        assertEquals(listOf(listOf("b0") to 1, listOf("a0", "b0") to 0), snapshots.map { s -> s.result.articles.map { it.title } to s.pending })
        assertTrue(snapshots.last().done)
    }

    @Test
    fun sourcesRunAtTheSameTimeNotOneAfterAnother() = runTest {
        val a = Slow(4_000, stories("a", 1, "gdelt"))
        val b = Slow(3_000, stories("b", 1, "rss"))

        FallbackNewsSource(listOf(a, b), minArticles = 100, hedgeDelayMs = 100).headlines(place)

        // One after another would take 7,000 ms; side by side it is the slowest one, 4,000 ms.
        assertTrue(currentTime in 4_000..4_200, "took $currentTime ms")
    }

    @Test
    fun aLowerPrioritySourceIsNeverStartedWhenTheOnesAboveGaveEnough() = runTest {
        val gdelt = Slow(0, stories("a", 5, "gdelt"))
        val google = Slow(0, stories("g", 5, "gnews"))

        val snapshots = FallbackNewsSource(listOf(gdelt, google), minArticles = 5, hedgeDelayMs = 1_000).headlinesFlow(place).toList()

        assertFalse(google.started, "Google should not be asked when it isn't needed")
        assertEquals(5, snapshots.last().result.articles.size)
        assertTrue(snapshots.last().done)
    }

    @Test
    fun asksLowerPrioritySourcesWhenTheOnesAboveAreSlowOrThin() = runTest {
        val gdelt = Slow(10_000, stories("a", 5, "gdelt"))
        val rss = Slow(0, stories("b", 2, "rss"))
        val google = Slow(0, stories("c", 3, "gnews"))

        val result = FallbackNewsSource(listOf(gdelt, rss, google), minArticles = 5, hedgeDelayMs = 1_000).headlines(place)

        assertTrue(rss.started && google.started)
        assertEquals(10, result.articles.size)
        assertEquals(listOf("gdelt", "gdelt", "gdelt", "gdelt", "gdelt", "rss", "rss", "gnews", "gnews", "gnews"), result.articles.map { it.via })
    }

    @Test
    fun aHangingSourceTimesOutAndTheOthersStillDeliver() = runTest {
        val hangs = Slow(1_000_000, stories("a", 1, "gdelt"))
        val works = Slow(10, stories("b", 3, "rss"))

        val result = FallbackNewsSource(listOf(hangs, works), minArticles = 5, hedgeDelayMs = 10, sourceTimeoutMs = 5_000).headlines(place)

        assertEquals(listOf("b0", "b1", "b2"), result.articles.map { it.title })
        assertTrue(hangs.cancelled)
        assertTrue(currentTime <= 5_100, "waited $currentTime ms")
    }

    @Test
    fun everySourceTimingOutIsReportedAsUnavailable() = runTest {
        val sources = listOf(Slow(1_000_000), Slow(1_000_000))

        val error = assertFailsWith<NewsUnavailableException> {
            FallbackNewsSource(sources, hedgeDelayMs = 10, sourceTimeoutMs = 5_000).headlines(place)
        }

        assertEquals("Headlines are unavailable right now", error.message)
        assertEquals(2, error.causes.size)
    }

    @Test
    fun stoppingEarlyCancelsSourcesStillWorking() = runTest {
        val quick = Slow(50, stories("a", 1, "gdelt"))
        val slow = Slow(100_000, stories("b", 1, "rss"))

        FallbackNewsSource(listOf(quick, slow), minArticles = 5, hedgeDelayMs = 10).headlinesFlow(place).first()

        assertTrue(slow.started && slow.cancelled && !slow.finished)
    }

    @Test
    fun theLastSnapshotDoesNotChangeWhenCollectedAgain() = runTest {
        val source = FallbackNewsSource(listOf(Slow(10, stories("a", 2, "gdelt")), Slow(20, stories("b", 2, "rss"))), minArticles = 99, hedgeDelayMs = 5)
        val first = source.headlinesFlow(place).toList().last()
        val second = source.headlinesFlow(place).toList().last()
        assertEquals(first, second)
        assertEquals(0, first.pending)
    }
}
