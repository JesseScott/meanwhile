package tt.co.jesses.meanwhile.core

import java.time.Instant
import java.time.ZoneId

/** How recent an article is, for headings in the list. */
enum class Recency { Today, Yesterday, Earlier }

/** By calendar day in [zone], not by hours elapsed, so a headline from 11 pm last night reads as "Yesterday". An unknown date is [Recency.Earlier]. */
fun Article.recency(now: Instant, zone: ZoneId): Recency {
    val seen = seenInstant() ?: return Recency.Earlier
    val days = seen.atZone(zone).toLocalDate().until(now.atZone(zone).toLocalDate()).days
    return when {
        days <= 0 -> Recency.Today // also covers a clock a little ahead of the publisher's
        days == 1 -> Recency.Yesterday
        else -> Recency.Earlier
    }
}

/**
 * Splits the list into recency groups, newest group first, leaving out empty ones. Within a group the order is
 * unchanged, so the sources' own priority (headlines published in the place come first) still counts.
 */
fun List<Article>.groupedByRecency(now: Instant, zone: ZoneId): List<Pair<Recency, List<Article>>> {
    val byGroup = groupBy { it.recency(now, zone) }
    return Recency.entries.mapNotNull { r -> byGroup[r]?.let { r to it } }
}
