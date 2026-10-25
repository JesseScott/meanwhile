package tt.co.jesses.meanwhile.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ContactTest {
    @Test
    fun addressesTheContactEmailWithASubject() {
        assertEquals("mailto:js@jesses.co.tt?subject=Meanwhile", mailtoUri(subject = "Meanwhile"))
    }

    @Test
    fun encodesSpacesAndSymbolsInTheSubject() {
        assertEquals(
            "mailto:js@jesses.co.tt?subject=Wrong%20headline%20%26%20more%3D%2B%3F",
            mailtoUri(subject = "Wrong headline & more=+?"),
        )
    }

    @Test
    fun leavesOutABlankSubject() {
        assertEquals("mailto:js@jesses.co.tt", mailtoUri())
        assertEquals("mailto:js@jesses.co.tt", mailtoUri(subject = "  "))
    }
}
