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

    /**
     * Which wording of the promise the user is answering. Raise it when reports start to carry something they did
     * not before: a choice made, and the prompts shown, under an older number no longer count, so the app asks again.
     * 2: reports include the country of the headlines loaded.
     */
    const val CONSENT_VERSION = 2

    fun shouldAsk(choice: AnalyticsChoice, goodLoads: Int, promptsShown: Int, canCollect: Boolean): Boolean =
        canCollect && choice == AnalyticsChoice.Unset && goodLoads >= MIN_GOOD_LOADS && promptsShown < MAX_PROMPTS
}

/**
 * The app's few saved settings and the recently picked places, on Jetpack DataStore. Kept in the core module so it can be tested on the JVM.
 *
 * The choice is stored by name, and anything unrecognised reads as [AnalyticsChoice.Unset], so a renamed or removed
 * value can't crash the app or silently turn collection on. A damaged settings file reads as defaults.
 *
 * The choice and the count of prompts belong to the [AnalyticsPrompt.CONSENT_VERSION] they were made under. Under an
 * older one they read as unset and zero, so collection is off until the user answers the new question.
 */
class SettingsRepository(private val store: DataStore<Preferences>) {

    private val data: Flow<Preferences> = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val analyticsChoice: Flow<AnalyticsChoice> = data.map { it.choice() }

    val introSeen: Flow<Boolean> = data.map { it[INTRO_SEEN] ?: false }

    /** The last few places the user picked from a search, newest first. Kept on the phone only. */
    val recentPlaces: Flow<List<RecentPlace>> = data.map { RecentPlaces.decode(it[RECENT_PLACES]) }

    suspend fun setAnalyticsChoice(choice: AnalyticsChoice) {
        store.edit {
            it[ANALYTICS_CHOICE] = choice.name
            it[CONSENT_VERSION] = AnalyticsPrompt.CONSENT_VERSION
        }
    }

    suspend fun setIntroSeen() {
        store.edit { it[INTRO_SEEN] = true }
    }

    suspend fun addRecentPlace(place: RecentPlace) {
        store.edit { it[RECENT_PLACES] = RecentPlaces.encode(RecentPlaces.add(RecentPlaces.decode(it[RECENT_PLACES]), place)) }
    }

    suspend fun clearRecentPlaces() {
        store.edit { it.remove(RECENT_PLACES) }
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
        store.edit {
            val shown = it.promptsShown()
            // The first prompt under a new consent version starts that version's record: the old answer goes.
            if (!it.consentIsCurrent()) it.remove(ANALYTICS_CHOICE)
            it[CONSENT_VERSION] = AnalyticsPrompt.CONSENT_VERSION
            it[PROMPTS_SHOWN] = shown + 1
        }
    }

    suspend fun shouldAskAboutAnalytics(canCollect: Boolean): Boolean {
        val prefs = data.first()
        return AnalyticsPrompt.shouldAsk(prefs.choice(), prefs[GOOD_LOADS] ?: 0, prefs.promptsShown(), canCollect)
    }

    /** Builds from before consent had a version stored none, which counts as 1. */
    private fun Preferences.consentIsCurrent() = (this[CONSENT_VERSION] ?: 1) == AnalyticsPrompt.CONSENT_VERSION

    private fun Preferences.choice(): AnalyticsChoice =
        if (consentIsCurrent()) AnalyticsChoice.entries.firstOrNull { it.name == this[ANALYTICS_CHOICE] } ?: AnalyticsChoice.Unset
        else AnalyticsChoice.Unset

    private fun Preferences.promptsShown(): Int = if (consentIsCurrent()) this[PROMPTS_SHOWN] ?: 0 else 0

    private companion object {
        val ANALYTICS_CHOICE = stringPreferencesKey("analytics_choice")
        val INTRO_SEEN = booleanPreferencesKey("intro_seen")
        val GOOD_LOADS = intPreferencesKey("good_loads")
        val PROMPTS_SHOWN = intPreferencesKey("prompts_shown")
        val CONSENT_VERSION = intPreferencesKey("consent_version")
        val RECENT_PLACES = stringPreferencesKey("recent_places")
    }
}
