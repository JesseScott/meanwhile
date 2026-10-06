package tt.co.jesses.meanwhile.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.math.abs

/** A place the user searched for and picked, kept so they can go back to it without typing it again. */
@Serializable
data class RecentPlace(val label: String, val lat: Double, val lon: Double) {
    val point: LatLon get() = LatLon(lat, lon)

    /**
     * The same place picked again: the same label, at about the same spot (within a hundredth of a degree, roughly
     * a kilometre). Two different places can share a label, so the label alone is not enough.
     */
    fun isSamePlaceAs(other: RecentPlace): Boolean =
        label.equals(other.label, ignoreCase = true) && abs(lat - other.lat) < 0.01 && abs(lon - other.lon) < 0.01
}

/** The short list of recently picked places: newest first, no repeats, never more than [MAX]. */
object RecentPlaces {
    const val MAX = 3

    private val json = Json { ignoreUnknownKeys = true }

    /** [place] goes to the front; an earlier entry for the same place is replaced, and the oldest falls off. */
    fun add(current: List<RecentPlace>, place: RecentPlace): List<RecentPlace> =
        (listOf(place) + current.filterNot { it.isSamePlaceAs(place) }).take(MAX)

    fun encode(places: List<RecentPlace>): String = json.encodeToString(places.take(MAX))

    /** Anything unreadable or out of range is dropped, so a damaged value reads as fewer places, never a crash. */
    fun decode(stored: String?): List<RecentPlace> {
        if (stored.isNullOrBlank()) return emptyList()
        val places = try {
            json.decodeFromString<List<RecentPlace>>(stored)
        } catch (e: SerializationException) {
            return emptyList()
        } catch (e: IllegalArgumentException) {
            return emptyList()
        }
        return places
            .filter { it.label.isNotBlank() && it.lat in -90.0..90.0 && it.lon in -180.0..180.0 }
            .fold(emptyList<RecentPlace>()) { kept, place -> if (kept.any { it.isSamePlaceAs(place) }) kept else kept + place }
            .take(MAX)
    }
}
