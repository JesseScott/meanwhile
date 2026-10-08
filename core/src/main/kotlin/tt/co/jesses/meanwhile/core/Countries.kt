package tt.co.jesses.meanwhile.core

/**
 * GDELT's `sourcecountry:` filter takes FIPS 10-4 codes, but geocoders return ISO 3166 codes.
 * Only codes that differ are listed; verify against GDELT's country list if a region looks empty.
 */
private val ISO_TO_FIPS_DIFFERENCES: Map<String, String> = mapOf(
    "AD" to "AN", "AG" to "AC", "AI" to "AV", "AQ" to "AY", "AS" to "AQ", "AT" to "AU", "AU" to "AS",
    "AW" to "AA", "AZ" to "AJ", "BA" to "BK", "BD" to "BG", "BF" to "UV", "BG" to "BU", "BH" to "BA",
    "BI" to "BY", "BJ" to "BN", "BL" to "TB", "BM" to "BD", "BN" to "BX", "BO" to "BL", "BS" to "BF",
    "BW" to "BC", "BY" to "BO", "BZ" to "BH", "CC" to "CK", "CD" to "CG", "CF" to "CT", "CG" to "CF",
    "CH" to "SZ", "CI" to "IV", "CK" to "CW", "CL" to "CI", "CN" to "CH", "CR" to "CS", "CW" to "UC",
    "CX" to "KT", "CZ" to "EZ", "DE" to "GM", "DK" to "DA", "DM" to "DO", "DO" to "DR", "DZ" to "AG",
    "EE" to "EN", "EH" to "WI", "ES" to "SP", "GA" to "GB", "GB" to "UK", "GD" to "GJ", "GE" to "GG",
    "GF" to "FG", "GG" to "GK", "GM" to "GA", "GN" to "GV", "GQ" to "EK", "GS" to "SX", "GU" to "GQ",
    "GW" to "PU", "HN" to "HO", "HT" to "HA", "IE" to "EI", "IL" to "IS", "IQ" to "IZ", "IS" to "IC",
    "JP" to "JA", "KH" to "CB", "KI" to "KR", "KM" to "CN", "KN" to "SC", "KP" to "KN", "KR" to "KS",
    "KW" to "KU", "KY" to "CJ", "LB" to "LE", "LC" to "ST", "LI" to "LS", "LK" to "CE", "LR" to "LI",
    "LS" to "LT", "LT" to "LH", "LV" to "LG", "MA" to "MO", "MC" to "MN", "ME" to "MJ", "MF" to "RN",
    "MG" to "MA", "MH" to "RM", "MM" to "BM", "MN" to "MG", "MO" to "MC", "MP" to "CQ", "MQ" to "MB",
    "MS" to "MH", "MU" to "MP", "MW" to "MI", "NA" to "WA", "NE" to "NG", "NG" to "NI", "NI" to "NU",
    "NU" to "NE", "OM" to "MU", "PA" to "PM", "PF" to "FP", "PG" to "PP", "PH" to "RP", "PM" to "SB",
    "PN" to "PC", "PR" to "RQ", "PS" to "WE", "PT" to "PO", "PW" to "PS", "PY" to "PA", "RS" to "RI",
    "RU" to "RS", "SB" to "BP", "SC" to "SE", "SD" to "SU", "SE" to "SW", "SG" to "SN", "SJ" to "SV",
    "SK" to "LO", "SN" to "SG", "SR" to "NS", "SS" to "OD", "ST" to "TP", "SV" to "ES", "SX" to "NN",
    "SZ" to "WZ", "TC" to "TK", "TD" to "CD", "TF" to "FS", "TG" to "TO", "TJ" to "TI", "TK" to "TL",
    "TL" to "TT", "TM" to "TX", "TN" to "TS", "TO" to "TN", "TR" to "TU", "TT" to "TD", "UA" to "UP",
    "VA" to "VT", "VG" to "VI", "VI" to "VQ", "VN" to "VM", "VU" to "NH", "XK" to "KV", "YE" to "YM",
    "YT" to "MF", "ZA" to "SF", "ZM" to "ZA", "ZW" to "ZI",
)

/**
 * Uninhabited or research-station-only territories. They turn up as the "nearest land" for
 * many ocean antipodes (Kerguelen is Vancouver's) but GDELT has no news from them.
 */
val NO_COVERAGE_ISO: Set<String> = setOf("TF", "AQ", "BV", "HM", "GS", "IO", "UM")

/**
 * Every country code the app may name in a usage report: the ISO 3166-1 alpha-2 list, plus XK (Kosovo), which
 * geocoders return. A fixed list, so that nothing but a country code can be sent in a country's place.
 */
val KNOWN_COUNTRIES: Set<String> = setOf(
    "AD", "AE", "AF", "AG", "AI", "AL", "AM", "AO", "AQ", "AR", "AS", "AT", "AU", "AW", "AX", "AZ", "BA", "BB",
    "BD", "BE", "BF", "BG", "BH", "BI", "BJ", "BL", "BM", "BN", "BO", "BQ", "BR", "BS", "BT", "BV", "BW", "BY",
    "BZ", "CA", "CC", "CD", "CF", "CG", "CH", "CI", "CK", "CL", "CM", "CN", "CO", "CR", "CU", "CV", "CW", "CX",
    "CY", "CZ", "DE", "DJ", "DK", "DM", "DO", "DZ", "EC", "EE", "EG", "EH", "ER", "ES", "ET", "FI", "FJ", "FK",
    "FM", "FO", "FR", "GA", "GB", "GD", "GE", "GF", "GG", "GH", "GI", "GL", "GM", "GN", "GP", "GQ", "GR", "GS",
    "GT", "GU", "GW", "GY", "HK", "HM", "HN", "HR", "HT", "HU", "ID", "IE", "IL", "IM", "IN", "IO", "IQ", "IR",
    "IS", "IT", "JE", "JM", "JO", "JP", "KE", "KG", "KH", "KI", "KM", "KN", "KP", "KR", "KW", "KY", "KZ", "LA",
    "LB", "LC", "LI", "LK", "LR", "LS", "LT", "LU", "LV", "LY", "MA", "MC", "MD", "ME", "MF", "MG", "MH", "MK",
    "ML", "MM", "MN", "MO", "MP", "MQ", "MR", "MS", "MT", "MU", "MV", "MW", "MX", "MY", "MZ", "NA", "NC", "NE",
    "NF", "NG", "NI", "NL", "NO", "NP", "NR", "NU", "NZ", "OM", "PA", "PE", "PF", "PG", "PH", "PK", "PL", "PM",
    "PN", "PR", "PS", "PT", "PW", "PY", "QA", "RE", "RO", "RS", "RU", "RW", "SA", "SB", "SC", "SD", "SE", "SG",
    "SH", "SI", "SJ", "SK", "SL", "SM", "SN", "SO", "SR", "SS", "ST", "SV", "SX", "SY", "SZ", "TC", "TD", "TF",
    "TG", "TH", "TJ", "TK", "TL", "TM", "TN", "TO", "TR", "TT", "TV", "TW", "TZ", "UA", "UG", "UM", "US", "UY",
    "UZ", "VA", "VC", "VE", "VG", "VI", "VN", "VU", "WF", "WS", "YE", "YT", "ZA", "ZM", "ZW", "XK",
)

private fun isAlpha2(code: String) = code.length == 2 && code.all { it in 'A'..'Z' }

/** ISO 3166 alpha-2 to the FIPS code GDELT expects, or null for blank or malformed input. */
fun fipsFor(iso: String): String? {
    val code = iso.trim().uppercase()
    if (!isAlpha2(code)) return null
    return ISO_TO_FIPS_DIFFERENCES[code] ?: code
}

/** Regional-indicator flag emoji for an ISO alpha-2 code, or empty if the code is malformed. */
fun flagEmoji(iso: String): String {
    val code = iso.trim().uppercase()
    if (!isAlpha2(code)) return ""
    return code.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}
