package tt.co.jesses.meanwhile

import android.app.Application
import android.content.pm.ApplicationInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tt.co.jesses.meanwhile.core.AnalyticsChoice
import tt.co.jesses.meanwhile.core.SettingsRepository
import tt.co.jesses.meanwhile.core.Telemetry

class MeanwhileApplication : Application() {
    /** For work that should outlive any one screen, such as watching the user's analytics choice. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var settings: SettingsRepository
        private set

    lateinit var telemetry: Telemetry
        private set

    override fun onCreate() {
        super.onCreate()
        AppLog.enabled = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        settings = SettingsRepository(settingsDataStore)
        telemetry = FirebaseTelemetry.create(this)

        // The saved choice is the only thing that turns collection on or off, so the About switch, the snackbar
        // and Firebase can't disagree. Until the first value arrives collection is off (the manifest starts it off),
        // so nothing is sent before the choice is known, and nothing is sent at all unless the user opted in.
        appScope.launch {
            var previous: AnalyticsChoice? = null
            settings.analyticsChoice.collect { choice ->
                telemetry.setEnabled(choice == AnalyticsChoice.Accepted)
                if (previous == AnalyticsChoice.Accepted && choice != AnalyticsChoice.Accepted) telemetry.clearData()
                previous = choice
            }
        }
    }
}
