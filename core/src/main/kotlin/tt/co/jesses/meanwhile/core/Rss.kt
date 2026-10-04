package tt.co.jesses.meanwhile.core

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URI
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.coroutines.cancellation.CancellationException

/** Sent to the sites we fetch feeds from, so they can see what is asking. */
const val NEWS_USER_AGENT = "Meanwhile/0.1 (hobby app; +https://jesses.co.tt)"

private val SEEN_OUT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

/** A DOM builder that refuses DTDs and external entities, so a hostile feed can't pull in files or make requests. */
private fun secureBuilder(): DocumentBuilder {
    val factory = DocumentBuilderFactory.newInstance()
    // Not every XML parser (Android's included) knows every feature, so each is best effort.
    runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
    runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    runCatching { factory.isXIncludeAware = false }
    runCatching { factory.setExpandEntityReferences(false) }
    return factory.newDocumentBuilder()
}

private fun NodeList.elements(): List<Element> =
    (0 until length).mapNotNull { item(it) as? Element }

private fun Element.child(tag: String): Element? =
    (0 until childNodes.length).mapNotNull { childNodes.item(it) as? Element }.firstOrNull { it.tagName == tag }

private fun Element.childText(vararg tags: String): String =
    tags.firstNotNullOfOrNull { child(it)?.textContent?.trim()?.takeIf(String::isNotEmpty) }.orEmpty()

private val TAGS = Regex("<[^>]*>")
private val SPACES = Regex("\\s+")
private val ENTITY = Regex("&(#[xX]?[0-9a-fA-F]+|[a-zA-Z]+);")
private val NAMED_ENTITIES = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
    "ndash" to "–", "mdash" to "—", "hellip" to "…", "rsquo" to "’", "lsquo" to "‘",
    "rdquo" to "”", "ldquo" to "“",
)

/** Decodes the HTML entities that survive XML parsing in feeds that escape their markup twice. */
internal fun unescapeHtml(text: String): String {
    if ('&' !in text) return text
    return ENTITY.replace(text) { match ->
        val entity = match.groupValues[1]
        val decoded = when {
            entity.startsWith("#x") || entity.startsWith("#X") -> entity.drop(2).toIntOrNull(16)
            entity.startsWith("#") -> entity.drop(1).toIntOrNull()
            else -> null
        }?.let { runCatching { String(Character.toChars(it)) }.getOrNull() } ?: NAMED_ENTITIES[entity]
        decoded ?: match.value
    }
}

private fun cleanText(raw: String): String = unescapeHtml(TAGS.replace(unescapeHtml(raw), " ")).replace(SPACES, " ").trim()

private fun hostOf(url: String): String =
    runCatching { URI(url.trim()).host }.getOrNull()?.removePrefix("www.")?.lowercase().orEmpty()

private fun toSeenDate(text: String): String {
    if (text.isBlank()) return ""
    val instant = runCatching { ZonedDateTime.parse(text.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(text.trim()).toInstant() }.getOrNull()
        ?: return ""
    return SEEN_OUT.format(instant)
}

/**
 * Parses an RSS 2.0 or Atom feed into articles, newest order as published. [keep] sees each item's title and a
 * tag-free summary, and can drop it. Anything unparseable gives an empty list, which means "no articles".
 */
fun parseFeed(
    xml: String,
    via: String,
    keep: (title: String, summary: String) -> Boolean = { _, _ -> true },
): List<Article> {
    val doc = try {
        secureBuilder().parse(InputSource(StringReader(xml.trim())))
    } catch (e: Exception) {
        return emptyList()
    }
    val items = doc.getElementsByTagName("item").elements() + doc.getElementsByTagName("entry").elements()
    return items.mapNotNull { item ->
        val source = item.child("source")
        val sourceName = source?.textContent?.trim().orEmpty()
        var title = cleanText(item.childText("title"))
        // Google News appends " - Outlet" to every title; the outlet is already carried separately.
        if (sourceName.isNotEmpty() && title.endsWith(" - $sourceName")) title = title.removeSuffix(" - $sourceName").trim()

        val link = item.childText("link").ifEmpty {
            // Atom puts the address in an attribute.
            item.childNodes.let { nodes ->
                (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
                    .filter { it.tagName == "link" }
                    .sortedBy { if (it.getAttribute("rel").let { rel -> rel.isEmpty() || rel == "alternate" }) 0 else 1 }
                    .firstNotNullOfOrNull { it.getAttribute("href").takeIf(String::isNotEmpty) }
            }.orEmpty()
        }
        if (title.isEmpty() || link.isEmpty()) return@mapNotNull null

        val summary = cleanText(item.childText("description", "summary", "content", "content:encoded"))
        if (!keep(title, summary)) return@mapNotNull null

        val domain = source?.getAttribute("url")?.takeIf(String::isNotEmpty)?.let(::hostOf)?.takeIf(String::isNotEmpty) ?: hostOf(link)
        Article(
            url = link,
            title = title,
            seenDate = toSeenDate(item.childText("pubDate", "published", "updated", "dc:date")),
            domain = domain,
            via = via,
        )
    }
}

/** A feed to read for a country. [requirePlaceName] is for regional feeds: keep only items that mention the place. */
data class RssFeed(val url: String, val requirePlaceName: Boolean = false)

private val RNZ_PACIFIC = RssFeed("https://www.rnz.co.nz/rss/pacific.xml", requirePlaceName = true)

/**
 * Outlet feeds worth reading when GDELT has little, keyed by FIPS code. Each URL was fetched and parsed (the Pacific
 * ones on 2026-10-01, Madagascar's on 2026-10-04). Many local outlets block plain feed requests or don't publish
 * one (the Fiji Times, the Solomon Star and the Samoa Observer didn't work), so this grows by checking, not guessing.
 *
 * Madagascar is here because it is the antipode of the west coast of North America, GDELT's answer for it is often
 * empty or rate limited, and what it does have is mostly one misfiled Taiwanese site.
 */
val CURATED_FEEDS: Map<String, List<RssFeed>> = buildMap {
    put("TN", listOf(RssFeed("https://matangitonga.to/rss.xml"), RNZ_PACIFIC)) // Tonga: its own outlet, plus RNZ
    put(
        "MA",
        listOf(
            RssFeed("https://2424.mg/feed/"), // French, daily
            RssFeed("https://newsmada.com/feed/"), // Malagasy and French
            RssFeed("https://www.rfi.fr/fr/tag/madagascar/rss"), // French, the Madagascar topic feed
            RssFeed("https://allafrica.com/tools/headlines/rdf/madagascar/headlines.rdf"), // English
        ),
    )
    // RNZ Pacific covers the whole region, so it is filtered to items that name the country.
    for (fips in listOf("WS", "FJ", "NH", "BP", "KR", "TV", "NR", "CW", "NE", "FM", "RM", "PS", "PP", "NC", "FP", "AQ", "GQ", "CQ", "TL", "WF")) {
        put(fips, listOf(RNZ_PACIFIC))
    }
}

/** Reads a hand-picked list of outlet RSS feeds. A country with no feeds simply has no articles from here. */
class RssNewsSource(
    private val client: HttpClient,
    private val feeds: Map<String, List<RssFeed>> = CURATED_FEEDS,
    private val maxArticles: Int = 50,
) : NewsSource {
    override suspend fun headlines(place: NewsPlace): NewsResult {
        val list = feeds[place.fips].orEmpty()
        if (list.isEmpty()) return NewsResult(emptyList(), WINDOW)

        val failures = mutableListOf<Throwable>()
        val articles = list.flatMap { feed ->
            try {
                fetch(feed, place)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Trace.log { "RSS ${feed.url} failed: ${e::class.simpleName}: ${e.message}" }
                failures += e
                emptyList()
            }
        }
        if (failures.size == list.size) throw failures.last()

        val newest = articles.distinctBy { it.url }.sortedByDescending { it.seenDate }.take(maxArticles)
        return NewsResult(newest, WINDOW)
    }

    private suspend fun fetch(feed: RssFeed, place: NewsPlace): List<Article> {
        val response = client.get(feed.url) { header(HttpHeaders.UserAgent, NEWS_USER_AGENT) }
        if (response.status != HttpStatusCode.OK) throw SourceFailedException("RSS", "HTTP ${response.status.value}")
        val keep: (String, String) -> Boolean =
            if (feed.requirePlaceName) { title, summary ->
                title.contains(place.name, ignoreCase = true) || summary.contains(place.name, ignoreCase = true)
            } else { _, _ -> true }
        return parseFeed(response.bodyAsText(), VIA, keep)
    }

    private companion object {
        const val VIA = "rss"
        const val WINDOW = "7d"
    }
}
