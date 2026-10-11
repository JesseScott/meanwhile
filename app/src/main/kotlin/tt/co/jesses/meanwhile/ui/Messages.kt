package tt.co.jesses.meanwhile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import tt.co.jesses.meanwhile.Progress
import tt.co.jesses.meanwhile.R
import tt.co.jesses.meanwhile.UiError
import tt.co.jesses.meanwhile.UiNotice
import tt.co.jesses.meanwhile.core.LocationAccess
import tt.co.jesses.meanwhile.core.Recency
import java.math.RoundingMode
import java.text.NumberFormat

/*
 * The only place user-facing wording for loads and failures is chosen. State carries typed values; these turn them
 * into sentences from res/values/strings.xml. None of them names a news source or a technical cause.
 */

/** What the app is, in one line. Shown under the app name on the main screen and on the location intro. */
@Composable
@ReadOnlyComposable
fun tagline(): String = stringResource(R.string.tagline)

@Composable
@ReadOnlyComposable
fun Progress.text(): String = when (this) {
    Progress.Locating -> stringResource(R.string.progress_locating)
    Progress.FindingLand -> stringResource(R.string.progress_finding_land)
    is Progress.FetchingHeadlines -> stringResource(R.string.progress_fetching_headlines, country)
    Progress.ReadingSea -> stringResource(R.string.progress_reading_sea)
    is Progress.TryingNext -> stringResource(R.string.progress_trying_next, previous)
    Progress.CheckingMore -> stringResource(R.string.progress_checking_more)
}

@Composable
@ReadOnlyComposable
fun UiError.text(): String = when (this) {
    UiError.LocationOff -> stringResource(R.string.error_location_off)
    UiError.LocationUnavailable -> stringResource(R.string.error_location_unavailable)
    UiError.NoLandNearby -> stringResource(R.string.error_no_land_nearby)
    UiError.NoHeadlines -> stringResource(R.string.error_no_headlines)
    UiError.Busy -> stringResource(R.string.error_busy)
    UiError.Offline -> stringResource(R.string.error_offline)
    UiError.Unknown -> stringResource(R.string.error_unknown)
}

@Composable
@ReadOnlyComposable
fun UiNotice.text(): String = when (this) {
    is UiNotice.NoPlaces -> stringResource(R.string.notice_no_places, query)
    UiNotice.LocationDenied -> stringResource(R.string.notice_location_denied)
    UiNotice.RefreshFailed -> stringResource(R.string.notice_refresh_failed)
}

/** The Location row on the About screen: its detail line, and the button that turns location on (none when it is on). */
@Composable
@ReadOnlyComposable
fun LocationAccess.detail(): String = when (this) {
    LocationAccess.On -> stringResource(R.string.location_access_on)
    LocationAccess.CanAsk -> stringResource(R.string.location_access_can_ask)
    LocationAccess.Blocked -> stringResource(R.string.location_access_blocked)
}

@Composable
@ReadOnlyComposable
fun LocationAccess.action(): String? = when (this) {
    LocationAccess.On -> null
    LocationAccess.CanAsk -> stringResource(R.string.location_access_turn_on)
    LocationAccess.Blocked -> stringResource(R.string.open_settings)
}

@Composable
@ReadOnlyComposable
fun Recency.text(): String = when (this) {
    Recency.Today -> stringResource(R.string.recency_today)
    Recency.Yesterday -> stringResource(R.string.recency_yesterday)
    Recency.Earlier -> stringResource(R.string.recency_earlier)
}

/** A whole number with the locale's digit grouping ("12,300"), to hand to a string resource as text. */
fun wholeNumber(value: Int): String = NumberFormat.getIntegerInstance().format(value)

/** A number with exactly one decimal in the locale's style ("1.5"), to hand to a string resource as text. */
fun oneDecimal(value: Double): String = NumberFormat.getNumberInstance().apply {
    minimumFractionDigits = 1
    maximumFractionDigits = 1
    roundingMode = RoundingMode.HALF_UP
}.format(value)
