package tt.co.jesses.meanwhile.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CachingStreamTest {
    private val place = NewsPlace("TO", "TN", "Tonga")

    private fun article(title: String) = Article(url = "https://x.example/$title", title = title, domain = "x.example")
    private fun result(vararg titles: String, window: String = "24h") = NewsResult(titles.map(::article), window)

    /** A streaming source that plays back [snapshots] then optionally fails. */
    private class Playback(private val snapshots: List<NewsProgress>, private val error: Throwable? = null) : StreamingNewsSource {
        var calls = 0
        override fun headlinesFlow(place: NewsPlace): Flow<NewsProgress> = flow {
            calls++
            snapshots.forEach { emit(it) }
            error?.let { throw it }
        }
        override suspend fun headlines(place: NewsPlace) = headlinesFlow(place).last().result
    }

    private suspend fun store(savedAt: Long, vararg titles: String) = InMemoryNewsCacheStore().also {
        it.put("TN", CacheEntry(savedAt, "24h", titles.map(::article)))
    }

    private fun titles(p: NewsProgress) = p.result.articles.map { it.title }

    @Test
    fun aFreshEntryIsServedAsOneFinishedSnapshotWithoutAskingTheSource() = runTest {
        val source = Playback(listOf(NewsProgress(result("live"), 0)))
        val cache = CachingNewsSource(source, store(savedAt = 900, "cached"), ttlMs = 1_000, clock = { 1_000 })

        val snapshots = cache.headlinesFlow(place).toList()

        assertEquals(listOf(listOf("cached") to 0), snapshots.map { titles(it) to it.pending })
        assertEquals(0, source.calls)
    }

    @Test
    fun aStaleEntryIsShownAtOnceWhileTheRefreshRuns() = runTest {
        val source = Playback(listOf(NewsProgress(result("live-1"), 1), NewsProgress(result("live-1", "live-2"), 0)))
        val cache = CachingNewsSource(source, store(savedAt = 0, "old"), ttlMs = 1_000, clock = { 5_000 })

        val snapshots = cache.headlinesFlow(place).toList()

        assertEquals(
            listOf(listOf("old") to 1, listOf("live-1") to 1, listOf("live-1", "live-2") to 0),
            snapshots.map { titles(it) to it.pending },
        )
    }

    @Test
    fun theCacheIsWrittenOnlyOnceEverySourceHasFinished() = runTest {
        val store = InMemoryNewsCacheStore()
        val source = Playback(listOf(NewsProgress(result("partial"), 1), NewsProgress(result("partial", "full"), 0)))
        val cache = CachingNewsSource(source, store, ttlMs = 1_000, clock = { 7 })

        // Whether the cache already held an entry at the moment each snapshot reached the collector.
        val cachedWhenSeen = mutableListOf<Boolean>()
        cache.headlinesFlow(place).collect { cachedWhenSeen += store.get("TN") != null }

        assertEquals(listOf(false, true), cachedWhenSeen) // the partial snapshot was not cached, the finished one was
        assertEquals(listOf("partial", "full"), store.get("TN")!!.articles.map { it.title })
        assertEquals(7L, store.get("TN")!!.savedAt)
    }

    @Test
    fun aPartialResultIsNotCachedIfTheStreamIsStoppedEarly() = runTest {
        val store = InMemoryNewsCacheStore()
        val source = Playback(listOf(NewsProgress(result("partial"), 1), NewsProgress(result("partial", "full"), 0)))

        CachingNewsSource(source, store, clock = { 7 }).headlinesFlow(place).first()

        assertNull(store.get("TN"))
    }

    @Test
    fun whenTheRefreshFailsTheStaleEntryIsKeptAsTheFinalResult() = runTest {
        val source = Playback(emptyList(), error = NewsUnavailableException("Tonga", emptyList()))
        val cache = CachingNewsSource(source, store(savedAt = 0, "old"), ttlMs = 1_000, clock = { 5_000 })

        val snapshots = cache.headlinesFlow(place).toList()

        assertEquals(listOf(listOf("old") to 1, listOf("old") to 0), snapshots.map { titles(it) to it.pending })
    }

    @Test
    fun withNothingCachedAFailureReachesTheCollector() = runTest {
        val source = Playback(emptyList(), error = NewsUnavailableException("Tonga", listOf(RateLimitedException("GDELT"))))
        val cache = CachingNewsSource(source, InMemoryNewsCacheStore(), clock = { 5_000 })

        val error = assertFailsWith<NewsUnavailableException> { cache.headlinesFlow(place).toList() }

        assertEquals(NewsFailure.RateLimited, error.reason)
    }

    @Test
    fun aPlainSourceStreamsAsOneFinishedSnapshot() = runTest {
        val plain = object : NewsSource {
            override suspend fun headlines(place: NewsPlace) = result("only")
        }
        assertEquals(listOf(listOf("only") to 0), plain.stream(place).toList().map { titles(it) to it.pending })
    }
}
