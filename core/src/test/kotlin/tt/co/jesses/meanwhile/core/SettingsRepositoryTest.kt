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
    fun remembersRecentPlacesNewestFirstAndKeepsOnlyThree() = runTest {
        val (repo, _) = repository()
        assertEquals(emptyList(), repo.recentPlaces.first())

        listOf("Oslo", "Lima", "Hanoi", "Cairo").forEachIndexed { i, name ->
            repo.addRecentPlace(RecentPlace(name, LatLon(i.toDouble(), i.toDouble())))
        }
        assertEquals(listOf("Cairo", "Hanoi", "Lima"), repo.recentPlaces.first().map { it.label })

        // Picking one again moves it to the front instead of adding a copy.
        repo.addRecentPlace(RecentPlace("Lima", LatLon(2.0, 2.0)))
        assertEquals(listOf("Lima", "Cairo", "Hanoi"), repo.recentPlaces.first().map { it.label })
    }

    @Test
    fun recentPlacesSurviveTheAppRestartingAndDamageReadsAsEmpty() = runTest {
        val file = File.createTempFile("settings", ".preferences_pb").also { it.delete() }
        val firstProcess = CoroutineScope(Dispatchers.IO + Job())
        SettingsRepository(newStore(firstProcess, file)).addRecentPlace(RecentPlace("Oslo, Norway", LatLon(59.9, 10.7)))
        firstProcess.cancel()
        firstProcess.coroutineContext[Job]!!.join()

        val reopened = SettingsRepository(newStore(backgroundScope, file))
        assertEquals(listOf(RecentPlace("Oslo, Norway", LatLon(59.9, 10.7))), reopened.recentPlaces.first())

        val (repo, store) = repository()
        store.edit { it[stringPreferencesKey("recent_places")] = "not a place" }
        assertEquals(emptyList(), repo.recentPlaces.first())
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
}
