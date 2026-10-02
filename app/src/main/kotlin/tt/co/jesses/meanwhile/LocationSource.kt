package tt.co.jesses.meanwhile

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import tt.co.jesses.meanwhile.core.LatLon
import kotlin.coroutines.resume

/** Coarse device location. Callers must hold ACCESS_COARSE_LOCATION. */
class LocationSource(context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun current(): LatLon? = suspendCancellableCoroutine { cont ->
        val start = System.nanoTime()
        val ms = { (System.nanoTime() - start) / 1_000_000 }
        AppLog.d("Meanwhile", "requesting device location")
        val cancel = CancellationTokenSource()
        cont.invokeOnCancellation { cancel.cancel() }
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancel.token)
            .addOnSuccessListener { fresh ->
                if (fresh != null) {
                    AppLog.d("Meanwhile", "got fresh location in ${ms()} ms")
                    cont.resume(fresh.toLatLon())
                } else {
                    AppLog.d("Meanwhile", "no fresh fix after ${ms()} ms, trying last known location")
                    client.lastLocation
                        .addOnSuccessListener { last ->
                            AppLog.d("Meanwhile", "last known location: ${if (last == null) "none" else "available"} after ${ms()} ms")
                            cont.resume(last?.toLatLon())
                        }
                        .addOnFailureListener { e ->
                            AppLog.w("Meanwhile", "last known location failed: ${e.message}")
                            cont.resume(null)
                        }
                }
            }
            .addOnFailureListener { e ->
                AppLog.w("Meanwhile", "location request failed after ${ms()} ms: ${e.message}")
                cont.resume(null)
            }
    }

    private fun Location.toLatLon() = LatLon(latitude, longitude)
}
