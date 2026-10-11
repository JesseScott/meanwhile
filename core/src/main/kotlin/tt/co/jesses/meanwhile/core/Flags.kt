package tt.co.jesses.meanwhile.core

import kotlin.math.pow

/*
 * Flags as a few bands of colour, for the soft wash behind the header. This is not artwork: emblems, stars and
 * fine stripes are left out, because the wash is blurred and faded until only the colours and their rough
 * arrangement are left. Colours are 0xRRGGBB.
 */

/** How a flag's colours are arranged, and so what [FlagStripes.colors] means. */
enum class FlagLayout {
    /** Equal bands from top to bottom. A wider band repeats its colour (Spain is red, yellow, yellow, red). */
    Horizontal,

    /** Equal bands from the hoist (the pole side) to the fly. */
    Vertical,

    /** Field, cross, and an optional inner cross; the upright sits towards the hoist (Finland, Norway). */
    NordicCross,

    /** Field, cross, and an optional inner cross; the upright sits in the middle (Switzerland, the UK). */
    CenteredCross,

    /** Field, disc, and an optional inner disc (Japan, Brazil). Also stands in for a central emblem. */
    Disc,

    /** A colour at the hoist, a triangle or a bar, followed by one to three horizontal bands (Czechia, Jordan). */
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

private val FRANCE = v(0x0055A4, WHITE, 0xEF4135)
private val NORWAY = nordic(0xBA0C2F, WHITE, 0x00205B)
private val NETHERLANDS = h(0xAE1C28, WHITE, 0x21468B)
private val AUSTRALIA = h(ENSIGN_BLUE)
private val UNITED_STATES = canton(0x3C3B6E, 0xB22234, WHITE, 0xB22234)

/**
 * Keyed by ISO 3166-1 alpha-2, covering [KNOWN_COUNTRIES]. Which places get a flag follows the flag emoji the
 * header already shows: territories without one of their own in common use take their state's.
 */
private val FLAGS: Map<String, FlagStripes> = mapOf(
    "AD" to v(0x10069F, 0xFEDD00, 0xD50032),
    "AE" to hoist(0xEF3340, 0x00732F, WHITE, BLACK),
    "AF" to v(BLACK, 0xD32011, 0x007A36),
    "AG" to h(BLACK, 0x0072C6, WHITE, 0xCE1126),
    "AI" to h(ENSIGN_BLUE),
    "AL" to disc(0xE41E20, BLACK),
    "AM" to h(0xD90012, 0x0033A0, 0xF2A800),
    "AO" to h(0xCC092F, BLACK),
    "AQ" to disc(0x3A7DCE, WHITE),
    "AR" to h(0x74ACDF, WHITE, 0x74ACDF),
    "AS" to v(0x000066, WHITE),
    "AT" to h(0xC8102E, WHITE, 0xC8102E),
    "AU" to AUSTRALIA,
    "AW" to h(0x418FDE),
    "AX" to nordic(0x0053A5, 0xFFCE00, 0xD21034),
    "AZ" to h(0x00B5E2, 0xEF3340, 0x509E2F),
    "BA" to v(0x002395, 0xFECB00, 0x002395),
    "BB" to v(0x00267F, 0xFFC726, 0x00267F),
    "BD" to disc(0x006A4E, 0xF42A41),
    "BE" to v(BLACK, 0xFDDA24, 0xEF3340),
    "BF" to h(0xEF2B2D, 0x009E49),
    "BG" to h(WHITE, 0x00966E, 0xD62612),
    "BH" to v(WHITE, 0xCE1126, 0xCE1126),
    "BI" to h(0xCE1126, 0x1EB53A, 0xCE1126),
    "BJ" to hoist(0x008751, 0xFCD116, 0xE8112D),
    "BL" to FRANCE,
    "BM" to h(0xC8102E),
    "BN" to h(0xF7E017, WHITE, BLACK, 0xF7E017),
    "BO" to h(0xDA291C, 0xF4E400, 0x007A33),
    "BQ" to NETHERLANDS,
    "BR" to disc(0x009C3B, 0xFFDF00, 0x002776),
    "BS" to hoist(BLACK, 0x00778B, 0xFFC72C, 0x00778B),
    "BT" to v(0xFFD520, 0xFF4E12),
    "BV" to NORWAY,
    "BW" to h(0x6DA9D2, BLACK, 0x6DA9D2),
    "BY" to h(0xCE1720, 0xCE1720, 0x007C30),
    "BZ" to disc(0x003F87, WHITE),
    "CA" to v(0xD80621, WHITE, WHITE, 0xD80621),
    "CC" to h(0x008000),
    "CD" to h(0x007FFF, 0xCE1021, 0x007FFF),
    "CF" to h(0x003082, WHITE, 0x289728, 0xFFCE00),
    "CG" to v(0x009543, 0xFBDE4A, 0xDC241F),
    "CH" to cross(0xDA291C, WHITE),
    "CI" to v(0xFF8200, WHITE, 0x009A44),
    "CK" to h(ENSIGN_BLUE),
    "CL" to canton(0x0039A6, WHITE, 0xD52B1E),
    "CM" to v(0x007A5E, 0xCE1126, 0xFCD116),
    "CN" to h(0xEE1C25),
    "CO" to h(0xFFCD00, 0xFFCD00, 0x003087, 0xC8102E),
    "CR" to h(0x001489, 0xDA291C, 0x001489),
    "CU" to hoist(0xCB1515, 0x002A8F, WHITE, 0x002A8F),
    "CV" to h(0x003893, 0x003893, WHITE, 0x003893),
    "CW" to h(0x002B7F, 0x002B7F, 0xF9E814, 0x002B7F),
    "CX" to v(0x0021AD, 0x1C8A42),
    "CY" to disc(WHITE, 0xD57800),
    "CZ" to hoist(0x11457E, WHITE, 0xD7141A),
    "DE" to h(BLACK, 0xDD0000, 0xFFCE00),
    "DJ" to hoist(WHITE, 0x6AB2E7, 0x12AD2B),
    "DK" to nordic(0xC8102E, WHITE),
    "DM" to disc(0x006B3F, 0xD41C30),
    "DO" to v(0x002D62, WHITE, 0xCE1126),
    "DZ" to v(0x006233, WHITE),
    "EC" to h(0xFFDD00, 0xFFDD00, 0x034EA2, 0xED1C24),
    "EE" to h(0x0072CE, BLACK, WHITE),
    "EG" to h(0xCE1126, WHITE, BLACK),
    "EH" to hoist(0xC4111B, BLACK, WHITE, 0x007A3D),
    "ER" to hoist(0xEA0437, 0x12AD2B, 0x4189DD),
    "ES" to h(0xAA151B, 0xF1BF00, 0xF1BF00, 0xAA151B),
    "ET" to h(0x078930, 0xFCDD09, 0xDA121A),
    "FI" to nordic(WHITE, 0x003580),
    "FJ" to h(0x68BFE5),
    "FK" to h(ENSIGN_BLUE),
    "FM" to h(0x75B2DD),
    "FO" to nordic(WHITE, 0x005EB8, 0xEF303E),
    "FR" to FRANCE,
    "GA" to h(0x009E60, 0xFCD116, 0x3A75C4),
    "GB" to cross(ENSIGN_BLUE, WHITE, 0xC8102E),
    "GD" to h(0xCE1126, 0xFCD116, 0x007A5E, 0xCE1126),
    "GE" to cross(WHITE, 0xFF0000),
    "GF" to FRANCE,
    "GG" to cross(WHITE, 0xE8112D, 0xF9DD16),
    "GH" to h(0xCE1126, 0xFCD116, 0x006B3F),
    "GI" to h(WHITE, WHITE, 0xDA000C),
    "GL" to h(WHITE, 0xD00C33),
    "GM" to h(0xCE1126, 0x0C1C8C, 0x3A7728),
    "GN" to v(0xCE1126, 0xFCD116, 0x009460),
    "GP" to FRANCE,
    "GQ" to hoist(0x0073CE, 0x3E9A00, WHITE, 0xE32118),
    "GR" to h(0x0D5EAF, WHITE, 0x0D5EAF, WHITE),
    "GS" to h(ENSIGN_BLUE),
    "GT" to v(0x4997D0, WHITE, 0x4997D0),
    "GU" to h(0x00297B),
    "GW" to hoist(0xCE1126, 0xFCD116, 0x009E49),
    "GY" to hoist(0xCE1126, 0x009E49, 0xFCD116, 0x009E49),
    "HK" to disc(0xDE2910, WHITE),
    "HM" to AUSTRALIA,
    "HN" to h(0x00BCE4, WHITE, 0x00BCE4),
    "HR" to h(0xFF0000, WHITE, 0x171796),
    "HT" to h(0x00209F, 0xD21034),
    "HU" to h(0xCE2939, WHITE, 0x477050),
    "ID" to h(0xFF0000, WHITE),
    "IE" to v(0x169B62, WHITE, 0xFF883E),
    "IL" to h(0x0038B8, WHITE, WHITE, 0x0038B8),
    "IM" to h(0xCF142B),
    "IN" to h(0xFF9933, WHITE, 0x138808),
    "IO" to h(ENSIGN_BLUE),
    "IQ" to h(0xCE1126, WHITE, BLACK),
    "IR" to h(0x239F40, WHITE, 0xDA0000),
    "IS" to nordic(0x02529C, WHITE, 0xDC1E35),
    "IT" to v(0x009246, WHITE, 0xCE2B37),
    "JE" to cross(WHITE, 0xDF112D),
    "JM" to cross(0x009B3A, 0xFED100),
    "JO" to hoist(0xCE1126, BLACK, WHITE, 0x007A3D),
    "JP" to disc(WHITE, 0xBC002D),
    "KE" to h(BLACK, 0xBB0000, 0x006600),
    "KG" to disc(0xE8112D, 0xFFEF00),
    "KH" to h(0x032EA1, 0xE00025, 0xE00025, 0x032EA1),
    "KI" to h(0xCE1126, 0x003F87),
    "KM" to hoist(0x3D8E33, 0xFFC61E, WHITE, 0xCE1126),
    "KN" to v(0x009E49, BLACK, 0xCE1126),
    "KP" to h(0x024FA2, 0xED1C27, 0xED1C27, 0x024FA2),
    "KR" to disc(WHITE, 0xCD2E3A, 0x0047A0),
    "KW" to hoist(BLACK, 0x007A3D, WHITE, 0xCE1126),
    "KY" to h(ENSIGN_BLUE),
    "KZ" to disc(0x00AFCA, 0xFEC50C),
    "LA" to h(0xCE1126, 0x002868, 0x002868, 0xCE1126),
    "LB" to h(0xED1C24, WHITE, WHITE, 0xED1C24),
    "LC" to disc(0x65CFFF, 0xFCD116),
    "LI" to h(0x002B7F, 0xCE1126),
    "LK" to v(0x00534E, 0xEB7400, 0x8D153A, 0x8D153A),
    "LR" to canton(0x002868, 0xBF0A30, WHITE, 0xBF0A30),
    "LS" to h(0x00209F, WHITE, 0x009543),
    "LT" to h(0xFDB913, 0x006A44, 0xC1272D),
    "LU" to h(0xED2939, WHITE, 0x00A1DE),
    "LV" to h(0x9E3039, WHITE, 0x9E3039),
    "LY" to h(0xE70013, BLACK, BLACK, 0x239E46),
    "MA" to disc(0xC1272D, 0x006233),
    "MC" to h(0xCE1126, WHITE),
    "MD" to v(0x0046AE, 0xFFD200, 0xCC092F),
    "ME" to disc(0xC40308, 0xD4AF3A),
    "MF" to FRANCE,
    "MG" to hoist(WHITE, 0xFC3D32, 0x007E3A),
    "MH" to h(0x003893),
    "MK" to disc(0xD20000, 0xFFE600),
    "ML" to v(0x14B53A, 0xFCD116, 0xCE1126),
    "MM" to h(0xFECB00, 0x34B233, 0xEA2839),
    "MN" to v(0xC4272F, 0x015197, 0xC4272F),
    "MO" to disc(0x00785E, WHITE),
    "MP" to h(0x0071BC),
    "MQ" to FRANCE,
    "MR" to h(0xD01C1F, 0x006233, 0x006233, 0xD01C1F),
    "MS" to h(ENSIGN_BLUE),
    "MT" to v(WHITE, 0xCF142B),
    "MU" to h(0xEA2839, 0x1A206D, 0xFFD500, 0x00A551),
    "MV" to disc(0xD21034, 0x007E3A),
    "MW" to h(BLACK, 0xCE1126, 0x339E35),
    "MX" to v(0x006847, WHITE, 0xCE1126),
    "MY" to canton(0x010066, 0xCC0001, WHITE, 0xCC0001),
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
    "NR" to h(0x002B7F, 0xFFC61E, 0x002B7F),
    "NU" to canton(ENSIGN_BLUE, 0xFEDD00),
    "NZ" to h(ENSIGN_BLUE),
    "OM" to hoist(0xDB161B, WHITE, 0xDB161B, 0x008000),
    "PA" to h(WHITE, 0xDA121A, 0x072357, WHITE),
    "PE" to v(0xD91023, WHITE, 0xD91023),
    "PF" to h(0xCE1126, WHITE, WHITE, 0xCE1126),
    "PG" to v(BLACK, 0xCE1126),
    "PH" to hoist(WHITE, 0x0038A8, 0xCE1126),
    "PK" to v(WHITE, 0x01411C, 0x01411C, 0x01411C),
    "PL" to h(WHITE, 0xDC143C),
    "PM" to FRANCE,
    "PN" to h(ENSIGN_BLUE),
    "PR" to hoist(0x0050F0, 0xED0000, WHITE, 0xED0000),
    "PS" to hoist(0xEE2A35, BLACK, WHITE, 0x009736),
    "PT" to v(0x006600, 0xFF0000, 0xFF0000),
    "PW" to disc(0x4AADD6, 0xFFDE00),
    "PY" to h(0xD52B1E, WHITE, 0x0038A8),
    "QA" to v(WHITE, 0x8A1538, 0x8A1538),
    "RE" to FRANCE,
    "RO" to v(0x002B7F, 0xFCD116, 0xCE1126),
    "RS" to h(0xC6363C, 0x0C4076, WHITE),
    "RU" to h(WHITE, 0x0039A6, 0xD52B1E),
    "RW" to h(0x00A1DE, 0x00A1DE, 0xFAD201, 0x20603D),
    "SA" to h(0x006C35),
    "SB" to v(0x0051BA, 0xFCD116, 0x215B33),
    "SC" to v(0x003F87, 0xFCD856, 0xD62828, 0x007A3D),
    "SD" to hoist(0x007229, 0xD21034, WHITE, BLACK),
    "SE" to nordic(0x006AA7, 0xFECC02),
    "SG" to h(0xEF3340, WHITE),
    "SH" to h(ENSIGN_BLUE),
    "SI" to h(WHITE, 0x005DA4, 0xED1C24),
    "SJ" to NORWAY,
    "SK" to h(WHITE, 0x0B4EA2, 0xEE1C25),
    "SL" to h(0x1EB53A, WHITE, 0x0072C6),
    "SM" to h(WHITE, 0x5EB6E4),
    "SN" to v(0x00853F, 0xFDEF42, 0xE31B23),
    "SO" to disc(0x4189DD, WHITE),
    "SR" to h(0x377E3F, 0xB40A2D, 0xB40A2D, 0x377E3F),
    "SS" to hoist(0x0F47AF, BLACK, 0xDA121A, 0x078930),
    "ST" to hoist(0xD21034, 0x12AD2B, 0xFFCE00, 0x12AD2B),
    "SV" to h(0x0047AB, WHITE, 0x0047AB),
    "SX" to hoist(WHITE, 0xDC171D, 0x012A87),
    "SY" to h(0x007A3D, WHITE, BLACK),
    "SZ" to h(0x3E5EB9, 0xB10C0C, 0xB10C0C, 0x3E5EB9),
    "TC" to h(ENSIGN_BLUE),
    "TD" to v(0x002664, 0xFECB00, 0xC60C30),
    "TF" to h(0x002395),
    "TG" to canton(0xD21034, 0x006A4E, 0xFFCE00, 0x006A4E),
    "TH" to h(0xA51931, 0x2D2A4A, 0x2D2A4A, 0xA51931),
    "TJ" to h(0xCC0000, WHITE, 0x006600),
    "TK" to h(0x00247D),
    "TL" to hoist(BLACK, 0xDC241F),
    "TM" to h(0x00843D),
    "TN" to disc(0xE70013, WHITE),
    "TO" to canton(WHITE, 0xC10000),
    "TR" to disc(0xE30A17, WHITE),
    "TT" to v(0xCE1126, BLACK, 0xCE1126),
    "TV" to h(0x009FCA),
    "TW" to canton(0x000095, 0xFE0000),
    "TZ" to v(0x1EB53A, BLACK, 0x00A3DD),
    "UA" to h(0x0057B7, 0xFFD700),
    "UG" to h(BLACK, 0xFCDC04, 0xD90000),
    "UM" to UNITED_STATES,
    "US" to UNITED_STATES,
    "UY" to h(WHITE, 0x0038A8, WHITE, 0x0038A8),
    "UZ" to h(0x0099B5, WHITE, 0x1EB53A),
    "VA" to v(0xFFE000, WHITE),
    "VC" to v(0x0072C6, 0xFCD116, 0xFCD116, 0x009E60),
    "VE" to h(0xFFCC00, 0x00247D, 0xCF142B),
    "VG" to h(ENSIGN_BLUE),
    "VI" to disc(WHITE, 0xFFD200),
    "VN" to disc(0xDA251D, 0xFFFF00),
    "VU" to hoist(BLACK, 0xD21034, 0x009543),
    "WF" to FRANCE,
    "WS" to canton(0x002B7F, 0xCE1126),
    "XK" to disc(0x244AA5, 0xD0A650),
    "YE" to h(0xCE1126, WHITE, BLACK),
    "YT" to FRANCE,
    "ZA" to hoist(BLACK, 0xE03C31, 0x007749, 0x001489),
    "ZM" to h(0x198A00),
    "ZW" to hoist(WHITE, 0x319208, 0xFFD200, 0xDE2010),
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
