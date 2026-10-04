package tt.co.jesses.meanwhile.core

import java.io.IOException

/** A news source told us to slow down. Which source is for logs, never for users. */
class RateLimitedException(val source: String) : Exception("$source rate limited")

/** A news source failed in some way other than rate limiting. The detail is for logs, never for users. */
class SourceFailedException(val source: String, detail: String) : Exception("$source failed: $detail")

/** A news source took too long, which in practice means it is busy or throttling us. */
class SourceTimedOutException(val source: String, val afterMs: Long) : Exception("$source timed out after $afterMs ms")

/** Why headlines couldn't be loaded, in terms a screen can map to its own wording. */
enum class NewsFailure { RateLimited, Network, Unknown }

/**
 * Nothing to show, and no way to know whether the place is quiet: every source failed, or some failed and the rest
 * found nothing.
 * The message is deliberately generic; [reason] says what kind of failure it was, and [causes] holds
 * the per-source detail for logging.
 */
class NewsUnavailableException(val placeName: String, val causes: List<Throwable>) :
    Exception("Headlines are unavailable right now") {
    val reason: NewsFailure = when {
        causes.any { it is RateLimitedException || it is SourceTimedOutException } -> NewsFailure.RateLimited
        causes.any { it is IOException } -> NewsFailure.Network
        else -> NewsFailure.Unknown
    }
}
