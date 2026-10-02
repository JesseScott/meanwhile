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
import tt.co.jesses.meanwhile.core.expandingSearchRings
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

        for (ring in expandingSearchRings(antipode)) {
            AppLog.d(TAG, "ring ${ring.radiusKm.roundToInt()} km: geocoding ${ring.points.size} points")
            val hits = coroutineScope {
                ring.points
                    .map { p -> async { geocoding.countryAt(p)?.let { iso -> iso to haversineKm(antipode, p) } } }
                    .awaitAll()
                    .filterNotNull()
                    .filter { it.first !in skip }
            }
            val nearest = hits.minByOrNull { it.second } ?: continue
            AppLog.d(TAG, "ring ${ring.radiusKm.roundToInt()} km: found ${nearest.first} at ${nearest.second.roundToInt()} km")
            toResolved(nearest.first, nearest.second)?.let { return it }
        }
        AppLog.w(TAG, "no land found within ${DEFAULT_SEARCH_RADII_KM.last().roundToInt()} km of the antipode")
        return null
    }

    private fun toResolved(iso: String, distanceKm: Double): ResolvedCountry? {
        val fips = fipsFor(iso) ?: return null
        val name = Locale.Builder().setRegion(iso).build().getDisplayCountry(Locale.ENGLISH).ifBlank { iso }
        return ResolvedCountry(iso, fips, name, distanceKm)
    }
}
