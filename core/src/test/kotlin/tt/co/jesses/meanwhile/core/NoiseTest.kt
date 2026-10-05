package tt.co.jesses.meanwhile.core

import kotlin.test.Test
import kotlin.test.assertEquals

class NoiseTest {
    private fun article(domain: String, title: String, via: String = "gdelt") =
        Article(url = "https://$domain/${title.hashCode()}", title = title, domain = domain, via = via)

    // From the app's cached Tonga results, 2026-10-01: the two job ads came through GDELT, the rest through Matangi's RSS.
    private val vacancies = listOf(
        "9529 Ministry of Finance - TASP vacancy procurement officer 1 - 23 October 2026",
        "9526 SPC vacancy communications 1 - 18 October 2026",
    )
    private val realNews = listOf(
        "Foreign relations: What is Tonga’s central strategic message?",
        "Pacific leaders call for Antarctic krill protection",
        "KlickEx partners with Tonga Post to expand money transfer services across Tonga",
        "Who decides who gets to ask the question?",
        "Tonga's digital security push needs authority limits for AI agents",
        "BSP introduces Saturday banking",
        "National Reserve Bank of Tonga maintains neutral stance while strengthening monetary policy transmission",
        "Tu’uma’u pē Tu’utu’uni Fakapa’anga Pāngike Pule ‘a Tonga΄, pea Hoko atu hono Fakamālohia e Ngāue",
        "BSP announces major sponsorship of 2026 Rugby League World Cup",
    )

    @Test
    fun dropsMatangiVacancyNoticesAndKeepsEveryRealStory() {
        val articles = vacancies.map { article("matangitonga.to", it) } + realNews.map { article("matangitonga.to", it, via = "rss") }

        val kept = articles.withoutNoise()

        assertEquals(realNews, kept.map { it.title })
    }

    @Test
    fun appliesToSubdomainsButOnlyToTheOutletsInTheTable() {
        val title = vacancies.first()
        assertEquals(emptyList(), listOf(article("www.matangitonga.to", title)).withoutNoise())
        // Same title from another outlet is left alone: the rule is about that outlet's habits, not the wording.
        assertEquals(1, listOf(article("example.org", title)).withoutNoise().size)
    }

    @Test
    fun doesNotDropAnOrdinaryHeadlineThatMentionsVacancies() {
        // No leading ID number, so it isn't one of the postings.
        val news = article("matangitonga.to", "Government freezes public service vacancies amid budget squeeze")
        assertEquals(listOf(news), listOf(news).withoutNoise())
    }

    @Test
    fun dropsLegalAndMeetingNoticesButNotStoriesAboutThem() {
        // Illustrative wording: the real Tonga Rugby Union title was not kept (issue #30).
        val notices = listOf(
            "Notice of Annual General Meeting - Tonga Rugby Union",
            "9531 Tonga Rugby Union - Notice of Annual General Meeting 2026",
            "NOTICE OF APPLICATION for a liquor licence",
        ).map { article("matangitonga.to", it) }
        val news = listOf(
            "Rugby union holds annual meeting after notice of changes",
            "Government gives notice of new fisheries rules",
        ).map { article("matangitonga.to", it) }
        assertEquals(news, (notices + news).withoutNoise())
    }

    @Test
    fun matchesCaseInsensitivelyAndAcceptsACustomTable() {
        val filters = mapOf("example.org" to listOf(Regex("^Sponsored:", RegexOption.IGNORE_CASE)))
        val articles = listOf(article("example.org", "SPONSORED: buy now"), article("example.org", "A real story"))
        assertEquals(listOf("A real story"), articles.withoutNoise(filters).map { it.title })
    }

    @Test
    fun cleanedAppliesTheMisfileRulesAndTheNoiseFiltersTogether() {
        val articles = listOf(
            article("storm.mg", "指數創高", via = "gdelt").copy(language = "Chinese"), // misfiled under Madagascar
            article("newsmada.com", "Une vraie histoire").copy(language = "French"),
            article("matangitonga.to", vacancies.first()),
        )
        assertEquals(listOf("Une vraie histoire"), articles.cleaned("MA").map { it.title })
    }
}
