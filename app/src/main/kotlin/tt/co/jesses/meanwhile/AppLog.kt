package tt.co.jesses.meanwhile

import android.util.Log

/**
 * Logging that only runs in debug builds. Log lines in this app can describe where the user is (origin, antipode,
 * searched place), so a release build writes none of them.
 */
object AppLog {
    @Volatile
    var enabled: Boolean = false

    fun d(tag: String, message: String) {
        if (enabled) Log.d(tag, message)
    }

    fun w(tag: String, message: String) {
        if (enabled) Log.w(tag, message)
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        if (enabled) Log.e(tag, message, error)
    }
}
