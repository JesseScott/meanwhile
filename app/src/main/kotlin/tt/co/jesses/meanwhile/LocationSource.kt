package tt.co.jesses.meanwhile

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
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
        Log.d("Meanwhile", "requesting device location")
        val cancel = CancellationTokenSource()
        cont.invokeOnCancellation { cancel.cancel() }
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancel.token)
            .addOnSuccessListener { fresh ->
                if (fresh != null) {
                    Log.d("Meanwhile", "got fresh location in ${ms()} ms")
                    cont.resume(fresh.toLatLon())
                } else {
                    Log.d("Meanwhile", "no fresh fix after ${ms()} ms, trying last known location")
                    client.lastLocation
                        .addOnSuccessListener { last ->
                            Log.d("Meanwhile", "last known location: ${if (last == null) "none" else "available"} after ${ms()} ms")
                            cont.resume(last?.toLatLon())
                        }
                        .addOnFailureListener { e ->
                            Log.w("Meanwhile", "last known location failed: ${e.message}")
                            cont.resume(null)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.w("Meanwhile", "location request failed after ${ms()} ms: ${e.message}")
                cont.resume(null)
            }
    }

    private fun Location.toLatLon() = LatLon(latitude, longitude)
}
