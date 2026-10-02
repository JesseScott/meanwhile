package tt.co.jesses.meanwhile.core

import java.io.IOException

/** A news source told us to slow down. Which source is for logs, never for users. */
class RateLimitedException(val source: String) : Exception("$source rate limited")

/** A news source failed in some way other than rate limiting. The detail is for logs, never for users. */
class SourceFailedException(val source: String, detail: String) : Exception("$source failed: $detail")

/** Why headlines couldn't be loaded, in terms a screen can map to its own wording. */
enum class NewsFailure { RateLimited, Network, Unknown }

/**
 * Every source failed, so there is nothing to show and no way to know whether the place is quiet.
 * The message is deliberately generic; [reason] says what kind of failure it was, and [causes] holds
 * the per-source detail for logging.
 */
class NewsUnavailableException(val placeName: String, val causes: List<Throwable>) :
    Exception("Headlines are unavailable right now") {
    val reason: NewsFailure = when {
        causes.any { it is RateLimitedException } -> NewsFailure.RateLimited
        causes.any { it is IOException } -> NewsFailure.Network
        else -> NewsFailure.Unknown
    }
}
