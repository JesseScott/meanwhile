package tt.co.jesses.meanwhile.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Where the user stands on sharing usage and crash data. [Unset] means they haven't been asked or haven't answered,
 * and counts as off. [Rejected] is an explicit no (they turned it off), so they are not asked again.
 */
enum class AnalyticsChoice { Unset, Accepted, Rejected }

/** How often, and when, the app may ask the user to turn on usage reports. */
object AnalyticsPrompt {
    /** Don't ask until the app has worked for them this many times. */
    const val MIN_GOOD_LOADS = 3

    /** Stop asking after this many prompts that got no answer. */
    const val MAX_PROMPTS = 2

    fun shouldAsk(choice: AnalyticsChoice, goodLoads: Int, promptsShown: Int, canCollect: Boolean): Boolean =
        canCollect && choice == AnalyticsChoice.Unset && goodLoads >= MIN_GOOD_LOADS && promptsShown < MAX_PROMPTS
}

/**
 * The app's few saved settings, on Jetpack DataStore. Kept in the core module so it can be tested on the JVM.
 *
 * The choice is stored by name, and anything unrecognised reads as [AnalyticsChoice.Unset], so a renamed or removed
 * value can't crash the app or silently turn collection on. A damaged settings file reads as defaults.
 */
class SettingsRepository(private val store: DataStore<Preferences>) {

    private val data: Flow<Preferences> = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val analyticsChoice: Flow<AnalyticsChoice> = data.map { prefs ->
        AnalyticsChoice.entries.firstOrNull { it.name == prefs[ANALYTICS_CHOICE] } ?: AnalyticsChoice.Unset
    }

    val introSeen: Flow<Boolean> = data.map { it[INTRO_SEEN] ?: false }

    /** The places the user last searched for and picked, newest first. Never the device's own location. */
    val recentPlaces: Flow<List<RecentPlace>> = data.map { decodeRecentPlaces(it[RECENT_PLACES]) }

    suspend fun addRecentPlace(place: RecentPlace) {
        store.edit { prefs ->
            prefs[RECENT_PLACES] = decodeRecentPlaces(prefs[RECENT_PLACES]).withNewest(place).encode()
        }
    }

    suspend fun setAnalyticsChoice(choice: AnalyticsChoice) {
        store.edit { it[ANALYTICS_CHOICE] = choice.name }
    }

    suspend fun setIntroSeen() {
        store.edit { it[INTRO_SEEN] = true }
    }

    /** Counts a load that gave the user something. Returns the new total. */
    suspend fun recordGoodLoad(): Int {
        var total = 0
        store.edit { prefs ->
            total = (prefs[GOOD_LOADS] ?: 0) + 1
            prefs[GOOD_LOADS] = total
        }
        return total
    }

    suspend fun recordPromptShown() {
        store.edit { it[PROMPTS_SHOWN] = (it[PROMPTS_SHOWN] ?: 0) + 1 }
    }

    suspend fun shouldAskAboutAnalytics(canCollect: Boolean): Boolean {
        val prefs = data.first()
        val choice = AnalyticsChoice.entries.firstOrNull { it.name == prefs[ANALYTICS_CHOICE] } ?: AnalyticsChoice.Unset
        return AnalyticsPrompt.shouldAsk(choice, prefs[GOOD_LOADS] ?: 0, prefs[PROMPTS_SHOWN] ?: 0, canCollect)
    }

    private companion object {
        val ANALYTICS_CHOICE = stringPreferencesKey("analytics_choice")
        val INTRO_SEEN = booleanPreferencesKey("intro_seen")
        val GOOD_LOADS = intPreferencesKey("good_loads")
        val PROMPTS_SHOWN = intPreferencesKey("prompts_shown")
        val RECENT_PLACES = stringPreferencesKey("recent_places")
    }
}
