package tt.co.jesses.meanwhile.core

/**
 * GDELT files an outlet under the country of its domain suffix first, so a suffix that doubles as a
 * vanity or generic domain pulls in sites from elsewhere: storm.mg, a Taiwanese outlet, became
 * "Madagascar" because of .mg. For the countries below, an article is kept only if its language is one
 * that country really publishes in. Countries not listed are left alone.
 *
 * Language names are the ones GDELT's DOC API reports. An empty set means the country has no real local
 * news online, so everything filed under it is a vanity domain.
 */
private val EXPECTED_LANGUAGES_BY_ISO: Map<String, Set<String>> = mapOf(
    "MG" to setOf("Malagasy", "French", "English"), // .mg
    "TO" to setOf("Tongan", "English"), // .to
    "WS" to setOf("Samoan", "English"), // .ws, "website"
    "NU" to setOf("Niuean", "English"), // .nu, "now" in Swedish, so mostly Swedish sites
    "FM" to setOf("English"), // .fm, radio
    "TV" to emptySet(), // .tv, video and streaming sites
    "CC" to emptySet(), // .cc, Cocos (Keeling) Islands
    "LY" to setOf("Arabic", "English"), // .ly, link shorteners
    "ME" to setOf("Serbian", "Croatian", "Bosnian", "Albanian", "English"), // .me, "me" sites
    "AI" to setOf("English"), // .ai, artificial intelligence startups
    "CO" to setOf("Spanish"), // .co, used as a short ".com"
)

/** The same table keyed by the FIPS codes GDELT's `sourcecountry` uses. */
val EXPECTED_LANGUAGES: Map<String, Set<String>> =
    EXPECTED_LANGUAGES_BY_ISO.mapKeys { (iso, _) -> requireNotNull(fipsFor(iso)) { "no FIPS code for $iso" } }

/**
 * Drops articles whose language doesn't fit the country they are filed under. Articles with no known
 * language are kept, since we can't tell.
 */
fun List<Article>.withoutLanguageMisfits(
    fips: String,
    expected: Map<String, Set<String>> = EXPECTED_LANGUAGES,
): List<Article> {
    val allowed = expected[fips]?.map { it.lowercase() }?.toSet() ?: return this
    return filter { it.language.isBlank() || it.language.lowercase() in allowed }
}

/** Everything that removes articles GDELT filed under the wrong country: the blocklist and the language rule. */
fun List<Article>.withoutMisfiled(fips: String): List<Article> =
    withoutBlocked(fips).withoutLanguageMisfits(fips)
