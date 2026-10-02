package tt.co.jesses.meanwhile.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Headlines so far while some sources are still answering. Each snapshot is complete and replaces the one
 * before it, so a collector may drop intermediate ones. The last has [pending] == 0.
 *
 * Articles are merged in source priority order and never reordered within a snapshot, but a slow,
 * high-priority source (GDELT) arriving late inserts its articles ahead of those already shown. A screen
 * can group by [Article.via] or only append, whichever suits it; the core doesn't decide that.
 */
data class NewsProgress(val result: NewsResult, val pending: Int) {
    val done: Boolean get() = pending == 0
}

/** A [NewsSource] that can also report progress while its sources answer. */
interface StreamingNewsSource : NewsSource {
    /**
     * Snapshots as sources finish. Completes after the last one, or throws [NewsUnavailableException] if
     * every source failed.
     */
    fun headlinesFlow(place: NewsPlace): Flow<NewsProgress>
}

/** Progress from any source: a streaming source's own, or one finished snapshot for a plain one. */
fun NewsSource.stream(place: NewsPlace): Flow<NewsProgress> =
    if (this is StreamingNewsSource) headlinesFlow(place) else flow { emit(NewsProgress(headlines(place), 0)) }
