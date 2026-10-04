package tt.co.jesses.meanwhile

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import tt.co.jesses.meanwhile.core.LatLon
import kotlin.coroutines.resume

/**
 * Coarse device location. Callers must hold ACCESS_COARSE_LOCATION.
 *
 * Knowing which half of the world you are on doesn't need a precise or brand-new fix, so the cheap answer comes first:
 * a recent last-known location is used straight away, a fresh fix is asked for only if there isn't one, and if that
 * is slow (indoors, no recent GPS or Wi-Fi position) an older last-known location beats an error.
 */
class LocationSource(private val context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    /** False when the phone's location switch is off, in which case no fix will ever come. */
    fun isEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return true
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    suspend fun current(): LatLon? {
        val start = System.nanoTime()
        val ms = { (System.nanoTime() - start) / 1_000_000 }
        AppLog.d("Meanwhile", "requesting device location")

        lastKnown(maxAgeMs = RECENT_MS)?.let {
            AppLog.d("Meanwhile", "using a recent last-known location after ${ms()} ms")
            return it
        }
        val fresh = withTimeoutOrNull(FRESH_FIX_TIMEOUT_MS) { freshFix() }
        if (fresh != null) {
            AppLog.d("Meanwhile", "got a fresh location in ${ms()} ms")
            return fresh
        }
        AppLog.d("Meanwhile", "no fresh fix after ${ms()} ms, trying any last-known location")
        return lastKnown(maxAgeMs = STALE_OK_MS)
    }

    @SuppressLint("MissingPermission")
    private suspend fun lastKnown(maxAgeMs: Long): LatLon? = suspendCancellableCoroutine { cont ->
        client.lastLocation
            .addOnSuccessListener { last ->
                val ageMs = last?.let { (System.currentTimeMillis() - it.time).coerceAtLeast(0) }
                cont.resume(if (last != null && ageMs != null && ageMs <= maxAgeMs) last.toLatLon() else null)
            }
            .addOnFailureListener { e ->
                AppLog.w("Meanwhile", "last known location failed: ${e.message}")
                cont.resume(null)
            }
    }

    @SuppressLint("MissingPermission")
    private suspend fun freshFix(): LatLon? = suspendCancellableCoroutine { cont ->
        val cancel = CancellationTokenSource()
        cont.invokeOnCancellation { cancel.cancel() }
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancel.token)
            .addOnSuccessListener { cont.resume(it?.toLatLon()) }
            .addOnFailureListener { e ->
                AppLog.w("Meanwhile", "location request failed: ${e.message}")
                cont.resume(null)
            }
    }

    private fun Location.toLatLon() = LatLon(latitude, longitude)

    private companion object {
        const val RECENT_MS = 2 * 60 * 60 * 1000L // a position from the last two hours is as good as new for this
        const val STALE_OK_MS = 24 * 60 * 60 * 1000L // better a day-old position than an error screen
        const val FRESH_FIX_TIMEOUT_MS = 30_000L
    }
}
