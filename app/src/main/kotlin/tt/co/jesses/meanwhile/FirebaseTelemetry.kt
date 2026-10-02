package tt.co.jesses.meanwhile

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import tt.co.jesses.meanwhile.core.NoTelemetry
import tt.co.jesses.meanwhile.core.Telemetry
import tt.co.jesses.meanwhile.core.TelemetryEvent

/** Firebase Analytics and Crashlytics behind the app's own [Telemetry] interface. */
class FirebaseTelemetry private constructor(
    private val analytics: FirebaseAnalytics,
    private val crashlytics: FirebaseCrashlytics,
) : Telemetry {

    override val available = true

    override fun log(event: TelemetryEvent) {
        val bundle = Bundle()
        for ((key, value) in event.params) {
            when (value) {
                is Boolean -> bundle.putString(key, value.toString())
                is Int -> bundle.putLong(key, value.toLong())
                is Long -> bundle.putLong(key, value)
                is String -> bundle.putString(key, value)
            }
        }
        analytics.logEvent(event.name, bundle)
    }

    override fun recordError(error: Throwable, where: String) {
        crashlytics.recordException(SanitizedError(where, error))
    }

    override fun setEnabled(enabled: Boolean) {
        analytics.setAnalyticsCollectionEnabled(enabled)
        crashlytics.setCrashlyticsCollectionEnabled(enabled)
        // Consent mode as well, which is what Google's EU consent rules look at. Analytics follows the user's choice.
        // The ad consents are always denied: Meanwhile has no ads and no use for them.
        val analyticsStatus = if (enabled) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED
        analytics.setConsent(
            mapOf(
                FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to analyticsStatus,
                FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
                FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED,
            ),
        )
    }

    override fun clearData() {
        // Resets the random app instance ID and deletes analytics data held on the device; also drops unsent crash reports.
        runCatching { analytics.resetAnalyticsData() }
        runCatching { crashlytics.deleteUnsentReports() }
    }

    companion object {
        /** Real telemetry if a Firebase project is configured (app/google-services.json), otherwise [NoTelemetry]. */
        fun create(context: Context): Telemetry {
            val configured = FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null
            if (!configured) return NoTelemetry
            return runCatching { FirebaseTelemetry(FirebaseAnalytics.getInstance(context), FirebaseCrashlytics.getInstance()) }
                .getOrDefault(NoTelemetry)
        }
    }
}

/** Carries only where it happened and the original's type and stack frames, never its message (which can hold a URL). */
private class SanitizedError(where: String, original: Throwable) : Exception("$where: ${original::class.java.simpleName}") {
    init {
        stackTrace = original.stackTrace
    }
}
