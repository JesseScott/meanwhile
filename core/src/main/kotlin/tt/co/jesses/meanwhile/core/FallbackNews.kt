package tt.co.jesses.meanwhile.core

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")

/** Titles that differ only in case or punctuation are the same story. */
private fun titleKey(title: String): String = NON_ALNUM.replace(title.lowercase(), " ").trim()

private sealed interface Outcome {
    data class Answered(val result: NewsResult) : Outcome
    data class Failed(val error: Throwable) : Outcome
    data object Skipped : Outcome
}

/**
 * Asks several sources about one place at once and merges what comes back, in the order the sources are given.
 * The point is to stay at the nearest place to the antipode: every source gets a turn there before the caller
 * gives up and moves farther away, and a thin result is topped up instead of thrown away.
 *
 * Sources are started in priority order, each [hedgeDelayMs] after the one before, so a fast source can answer
 * before a slower, lower-priority one is even asked. If the sources above one already gave [minArticles] by its
 * turn, it is skipped. A source slower than [sourceTimeoutMs] counts as failed.
 *
 * A source that fails is skipped. Only if every source fails does this throw, and then as a
 * [NewsUnavailableException] whose message says nothing about which source or why. A source that answers with
 * nothing counts as an answer: the place is simply quiet.
 */
class FallbackNewsSource(
    private val sources: List<NewsSource>,
    private val minArticles: Int = 5,
    private val hedgeDelayMs: Long = 1_500,
    private val sourceTimeoutMs: Long = 45_000,
) : StreamingNewsSource {

    /** Waits for every source, so the result is as complete as it will get. */
    override suspend fun headlines(place: NewsPlace): NewsResult = headlinesFlow(place).last().result

    override fun headlinesFlow(place: NewsPlace): Flow<NewsProgress> = channelFlow {
        if (sources.isEmpty()) {
            send(NewsProgress(NewsResult(emptyList(), "24h"), 0))
            return@channelFlow
        }

        val lock = Mutex()
        val outcomes = arrayOfNulls<Outcome>(sources.size) // null means still pending
        val failures = mutableListOf<Throwable>()

        /** Articles from the finished sources before [upTo], in priority order, repeats dropped. Call under [lock]. */
        fun merged(upTo: Int = sources.size): NewsResult {
            val seen = LinkedHashMap<String, Article>()
            var widest = "24h"
            for (i in 0 until upTo) {
                val answered = outcomes[i] as? Outcome.Answered ?: continue
                val before = seen.size
                answered.result.articles.forEach { seen.putIfAbsent(titleKey(it.title), it) }
                if (seen.size > before && answered.result.window == "7d") widest = "7d"
            }
            return NewsResult(seen.values.toList(), widest)
        }

        suspend fun finish(index: Int, outcome: Outcome) = lock.withLock {
            outcomes[index] = outcome
            if (outcome is Outcome.Failed) failures += outcome.error
            val pending = outcomes.count { it == null }
            // Nothing to show until some source has answered; if none ever does, the end of this block throws.
            if (outcomes.any { it is Outcome.Answered }) send(NewsProgress(merged(), pending))
        }

        coroutineScope {
            sources.forEachIndexed { index, source ->
                launch {
                    val name = source::class.simpleName
                    if (index > 0) {
                        delay(index * hedgeDelayMs)
                        if (lock.withLock { merged(upTo = index).articles.size } >= minArticles) {
                            Trace.log { "$name skipped for ${place.name}: the sources above it gave enough" }
                            finish(index, Outcome.Skipped)
                            return@launch
                        }
                    }
                    val outcome = try {
                        val result = withTimeoutOrNull(sourceTimeoutMs) { source.headlines(place) }
                        if (result == null) {
                            Outcome.Failed(SourceFailedException(name.orEmpty(), "timed out after $sourceTimeoutMs ms"))
                        } else {
                            // Cleaned before counting, so non-news can't make a thin place look like it has enough.
                            Trace.log { "$name gave ${result.articles.size} for ${place.name}" }
                            Outcome.Answered(result.copy(articles = result.articles.cleaned(place.fips)))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Trace.log { "$name failed for ${place.name}: ${e::class.simpleName}: ${e.message}" }
                        Outcome.Failed(e)
                    }
                    finish(index, outcome)
                }
            }
        }

        if (outcomes.none { it is Outcome.Answered }) throw NewsUnavailableException(place.name, failures)
    }
}
