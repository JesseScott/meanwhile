package tt.co.jesses.meanwhile.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Every case here is a date string a real feed was serving on 2026-10-04. */
class FeedDatesTest {
    private val now = Instant.parse("2026-10-05T00:10:00Z")

    @Test
    fun anEnglishRfc822DateIsRead() {
        assertEquals(Instant.parse("2026-10-04T20:28:00Z"), parseFeedDate("Sun, 04 Oct 2026 20:28:00 +0000"))
    }

    @Test
    fun aFrenchWeekdayNameDoesNotStopTheDateBeingRead() {
        // Défi Media, Mauritius: "dim" is Sunday.
        assertEquals(Instant.parse("2026-10-04T16:00:00Z"), parseFeedDate("dim, 04 Oct 2026 20:00:00 +0400"))
    }

    @Test
    fun isoDatesWithAndWithoutAnOffsetAreRead() {
        assertEquals(Instant.parse("2026-10-04T08:00:00Z"), parseFeedDate("2026-10-04T10:00:00+02:00"))
        assertEquals(Instant.parse("2026-10-04T08:00:00Z"), parseFeedDate("2026-10-04T08:00:00Z"))
    }

    @Test
    fun aDateWithNoTimeZoneIsTakenAsUtc() {
        // ECNS writes "2026-10-04 16:02:36" with no zone.
        assertEquals(Instant.parse("2026-10-04T16:02:36Z"), parseFeedDate("2026-10-04 16:02:36"))
    }

    @Test
    fun sixthToneStyleDatesAreRead() {
        assertEquals(Instant.parse("2026-10-02T11:50:45Z"), parseFeedDate("2026 Oct 02   04:50:45 PDT"))
        assertEquals(Instant.parse("2026-10-02T00:00:00Z"), parseFeedDate("Oct 02, 2026"))
    }

    @Test
    fun nonsenseIsUnknown() {
        assertNull(parseFeedDate(""))
        assertNull(parseFeedDate("yesterday-ish"))
    }

    @Test
    fun theSeenFormIsTheOneTheRestOfTheAppUses() {
        assertEquals("20261004T202800Z", seenDateFor("Sun, 04 Oct 2026 20:28:00 +0000", now))
    }

    @Test
    fun a1970PlaceholderIsUnknownNotTheOldestArticleThereEverWas() {
        // Imaz Press, Réunion, fills undated items with "Thu, 01 Jan 1970 04:00:00 +0400".
        assertEquals("", seenDateFor("Thu, 01 Jan 1970 04:00:00 +0400", now))
    }

    @Test
    fun aDateInTheFutureIsPulledBackToNow() {
        // Inquirer writes Manila time with a +0000 label, so items arrive about eight hours ahead of the clock.
        assertEquals("20261005T001000Z", seenDateFor("Mon, 05 Oct 2026 08:08:06 +0000", now))
    }

    @Test
    fun aFewMinutesAheadIsClockSkewAndLeftAlone() {
        assertEquals("20261005T001300Z", seenDateFor("Mon, 05 Oct 2026 00:13:00 +0000", now))
    }
}
