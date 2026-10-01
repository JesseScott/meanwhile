package tt.co.jesses.meanwhile.core

import kotlin.test.Test
import kotlin.test.assertEquals

class BlocklistTest {
    private fun article(domain: String) = Article(url = "https://$domain/x", title = domain, domain = domain)

    @Test
    fun dropsMisfiledOutletsAndSubdomainsForThatCountryOnly() {
        val articles = listOf(article("storm.mg"), article("www.storm.mg"), article("newsmada.com"), article("notstorm.mg"))
        val blocked = mapOf("MA" to setOf("storm.mg"))

        assertEquals(listOf("newsmada.com", "notstorm.mg"), articles.withoutBlocked("MA", blocked).map { it.domain })
        assertEquals(articles, articles.withoutBlocked("TW", blocked))
    }

    @Test
    fun defaultBlocklistCoversStormMg() {
        assertEquals(emptyList(), listOf(article("storm.mg")).withoutBlocked("MA"))
    }
}
