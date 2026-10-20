package tt.co.jesses.meanwhile.core

import kotlin.test.Test
import kotlin.test.assertEquals

class FeedbackTest {
    @Test
    fun fillsInTheAppVersionAndDevice() {
        assertEquals(
            "https://tally.so/r/o964QM?app_version=0.1.11&device=Pixel%207%2C%20Android%2015",
            feedbackFormUrl("0.1.11", "Pixel 7", "15"),
        )
    }

    @Test
    fun encodesAwkwardCharacters() {
        assertEquals(
            "https://tally.so/r/o964QM?app_version=1.0%2Bbeta&device=SM-A546B%2F%26x%2C%20Android%2014",
            feedbackFormUrl("1.0+beta", "SM-A546B/&x", "14"),
        )
    }

    @Test
    fun leavesOutWhatCantBeRead() {
        assertEquals("https://tally.so/r/o964QM?device=Pixel%207", feedbackFormUrl(null, " Pixel 7 ", ""))
        assertEquals("https://tally.so/r/o964QM?app_version=0.1.11&device=Android%2015", feedbackFormUrl("0.1.11", null, "15"))
        assertEquals("https://tally.so/r/o964QM", feedbackFormUrl(null, null, null))
    }
}
