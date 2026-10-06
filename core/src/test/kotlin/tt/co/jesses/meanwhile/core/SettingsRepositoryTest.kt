package tt.co.jesses.meanwhile.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsRepositoryTest {
    private fun newStore(scope: CoroutineScope, file: File = File.createTempFile("settings", ".preferences_pb").also { it.delete() }): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    private fun TestScope.repository(file: File? = null): Pair<SettingsRepository, DataStore<Preferences>> {
        val store = if (file == null) newStore(backgroundScope) else newStore(backgroundScope, file)
        return SettingsRepository(store) to store
    }

    @Test
    fun startsUnsetAndUnseen() = runTest {
        val (repo, _) = repository()
        assertEquals(AnalyticsChoice.Unset, repo.analyticsChoice.first())
        assertFalse(repo.introSeen.first())
    }

    @Test
    fun remembersTheChoiceAndTheIntro() = runTest {
        val (repo, _) = repository()
        repo.setAnalyticsChoice(AnalyticsChoice.Accepted)
        repo.setIntroSeen()
        assertEquals(AnalyticsChoice.Accepted, repo.analyticsChoice.first())
        assertTrue(repo.introSeen.first())

        repo.setAnalyticsChoice(AnalyticsChoice.Rejected)
        assertEquals(AnalyticsChoice.Rejected, repo.analyticsChoice.first())
    }

    @Test
    fun theChoiceSurvivesTheAppRestarting() = runTest {
        val file = File.createTempFile("settings", ".preferences_pb").also { it.delete() }
        // The first process: write the choice, then end it (DataStore allows only one live instance per file).
        val firstProcess = CoroutineScope(Dispatchers.IO + Job())
        SettingsRepository(newStore(firstProcess, file)).setAnalyticsChoice(AnalyticsChoice.Accepted)
        firstProcess.cancel()
        firstProcess.coroutineContext[Job]!!.join()

        // The next launch reads the same file.
        val reopened = SettingsRepository(newStore(backgroundScope, file))
        assertEquals(AnalyticsChoice.Accepted, reopened.analyticsChoice.first())
    }

    @Test
    fun anUnrecognisedStoredValueReadsAsUnsetNotAsAccepted() = runTest {
        val (repo, store) = repository()
        store.edit { it[stringPreferencesKey("analytics_choice")] = "Enabled_v0" }
        assertEquals(AnalyticsChoice.Unset, repo.analyticsChoice.first())
        store.edit { it[stringPreferencesKey("analytics_choice")] = "" }
        assertEquals(AnalyticsChoice.Unset, repo.analyticsChoice.first())
    }

    @Test
    fun aDamagedSettingsFileReadsAsDefaultsInsteadOfCrashing() = runTest {
        val file = File.createTempFile("settings", ".preferences_pb")
        file.writeBytes(byteArrayOf(0x13, 0x37, 0x00, 0x7F, 0x42)) // not a valid preferences file
        val (repo, _) = repository(file)

        assertEquals(AnalyticsChoice.Unset, repo.analyticsChoice.first())
        assertFalse(repo.introSeen.first())
        assertFalse(repo.shouldAskAboutAnalytics(canCollect = true))
    }

    @Test
    fun countsGoodLoadsAndPrompts() = runTest {
        val (repo, _) = repository()
        assertEquals(listOf(1, 2, 3), List(3) { repo.recordGoodLoad() })
    }

    @Test
    fun asksOnlyAfterEnoughGoodLoadsAndOnlyTwice() = runTest {
        val (repo, _) = repository()
        assertFalse(repo.shouldAskAboutAnalytics(canCollect = true), "too early")

        repeat(AnalyticsPrompt.MIN_GOOD_LOADS) { repo.recordGoodLoad() }
        assertTrue(repo.shouldAskAboutAnalytics(canCollect = true))

        repo.recordPromptShown()
        assertTrue(repo.shouldAskAboutAnalytics(canCollect = true), "asked once, may ask once more")
        repo.recordPromptShown()
        assertFalse(repo.shouldAskAboutAnalytics(canCollect = true), "asked twice, stop")
    }

    @Test
    fun neverAsksOnceTheUserHasAnswered() = runTest {
        val (repo, _) = repository()
        repeat(AnalyticsPrompt.MIN_GOOD_LOADS) { repo.recordGoodLoad() }

        repo.setAnalyticsChoice(AnalyticsChoice.Accepted)
        assertFalse(repo.shouldAskAboutAnalytics(canCollect = true))
        repo.setAnalyticsChoice(AnalyticsChoice.Rejected)
        assertFalse(repo.shouldAskAboutAnalytics(canCollect = true))
    }

    @Test
    fun neverAsksWhenThereIsNothingToOptInTo() = runTest {
        val (repo, _) = repository()
        repeat(AnalyticsPrompt.MIN_GOOD_LOADS) { repo.recordGoodLoad() }
        assertFalse(repo.shouldAskAboutAnalytics(canCollect = false))
    }

    @Test
    fun theAskRuleItselfHasNoSurprisingEdges() {
        val ask = { choice: AnalyticsChoice, loads: Int, prompts: Int, can: Boolean -> AnalyticsPrompt.shouldAsk(choice, loads, prompts, can) }
        assertTrue(ask(AnalyticsChoice.Unset, 3, 0, true))
        assertFalse(ask(AnalyticsChoice.Unset, 2, 0, true))
        assertFalse(ask(AnalyticsChoice.Unset, 3, 2, true))
        assertFalse(ask(AnalyticsChoice.Unset, 3, 0, false))
        assertFalse(ask(AnalyticsChoice.Accepted, 99, 0, true))
        assertFalse(ask(AnalyticsChoice.Rejected, 99, 0, true))
    }

    private fun place(label: String, lat: Double = 1.0, lon: Double = 2.0) = RecentPlace(label, lat, lon)

    @Test
    fun keepsTheLastThreePlacesNewestFirst() = runTest {
        val (repo, _) = repository()
        assertEquals(emptyList(), repo.recentPlaces.first())

        listOf("Reykjavik", "Lima", "Hobart", "Nuuk").forEach { repo.addRecentPlace(place(it)) }
        assertEquals(listOf("Nuuk", "Hobart", "Lima"), repo.recentPlaces.first().map { it.label })
    }

    @Test
    fun pickingAPlaceAgainMovesItToTheFrontInsteadOfRepeatingIt() = runTest {
        val (repo, _) = repository()
        listOf("Reykjavik", "Lima", "reykjavik").forEach { repo.addRecentPlace(place(it, lat = 64.1, lon = -21.9)) }
        val places = repo.recentPlaces.first()
        assertEquals(listOf("reykjavik", "Lima"), places.map { it.label })
        assertEquals(LatLon(64.1, -21.9), places.first().point)
    }

    @Test
    fun twoDifferentPlacesWithTheSameLabelAreBothKept() = runTest {
        val (repo, store) = repository()
        repo.addRecentPlace(place("Springfield", lat = 39.80, lon = -89.64))
        repo.addRecentPlace(place("Lima"))
        repo.addRecentPlace(place("Springfield", lat = 37.21, lon = -93.29))
        assertEquals(listOf("Springfield", "Lima", "Springfield"), repo.recentPlaces.first().map { it.label })

        // The first Springfield again, a few metres off: it moves to the front and is not listed twice.
        repo.addRecentPlace(place("springfield", lat = 39.801, lon = -89.641))
        val places = repo.recentPlaces.first()
        assertEquals(listOf("springfield", "Springfield", "Lima"), places.map { it.label })
        assertEquals(listOf(39.801, 37.21), places.take(2).map { it.lat })

        // A stored list that repeats a place is tidied on reading, and keeps same-named places that are far apart.
        store.edit {
            it[stringPreferencesKey("recent_places")] =
                """[{"label":"Springfield","lat":39.8,"lon":-89.64},{"label":"Springfield","lat":39.8,"lon":-89.64},{"label":"Springfield","lat":37.21,"lon":-93.29}]"""
        }
        assertEquals(listOf(39.8, 37.21), repo.recentPlaces.first().map { it.lat })
    }

    @Test
    fun recentPlacesCanBeCleared() = runTest {
        val (repo, _) = repository()
        repo.addRecentPlace(place("Lima"))
        repo.clearRecentPlaces()
        assertEquals(emptyList(), repo.recentPlaces.first())
    }

    @Test
    fun damagedOrOutOfRangeRecentPlacesAreDroppedInsteadOfCrashing() = runTest {
        val (repo, store) = repository()
        val key = stringPreferencesKey("recent_places")
        store.edit { it[key] = "not json" }
        assertEquals(emptyList(), repo.recentPlaces.first())

        store.edit { it[key] = """[{"label":"Nowhere","lat":123.0,"lon":0.0},{"label":"","lat":1.0,"lon":1.0},{"label":"Lima","lat":-12.0,"lon":-77.0}]""" }
        assertEquals(listOf("Lima"), repo.recentPlaces.first().map { it.label })

        // A longer list than the app would write is cut down, and adding still works on top of it.
        store.edit { prefs -> prefs[key] = RecentPlacesTestData.fivePlacesJson }
        assertEquals(RecentPlaces.MAX, repo.recentPlaces.first().size)
        repo.addRecentPlace(place("Hobart"))
        assertEquals("Hobart", repo.recentPlaces.first().first().label)
        assertEquals(RecentPlaces.MAX, repo.recentPlaces.first().size)
    }
}

private object RecentPlacesTestData {
    val fivePlacesJson = (1..5).joinToString(prefix = "[", postfix = "]") { """{"label":"Place $it","lat":$it.0,"lon":$it.0}""" }
}
