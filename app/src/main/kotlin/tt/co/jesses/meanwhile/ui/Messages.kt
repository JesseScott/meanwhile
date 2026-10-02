package tt.co.jesses.meanwhile.ui

import tt.co.jesses.meanwhile.Progress
import tt.co.jesses.meanwhile.UiError
import tt.co.jesses.meanwhile.UiNotice

/*
 * The only place user-facing wording for loads and failures lives. State carries typed values; these turn them
 * into sentences. None of them names a news source or a technical cause.
 */

/** What the app is, in one line. Shown under the app name on the main screen and on the location intro. */
const val TAGLINE = "news from the other side of the world"

fun Progress.text(): String = when (this) {
    Progress.Locating -> "Getting your location…"
    Progress.FindingLand -> "Looking for land on the other side of the world…"
    is Progress.FetchingHeadlines -> "Fetching headlines from $country… this can take up to a minute the first time."
    Progress.ReadingSea -> "Reading the sea…"
    is Progress.TryingNext -> "$previous had no headlines, trying the next closest country…"
}

fun UiError.text(): String = when (this) {
    UiError.LocationUnavailable -> "Couldn't get your location. Try again, or search for a place."
    UiError.NoLandNearby -> "Couldn't find any land near your antipode. Try another place."
    UiError.NoHeadlines -> "No headlines found near your antipode. Try another place."
    UiError.Busy -> "Headlines are busy right now. Try again in a minute."
    UiError.Offline -> "You seem to be offline. Check your connection and try again."
    UiError.Unknown -> "Something went wrong. Try again."
}

fun UiNotice.text(): String = when (this) {
    is UiNotice.NoPlaces -> "No places found for \"$query\"."
    UiNotice.LocationDenied -> "Location is off for Meanwhile. Search for a place, or turn it on in your phone's settings."
    UiNotice.RefreshFailed -> "Couldn't refresh. Showing what we had."
}
