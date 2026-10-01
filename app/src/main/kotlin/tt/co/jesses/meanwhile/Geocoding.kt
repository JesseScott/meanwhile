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
import tt.co.jesses.meanwhile.core.LatLon
import tt.co.jesses.meanwhile.core.expandingSearchRings
import tt.co.jesses.meanwhile.core.fipsFor
import tt.co.jesses.meanwhile.core.haversineKm
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

data class NamedPlace(val label: String, val point: LatLon)

class Geocoding(context: Context) {
    private val geocoder = Geocoder(context, Locale.ENGLISH)

    /** ISO country code of the land at [point], or null over open water or when geocoding fails. */
    suspend fun countryAt(point: LatLon): String? =
        lookup(point.lat, point.lon).firstOrNull()?.countryCode?.uppercase()

    suspend fun search(query: String): List<NamedPlace> =
        searchByName(query).mapNotNull { address ->
            if (!address.hasLatitude() || !address.hasLongitude()) return@mapNotNull null
            val label = listOfNotNull(address.locality ?: address.featureName, address.adminArea, address.countryName)
                .distinct()
                .joinToString(", ")
            NamedPlace(label, LatLon(address.latitude, address.longitude))
        }

    private suspend fun lookup(lat: Double, lon: Double): List<Address> {
        if (!Geocoder.isPresent()) return emptyList()
        return if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses)
                    override fun onError(errorMessage: String?) = cont.resume(emptyList())
                })
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
        if (!Geocoder.isPresent()) return emptyList()
        return if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocationName(query, 5, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses)
                    override fun onError(errorMessage: String?) = cont.resume(emptyList())
                })
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
    suspend fun resolve(antipode: LatLon): ResolvedCountry? {
        geocoding.countryAt(antipode)?.let { iso -> toResolved(iso, 0.0)?.let { return it } }

        for (ring in expandingSearchRings(antipode)) {
            val hits = coroutineScope {
                ring.points
                    .map { p -> async { geocoding.countryAt(p)?.let { iso -> iso to haversineKm(antipode, p) } } }
                    .awaitAll()
                    .filterNotNull()
            }
            val nearest = hits.minByOrNull { it.second } ?: continue
            toResolved(nearest.first, nearest.second)?.let { return it }
        }
        return null
    }

    private fun toResolved(iso: String, distanceKm: Double): ResolvedCountry? {
        val fips = fipsFor(iso) ?: return null
        val name = Locale.Builder().setRegion(iso).build().getDisplayCountry(Locale.ENGLISH).ifBlank { iso }
        return ResolvedCountry(iso, fips, name, distanceKm)
    }
}
