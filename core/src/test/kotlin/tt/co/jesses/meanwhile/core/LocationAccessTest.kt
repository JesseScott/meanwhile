package tt.co.jesses.meanwhile.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocationAccessTest {
    @Test
    fun grantedIsOnWhateverElseIsTrue() {
        assertEquals(LocationAccess.On, LocationAccess.of(granted = true, refusedBefore = false, shouldExplain = false))
        assertEquals(LocationAccess.On, LocationAccess.of(granted = true, refusedBefore = true, shouldExplain = false))
    }

    @Test
    fun neverAskedCanStillAsk() {
        assertEquals(LocationAccess.CanAsk, LocationAccess.of(granted = false, refusedBefore = false, shouldExplain = false))
    }

    @Test
    fun refusedOnceCanStillAsk() {
        assertEquals(LocationAccess.CanAsk, LocationAccess.of(granted = false, refusedBefore = true, shouldExplain = true))
    }

    @Test
    fun refusedTwiceIsBlocked() {
        assertEquals(LocationAccess.Blocked, LocationAccess.of(granted = false, refusedBefore = true, shouldExplain = false))
    }

    @Test
    fun aRealRefusalCountsAndAClosedDialogDoesNot() {
        // First refusal: Android now says the app may explain itself.
        assertTrue(LocationAccess.refusalCounts(shouldExplain = true, elapsedMillis = 3_000))
        // The dialog was closed without an answer: slow, and nothing to explain.
        assertFalse(LocationAccess.refusalCounts(shouldExplain = false, elapsedMillis = 3_000))
        // No dialog at all: Android answered for the user, as it does after two refusals.
        assertTrue(LocationAccess.refusalCounts(shouldExplain = false, elapsedMillis = 50))
    }
}
