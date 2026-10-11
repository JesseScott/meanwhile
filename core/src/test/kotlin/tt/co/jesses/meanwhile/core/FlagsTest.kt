package tt.co.jesses.meanwhile.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlagsTest {
    @Test
    fun knowsTheStripesOfSimpleFlags() {
        assertEquals(FlagStripes(FlagLayout.Horizontal, listOf(0x005BBB, 0xFFD500)), flagStripesFor("UA"))
        assertEquals(FlagStripes(FlagLayout.Horizontal, listOf(0x000000, 0xDD0000, 0xFFCE00)), flagStripesFor("DE"))
        assertEquals(FlagStripes(FlagLayout.Vertical, listOf(0x002654, 0xFFFFFF, 0xCE1126)), flagStripesFor("FR"))
    }

    @Test
    fun crossesAndDiscsHaveTheirOwnLayouts() {
        assertEquals(FlagStripes(FlagLayout.NordicCross, listOf(0xFFFFFF, 0x002F6C)), flagStripesFor("FI"))
        assertEquals(FlagStripes(FlagLayout.CenteredCross, listOf(0xFF0000, 0xFFFFFF)), flagStripesFor("CH"))
        assertEquals(FlagLayout.CenteredCross, flagStripesFor("GB")?.layout)
        assertEquals(FlagStripes(FlagLayout.Disc, listOf(0xFFFFFF, 0xBC002D)), flagStripesFor("JP"))
    }

    @Test
    fun acceptsLowerCaseAndPadding() {
        assertEquals(flagStripesFor("NZ"), flagStripesFor(" nz "))
    }

    @Test
    fun malformedOrUnknownCodesHaveNoFlag() {
        assertNull(flagStripesFor(""))
        assertNull(flagStripesFor("N"))
        assertNull(flagStripesFor("??"))
        assertNull(flagStripesFor("ZZ"))
        assertNull(flagStripesFor("none"))
    }

    @Test
    fun everyKnownCountryHasAFlag() {
        assertEquals(emptyList(), KNOWN_COUNTRIES.filter { flagStripesFor(it) == null })
    }

    @Test
    fun everyFlagHasAColourCountThatFitsItsLayout() {
        KNOWN_COUNTRIES.forEach { iso ->
            val flag = flagStripesFor(iso)!!
            val allowed = when (flag.layout) {
                FlagLayout.Horizontal, FlagLayout.Vertical -> 1..5
                FlagLayout.NordicCross, FlagLayout.CenteredCross, FlagLayout.Disc -> 2..3
                FlagLayout.Hoist -> 2..5
                FlagLayout.Canton -> 2..4
            }
            assertTrue(flag.colors.size in allowed, "$iso has ${flag.colors.size} colours for ${flag.layout}")
            assertTrue(flag.colors.all { it in 0x000000..0xFFFFFF }, "$iso has a colour that is not 0xRRGGBB")
        }
    }

    @Test
    fun blendsAndMeasuresContrast() {
        assertEquals(0x808080, blendOver(0xFFFFFF, 0.5f, 0x000000))
        assertEquals(0x123456, blendOver(0xFFFFFF, 0f, 0x123456))
        assertEquals(21.0, contrastRatio(0x000000, 0xFFFFFF), 0.01)
        assertEquals(1.0, contrastRatio(0x4C54B4, 0x4C54B4), 0.0001)
    }

    /**
     * The header's text sits on the wash, so its weakest text colour must stay readable (4.5:1) over every flag
     * colour at the wash's strongest. The surface and text colours are the theme's (ui/theme/Theme.kt); keep them
     * in step if the palette changes.
     */
    @Test
    fun headerTextStaysReadableOverEveryFlagColour() {
        val themes = listOf(
            // surface, onSurfaceVariant, strongest wash
            Triple(0xFBFCFF, 0x434653, FLAG_WASH_ALPHA_LIGHT),
            Triple(0x1A1B21, 0xC2C6D5, FLAG_WASH_ALPHA_DARK),
        )
        val colours = KNOWN_COUNTRIES.flatMap { flagStripesFor(it)!!.colors }.toSet()
        themes.forEach { (surface, text, alpha) ->
            colours.forEach { colour ->
                val ratio = contrastRatio(text, blendOver(colour, alpha, surface))
                assertTrue(ratio >= 4.5, "contrast %.2f over #%06X on surface #%06X".format(ratio, colour, surface))
            }
        }
    }
}
