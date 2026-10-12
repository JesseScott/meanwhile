package tt.co.jesses.meanwhile.ui

import android.net.Uri
import android.text.format.DateUtils
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.width
import tt.co.jesses.meanwhile.NamedPlace
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tt.co.jesses.meanwhile.R
import tt.co.jesses.meanwhile.ResolvedCountry
import tt.co.jesses.meanwhile.Status
import tt.co.jesses.meanwhile.UiError
import tt.co.jesses.meanwhile.UiEvent
import tt.co.jesses.meanwhile.UiNotice
import tt.co.jesses.meanwhile.UiState
import tt.co.jesses.meanwhile.ViewMode
import tt.co.jesses.meanwhile.antipodeIsWater
import tt.co.jesses.meanwhile.core.Article
import tt.co.jesses.meanwhile.core.DayPhase
import tt.co.jesses.meanwhile.core.Recency
import tt.co.jesses.meanwhile.core.groupedByRecency
import tt.co.jesses.meanwhile.core.LatLon
import tt.co.jesses.meanwhile.core.MarineConditions
import tt.co.jesses.meanwhile.core.TelemetryEvent
import tt.co.jesses.meanwhile.core.approxUtcOffsetHours
import tt.co.jesses.meanwhile.core.compassPoint
import tt.co.jesses.meanwhile.core.dayPhaseOf
import tt.co.jesses.meanwhile.core.flagEmoji
import tt.co.jesses.meanwhile.core.oceanAt
import tt.co.jesses.meanwhile.core.roughKm
import tt.co.jesses.meanwhile.core.seaState
import tt.co.jesses.meanwhile.core.seenInstant
import tt.co.jesses.meanwhile.core.sunAltitudeDeg
import tt.co.jesses.meanwhile.showingOcean
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/** Screens shorter than this (small phones, landscape) get a shorter header: no tagline, and a one-line note. */
private const val SHORT_SCREEN_DP = 700

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeanwhileScreen(
    state: UiState,
    showFlagWash: Boolean,
    onUseLocation: () -> Unit,
    onEvent: (UiEvent) -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
) {
    // Headlines sit under Today / Yesterday / Earlier headings; a late arrival slots into its own day.
    val groups = remember(state.articles) { state.articles.groupedByRecency(Instant.now(), ZoneId.systemDefault()) }
    // The search text and focus live here because the list below reacts to them: an empty, focused bar (or a screen
    // with nothing on it yet) offers the recently picked places at the top of the list.
    var query by rememberSaveable { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    val showRecent = state.recentPlaces.isNotEmpty() && state.searchResults.isEmpty() && query.isEmpty() &&
        (searchFocused || state.status == Status.Idle)
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    // The list may be scrolled down when the bar is tapped; bring the recent places into view.
    LaunchedEffect(showRecent) { if (showRecent) listState.scrollToItem(0) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        // The part that stays put. It only ever holds things whose size doesn't depend on what is loading:
        // the place (one line each), the view switch, and the search bar. Messages, errors and progress text
        // live in the list below, so nothing here moves when a load starts or finishes. With big text or a small
        // screen it would fill the phone, so it is capped: the lines above the search bar scroll on their own, and
        // the search bar stays in view whatever happens.
        Box(Modifier.fillMaxWidth()) {
            // The country's flag as a faint wash behind it all. It matches the column's size and never sets it,
            // and there is none over the sea, before a country is known, or when it is turned off in About.
            FlagWash(
                iso = state.country?.iso?.takeIf { showFlagWash && state.antipode != null && !state.showingOcean },
                modifier = Modifier.matchParentSize(),
            )
            // The screen's height, not the window's, so the header doesn't change when the keyboard opens.
            val shortScreen = LocalConfiguration.current.screenHeightDp < SHORT_SCREEN_DP
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = this@BoxWithConstraints.maxHeight * 0.5f)
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Text here stops growing at 1.5x, so the largest settings don't hand half the screen to the header.
                CappedFontScale {
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Header(state, compact = shortScreen, onOpenAbout = onOpenAbout)
                        if (state.antipodeIsWater) ModeSwitch(state.mode) { onEvent(UiEvent.SetMode(it)) }
                    }
                    SearchBar(
                        query = query,
                        onQueryChange = { query = it },
                        onFocusChange = { searchFocused = it },
                        places = state.searchResults,
                        onSearch = { onEvent(UiEvent.Search(it)) },
                        onPick = { onEvent(UiEvent.PickPlace(it)) },
                        onDismissPlaces = { onEvent(UiEvent.DismissPlaces) },
                        onUseLocation = onUseLocation,
                    )
                }
            }
        }
        // A fixed-height strip: the rule under the header, with a thin progress bar over it while anything loads.
        Box(Modifier.fillMaxWidth().height(4.dp)) {
            HorizontalDivider(Modifier.align(Alignment.BottomCenter))
            if (state.status == Status.Loading || state.loadingMore) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), trackColor = MaterialTheme.colorScheme.primaryContainer)
            }
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onEvent(UiEvent.Refresh) },
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (showRecent) item(key = "recent") {
                    RecentPlaces(
                        places = state.recentPlaces,
                        onPick = {
                            keyboard?.hide()
                            focus.clearFocus(force = true)
                            onEvent(UiEvent.PickPlace(it))
                        },
                        onClear = { onEvent(UiEvent.ClearRecentPlaces) },
                    )
                }
                state.notice?.let { notice ->
                    item(key = "notice") { NoticeItem(notice, onOpenSettings) }
                }
                when (state.status) {
                    Status.Loading -> item(key = "loading") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(state.progress?.text() ?: stringResource(R.string.progress_loading), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Status.Error -> item(key = "error") {
                        Column {
                            Text((state.error ?: UiError.Unknown).text(), color = MaterialTheme.colorScheme.error)
                            val noLocation = state.error == UiError.LocationUnavailable || state.error == UiError.LocationOff
                            Row {
                                TextButton(onClick = {
                                    // Without a location there is nothing to refresh, so ask for the location again.
                                    if (noLocation) onUseLocation() else onEvent(UiEvent.Refresh)
                                }) { Text(stringResource(R.string.error_try_again)) }
                                // Only the phone's own switch can fix this, so say where it is.
                                if (state.error == UiError.LocationOff) {
                                    TextButton(onClick = onOpenLocationSettings) { Text(stringResource(R.string.error_location_settings)) }
                                }
                            }
                        }
                    }
                    // A notice (such as "location is off") says it better than the generic hint, so don't show both.
                    Status.Idle -> if (state.notice == null) item(key = "hint") {
                        Text(
                            stringResource(R.string.main_hint),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Status.Ready -> {
                        if (state.showingOcean) {
                            item { OceanCard(state) }
                        } else {
                            if (state.window == "7d" && groups.firstOrNull()?.first != Recency.Today) {
                                item { Text(stringResource(R.string.main_quiet_today), style = MaterialTheme.typography.bodySmall) }
                            }
                            if (state.articles.isEmpty()) {
                                item {
                                    val name = state.country?.name
                                    Text(
                                        if (name != null) stringResource(R.string.main_no_headlines_for_country, name)
                                        else stringResource(R.string.main_no_headlines_for_region),
                                    )
                                }
                            }
                            groups.forEach { (recency, articles) ->
                                item(key = "group-$recency") { RecencyHeading(recency) }
                                items(articles, key = { it.url }) { ArticleRow(it) }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun NoticeItem(notice: UiNotice, onOpenSettings: () -> Unit) {
    Column {
        Text(
            notice.text(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (notice == UiNotice.RefreshFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Android stops showing the permission dialog after two refusals, so the only way back is the app's settings.
        if (notice == UiNotice.LocationDenied) TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.open_settings)) }
    }
}

@Composable
private fun ModeSwitch(mode: ViewMode, onSetMode: (ViewMode) -> Unit) {
    val options = listOf(ViewMode.Land to R.string.mode_nearest_land, ViewMode.Ocean to R.string.mode_open_ocean)
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = mode == value,
                onClick = { onSetMode(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                // The default is the secondary (teal) container, which read as a second highlight colour.
                // One accent for "selected" and "actionable" everywhere: the primary indigo.
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    activeBorderColor = MaterialTheme.colorScheme.primary,
                ),
                // No checkmark: the colour already marks the choice, and the icon's width made large labels wrap,
                // which grew the header.
                icon = {},
            ) { Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/**
 * One row to say where to look: type a place and press search, or tap the target to use the phone's location.
 * Matching places drop down under the bar as an overlay, so they stay in view whatever the list below does and
 * don't push anything around.
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    places: List<NamedPlace>,
    onSearch: (String) -> Unit,
    onPick: (NamedPlace) -> Unit,
    onDismissPlaces: () -> Unit,
    onUseLocation: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val density = LocalDensity.current
    var barWidth by remember { mutableStateOf(0.dp) }
    // Closing the popup can hand focus back to the field, so clear it again once the matches are gone.
    var pickedPlace by remember { mutableStateOf(false) }
    LaunchedEffect(places.isEmpty(), pickedPlace) {
        if (pickedPlace && places.isEmpty()) {
            keyboard?.hide()
            focus.clearFocus(force = true)
            pickedPlace = false
        }
    }
    Box {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { onFocusChange(it.isFocused) }
                .onGloballyPositioned { barWidth = with(density) { it.size.width.toDp() } },
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                Row {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            onQueryChange("")
                            onDismissPlaces()
                        }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_clear)) }
                    }
                    IconButton(onClick = {
                        onQueryChange("")
                        keyboard?.hide()
                        focus.clearFocus()
                        onUseLocation()
                    }) { Icon(MyLocationIcon, contentDescription = stringResource(R.string.search_use_my_location)) }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (query.isNotBlank()) {
                    onSearch(query)
                    keyboard?.hide()
                    focus.clearFocus()
                }
            }),
        )
        // Not focusable, so showing the matches doesn't steal focus or close the keyboard from under the user.
        DropdownMenu(
            expanded = places.isNotEmpty(),
            onDismissRequest = onDismissPlaces,
            modifier = Modifier.width(barWidth),
            properties = PopupProperties(focusable = false),
        ) {
            places.forEach { place ->
                DropdownMenuItem(
                    text = { Text(place.label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        keyboard?.hide()
                        focus.clearFocus(force = true)
                        pickedPlace = true
                        onPick(place)
                    },
                )
            }
        }
    }
}

/** The last few places picked from a search, to go back to with one tap. Lives in the list, so it never covers the bar. */
@Composable
private fun RecentPlaces(places: List<NamedPlace>, onPick: (NamedPlace) -> Unit, onClear: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.recent_places_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onClear) { Text(stringResource(R.string.recent_places_clear)) }
        }
        places.forEach { place ->
            HorizontalDivider()
            Text(
                place.label,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(place) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Material's "my location" target, drawn here because the core icon set doesn't include it. */
private val MyLocationIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "MyLocation",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(
            "M12,8c-2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4 -1.79,-4 -4,-4zM20.94,11c-0.46,-4.17 -3.77,-7.48 -7.94,-7.94L13,1h-2v2.06" +
                "C6.83,3.52 3.52,6.83 3.06,11L1,11v2h2.06c0.46,4.17 3.77,7.48 7.94,7.94L11,23h2v-2.06c4.17,-0.46 7.48,-3.77 7.94,-7.94L23,13v-2h-2.06z" +
                "M12,19c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z",
        ).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()
}

@Composable
private fun Header(state: UiState, compact: Boolean, onOpenAbout: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // The settings icon shares the title's row, so it lines up with "Meanwhile" and not with the middle of the
        // title and tagline together. The tagline sits under the whole row.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = onOpenAbout) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.header_open_about))
            }
        }
        if (!compact) {
            Text(
                tagline(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.offset(y = (-8).dp),
            )
        }
        val country = state.country
        val antipode = state.antipode
        if (country != null && antipode != null) {
            val ocean = state.showingOcean
            val title = if (ocean) "🌊 ${oceanAt(antipode).text()}" else "${flagEmoji(country.iso)} ${country.name}"
            // Each line is one line, and both views use the same text for the open-water note, so switching
            // between them can't change the header's height.
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val place = state.originLabel?.substringBefore(",")?.trim()
            val time = localTimeThere(antipode)
            Text(
                if (place != null) stringResource(R.string.header_opposite_place, place, time)
                else stringResource(R.string.header_opposite_you, time),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (country.distanceKm > 0) {
                Text(
                    openWaterNote(country),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun openWaterNote(country: ResolvedCountry): String =
    stringResource(R.string.header_open_water_note, country.name, wholeNumber(roughKm(country.distanceKm)))

private fun localTimeThere(antipode: LatLon): String {
    val offset = ZoneOffset.ofHours(approxUtcOffsetHours(antipode.lon))
    return ZonedDateTime.now(offset).format(DateTimeFormatter.ofPattern("h:mm a"))
}

@Composable
private fun sunDescription(antipode: LatLon): String {
    val altitude = sunAltitudeDeg(antipode, Instant.now())
    val degrees = abs(altitude).roundToInt()
    return when (dayPhaseOf(altitude)) {
        DayPhase.Day -> stringResource(R.string.ocean_sun_day, degrees)
        DayPhase.Twilight -> stringResource(R.string.ocean_sun_twilight, degrees)
        DayPhase.Night -> stringResource(R.string.ocean_sun_night, degrees)
    }
}

@Composable
private fun OceanCard(state: UiState) {
    val antipode = state.antipode ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.ocean_card_title), style = MaterialTheme.typography.titleMedium)
            Text(sunDescription(antipode), style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
            val marine = state.marine
            if (marine == null) {
                Text(stringResource(R.string.ocean_no_sea_data), style = MaterialTheme.typography.bodyMedium)
            } else {
                MarineRows(marine)
            }
            Text(
                stringResource(R.string.ocean_sea_data_credit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MarineRows(marine: MarineConditions) {
    marine.waveHeightM?.let { height ->
        val detail = listOfNotNull(
            seaState(height).text(),
            marine.waveDirectionDeg?.let { stringResource(R.string.ocean_waves_from, compassPoint(it).text()) },
            marine.wavePeriodS?.let { stringResource(R.string.ocean_waves_every, it.roundToInt()) },
        ).joinToString(", ")
        Fact(stringResource(R.string.ocean_waves), stringResource(R.string.unit_metres, oneDecimal(height)), detail)
    }
    marine.swellHeightM?.let { Fact(stringResource(R.string.ocean_swell), stringResource(R.string.unit_metres, oneDecimal(it)), null) }
    marine.seaTempC?.let { Fact(stringResource(R.string.ocean_water_temperature), stringResource(R.string.unit_celsius, oneDecimal(it)), null) }
    marine.currentKmh?.let { Fact(stringResource(R.string.ocean_current), stringResource(R.string.unit_km_per_hour, oneDecimal(it)), null) }
}

@Composable
private fun Fact(label: String, value: String, detail: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A day heading in the gold (tertiary) accent, with a rule running out to the edge. */
@Composable
private fun RecencyHeading(recency: Recency) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            recency.text().uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        )
        HorizontalDivider(Modifier.weight(1f))
    }
}

@Composable
private fun ArticleRow(article: Article) {
    val context = LocalContext.current
    val telemetry = LocalTelemetry.current
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // Only which source supplied it is reported, never the article, its outlet or its place.
                telemetry.log(TelemetryEvent.ArticleOpened(article.via))
                runCatching { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(article.url)) }
            },
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(article.title, style = MaterialTheme.typography.titleMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
            val age = article.seenInstant()?.let {
                DateUtils.getRelativeTimeSpanString(it.toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    listOfNotNull(article.domain.takeIf { it.isNotBlank() }, article.language.takeIf { it.isNotBlank() }, age?.toString())
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                // Headlines from an outlet in or about that place, as opposed to a worldwide wire.
                if (article.via == "rss") {
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            stringResource(R.string.main_local_outlet),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
    }
}
