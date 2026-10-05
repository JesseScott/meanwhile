package tt.co.jesses.meanwhile.core

private val RNZ_PACIFIC = RssFeed("https://www.rnz.co.nz/rss/pacific.xml", requirePlaceName = true)

/**
 * Outlet feeds read alongside GDELT, keyed by FIPS code, as insurance for the places people actually land on:
 * GDELT often answers nothing at all (rate limited, or an empty page), and for small places what it has is thin.
 *
 * Each feed was fetched with the app's own User-Agent and run through the app's own parser with its own HTTP
 * client (tools/coverage-survey, LiveFeedsCheck). Picked, in order, for: English where an outlet has an English
 * edition, then fresh (a newest item within a day), then small (the app fetches every feed for a country on each
 * load, so nothing over about 300 KB: El Comercio's Peru feed is 1.5 MB) and https only. Madagascar, Tonga and the
 * Pacific were checked earlier (2026-10-01 and -04); everything else on 2026-10-04.
 *
 * Many outlets block plain feed requests or don't publish one (the Fiji Times, Fijivillage, Xinhua, China Daily,
 * Radio1 Tahiti, Midi Madagasikara, ...), so this grows by checking, not guessing. Where the page says that a place
 * is covered by a language other than English, that is what the titles will be in: the app shows them as published.
 */
val CURATED_FEEDS: Map<String, List<RssFeed>> = buildMap {
    // Madagascar: the antipode of the west coast of North America, with a thin and mostly misfiled GDELT result.
    put(
        "MA",
        listOf(
            RssFeed("https://2424.mg/feed/"), // French, daily
            RssFeed("https://newsmada.com/feed/"), // Malagasy and French
            RssFeed("https://www.rfi.fr/fr/tag/madagascar/rss"), // French, the Madagascar topic feed
            RssFeed("https://allafrica.com/tools/headlines/rdf/madagascar/headlines.rdf"), // English
        ),
    )

    // Oceania: the antipode of most of western Europe, and of eastern North America
    put("NZ", listOf(RssFeed("https://www.rnz.co.nz/rss/national.xml"), RssFeed("https://www.stuff.co.nz/rss"))) // English
    put("AS", listOf(RssFeed("https://www.abc.net.au/news/feed/51120/rss.xml"), RssFeed("https://www.smh.com.au/rss/feed.xml"))) // English
    put("TN", listOf(RssFeed("https://matangitonga.to/rss.xml"), RNZ_PACIFIC)) // Tonga: its own outlet, plus RNZ
    put("FJ", listOf(RssFeed("https://www.fbcnews.com.fj/feed/"), RNZ_PACIFIC)) // English; the Fiji Times blocks feeds
    put("FP", listOf(RssFeed("https://www.tahiti-infos.com/xml/syndication.rss"), RNZ_PACIFIC)) // French
    // RNZ Pacific covers the whole region, so it is filtered to items that name the country.
    for (fips in listOf("WS", "NH", "BP", "KR", "TV", "NR", "CW", "NE", "FM", "RM", "PS", "PP", "NC", "AQ", "GQ", "CQ", "TL", "WF")) {
        put(fips, listOf(RNZ_PACIFIC))
    }

    // South America: the antipode of much of South and East Asia, and of Indonesia and China's neighbours
    put("PE", listOf(RssFeed("https://andina.pe/ingles/rss.aspx"), RssFeed("https://andina.pe/agencia/rss/rss.aspx"))) // English, Spanish (state agency)
    put(
        "AR",
        listOf(
            RssFeed("https://www.batimes.com.ar/feed"), // English (Buenos Aires Times)
            RssFeed("https://www.clarin.com/rss/lo-ultimo/"), // Spanish
            RssFeed("https://www.ambito.com/rss/pages/home.xml"), // Spanish
        ),
    )
    put(
        "BR",
        listOf(
            RssFeed("https://agenciabrasil.ebc.com.br/en/rss/ultimasnoticias/feed.xml"), // English (state agency)
            RssFeed("https://agenciabrasil.ebc.com.br/rss/ultimasnoticias/feed.xml"), // Portuguese
            RssFeed("https://feeds.folha.uol.com.br/emcimadahora/rss091.xml"), // Portuguese, ISO-8859-1
        ),
    )
    put("CI", listOf(RssFeed("https://www.cooperativa.cl/noticias/site/tax/port/all/rss____1.xml"), RssFeed("https://www.theclinic.cl/feed/"))) // Spanish
    put("CO", listOf(RssFeed("https://www.eltiempo.com/rss/colombia.xml"), RssFeed("https://www.elcolombiano.com/rss/colombia.xml"))) // Spanish
    put(
        "UY",
        listOf(
            RssFeed("https://www.montevideo.com.uy/anxml.aspx?58"),
            RssFeed("https://www.elobservador.com.uy/rss/pages/home.xml"),
            RssFeed("https://www.elpais.com.uy/rss"),
        ),
    ) // Spanish
    put("EC", listOf(RssFeed("https://www.elcomercio.com/feed/"), RssFeed("https://www.eluniverso.com/arc/outboundfeeds/rss/?outputType=xml"))) // Spanish

    // Asia: the antipode of much of South America
    put("CH", listOf(RssFeed("https://www.cgtn.com/subscribe/rss/section/china.xml"), RssFeed("https://www.sixthtone.com/rss"))) // English; China Daily and Xinhua feeds are dead
    put("JA", listOf(RssFeed("https://www.japantimes.co.jp/feed/"), RssFeed("https://japantoday.com/feed"))) // English
    put("ID", listOf(RssFeed("https://rss.thejakartapost.com/home"), RssFeed("https://en.antaranews.com/rss/news.xml"))) // English
    put(
        "CB",
        listOf(
            RssFeed("https://www.khmertimeskh.com/category/national/feed/"), // English (the main feed mixes in Khmer)
            RssFeed("https://www.phnompenhpost.com/rss"), // English
            RssFeed("https://cambojanews.com/feed/"), // English
        ),
    )
    put(
        "RP",
        listOf(
            RssFeed("https://www.philstar.com/rss/headlines"),
            RssFeed("https://data.gmanetwork.com/gno/rss/news/feed.xml"),
            RssFeed("https://newsinfo.inquirer.net/feed"), // dates run hours ahead; the parser pulls them back to now
        ),
    ) // English

    // Africa and the Indian Ocean
    put("MP", listOf(RssFeed("https://www.newsmoris.com/feed/"), RssFeed("https://defimedia.info/rss.xml"))) // Mauritius: English, French
    put("RE", listOf(RssFeed("https://www.zinfos974.com/xml/syndication.rss"), RssFeed("https://www.imazpress.com/feed"))) // Réunion: French
    put("MO", listOf(RssFeed("https://en.hespress.com/feed"), RssFeed("https://fr.hespress.com/feed"))) // Morocco: English, French
    put("SF", listOf(RssFeed("https://www.dailymaverick.co.za/dmrss/"))) // English; News24, TimesLive and others refuse
    put("BC", listOf(RssFeed("https://www.thegazette.news/feed/"), RssFeed("https://sundaystandard.info/feed/"))) // Botswana: English, thin

    // The big countries GDELT usually covers well, as a fallback for the days it doesn't
    put("US", listOf(RssFeed("https://feeds.npr.org/1001/rss.xml"), RssFeed("https://rss.nytimes.com/services/xml/rss/nyt/HomePage.xml")))
    put("CA", listOf(RssFeed("https://globalnews.ca/feed/"), RssFeed("https://www.theglobeandmail.com/arc/outboundfeeds/rss/category/canada/")))
    put("SP", listOf(RssFeed("https://e00-elmundo.uecdn.es/elmundo/rss/portada.xml"))) // Spanish; El País is 600 KB
}
