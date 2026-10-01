package tt.co.jesses.meanwhile.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CountriesTest {
    @Test
    fun mapsIsoToFipsForCodesThatDiffer() {
        assertEquals("AS", fipsFor("AU"))
        assertEquals("CI", fipsFor("CL"))
        assertEquals("GM", fipsFor("DE"))
        assertEquals("UK", fipsFor("GB"))
        assertEquals("MP", fipsFor("MU"))
        assertEquals("SP", fipsFor("ES"))
    }

    @Test
    fun identicalCodesPassThrough() {
        assertEquals("NZ", fipsFor("nz"))
        assertEquals("AR", fipsFor("AR"))
        assertEquals("FJ", fipsFor("FJ"))
    }

    @Test
    fun rejectsMalformedCodes() {
        assertNull(fipsFor(""))
        assertNull(fipsFor("N"))
        assertNull(fipsFor("N1"))
    }

    @Test
    fun flagEmojiUsesRegionalIndicators() {
        assertEquals("🇳🇿", flagEmoji("NZ"))
        assertEquals("", flagEmoji("??"))
    }
}
