package tt.co.jesses.meanwhile.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test

/**
 * Runs candidate feeds through the app's own [RssNewsSource] with the app's HTTP engine (OkHttp), over the real
 * network. Does nothing unless -DliveFeeds=<csv> is passed. The CSV is tools/coverage-survey/out/feeds_verified.csv
 * (columns country, fips, outlet, url, ...). Writes the results next to it as feeds_app_check.csv.
 *
 * It exists because a site can answer curl and refuse OkHttp (or the other way round), and because only the app's
 * parser can say whether an odd date format or encoding comes out right.
 */
class LiveFeedsCheck {
    @Test
    fun checkEveryCandidateFeed() {
        val path = System.getProperty("liveFeeds") ?: return
        val input = File(path)
        val lines = input.readLines()
        val header = parseCsvLine(lines.first())
        val col = header.withIndex().associate { (i, name) -> name to i }
        val out = StringBuilder("country,fips,url,result,articles,newest_seen,oldest_seen,future_dated,first_title,error")
        out.append('\n')
        HttpClient(OkHttp).use { client ->
            for (line in lines.drop(1).filter { it.isNotBlank() }) {
                val cells = parseCsvLine(line)
                val fips = cells[col.getValue("fips")]
                val country = cells[col.getValue("country")]
                val url = cells[col.getValue("url")]
                val source = RssNewsSource(client, feeds = mapOf(fips to listOf(RssFeed(url))), maxArticles = 500)
                val place = NewsPlace("XX", fips, country)
                val now = System.currentTimeMillis()
                val row = try {
                    val result = runBlocking { source.headlines(place) }
                    val seen = result.articles.mapNotNull { it.seenInstant() }.sorted()
                    val future = seen.count { it.toEpochMilli() > now + 5 * 60_000 }
                    listOf(
                        "OK", result.articles.size.toString(), seen.lastOrNull()?.toString().orEmpty(),
                        seen.firstOrNull()?.toString().orEmpty(), future.toString(), result.articles.firstOrNull()?.title.orEmpty(), "",
                    )
                } catch (e: Exception) {
                    listOf("FAIL", "0", "", "", "0", "", "${e::class.simpleName}: ${e.message}".take(80))
                }
                println("LIVE ${row[0]} ${country.take(14)} ${row[1]} articles future=${row[4]} $url ${row[6]}")
                out.append((listOf(country, fips, url) + row).joinToString(",") { csv(it) }).append('\n')
                Thread.sleep(1_000)
            }
        }
        File(input.parentFile, "feeds_app_check.csv").writeText(out.toString())
    }

    private fun csv(value: String) = "\"" + value.replace("\"", "\"\"").replace('\n', ' ') + "\""

    private fun parseCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    sb.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> {
                    cells += sb.toString()
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        cells += sb.toString()
        return cells
    }
}
