package tt.co.jesses.meanwhile

import tt.co.jesses.meanwhile.core.Article
import tt.co.jesses.meanwhile.core.NewsProgress
import tt.co.jesses.meanwhile.core.NewsResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NewsReducerTest {
    private fun article(n: Int, domain: String = "example$n.com") =
        Article(title = "A real headline number $n", url = "https://$domain/$n", domain = domain)

    private fun snapshot(vararg articles: Article, pending: Int, window: String = "24h") =
        NewsProgress(NewsResult(articles.toList(), window), pending)

    private fun UiState.fold(progress: NewsProgress) = withNewsProgress(progress, fips = "XX", maxPerDomain = 8, maxArticles = 100)

    @Test
    fun `first snapshot makes the screen ready while others are pending`() {
        val next = UiState(status = Status.Loading, progress = Progress.FetchingHeadlines("Tonga"))
            .fold(snapshot(article(1), pending = 2))
        assertEquals(Status.Ready, next.status)
        assertEquals(1, next.articles.size)
        assertTrue(next.loadingMore)
        assertEquals(Progress.CheckingMore, next.progress)
    }

    @Test
    fun `final snapshot stops the loading indicator`() {
        val next = UiState().fold(snapshot(article(1), article(2), pending = 0))
        assertFalse(next.loadingMore)
        assertNull(next.progress)
    }

    @Test
    fun `a late snapshot replaces the list and keeps urls so rows keep their place`() {
        val first = UiState().fold(snapshot(article(2), pending = 1))
        val second = first.fold(snapshot(article(1), article(2), pending = 0))
        assertEquals(listOf(article(1).url, article(2).url), second.articles.map { it.url })
    }

    @Test
    fun `an empty snapshot changes nothing`() {
        val loading = UiState(status = Status.Loading, progress = Progress.FetchingHeadlines("Tonga"))
        assertSame(loading, loading.fold(snapshot(pending = 1)))
    }

    @Test
    fun `a refresh keeps its old content and stops spinning on the first snapshot`() {
        val ready = UiState(status = Status.Ready, articles = listOf(article(9)), isRefreshing = true)
        val next = ready.fold(snapshot(article(1), pending = 1))
        assertFalse(next.isRefreshing)
        assertEquals(listOf(article(1).url), next.articles.map { it.url })
    }

    @Test
    fun `finishing clears every loading flag`() {
        val done = UiState(status = Status.Ready, loadingMore = true, isRefreshing = true, progress = Progress.CheckingMore).withNewsFinished()
        assertFalse(done.loadingMore)
        assertFalse(done.isRefreshing)
        assertNull(done.progress)
    }
}
