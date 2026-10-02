package tt.co.jesses.meanwhile

import tt.co.jesses.meanwhile.core.NewsProgress
import tt.co.jesses.meanwhile.core.capPerDomain
import tt.co.jesses.meanwhile.core.cleaned

/**
 * Folds one snapshot of headlines into the screen state. Pure, so it can be tested without a phone.
 *
 * Each snapshot is complete and replaces the last. The articles stay in source priority order, so a slow,
 * high-priority source that lands late puts its articles ahead of those already shown; the list is keyed by URL,
 * so what is on screen keeps its place.
 *
 * A snapshot with nothing left after cleaning (for example only job ads) changes nothing about what is shown, so
 * the screen keeps its loading state until something real arrives, or the caller sees the stream finish empty.
 */
internal fun UiState.withNewsProgress(
    progress: NewsProgress,
    fips: String,
    maxPerDomain: Int,
    maxArticles: Int,
): UiState {
    val articles = progress.result.articles.cleaned(fips).capPerDomain(maxPerDomain).take(maxArticles)
    val stillWorking = !progress.done
    if (articles.isEmpty()) return this
    return copy(
        status = Status.Ready,
        articles = articles,
        window = progress.result.window,
        error = null,
        isRefreshing = false,
        loadingMore = stillWorking,
        progress = if (stillWorking) Progress.CheckingMore else null,
    )
}

/** The stream is over: nothing more is coming, whatever happened. */
internal fun UiState.withNewsFinished(): UiState = copy(loadingMore = false, isRefreshing = false, progress = null)
