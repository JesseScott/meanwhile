package tt.co.jesses.meanwhile.ui

import android.net.Uri
import android.text.format.DateUtils
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import tt.co.jesses.meanwhile.core.LatLon
import tt.co.jesses.meanwhile.core.MarineConditions
import tt.co.jesses.meanwhile.core.TelemetryEvent
import tt.co.jesses.meanwhile.core.approxUtcOffsetHours
import tt.co.jesses.meanwhile.core.compassPoint
import tt.co.jesses.meanwhile.core.dayPhaseOf
import tt.co.jesses.meanwhile.core.flagEmoji
import tt.co.jesses.meanwhile.core.oceanNameAt
import tt.co.jesses.meanwhile.core.seaState
import tt.co.jesses.meanwhile.core.seenInstant
import tt.co.jesses.meanwhile.core.sunAltitudeDeg
import tt.co.jesses.meanwhile.showingOcean
import java.time.Instant
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
    Column(Modifier.fillMaxSize()) {
        // Everything above the results stays put; only the results scroll, and pulling them down refreshes.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Header(state, onOpenAbout)
            if (state.antipodeIsWater) ModeSwitch(state.mode) { onEvent(UiEvent.SetMode(it)) }
            Controls(
                onSearch = { onEvent(UiEvent.Search(it)) },
                onUseLocation = onUseLocation,
            )
            state.searchResults.forEach { place ->
                TextButton(onClick = { onEvent(UiEvent.PickPlace(place)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(place.label, modifier = Modifier.fillMaxWidth())
                }
            }
            StatusLine(
                state = state,
                onOpenSettings = onOpenSettings,
                onTryAgain = {
                    // Without a location there is nothing to refresh, so ask for the location again.
                    if (state.error == UiError.LocationUnavailable) onUseLocation() else onEvent(UiEvent.Refresh)
                },
            )
        }
        HorizontalDivider()

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
                if (state.status == Status.Ready) {
                    if (state.showingOcean) {
                        item { OceanCard(state) }
                    } else {
                        if (state.window == "7d") {
                            item { Text("Quiet there today, so this shows the last week.", style = MaterialTheme.typography.bodySmall) }
                        }
                        if (state.articles.isEmpty()) {
                            item { Text("No headlines found for ${state.country?.name ?: "that region"}.") }
                        }
                        items(state.articles, key = { it.url }) { ArticleRow(it) }
                    }
                }
            }
        }
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
            ) { Text(label) }
        }
    }
}

@Composable
private fun Controls(onSearch: (String) -> Unit, onUseLocation: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Try a different place") },
        singleLine = true,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onSearch(query) }, enabled = query.isNotBlank()) { Text("Search") }
        OutlinedButton(onClick = onUseLocation) { Text("Use my location") }
    }
}

@Composable
private fun StatusLine(state: UiState, onTryAgain: () -> Unit, onOpenSettings: () -> Unit) {
    when (state.status) {
        Status.Loading -> Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(state.progress?.text() ?: "Loading…", style = MaterialTheme.typography.bodyMedium)
        }
        Status.Error -> Column {
            Text((state.error ?: UiError.Unknown).text(), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onTryAgain) { Text("Try again") }
        }
        // A notice (such as "location is off") says it better than the generic hint, so don't show both.
        Status.Idle -> if (state.notice == null) {
            Text(
                "Allow location, or search for a place, to see what's happening on the other side of the world.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Status.Ready -> Unit
    }
    state.notice?.let { notice ->
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
private fun Header(state: UiState, onOpenAbout: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Meanwhile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    TAGLINE,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onOpenAbout) {
                Icon(Icons.Filled.Settings, contentDescription = "About, privacy and licenses")
            }
        }
        val country = state.country
        val antipode = state.antipode
        if (country != null && antipode != null) {
            val ocean = state.showingOcean
            val title = if (ocean) "🌊 ${oceanNameAt(antipode)}" else "${flagEmoji(country.iso)} ${country.name}"
            Text(title, style = MaterialTheme.typography.titleLarge)
            val place = state.originLabel?.takeIf { it != YOUR_LOCATION } ?: "you"
            Text(
                "Opposite $place. It's about ${localTimeThere(antipode)} there.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (country.distanceKm > 0) {
                Text(
                    if (ocean) nearestLandNote(country) else closestLandNote(country),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun closestLandNote(country: ResolvedCountry): String =
    "Your antipode is open water. Closest land found: ${country.name}, about ${country.distanceKm.roundToInt()} km away."

private fun nearestLandNote(country: ResolvedCountry): String =
    "Nearest land: ${country.name}, about ${country.distanceKm.roundToInt()} km away."

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

@Composable
private fun ArticleRow(article: Article) {
    val context = LocalContext.current
    val telemetry = LocalTelemetry.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // Only which source supplied it is reported, never the article, its outlet or its place.
                telemetry.log(TelemetryEvent.ArticleOpened(article.via))
                runCatching { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(article.url)) }
            },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(article.title, style = MaterialTheme.typography.titleMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
            val age = article.seenInstant()?.let {
                DateUtils.getRelativeTimeSpanString(it.toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            }
            Text(
                listOfNotNull(article.domain.takeIf { it.isNotBlank() }, article.language.takeIf { it.isNotBlank() }, age?.toString())
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
