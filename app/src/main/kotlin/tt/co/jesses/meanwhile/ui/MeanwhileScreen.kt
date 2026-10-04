package tt.co.jesses.meanwhile.ui

import android.net.Uri
import android.text.format.DateUtils
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import tt.co.jesses.meanwhile.core.oceanNameAt
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

/** The label the view model gives a place that came from the device's location. */
private const val YOUR_LOCATION = "Your location"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeanwhileScreen(
    state: UiState,
    onUseLocation: () -> Unit,
    onEvent: (UiEvent) -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // Headlines sit under Today / Yesterday / Earlier headings; a late arrival slots into its own day.
    val groups = remember(state.articles) { state.articles.groupedByRecency(Instant.now(), ZoneId.systemDefault()) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        // The part that stays put. It only ever holds things whose size doesn't depend on what is loading:
        // the place (one line each), the view switch, and the search bar. Messages, errors and progress text
        // live in the list below, so nothing here moves when a load starts or finishes. With big text or a small
        // screen it would fill the phone, so it is capped and scrolls on its own.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = this@BoxWithConstraints.maxHeight * 0.5f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Header(state, onOpenAbout)
            if (state.antipodeIsWater) ModeSwitch(state.mode) { onEvent(UiEvent.SetMode(it)) }
            SearchBar(
                places = state.searchResults,
                onSearch = { onEvent(UiEvent.Search(it)) },
                onPick = { onEvent(UiEvent.PickPlace(it)) },
                onDismissPlaces = { onEvent(UiEvent.DismissPlaces) },
                onUseLocation = onUseLocation,
            )
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
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.notice?.let { notice ->
                    item(key = "notice") { NoticeItem(notice, onOpenSettings) }
                }
                when (state.status) {
                    Status.Loading -> item(key = "loading") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Text(state.progress?.text() ?: "Loading…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Status.Error -> item(key = "error") {
                        Column {
                            Text((state.error ?: UiError.Unknown).text(), color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = {
                                // Without a location there is nothing to refresh, so ask for the location again.
                                if (state.error == UiError.LocationUnavailable) onUseLocation() else onEvent(UiEvent.Refresh)
                            }) { Text("Try again") }
                        }
                    }
                    // A notice (such as "location is off") says it better than the generic hint, so don't show both.
                    Status.Idle -> if (state.notice == null) item(key = "hint") {
                        Text(
                            "Allow location, or search for a place, to see what's happening on the other side of the world.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Status.Ready -> {
                        if (state.showingOcean) {
                            item { OceanCard(state) }
                        } else {
                            if (state.window == "7d" && groups.firstOrNull()?.first != Recency.Today) {
                                item { Text("Quiet there today, so this shows the last week.", style = MaterialTheme.typography.bodySmall) }
                            }
                            if (state.articles.isEmpty()) {
                                item { Text("No headlines found for ${state.country?.name ?: "that region"}.") }
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
        if (notice == UiNotice.LocationDenied) TextButton(onClick = onOpenSettings) { Text("Open settings") }
    }
}

@Composable
private fun ModeSwitch(mode: ViewMode, onSetMode: (ViewMode) -> Unit) {
    val options = listOf(ViewMode.Land to "Nearest land", ViewMode.Ocean to "Open ocean")
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
            ) { Text(label) }
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
    places: List<NamedPlace>,
    onSearch: (String) -> Unit,
    onPick: (NamedPlace) -> Unit,
    onDismissPlaces: () -> Unit,
    onUseLocation: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val density = LocalDensity.current
    var barWidth by remember { mutableStateOf(0.dp) }
    Box {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { barWidth = with(density) { it.size.width.toDp() } },
            placeholder = { Text("Search for a place") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                Row {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
                    }
                    IconButton(onClick = {
                        keyboard?.hide()
                        focus.clearFocus()
                        onUseLocation()
                    }) { Icon(MyLocationIcon, contentDescription = "Use my location") }
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
                        onPick(place)
                    },
                )
            }
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
private fun Header(state: UiState, onOpenAbout: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // The settings icon shares the title's row, so it lines up with "Meanwhile" and not with the middle of the
        // title and tagline together. The tagline sits under the whole row.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Meanwhile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = onOpenAbout) {
                Icon(Icons.Filled.Settings, contentDescription = "About, privacy and licenses")
            }
        }
        Text(
            TAGLINE,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.offset(y = (-8).dp),
        )
        val country = state.country
        val antipode = state.antipode
        if (country != null && antipode != null) {
            val ocean = state.showingOcean
            val title = if (ocean) "🌊 ${oceanNameAt(antipode)}" else "${flagEmoji(country.iso)} ${country.name}"
            // Each line is one line, and both views use the same text for the open-water note, so switching
            // between them can't change the header's height.
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val place = state.originLabel?.takeIf { it != YOUR_LOCATION }?.substringBefore(",")?.trim() ?: "you"
            Text(
                "Opposite $place · ${localTimeThere(antipode)} there",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (country.distanceKm > 0) {
                Text(
                    openWaterNote(country),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun openWaterNote(country: ResolvedCountry): String =
    "Open water. Nearest land: ${country.name}, about ${"%,d".format(roughKm(country.distanceKm))} km away."

private fun localTimeThere(antipode: LatLon): String {
    val offset = ZoneOffset.ofHours(approxUtcOffsetHours(antipode.lon))
    return ZonedDateTime.now(offset).format(DateTimeFormatter.ofPattern("h:mm a"))
}

private fun sunDescription(antipode: LatLon): String {
    val altitude = sunAltitudeDeg(antipode, Instant.now())
    val degrees = abs(altitude).roundToInt()
    return when (dayPhaseOf(altitude)) {
        DayPhase.Day -> "Daytime. The sun is $degrees° above the horizon."
        DayPhase.Twilight -> "Twilight. The sun is just $degrees° below the horizon."
        DayPhase.Night -> "Night. The sun is $degrees° below the horizon."
    }
}

@Composable
private fun OceanCard(state: UiState) {
    val antipode = state.antipode ?: return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Right now, on the other side", style = MaterialTheme.typography.titleMedium)
            Text(sunDescription(antipode), style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
            val marine = state.marine
            if (marine == null) {
                Text("No sea data for this spot right now.", style = MaterialTheme.typography.bodyMedium)
            } else {
                MarineRows(marine)
            }
            Text(
                "Sea data: Open-Meteo.com Marine API (CC BY 4.0), from DWD and other weather services.",
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
            seaState(height),
            marine.waveDirectionDeg?.let { "from the ${compassPoint(it)}" },
            marine.wavePeriodS?.let { "every ${it.roundToInt()} s" },
        ).joinToString(", ")
        Fact("Waves", "${"%.1f".format(height)} m", detail)
    }
    marine.swellHeightM?.let { Fact("Swell", "${"%.1f".format(it)} m", null) }
    marine.seaTempC?.let { Fact("Water temperature", "${"%.1f".format(it)} °C", null) }
    marine.currentKmh?.let { Fact("Current", "${"%.1f".format(it)} km/h", null) }
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
                            "Local outlet",
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
