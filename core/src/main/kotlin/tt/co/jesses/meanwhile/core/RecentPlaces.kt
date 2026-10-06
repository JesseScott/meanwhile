package tt.co.jesses.meanwhile.core

/** A place the user searched for and picked, kept on the device so it can be chosen again without typing. */
data class RecentPlace(val label: String, val point: LatLon)

/** The most places kept. */
const val MAX_RECENT_PLACES = 3

/** The list with [place] first, any earlier entry for the same label dropped, and no more than [max] kept. */
fun List<RecentPlace>.withNewest(place: RecentPlace, max: Int = MAX_RECENT_PLACES): List<RecentPlace> =
    (listOf(place) + filterNot { it.label == place.label }).take(max)

/** One place per line, as `lat<TAB>lon<TAB>label`. Tabs and line breaks in a label are turned into spaces. */
fun List<RecentPlace>.encode(): String =
    joinToString("\n") { "${it.point.lat}\t${it.point.lon}\t${it.label.replace(Regex("[\t\r\n]"), " ")}" }

/** Reads [encode]'s format. A line that doesn't parse, or has coordinates out of range, is skipped, never a crash. */
fun decodeRecentPlaces(raw: String?, max: Int = MAX_RECENT_PLACES): List<RecentPlace> =
    raw.orEmpty().lineSequence().mapNotNull { line ->
        val parts = line.split("\t", limit = 3)
        val lat = parts.getOrNull(0)?.toDoubleOrNull()
        val lon = parts.getOrNull(1)?.toDoubleOrNull()
        val label = parts.getOrNull(2)?.trim()
        if (lat == null || lon == null || label.isNullOrEmpty()) null
        else if (lat !in -90.0..90.0 || lon !in -180.0..180.0) null
        else RecentPlace(label, LatLon(lat, lon))
    }.distinctBy { it.label }.take(max).toList()
