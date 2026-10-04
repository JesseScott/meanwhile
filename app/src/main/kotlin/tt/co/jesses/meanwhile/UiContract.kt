package tt.co.jesses.meanwhile

import tt.co.jesses.meanwhile.core.Article
import tt.co.jesses.meanwhile.core.LatLon
import tt.co.jesses.meanwhile.core.MarineConditions
import tt.co.jesses.meanwhile.core.ModeSwitchVia

/*
 * The screen's contract: the UI sends [UiEvent]s, the view model answers with an immutable [UiState] and
 * occasional one-shot [UiEffect]s. Nothing here is user-facing text; wording lives in the UI layer, so what the
 * state says ("rate limited") never leaks the name of a news source.
 */

enum class Status { Idle, Loading, Ready, Error }

/** What to show when the antipode is open water: headlines from the nearest land, or the sea itself. */
enum class ViewMode { Land, Ocean }

/** What a load is doing right now, for the status line. */
sealed interface Progress {
    data object Locating : Progress
    data object FindingLand : Progress
    data class FetchingHeadlines(val country: String) : Progress
    data object ReadingSea : Progress
    data class TryingNext(val previous: String) : Progress

    /** Headlines are already showing, and other sources are still answering. */
    data object CheckingMore : Progress
}

/** Why a load failed outright, and nothing about which service failed. */
enum class UiError { LocationUnavailable, NoLandNearby, NoHeadlines, Busy, Offline, Unknown }

/** Something worth saying that doesn't replace the screen. */
sealed interface UiNotice {
    data class NoPlaces(val query: String) : UiNotice
    data object LocationDenied : UiNotice
    data object RefreshFailed : UiNotice
}

data class UiState(
    val status: Status = Status.Idle,
    val mode: ViewMode = ViewMode.Land,
    /** Where the user is, as a label ("Your location" or a searched place). */
    val originLabel: String? = null,
    val antipode: LatLon? = null,
    val country: ResolvedCountry? = null,
    val articles: List<Article> = emptyList(),
    /** Sea conditions at the antipode, set in [ViewMode.Ocean]; null if the source had none. */
    val marine: MarineConditions? = null,
    /** The window the articles came from: "24h", or "7d" when the last day was thin. */
    val window: String = "24h",
    val searchResults: List<NamedPlace> = emptyList(),
    /** Set with [Status.Error]. */
    val error: UiError? = null,
    val notice: UiNotice? = null,
    /** What the load is doing; only meaningful while [status] is [Status.Loading]. */
    val progress: Progress? = null,
    /** A pull-to-refresh or retry is running over content that stays on screen. */
    val isRefreshing: Boolean = false,
    /** Headlines are showing, but some sources haven't answered yet and more may be added. */
    val loadingMore: Boolean = false,
)

/** True when the antipode is open water, so the user can choose between nearest land and the sea. */
val UiState.antipodeIsWater: Boolean
    get() = (country?.distanceKm ?: 0.0) > 0

val UiState.showingOcean: Boolean
    get() = mode == ViewMode.Ocean && antipodeIsWater

sealed interface UiEvent {
    /** The location permission is held; find the user. */
    data object UseMyLocation : UiEvent
    data object LocationDenied : UiEvent
    data class Search(val query: String) : UiEvent
    data class PickPlace(val place: NamedPlace) : UiEvent
    data class SetMode(val mode: ViewMode, val via: ModeSwitchVia = ModeSwitchVia.Switch) : UiEvent
    /** Pull to refresh, or "try again" after an error. */
    data object Refresh : UiEvent

    /** The user dismissed the list of matching places without choosing one. */
    data object DismissPlaces : UiEvent
}

/** Things that happen once, not things that are true: a screen shows each and forgets it. */
sealed interface UiEffect {
    /** The antipode is open water and we are showing the sea; offer headlines from the nearest land. */
    data class OfferNearestLand(val country: String, val distanceKm: Int) : UiEffect

    /** The app has worked for the user a few times: ask, gently, if they'd share usage and crash reports. */
    data object AskToShareUsage : UiEffect
}
