package tt.co.jesses.meanwhile.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Dates as outlets really write them. A feed's pubDate is supposed to be RFC 822, and mostly is, but the ones the
 * app depends on also use French weekday names, ISO strings, dates with no time zone, "2026 Oct 02 04:50:45 PDT",
 * date-only strings, a 1970 placeholder for "no date", and time zones that are simply wrong (Manila time labelled
 * +0000). Wrong in one direction or another would put a headline under the wrong day, so each of these is handled
 * here, and what can't be trusted is treated as unknown rather than guessed.
 */

private val WEEKDAY_PREFIX = Regex("^[\\p{L}.]{2,10},\\s*")
private val SPACES = Regex("\\s+")

private val SPELLED_OUT: List<DateTimeFormatter> = listOf(
    DateTimeFormatter.ofPattern("yyyy MMM d HH:mm:ss z", Locale.ENGLISH), // 2026 Oct 02 04:50:45 PDT
    DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss z", Locale.ENGLISH), // RFC 822 with a zone name, weekday already dropped
)
private val DATE_ONLY: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH) // Oct 02, 2026
private val FLOATING: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH) // no zone at all

/** Anything dated before this is a placeholder ("Thu, 01 Jan 1970"), not a publication date. */
private val EARLIEST_REAL: Instant = Instant.parse("1995-01-01T00:00:00Z")

/** The instant a feed's date text means, or null if it can't be read. Ignores the clock; see [seenDateFor]. */
fun parseFeedDate(text: String): Instant? {
    val raw = text.trim().replace(SPACES, " ")
    if (raw.isEmpty()) return null

    // A weekday name in the wrong language (dim, 04 Oct 2026) is the one thing RFC 1123 parsing won't forgive, and
    // it adds nothing since the date is already there.
    val noWeekday = WEEKDAY_PREFIX.replace(raw, "")

    return runCatching { ZonedDateTime.parse(noWeekday, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
        ?: runCatching { Instant.parse(raw) }.getOrNull()
        ?: SPELLED_OUT.firstNotNullOfOrNull { f -> runCatching { ZonedDateTime.parse(noWeekday, f).toInstant() }.getOrNull() }
        ?: runCatching { LocalDateTime.parse(raw, FLOATING).toInstant(ZoneOffset.UTC) }.getOrNull()
        ?: runCatching { LocalDate.parse(raw, DATE_ONLY).atStartOfDay().toInstant(ZoneOffset.UTC) }.getOrNull()
}

private val SEEN_OUT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

/**
 * The article's date in the form the rest of the app uses ("20261002T045045Z"), or "" for unknown.
 *
 * Placeholder dates before 1995 are unknown. A date more than a few minutes in the future can only be a wrong time
 * zone, so it is pulled back to [now] instead of showing a headline as coming from tomorrow.
 */
fun seenDateFor(text: String, now: Instant): String {
    val parsed = parseFeedDate(text) ?: return ""
    if (parsed.isBefore(EARLIEST_REAL)) return ""
    val clamped = if (parsed.isAfter(now.plusSeconds(5 * 60))) now else parsed
    return SEEN_OUT.format(clamped)
}
