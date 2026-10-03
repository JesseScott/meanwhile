package tt.co.jesses.meanwhile.core

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class RecencyTest {
    private val utc = ZoneId.of("UTC")
    private val now = Instant.parse("2026-10-02T10:00:00Z")

    private fun at(seen: String, title: String = seen) = Article(url = "https://x.example/$title", title = title, seenDate = seen)

    @Test
    fun groupsByCalendarDayNotElapsedHours() {
        // 23:30 last night is only 10.5 hours ago but is still "yesterday".
        assertEquals(Recency.Yesterday, at("20261001T233000Z").recency(now, utc))
        assertEquals(Recency.Today, at("20261002T000500Z").recency(now, utc))
        assertEquals(Recency.Earlier, at("20260930T120000Z").recency(now, utc))
    }

    @Test
    fun theViewersTimeZoneDecidesWhereMidnightIs() {
        val auckland = ZoneId.of("Pacific/Auckland") // UTC+13 in October
        // 10:00 UTC on 2 Oct is 23:00 on 2 Oct in Auckland; 13:00 UTC on 1 Oct is 02:00 on 2 Oct there.
        assertEquals(Recency.Today, at("20261001T130000Z").recency(now, auckland))
        assertEquals(Recency.Yesterday, at("20261001T100000Z").recency(now, auckland))
    }

    @Test
    fun anUnknownOrFutureDate() {
        assertEquals(Recency.Earlier, at("").recency(now, utc))
        assertEquals(Recency.Today, at("20261003T010000Z").recency(now, utc))
    }

    @Test
    fun groupsAreNewestFirstEmptyOnesDroppedAndOrderWithinAGroupIsKept() {
        val list = listOf(
            at("20260929T000000Z", "old"),
            at("20261002T090000Z", "today-b"),
            at("20261002T010000Z", "today-a"),
        )
        val groups = list.groupedByRecency(now, utc)
        assertEquals(listOf(Recency.Today, Recency.Earlier), groups.map { it.first })
        assertEquals(listOf("today-b", "today-a"), groups[0].second.map { it.title })
    }

    @Test
    fun noArticlesNoGroups() {
        assertEquals(emptyList(), emptyList<Article>().groupedByRecency(now, utc))
    }
}
