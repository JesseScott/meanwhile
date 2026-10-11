package tt.co.jesses.meanwhile.core

import kotlin.math.pow

/*
 * Flags as a few bands of colour, for the soft wash behind the header. This is not artwork: emblems, stars and
 * fine stripes are left out, because the wash is blurred and faded until only the colours and their rough
 * arrangement are left. Colours are 0xRRGGBB.
 *
 * Every colour is taken from the flag's image on flagcdn.com, not from memory, and the arrangements were compared
 * with those images side by side. After changing an entry, run tools/flag-colours/check.py: it reports any colour
 * that is not in the country's reference flag.
 */

/** How a flag's colours are arranged, and so what [FlagStripes.colors] means. */
enum class FlagLayout {
    /** One to five equal bands from top to bottom. A wider band repeats its colour (Spain is red, yellow, yellow, red). */
    Horizontal,

    /** One to five equal bands from the hoist (the pole side) to the fly. */
    Vertical,

    /** Field, cross, and an optional inner cross; the upright sits towards the hoist (Finland, Norway). */
    NordicCross,

    /** Field, cross, and an optional inner cross; the upright sits in the middle (Switzerland, the UK). */
    CenteredCross,

    /** Field, disc, and an optional inner disc (Japan, Brazil). Also stands in for a central emblem. */
    Disc,

    /** A colour at the hoist, a triangle or a bar, followed by one to four horizontal bands (Czechia, Jordan). */
    Hoist,

    /** A colour in the top hoist corner, followed by one to three horizontal bands (Chile, the United States). */
    Canton,
}

data class FlagStripes(val layout: FlagLayout, val colors: List<Int>)

private const val WHITE = 0xFFFFFF
private const val BLACK = 0x000000

/** The blue of the British ensigns, which many territories fly with a badge the wash would not show. */
private const val ENSIGN_BLUE = 0x012169

private fun h(vararg colors: Int) = FlagStripes(FlagLayout.Horizontal, colors.toList())
private fun v(vararg colors: Int) = FlagStripes(FlagLayout.Vertical, colors.toList())
private fun nordic(vararg colors: Int) = FlagStripes(FlagLayout.NordicCross, colors.toList())
private fun cross(vararg colors: Int) = FlagStripes(FlagLayout.CenteredCross, colors.toList())
private fun disc(vararg colors: Int) = FlagStripes(FlagLayout.Disc, colors.toList())
private fun hoist(vararg colors: Int) = FlagStripes(FlagLayout.Hoist, colors.toList())
private fun canton(vararg colors: Int) = FlagStripes(FlagLayout.Canton, colors.toList())

private val FRANCE = v(0x002654, WHITE, 0xCE1126)
private val NORWAY = nordic(0xBA0C2F, WHITE, 0x00205B)
private val NETHERLANDS = h(0xAE1C28, WHITE, 0x21468B)
private val AUSTRALIA = h(ENSIGN_BLUE)
private val UNITED_STATES = canton(0x0A3161, 0xB31942, WHITE, 0xB31942)

/**
 * Keyed by ISO 3166-1 alpha-2, covering [KNOWN_COUNTRIES]. Every code the header can name gets a wash, as every
 * code gets a flag emoji there. A territory that shares its state's flag takes that one (the French tricolour for
 * French Guiana, Guadeloupe, Martinique, New Caledonia and others, the Dutch for the Caribbean Netherlands), even
 * where the reference images show a local or unofficial one.
 */
private val FLAGS: Map<String, FlagStripes> = mapOf(
    "AD" to v(0x10069F, 0xFEDD00, 0xD50032),
    "AE" to hoist(0xC8102E, 0x00843D, WHITE, BLACK),
    "AF" to v(BLACK, 0xD32011, 0x007A36),
    "AG" to disc(0xCE1126, BLACK, 0x0072C6),
    "AI" to h(ENSIGN_BLUE),
    "AL" to disc(0xFF0000, BLACK),
    "AM" to h(0xD90012, 0x0033A0, 0xF2A800),
    "AO" to h(0xCC092F, BLACK),
    "AQ" to disc(0x009EDB, WHITE),
    "AR" to h(0x74ACDF, WHITE, 0x74ACDF),
    "AS" to v(0x000066, WHITE),
    "AT" to h(0xC8102E, WHITE, 0xC8102E),
    "AU" to AUSTRALIA,
    "AW" to h(0x418FDE),
    "AX" to nordic(0x0064AD, 0xFFD300, 0xDA0E15),
    "AZ" to h(0x00B5E2, 0xEF3340, 0x509E2F),
    "BA" to v(0x002395, 0xFECB00, 0x002395),
    "BB" to v(0x00267F, 0xFFC726, 0x00267F),
    "BD" to disc(0x006A4E, 0xF42A41),
    "BE" to v(BLACK, 0xFDDA25, 0xEF3340),
    "BF" to h(0xEF2B2D, 0x009E49),
    "BG" to h(WHITE, 0x00966E, 0xD62612),
    "BH" to v(WHITE, 0xDA291C, 0xDA291C),
    "BI" to h(0xC8102E, 0x43B02A, 0xC8102E),
    "BJ" to hoist(0x008751, 0xFCD116, 0xE8112D),
    "BL" to FRANCE,
    "BM" to h(0xC8102E),
    "BN" to h(0xF7E017, WHITE, BLACK, 0xF7E017),
    "BO" to h(0xD32011, 0xF0E53C, 0x00702D),
    "BQ" to NETHERLANDS,
    "BR" to disc(0x009440, 0xFFCB00, 0x302681),
    "BS" to hoist(BLACK, 0x00778B, 0xFFC72C, 0x00778B),
    "BT" to v(0xFFCD00, 0xFF671F),
    "BV" to NORWAY,
    "BW" to h(0x6DA9D2, BLACK, 0x6DA9D2),
    "BY" to h(0xCE1720, 0xCE1720, 0x007C30),
    "BZ" to disc(0x171696, WHITE),
    "CA" to v(0xFF0000, WHITE, WHITE, 0xFF0000),
    "CC" to h(0x008000),
    "CD" to h(0x007FFF, 0xCE1021, 0x007FFF),
    "CF" to h(0x003082, WHITE, 0x289728, 0xFFCE00),
    "CG" to v(0x009543, 0xFBDE4A, 0xDC241F),
    "CH" to cross(0xFF0000, WHITE),
    "CI" to v(0xF77F00, WHITE, 0x009E60),
    "CK" to h(ENSIGN_BLUE),
    "CL" to canton(0x0032A0, WHITE, 0xDA291C),
    "CM" to v(0x007A5E, 0xCE1126, 0xFCD116),
    "CN" to h(0xEE1C25),
    "CO" to h(0xFFCD00, 0xFFCD00, 0x003087, 0xC8102E),
    "CR" to h(0x001489, WHITE, 0xDA291C, WHITE, 0x001489),
    "CU" to hoist(0xCB1515, 0x002A8F, WHITE, 0x002A8F),
    "CV" to h(0x003893, 0x003893, WHITE, 0x003893),
    "CW" to h(0x002B7F, 0x002B7F, 0xF9E814, 0x002B7F),
    "CX" to v(ENSIGN_BLUE, 0x0DB02B),
    "CY" to disc(WHITE, 0xD67900),
    "CZ" to hoist(0x11457E, WHITE, 0xD7141A),
    "DE" to h(BLACK, 0xDD0000, 0xFFCE00),
    "DJ" to hoist(WHITE, 0x6AB2E7, 0x12AD2B),
    "DK" to nordic(0xC8102E, WHITE),
    "DM" to disc(0x046A38, 0xD50032),
    "DO" to v(0x002D62, WHITE, 0xCE1126),
    "DZ" to v(0x006633, WHITE),
    "EC" to h(0xFFDD00, 0xFFDD00, 0x034EA2, 0xED1C24),
    "EE" to h(0x0072CE, BLACK, WHITE),
    "EG" to h(0xCE1126, WHITE, BLACK),
    "EH" to hoist(0xC4111B, BLACK, WHITE, 0x007A3D),
    "ER" to hoist(0xE4002B, 0x43B02A, 0x418FDE),
    "ES" to h(0xAD1519, 0xFABD00, 0xFABD00, 0xAD1519),
    "ET" to h(0x078930, 0xFCDD09, 0xDA121A),
    "FI" to nordic(WHITE, 0x002F6C),
    "FJ" to h(0x62B5E5),
    "FK" to h(ENSIGN_BLUE),
    "FM" to h(0x75B2DD),
    "FO" to nordic(WHITE, 0x015EB8, 0xEF3340),
    "FR" to FRANCE,
    "GA" to h(0x009E60, 0xFCD116, 0x3A75C4),
    "GB" to cross(ENSIGN_BLUE, WHITE, 0xC8102E),
    "GD" to h(0xCE1126, 0xFCD116, 0x007A5E, 0xCE1126),
    "GE" to cross(WHITE, 0xFF0000),
    "GF" to FRANCE,
    "GG" to cross(WHITE, 0xE8112D, 0xF9DD16),
    "GH" to h(0xCE1126, 0xFCD116, 0x006B3F),
    "GI" to h(WHITE, WHITE, 0xDA000C),
    "GL" to h(WHITE, 0xC8102E),
    "GM" to h(0xCE1126, 0x0C1C8C, 0x3A7728),
    "GN" to v(0xCE1126, 0xFCD116, 0x009460),
    "GP" to FRANCE,
    "GQ" to hoist(0x0073CE, 0x3E9A00, WHITE, 0xE32118),
    "GR" to h(0x0D5EAF, WHITE, 0x0D5EAF, WHITE, 0x0D5EAF),
    "GS" to h(ENSIGN_BLUE),
    "GT" to v(0x4997D0, WHITE, 0x4997D0),
    "GU" to h(0x00297B),
    "GW" to hoist(0xCE1126, 0xFCD116, 0x009E49),
    "GY" to hoist(0xBE1E2D, 0x2A936A, 0xFFC20E, 0x2A936A),
    "HK" to disc(0xEE1C25, WHITE),
    "HM" to AUSTRALIA,
    "HN" to h(0x00BCE4, WHITE, 0x00BCE4),
    "HR" to h(0xFF0000, WHITE, 0x171796),
    "HT" to h(0x00209F, 0xD21034),
    "HU" to h(0xCE2939, WHITE, 0x477050),
    "ID" to h(0xFF0000, WHITE),
    "IE" to v(0x169B62, WHITE, 0xFF883E),
    "IL" to h(WHITE, 0x0038B8, WHITE, 0x0038B8, WHITE),
    "IM" to h(0xCF142B),
    "IN" to h(0xFF6820, WHITE, 0x046A38),
    "IO" to h(ENSIGN_BLUE, WHITE, ENSIGN_BLUE, WHITE, ENSIGN_BLUE),
    "IQ" to h(0xCD1125, WHITE, BLACK),
    "IR" to h(0x239F40, WHITE, 0xDA0000),
    "IS" to nordic(0x02529C, WHITE, 0xDC1E35),
    "IT" to v(0x009246, WHITE, 0xCE2B37),
    "JE" to cross(WHITE, 0xCF142B),
    "JM" to cross(0x007749, 0xFFB81C),
    "JO" to hoist(0xCE1126, BLACK, WHITE, 0x007A3D),
    "JP" to disc(WHITE, 0xBC002D),
    "KE" to h(BLACK, 0xBB0000, 0x006600),
    "KG" to disc(0xFF0000, 0xFFFF00),
    "KH" to h(0x032EA1, 0xE00025, 0xE00025, 0x032EA1),
    "KI" to h(0xC81010, 0xC81010, 0x183070, WHITE, 0x183070),
    "KM" to hoist(0x009639, 0xFFD100, WHITE, 0xEF3340, 0x003DA5),
    "KN" to v(0x009739, BLACK, 0xC8102E),
    "KP" to h(0x034DA2, 0xEC1D25, 0xEC1D25, 0x034DA2),
    "KR" to disc(WHITE, 0xCD2E3A, 0x0047A0),
    "KW" to hoist(BLACK, 0x007A3D, WHITE, 0xCE1126),
    "KY" to h(ENSIGN_BLUE),
    "KZ" to disc(0x00ABC2, 0xFFEC2D),
    "LA" to h(0xCE1126, 0x002868, 0x002868, 0xCE1126),
    "LB" to h(0xD31624, WHITE, WHITE, 0xD31624),
    "LC" to disc(0x66CCFF, 0xFCD116),
    "LI" to h(0x002B7F, 0xCE1126),
    "LK" to v(0x00534E, 0xEB7400, 0xFFBE29, 0x8D153A, 0x8D153A),
    "LR" to canton(0x002868, 0xBF0A30, WHITE, 0xBF0A30),
    "LS" to h(0x001489, WHITE, 0x009A44),
    "LT" to h(0xFDB913, 0x006A44, 0xC1272D),
    "LU" to h(0xED2939, WHITE, 0x00A1DE),
    "LV" to h(0x9D2235, 0x9D2235, WHITE, 0x9D2235, 0x9D2235),
    "LY" to h(0xE70013, BLACK, BLACK, 0x239E46),
    "MA" to h(0xC1272D),
    "MC" to h(0xCE1126, WHITE),
    "MD" to v(0x0046AE, 0xFFD200, 0xCC092F),
    "ME" to disc(0xE30000, 0xFFC000),
    "MF" to FRANCE,
    "MG" to hoist(WHITE, 0xFC3D32, 0x007E3A),
    "MH" to h(0x003893),
    "MK" to disc(0xD82126, 0xF8E92E),
    "ML" to v(0x14B53A, 0xFCD116, 0xCE1126),
    "MM" to h(0xFECB00, 0x34B233, 0xEA2839),
    "MN" to v(0xDA2031, 0x0066B2, 0xDA2031),
    "MO" to disc(0x0F7562, WHITE),
    "MP" to h(0x0033A1),
    "MQ" to FRANCE,
    "MR" to h(0xD01C1F, 0x00A95C, 0x00A95C, 0x00A95C, 0xD01C1F),
    "MS" to h(ENSIGN_BLUE),
    "MT" to v(WHITE, 0xCF142B),
    "MU" to h(0xEA2839, 0x1A206D, 0xFFD500, 0x00A551),
    "MV" to disc(0xD21034, 0x007E3A),
    "MW" to h(BLACK, 0xCE1126, 0x339E35),
    "MX" to v(0x006847, WHITE, 0xCE1126),
    "MY" to canton(0x000066, 0xCC0000, WHITE, 0xCC0000),
    "MZ" to hoist(0xD21034, 0x007168, BLACK, 0xFCE100),
    "NA" to v(0x003580, 0xD21034, 0x009543),
    "NC" to FRANCE,
    "NE" to h(0xE05206, WHITE, 0x0DB02B),
    "NF" to v(0x007934, WHITE, 0x007934),
    "NG" to v(0x008751, WHITE, 0x008751),
    "NI" to h(0x0067C6, WHITE, 0x0067C6),
    "NL" to NETHERLANDS,
    "NO" to NORWAY,
    "NP" to h(0xDC143C),
    "NR" to h(ENSIGN_BLUE, ENSIGN_BLUE, 0xFFC72C, ENSIGN_BLUE, ENSIGN_BLUE),
    "NU" to canton(ENSIGN_BLUE, 0xFEDD00),
    "NZ" to h(ENSIGN_BLUE),
    "OM" to hoist(0xDF1A23, WHITE, 0xDF1A23, 0x007E3C),
    "PA" to h(WHITE, 0xDA121A, 0x072357, WHITE),
    "PE" to v(0xD91023, WHITE, 0xD91023),
    "PF" to h(0xCE1126, WHITE, WHITE, 0xCE1126),
    "PG" to v(BLACK, 0xCE1126),
    "PH" to hoist(WHITE, 0x0038A8, 0xCE1126),
    "PK" to v(WHITE, 0x01411C, 0x01411C, 0x01411C),
    "PL" to h(WHITE, 0xDC143C),
    "PM" to FRANCE,
    "PN" to h(ENSIGN_BLUE),
    "PR" to hoist(0x0044FF, 0xEE0000, WHITE, 0xEE0000),
    "PS" to hoist(0xED2E38, BLACK, WHITE, 0x009639),
    "PT" to v(0x006600, 0xFF0000, 0xFF0000),
    "PW" to disc(0x0099FF, 0xFFFF00),
    "PY" to h(0xD52B1E, WHITE, 0x0038A8),
    "QA" to v(WHITE, 0x8A1538, 0x8A1538),
    "RE" to FRANCE,
    "RO" to v(0x002B7F, 0xFCD116, 0xCE1126),
    "RS" to h(0xC6363C, 0x0C4076, WHITE),
    "RU" to h(WHITE, 0x0039A6, 0xD52B1E),
    "RW" to h(0x00A3E0, 0x00A3E0, 0xFAD201, 0x20603D),
    "SA" to h(0x005430),
    "SB" to v(0x0051BA, 0xFCD116, 0x215B33),
    "SC" to v(0x002F6C, 0xFED141, 0xD22730, WHITE, 0x007A33),
    "SD" to hoist(0x007229, 0xD21034, WHITE, BLACK),
    "SE" to nordic(0x005293, 0xFECB00),
    "SG" to h(0xED2939, WHITE),
    "SH" to h(ENSIGN_BLUE),
    "SI" to h(WHITE, 0x0000FF, 0xFF0000),
    "SJ" to NORWAY,
    "SK" to h(WHITE, 0x0B4EA2, 0xEE1C25),
    "SL" to h(0x1EB53A, WHITE, 0x0072C6),
    "SM" to h(WHITE, 0x5EB6E4),
    "SN" to v(0x00853F, 0xFDEF42, 0xE31B23),
    "SO" to disc(0x418FDE, WHITE),
    "SR" to h(0x377E3F, WHITE, 0xB40A2D, WHITE, 0x377E3F),
    "SS" to hoist(0x00B6F2, BLACK, 0xE22028, 0x00914C),
    "ST" to hoist(0xEF3340, 0x009739, 0xFFD100, 0x009739),
    "SV" to h(0x0047AB, WHITE, 0x0047AB),
    "SX" to hoist(WHITE, 0xBA0C0C, 0x173D89),
    "SY" to h(0x007A3D, WHITE, BLACK),
    "SZ" to h(0x3E5EB9, 0xB10C0C, 0xB10C0C, 0x3E5EB9),
    "TC" to h(0x002868),
    "TD" to v(0x002664, 0xFECB00, 0xC60C30),
    "TF" to h(0x214D9B),
    "TG" to canton(0xD21034, 0x006A4E, 0xFFCE00, 0x006A4E),
    "TH" to h(0xA51931, 0xF4F5F8, 0x2D2A4A, 0xF4F5F8, 0xA51931),
    "TJ" to h(0xCC0000, WHITE, 0x006600),
    "TK" to disc(ENSIGN_BLUE, 0xFFD100),
    "TL" to hoist(BLACK, 0xDA291C),
    "TM" to h(0x00843D),
    "TN" to disc(0xE70013, WHITE),
    "TO" to canton(WHITE, 0xC10000),
    "TR" to disc(0xE30A17, WHITE),
    "TT" to v(0xDA1A35, BLACK, 0xDA1A35),
    "TV" to h(0x009CDE),
    "TW" to canton(0x000094, 0xFE0000),
    "TZ" to v(0x1EB53A, BLACK, 0x00A3DD),
    "UA" to h(0x005BBB, 0xFFD500),
    "UG" to h(BLACK, 0xFCDC04, 0xD90000),
    "UM" to UNITED_STATES,
    "US" to UNITED_STATES,
    "UY" to h(WHITE, 0x0038A8, WHITE, 0x0038A8),
    "UZ" to h(0x3081F7, WHITE, 0x308738),
    "VA" to v(0xFFF200, WHITE),
    "VC" to v(0x002674, 0xFCD022, 0xFCD022, 0x007C2E),
    "VE" to h(0xFFCC00, 0x00247D, 0xCF142B),
    "VG" to h(ENSIGN_BLUE),
    "VI" to disc(WHITE, 0xF4C53D),
    "VN" to disc(0xFF0000, 0xFFEB00),
    "VU" to hoist(BLACK, 0xD21034, 0x009543),
    "WF" to FRANCE,
    "WS" to canton(0x002B7F, 0xCE1126),
    "XK" to disc(0x244AA5, 0xD0A650),
    "YE" to h(0xCE1126, WHITE, BLACK),
    "YT" to FRANCE,
    "ZA" to hoist(BLACK, 0xE03C31, 0x007749, 0x001489),
    "ZM" to h(0x198A00),
    "ZW" to hoist(WHITE, 0x006400, 0xFFD200, 0xD40000),
)

/** The stripes for an ISO alpha-2 code, or null for a malformed or unknown one (which then gets no wash). */
fun flagStripesFor(iso: String): FlagStripes? = FLAGS[iso.trim().uppercase()]

/**
 * How much of the flag shows through the header's surface colour, at its strongest. Low enough that the header's
 * text keeps its contrast over any flag colour (checked in FlagsTest), and lower in the dark theme so that a white
 * flag doesn't turn into a bright band.
 */
const val FLAG_WASH_ALPHA_LIGHT = 0.22f
const val FLAG_WASH_ALPHA_DARK = 0.10f

/** [top] at [alpha] over an opaque [bottom], channel by channel in sRGB, as the screen composites it. */
fun blendOver(top: Int, alpha: Float, bottom: Int): Int {
    fun channel(shift: Int): Int {
        val t = (top shr shift) and 0xFF
        val b = (bottom shr shift) and 0xFF
        return (t * alpha + b * (1 - alpha) + 0.5f).toInt().coerceIn(0, 255)
    }
    return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

/** WCAG 2 contrast ratio between two opaque colours, from 1 (the same) to 21 (black on white). */
fun contrastRatio(a: Int, b: Int): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

private fun relativeLuminance(rgb: Int): Double {
    fun linear(shift: Int): Double {
        val c = ((rgb shr shift) and 0xFF) / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0)
}
