package tt.co.jesses.meanwhile

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tt.co.jesses.meanwhile.core.DEFAULT_SEARCH_RADII_KM
import tt.co.jesses.meanwhile.core.LatLon
import tt.co.jesses.meanwhile.core.NO_COVERAGE_ISO
import tt.co.jesses.meanwhile.core.estimateCoastKm
import tt.co.jesses.meanwhile.core.expandingSearchRings
import tt.co.jesses.meanwhile.core.probesAlong
import tt.co.jesses.meanwhile.core.fipsFor
import tt.co.jesses.meanwhile.core.haversineKm
import java.io.IOException
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.coroutines.resume

data class NamedPlace(val label: String, val point: LatLon)

private const val TAG = "Meanwhile"
private const val GEOCODER_TIMEOUT_MS = 8_000L

class Geocoding(context: Context) {
    private val geocoder = Geocoder(context, Locale.ENGLISH)

    init {
        AppLog.d(TAG, "Geocoder.isPresent=${Geocoder.isPresent()} sdk=${Build.VERSION.SDK_INT}")
    }

    /** ISO country code of the land at [point], or null over open water or when geocoding fails. */
    suspend fun countryAt(point: LatLon): String? {
        val start = System.nanoTime()
        val iso = lookup(point.lat, point.lon).firstOrNull()?.countryCode?.uppercase()
        val ms = (System.nanoTime() - start) / 1_000_000
        AppLog.d(TAG, "geocode (${"%.2f".format(point.lat)}, ${"%.2f".format(point.lon)}) -> ${iso ?: "no country"} in $ms ms")
        return iso
    }

    suspend fun search(query: String): List<NamedPlace> {
        val start = System.nanoTime()
        val addresses = searchByName(query)
        val ms = (System.nanoTime() - start) / 1_000_000
        AppLog.d(TAG, "search \"$query\" -> ${addresses.size} results in $ms ms")
        return addresses.toPlaces()
    }

    private fun List<Address>.toPlaces(): List<NamedPlace> =
        mapNotNull { address ->
            if (!address.hasLatitude() || !address.hasLongitude()) return@mapNotNull null
            val label = listOfNotNull(address.locality ?: address.featureName, address.adminArea, address.countryName)
                .distinct()
                .joinToString(", ")
            NamedPlace(label, LatLon(address.latitude, address.longitude))
        }

    private suspend fun lookup(lat: Double, lon: Double): List<Address> {
        if (!Geocoder.isPresent()) {
            AppLog.w(TAG, "Geocoder not present on this device")
            return emptyList()
        }
        return if (Build.VERSION.SDK_INT >= 33) {
            withTimeoutOrNull(GEOCODER_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses)
                        override fun onError(errorMessage: String?) {
                            AppLog.w(TAG, "geocoder error: $errorMessage")
                            cont.resume(emptyList())
                        }
                    })
                }
            } ?: run {
                AppLog.w(TAG, "geocoder timed out after $GEOCODER_TIMEOUT_MS ms")
                emptyList()
            }
        } else {
            withContext(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lon, 1).orEmpty()
                } catch (e: IOException) {
                    emptyList()
                }
            }
        }
    }

    private suspend fun searchByName(query: String): List<Address> {
        if (!Geocoder.isPresent()) {
            AppLog.w(TAG, "Geocoder not present on this device")
            return emptyList()
        }
        return if (Build.VERSION.SDK_INT >= 33) {
            withTimeoutOrNull(GEOCODER_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(query, 5, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses)
                        override fun onError(errorMessage: String?) {
                            AppLog.w(TAG, "geocoder search error: $errorMessage")
                            cont.resume(emptyList())
                        }
                    })
                }
            } ?: run {
                AppLog.w(TAG, "geocoder search timed out after $GEOCODER_TIMEOUT_MS ms")
                emptyList()
            }
        } else {
            withContext(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(query, 5).orEmpty()
                } catch (e: IOException) {
                    emptyList()
                }
            }
        }
    }
}

data class ResolvedCountry(
    val iso: String,
    val fips: String,
    val name: String,
    /** 0 when the antipode itself is on land, otherwise the distance to the closest land found. */
    val distanceKm: Double,
)

/** Finds the country at a point, or the nearest one when the point is open ocean. */
class AntipodeResolver(private val geocoding: Geocoding) {
    private companion object {
        const val MAX_CANDIDATES = 6
    }


    /**
     * The nearest country to [antipode] that is worth fetching news for. Territories in
     * [NO_COVERAGE_ISO] and anything in [exclude] (countries already tried and found empty)
     * are skipped, and the search keeps going outward.
     */
    suspend fun resolve(antipode: LatLon, exclude: Set<String> = emptySet()): ResolvedCountry? {
        val skip = NO_COVERAGE_ISO + exclude
        AppLog.d(TAG, "resolving antipode (${"%.3f".format(antipode.lat)}, ${"%.3f".format(antipode.lon)}), skipping $skip")
        geocoding.countryAt(antipode)?.takeIf { it !in skip }?.let { iso -> toResolved(iso, 0.0)?.let { return it } }
        AppLog.d(TAG, "antipode is not on usable land, searching outward for the nearest country")

        val rings = expandingSearchRings(antipode)
        for ((ringIndex, ring) in rings.withIndex()) {
            AppLog.d(TAG, "ring ${ring.radiusKm.roundToInt()} km: geocoding ${ring.points.size} points")
            val hits = coroutineScope {
                ring.points
                    .mapIndexed { i, p -> async { geocoding.countryAt(p)?.let { iso -> Hit(iso, haversineKm(antipode, p), i) } } }
                    .awaitAll()
                    .filterNotNull()
                    .filter { it.iso !in skip }
            }
            if (hits.isEmpty()) continue
            AppLog.d(TAG, "ring ${ring.radiusKm.roundToInt()} km: land at ${hits.size} of ${ring.points.size} points (${hits.map { it.iso }.distinct()})")
            // Every hit in a ring is exactly the ring's radius away, so which country "won" would be down to the order
            // the bearings happen to be checked in. Narrow down each direction that found land and take the nearest.
            val inner = if (ringIndex == 0) 0.0 else rings[ringIndex - 1].radiusKm
            val candidates = candidateHits(hits)
            var best: Triple<String, Double, Hit>? = null
            for (hit in candidates) {
                val bearing = hit.pointIndex * 360.0 / ring.points.size
                val (refinedIso, km) = refine(antipode, bearing, inner, ring.radiusKm, skip)
                val iso = refinedIso ?: hit.iso
                AppLog.d(TAG, "bearing ${bearing.roundToInt()}°: $iso at about ${km.roundToInt()} km")
                if (best == null || km < best.second) best = Triple(iso, km, hit)
            }
            val (iso, km, hit) = best ?: continue
            (toResolved(iso, km) ?: toResolved(hit.iso, hit.distanceKm))?.let { return it }
        }
        AppLog.w(TAG, "no land found within ${DEFAULT_SEARCH_RADII_KM.last().roundToInt()} km of the antipode")
        return null
    }

    private class Hit(val iso: String, val distanceKm: Double, val pointIndex: Int)

    /** A few directions that found land, spread over the countries found rather than all in one, to keep the checks few. */
    private fun candidateHits(hits: List<Hit>): List<Hit> =
        hits.groupBy { it.iso }.values.flatMap { it.take(2) }.take(MAX_CANDIDATES)

    /** Checks a handful of points between two rings, in parallel, along the bearing where land was found. */
    private suspend fun refine(antipode: LatLon, bearingDeg: Double, innerKm: Double, outerKm: Double, skip: Set<String>): Pair<String?, Double> {
        val probes = probesAlong(antipode, bearingDeg, innerKm, outerKm)
        val land = coroutineScope {
            probes.map { p -> async { geocoding.countryAt(p.point)?.takeIf { it !in skip } } }.awaitAll()
        }
        val km = estimateCoastKm(innerKm, outerKm, probes.map { it.distanceKm }, land.map { it != null })
        // The first probe to find land may be a different country from the ring's hit; if none did, the ring's stands.
        return land.firstOrNull { it != null } to km
    }

    private fun toResolved(iso: String, distanceKm: Double): ResolvedCountry? {
        val fips = fipsFor(iso) ?: return null
        val name = Locale.Builder().setRegion(iso).build().getDisplayCountry(Locale.ENGLISH).ifBlank { iso }
        return ResolvedCountry(iso, fips, name, distanceKm)
    }
}
