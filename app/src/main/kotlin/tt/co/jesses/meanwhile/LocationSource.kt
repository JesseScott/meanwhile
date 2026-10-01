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
        val cancel = CancellationTokenSource()
        cont.invokeOnCancellation { cancel.cancel() }
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancel.token)
            .addOnSuccessListener { fresh ->
                if (fresh != null) {
                    cont.resume(fresh.toLatLon())
                } else {
                    client.lastLocation
                        .addOnSuccessListener { last -> cont.resume(last?.toLatLon()) }
                        .addOnFailureListener { cont.resume(null) }
                }
            }
            .addOnFailureListener { cont.resume(null) }
    }

    private fun Location.toLatLon() = LatLon(latitude, longitude)
}
