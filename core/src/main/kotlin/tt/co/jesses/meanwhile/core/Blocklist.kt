package tt.co.jesses.meanwhile.core

/**
 * Outlets GDELT files under the wrong country, keyed by FIPS code. GDELT appears to infer an
 * outlet's country partly from its domain suffix, so vanity TLDs misfile: storm.mg is a Taiwanese
 * site that lands under Madagascar (.mg). Add entries as they turn up in a feed.
 */
val DEFAULT_BLOCKED_DOMAINS: Map<String, Set<String>> = mapOf(
    "MA" to setOf("storm.mg"),
)

/** Drops articles from outlets that are blocked for [fips], matching the domain or any subdomain of it. */
fun List<Article>.withoutBlocked(
    fips: String,
    blocked: Map<String, Set<String>> = DEFAULT_BLOCKED_DOMAINS,
): List<Article> {
    val domains = blocked[fips].orEmpty()
    if (domains.isEmpty()) return this
    return filterNot { article -> domains.any { domainMatches(article.domain, it) } }
}

/** True if [host] is [domain] or a subdomain of it. */
internal fun domainMatches(host: String, domain: String): Boolean {
    val h = host.lowercase()
    return h == domain || h.endsWith(".$domain")
}
