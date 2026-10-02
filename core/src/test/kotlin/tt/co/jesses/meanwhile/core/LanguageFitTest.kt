package tt.co.jesses.meanwhile.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LanguageFitTest {
    private fun article(domain: String, language: String) =
        Article(url = "https://$domain/x", title = "$domain $language", domain = domain, language = language)

    @Test
    fun dropsAChineseSiteFiledUnderMadagascarButKeepsFrenchAndEnglish() {
        val articles = listOf(
            article("storm.mg", "Chinese"),
            article("newsmada.com", "French"),
            article("example.mg", "English"),
        )
        assertEquals(listOf("newsmada.com", "example.mg"), articles.withoutLanguageMisfits("MA").map { it.domain })
    }

    @Test
    fun keepsArticlesWhoseLanguageIsUnknown() {
        val articles = listOf(article("mystery.mg", ""), article("storm.mg", "Chinese"))
        assertEquals(listOf("mystery.mg"), articles.withoutLanguageMisfits("MA").map { it.domain })
    }

    @Test
    fun matchesLanguageNamesIgnoringCase() {
        assertEquals(1, listOf(article("newsmada.com", "FRENCH")).withoutLanguageMisfits("MA").size)
    }

    @Test
    fun countriesWithNoLocalNewsDropEverythingWithAKnownLanguage() {
        // .tv is Tuvalu's suffix, but every .tv site is a video or streaming site.
        val articles = listOf(article("twitch.tv", "English"), article("news.tv", "Spanish"), article("odd.tv", ""))
        assertEquals(listOf("odd.tv"), articles.withoutLanguageMisfits(fipsFor("TV")!!).map { it.domain })
    }

    @Test
    fun leavesCountriesNotInTheTableAlone() {
        val articles = listOf(article("nzherald.co.nz", "English"), article("odd.nz", "Chinese"))
        assertEquals(articles, articles.withoutLanguageMisfits("NZ"))
    }

    @Test
    fun usesTheTableGivenWhenOneIsPassed() {
        val articles = listOf(article("a.example", "German"), article("b.example", "French"))
        val custom = mapOf("XX" to setOf("German"))
        assertEquals(listOf("a.example"), articles.withoutLanguageMisfits("XX", custom).map { it.domain })
    }

    @Test
    fun combinesWithTheBlocklist() {
        val articles = listOf(
            article("storm.mg", "Chinese"), // caught by both
            article("blocked-but-french.mg", "French"), // only the blocklist would catch this one, if listed
            article("newsmada.com", "French"),
        )
        val blocked = mapOf("MA" to setOf("blocked-but-french.mg"))
        val result = articles.withoutBlocked("MA", blocked).withoutLanguageMisfits("MA")
        assertEquals(listOf("newsmada.com"), result.map { it.domain })
    }

    @Test
    fun withoutMisfiledRemovesStormMgUsingTheDefaults() {
        val articles = listOf(article("storm.mg", "Chinese"), article("newsmada.com", "French"))
        assertEquals(listOf("newsmada.com"), articles.withoutMisfiled("MA").map { it.domain })
    }

    @Test
    fun theTableIsKeyedByFipsNotIsoCodes() {
        // ISO to FIPS differs for these, so a table keyed by ISO would silently never match GDELT's codes.
        val expectedKeys = setOf("MA", "TN", "WS", "NE", "FM", "TV", "CK", "LY", "MJ", "AV", "CO")
        assertEquals(expectedKeys, EXPECTED_LANGUAGES.keys)
        assertTrue("MG" !in EXPECTED_LANGUAGES.keys, "MG is Madagascar's ISO code; GDELT uses MA")
    }
}
