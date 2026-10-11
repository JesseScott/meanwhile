package tt.co.jesses.meanwhile.core

/**
 * Where the app stands on the location permission. Android shows its dialog at most twice; after the second refusal
 * a request comes straight back refused, and the only way to turn location on is the app's page in system settings.
 */
enum class LocationAccess {
    On,

    /** Off, and Android will still show its dialog if asked. */
    CanAsk,

    /** Off, and Android no longer shows its dialog: only system settings can turn it on. */
    Blocked;

    companion object {
        /**
         * A refusal that comes back faster than this was answered by Android, not by a person: no dialog was shown.
         * A person takes far longer than this to read and tap.
         */
        const val NO_DIALOG_MILLIS = 400L

        /**
         * Android only says "you may explain yourself" ([shouldExplain], its shouldShowRequestPermissionRationale)
         * between the first refusal and the second. Before the first and after the second it says no both times, so
         * [refusedBefore], saved when a refusal is seen, tells the two apart.
         */
        fun of(granted: Boolean, refusedBefore: Boolean, shouldExplain: Boolean): LocationAccess = when {
            granted -> On
            refusedBefore && !shouldExplain -> Blocked
            else -> CanAsk
        }

        /**
         * What a refusal just received says about next time: true if it should be remembered as a refusal. Closing
         * the dialog without answering is neither (slow, and nothing to explain), so it is asked again as if new.
         */
        fun refusalCounts(shouldExplain: Boolean, elapsedMillis: Long): Boolean =
            shouldExplain || elapsedMillis < NO_DIALOG_MILLIS
    }
}
