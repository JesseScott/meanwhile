package tt.co.jesses.meanwhile.core

/**
 * Minimal logging hook so the pure-Kotlin core can log without depending on Android.
 * The app points [sink] at Logcat; by default messages are dropped.
 */
object Trace {
    @Volatile
    var sink: (String) -> Unit = {}

    inline fun log(message: () -> String) = sink(message())

    /** Runs [block], logging how long it took and whether it threw. */
    inline fun <T> timed(label: String, block: () -> T): T {
        val start = System.nanoTime()
        try {
            val result = block()
            sink("$label took ${(System.nanoTime() - start) / 1_000_000} ms")
            return result
        } catch (e: Throwable) {
            sink("$label FAILED after ${(System.nanoTime() - start) / 1_000_000} ms: ${e::class.simpleName}: ${e.message}")
            throw e
        }
    }
}
