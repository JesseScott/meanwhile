package tt.co.jesses.meanwhile.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RssTest {
    // Trimmed from the live RNZ Pacific feed, 2026-10-01.
    private val rnz = """
        <?xml version="1.0" encoding="utf-8"?>
        <rss version="2.0"><channel><title>RNZ Pacific</title>
        <item>
          <title>Fijian PM Rabuka confident of numbers as parliament debates crucial constitution bill</title>
          <description><![CDATA[Sitiveni Rabuka says he is confident of securing the votes, as parliament debates changes to Fiji's constitution.]]></description>
          <pubDate>Fri, 02 Oct 2026 14:44:22 +1300</pubDate>
          <link>https://www.rnz.co.nz/news/pacific/1682784/fijian-pm-rabuka-confident-of-numbers</link>
          <guid isPermaLink="false">d009d70dfee792ae6190e99ae952ba4192230a75</guid>
        </item>
        <item>
          <title>Tonga marks the anniversary of the national cyclone drill</title>
          <description>The kingdom ran its drill on Thursday.</description>
          <pubDate>Fri, 02 Oct 2026 09:00:00 +1300</pubDate>
          <link>https://www.rnz.co.nz/news/pacific/1682700/tonga-cyclone-drill</link>
        </item>
        </channel></rss>
    """.trimIndent()

    // Trimmed from a live Google News RSS search, 2026-10-01: outlet in <source>, and " - Outlet" on the title.
    private val google = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/"><channel><title>"Tonga when:7d" - Google News</title>
        <item><title>Randy Orton looks to take out Tama Tonga and The Bloodline King of the Ring Semifinals - WWE</title>
        <link>https://news.google.com/rss/articles/CBMiswFBVV95cUxQ?oc=5</link>
        <guid isPermaLink="false">CBMiswFBVV95cUxQ</guid><pubDate>Sun, 27 Sep 2026 07:00:00 GMT</pubDate>
        <description>&lt;a href="https://news.google.com/rss/articles/CBMiswFBVV95cUxQ?oc=5" target="_blank"&gt;Randy Orton&lt;/a&gt;&amp;nbsp;&amp;nbsp;&lt;font color="#6f6f6f"&gt;WWE&lt;/font&gt;</description>
        <source url="https://www.wwe.com">WWE</source></item>
        </channel></rss>
    """.trimIndent()

    @Test
    fun parsesAnRss20Feed() {
        val articles = parseFeed(rnz, "rss")
        assertEquals(2, articles.size)

        val first = articles[0]
        assertEquals("Fijian PM Rabuka confident of numbers as parliament debates crucial constitution bill", first.title)
        assertEquals("https://www.rnz.co.nz/news/pacific/1682784/fijian-pm-rabuka-confident-of-numbers", first.url)
        assertEquals("rnz.co.nz", first.domain)
        assertEquals("20261002T014422Z", first.seenDate) // 14:44 at +13:00 is 01:44 UTC
        assertEquals("rss", first.via)
    }

    @Test
    fun readsTheOutletAndStripsTheOutletSuffixFromGoogleTitles() {
        val article = parseFeed(google, "gnews").single()
        assertEquals("Randy Orton looks to take out Tama Tonga and The Bloodline King of the Ring Semifinals", article.title)
        assertEquals("wwe.com", article.domain)
        assertEquals("20260927T070000Z", article.seenDate)
        assertTrue(article.url.startsWith("https://news.google.com/rss/articles/"))
    }

    @Test
    fun keepCanDropItemsUsingTitleAndSummary() {
        val mentionsTonga = parseFeed(rnz, "rss") { title, summary ->
            title.contains("Tonga", ignoreCase = true) || summary.contains("Tonga", ignoreCase = true)
        }
        assertEquals(listOf("Tonga marks the anniversary of the national cyclone drill"), mentionsTonga.map { it.title })
    }

    @Test
    fun parsesAnAtomFeed() {
        // Synthetic: no real Atom feed was needed yet, so this one is written to the Atom spec.
        val atom = """
            <feed xmlns="http://www.w3.org/2005/Atom"><title>Example</title>
            <entry><title>Harbour reopens</title>
              <link rel="self" href="https://x.example/self"/><link rel="alternate" href="https://www.x.example/harbour"/>
              <updated>2026-10-01T05:00:00Z</updated><summary>The harbour reopened on Tuesday.</summary></entry>
            </feed>
        """.trimIndent()
        val article = parseFeed(atom, "rss").single()
        assertEquals("Harbour reopens", article.title)
        assertEquals("https://www.x.example/harbour", article.url)
        assertEquals("x.example", article.domain)
        assertEquals("20261001T050000Z", article.seenDate)
    }

    @Test
    fun decodesEntitiesThatFeedsEscapeTwice() {
        val feed = """<rss><channel><item><title>Tonga&amp;#8217;s strategy &amp;amp; more</title><link>https://a.example/1</link></item></channel></rss>"""
        assertEquals("Tonga’s strategy & more", parseFeed(feed, "rss").single().title)
    }

    @Test
    fun refusesDocumentsThatDeclareEntities() {
        // A hostile feed trying to read a local file through an external entity must yield nothing, not the file.
        val hostile = """
            <?xml version="1.0"?>
            <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <rss><channel><item><title>&xxe;</title><link>https://a.example/1</link></item></channel></rss>
        """.trimIndent()
        assertEquals(emptyList(), parseFeed(hostile, "rss"))
    }

    @Test
    fun skipsItemsWithoutATitleOrLinkAndToleratesGarbage() {
        val partial = """<rss><channel><item><title>No link</title></item><item><link>https://a.example/2</link></item></channel></rss>"""
        assertEquals(emptyList(), parseFeed(partial, "rss"))
        assertEquals(emptyList(), parseFeed("not xml at all", "rss"))
        assertEquals(emptyList(), parseFeed("", "rss"))
    }

    @Test
    fun leavesTheDateEmptyWhenItCannotBeRead() {
        val feed = """<rss><channel><item><title>T</title><link>https://a.example/1</link><pubDate>last Tuesday</pubDate></item></channel></rss>"""
        assertEquals("", parseFeed(feed, "rss").single().seenDate)
    }

    @Test
    fun unescapesNumericAndNamedEntities() {
        assertEquals("a & b – c é", unescapeHtml("a &amp; b &ndash; c &#233;"))
        assertEquals("é", unescapeHtml("&#xE9;"))
        assertEquals("&unknown;", unescapeHtml("&unknown;"))
    }
}
