package tt.co.jesses.meanwhile.core

/**
 * Titles to drop per outlet: items that aren't news, from outlets that are otherwise fine. Keyed by domain, not by
 * country or feed, because the same outlet reaches us through GDELT and RSS, and a rule has to catch
 * all three. (Matangi Tonga's own feed has none of these; its job ads came in through GDELT.)
 *
 * This differs from the blocklist and the language rule, which remove outlets GDELT filed under the wrong country.
 */
val DEFAULT_TITLE_FILTERS: Map<String, List<Regex>> = mapOf(
    // Government and agency job ads, e.g. "9529 Ministry of Finance - TASP vacancy procurement officer 1 - 23 October 2026".
    "matangitonga.to" to listOf(Regex("""^\d{3,5}\s+.*\bvacanc(y|ies)\b""", RegexOption.IGNORE_CASE)),
)

/** Drops articles whose title matches a filter for their outlet, including the outlet's subdomains. */
fun List<Article>.withoutNoise(filters: Map<String, List<Regex>> = DEFAULT_TITLE_FILTERS): List<Article> = filter { article ->
    val patterns = filters.entries.firstOrNull { (domain, _) -> domainMatches(article.domain, domain) }?.value
    patterns == null || patterns.none { it.containsMatchIn(article.title) }
}

/** Everything that should not reach the screen: outlets filed under the wrong country, and non-news from good outlets. */
fun List<Article>.cleaned(fips: String): List<Article> = withoutMisfiled(fips).withoutNoise()
