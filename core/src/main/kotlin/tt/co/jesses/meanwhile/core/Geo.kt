package tt.co.jesses.meanwhile.core

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

const val EARTH_RADIUS_KM = 6371.0088

data class LatLon(val lat: Double, val lon: Double) {
    init {
        require(lat in -90.0..90.0) { "lat out of range: $lat" }
        require(lon in -180.0..180.0) { "lon out of range: $lon" }
    }
}

/** Wraps any longitude into [-180, 180). */
fun normalizeLon(lon: Double): Double = ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0

/** The point on the opposite side of the Earth. */
fun LatLon.antipode(): LatLon = LatLon(-lat, normalizeLon(lon + 180.0))

fun haversineKm(a: LatLon, b: LatLon): Double {
    val dLat = rad(b.lat - a.lat)
    val dLon = rad(b.lon - a.lon)
    val h = sin(dLat / 2).pow(2) + cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(h)))
}

fun LatLon.destination(bearingDeg: Double, distanceKm: Double): LatLon {
    val d = distanceKm / EARTH_RADIUS_KM
    val bearing = rad(bearingDeg)
    val lat1 = rad(lat)
    val lon1 = rad(lon)
    val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(bearing))
    val lon2 = lon1 + atan2(sin(bearing) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
    return LatLon(deg(lat2).coerceIn(-90.0, 90.0), normalizeLon(deg(lon2)))
}

/** Rough UTC offset from longitude alone (solar time), good enough for an "about" clock. */
fun approxUtcOffsetHours(lon: Double): Int = (lon / 15.0).roundToInt().coerceIn(-12, 14)

class SearchRing(val radiusKm: Double, val points: List<LatLon>)

val DEFAULT_SEARCH_RADII_KM = listOf(25.0, 50.0, 100.0, 200.0, 400.0, 800.0, 1600.0, 3200.0, 5000.0, 8000.0)

/**
 * Rings of points around [center], nearest first. When the antipode is open ocean, a caller
 * geocodes each ring in turn to find the closest land.
 */
fun expandingSearchRings(
    center: LatLon,
    radiiKm: List<Double> = DEFAULT_SEARCH_RADII_KM,
    bearings: Int = 16,
): List<SearchRing> = radiiKm.map { radius ->
    SearchRing(radius, List(bearings) { i -> center.destination(i * 360.0 / bearings, radius) })
}

private fun rad(deg: Double) = deg * PI / 180.0
private fun deg(rad: Double) = rad * 180.0 / PI

/** A point to check on the way out from a centre, [distanceKm] away from it. */
class Probe(val distanceKm: Double, val point: LatLon)

/**
 * [steps] evenly spaced probes along [bearingDeg] between [innerKm] and [outerKm], neither end included. The search
 * rings only say that land is somewhere between two radii (each double the one before, so 1,600 to 3,200 km is a
 * factor of two); these narrow it down along the bearing where land was found.
 */
fun probesAlong(center: LatLon, bearingDeg: Double, innerKm: Double, outerKm: Double, steps: Int = 7): List<Probe> =
    List(steps) { i ->
        val d = innerKm + (outerKm - innerKm) * (i + 1) / (steps + 1)
        Probe(d, center.destination(bearingDeg, d))
    }

/**
 * Where the coast probably is along a bearing, given which probes (in order of distance) found land: halfway between
 * the last probe that did not and the first that did. With no land in between it is halfway between the last probe
 * and [outerKm], where the ring found some.
 */
fun estimateCoastKm(innerKm: Double, outerKm: Double, probeKm: List<Double>, isLand: List<Boolean>): Double {
    require(probeKm.size == isLand.size) { "one answer per probe" }
    val first = isLand.indexOfFirst { it }
    return when {
        first == 0 -> (innerKm + probeKm[0]) / 2
        first > 0 -> (probeKm[first - 1] + probeKm[first]) / 2
        probeKm.isEmpty() -> outerKm
        else -> (probeKm.last() + outerKm) / 2
    }
}

/** A distance worth showing: to the nearest 50 km under 1,000 km, to the nearest 100 km above. Zero stays zero. */
fun roughKm(km: Double): Int = when {
    km <= 0 -> 0
    km < 1000 -> (kotlin.math.round(km / 50) * 50).toInt().coerceAtLeast(50)
    else -> (kotlin.math.round(km / 100) * 100).toInt()
}
