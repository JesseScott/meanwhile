package tt.co.jesses.meanwhile.core

import kotlin.coroutines.cancellation.CancellationException

private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")

/** Titles that differ only in case or punctuation are the same story. */
private fun titleKey(title: String): String = NON_ALNUM.replace(title.lowercase(), " ").trim()

/**
 * Tries [sources] in order for one place and keeps going until it has [minArticles], topping up from the next
 * source instead of throwing away a thin result. The point is to stay at the nearest place to the antipode:
 * every source gets a turn there before the caller gives up and moves farther away.
 *
 * A source that fails is skipped. Only if every source fails does this throw, and then as a
 * [NewsUnavailableException] whose message says nothing about which source or why. A source that answers with
 * nothing counts as an answer: the place is simply quiet.
 */
class FallbackNewsSource(
    private val sources: List<NewsSource>,
    private val minArticles: Int = 5,
) : NewsSource {
    override suspend fun headlines(place: NewsPlace): NewsResult {
        val merged = LinkedHashMap<String, Article>()
        val failures = mutableListOf<Throwable>()
        var answered = false
        var widest = "24h"

        for (source in sources) {
            try {
                val result = source.headlines(place)
                answered = true
                // Cleaned before counting, so non-news can't make a thin place look like it has enough.
                val articles = result.articles.cleaned(place.fips)
                val before = merged.size
                for (article in articles) merged.putIfAbsent(titleKey(article.title), article)
                if (merged.size > before && result.window == "7d") widest = "7d"
                Trace.log { "${source::class.simpleName} gave ${result.articles.size} (${articles.size} after cleaning) for ${place.name}; ${merged.size} so far" }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures += e
                Trace.log { "${source::class.simpleName} failed for ${place.name}: ${e::class.simpleName}: ${e.message}" }
            }
            if (merged.size >= minArticles) break
        }

        if (!answered) throw NewsUnavailableException(place.name, failures)
        return NewsResult(merged.values.toList(), widest)
    }
}
