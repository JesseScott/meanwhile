package tt.co.jesses.meanwhile.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.pm.PackageInfoCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tt.co.jesses.meanwhile.MeanwhileApplication
import tt.co.jesses.meanwhile.R
import tt.co.jesses.meanwhile.core.AnalyticsChoice
import tt.co.jesses.meanwhile.core.LocationAccess
import tt.co.jesses.meanwhile.core.TelemetryEvent
import tt.co.jesses.meanwhile.core.feedbackFormUrl

private const val KOFI_URL = "https://ko-fi.com/jessescott"
private const val SITE_URL = "https://jesses.co.tt"
private const val PRIVACY_URL = "https://www.jesses.co.tt/privacy-meanwhile.html"

@Composable
fun AboutScreen(locationAccess: LocationAccess, onTurnOnLocation: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val telemetry = LocalTelemetry.current
    val settings = remember { (context.applicationContext as MeanwhileApplication).settings }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.about_back))
            }
            Text(
                stringResource(R.string.about_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        HorizontalDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Section(stringResource(R.string.app_name)) {
                val version = remember(context) { appVersion(context) }
                Body(
                    version?.let { (name, code) -> stringResource(R.string.about_version, name, code) }
                        ?: stringResource(R.string.about_version_unknown),
                )
                Body(stringResource(R.string.about_what_it_does))
                Body(stringResource(R.string.about_hobby))
                LinkButton(stringResource(R.string.about_link_site), SITE_URL)
            }

            Section(stringResource(R.string.about_location_title)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.about_use_my_location), style = MaterialTheme.typography.titleMedium)
                        Text(
                            locationAccess.detail(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    locationAccess.action()?.let { label -> OutlinedButton(onClick = onTurnOnLocation) { Text(label) } }
                }
            }

            Section(stringResource(R.string.about_appearance_title)) {
                val flagWash by settings.flagWash.collectAsStateWithLifecycle(initialValue = true)
                SwitchRow(
                    title = stringResource(R.string.about_flag_wash_title),
                    detail = stringResource(R.string.about_flag_wash_detail),
                    checked = flagWash,
                    onCheckedChange = { on -> scope.launch { settings.setFlagWash(on) } },
                )
            }

            // The two long sections start folded, so the screen isn't a wall of text at large sizes.
            CollapsibleSection(
                title = stringResource(R.string.about_sources_title),
                details = {
                    Body(stringResource(R.string.about_sources_headlines))
                    LinkButton(stringResource(R.string.about_link_gdelt), "https://www.gdeltproject.org")
                    Body(stringResource(R.string.about_sources_sea))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinkButton(stringResource(R.string.about_link_open_meteo), "https://open-meteo.com")
                        LinkButton(stringResource(R.string.about_link_cc_by), "https://creativecommons.org/licenses/by/4.0/")
                    }
                    Body(stringResource(R.string.about_sources_places))
                },
            )

            CollapsibleSection(
                title = stringResource(R.string.about_privacy_title),
                details = {
                    Bullet(stringResource(R.string.about_privacy_location))
                    Bullet(stringResource(R.string.about_privacy_leaves_phone))
                    Bullet(stringResource(R.string.about_privacy_reports))
                    Bullet(stringResource(R.string.about_privacy_stored))
                    Bullet(stringResource(R.string.about_privacy_no_ads))
                },
            ) {
                // The switch and the policy links stay in view when the section is folded.
                // The saved choice is the only source of truth; the application turns collection on and off from it.
                val choice by settings.analyticsChoice.collectAsStateWithLifecycle(initialValue = AnalyticsChoice.Unset)
                SwitchRow(
                    title = stringResource(R.string.about_share_usage_title),
                    detail = stringResource(R.string.about_share_usage_detail),
                    checked = choice == AnalyticsChoice.Accepted,
                    // Turning it off is an explicit no, so the app won't ask again.
                    onCheckedChange = { on ->
                        scope.launch { settings.setAnalyticsChoice(if (on) AnalyticsChoice.Accepted else AnalyticsChoice.Rejected) }
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinkButton(stringResource(R.string.about_link_privacy_policy), PRIVACY_URL)
                    LinkButton(stringResource(R.string.about_link_firebase), "https://firebase.google.com/support/privacy")
                }

                // Only in debug builds: lets you check that crash reports arrive in the Firebase console.
                if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                    TextButton(onClick = { throw RuntimeException("Test crash from the About screen (debug build)") }) {
                        Text("Send a test crash (debug builds only)")
                    }
                }
            }

            Section(stringResource(R.string.about_feedback_title)) {
                Body(stringResource(R.string.about_feedback_body))
                OutlinedButton(onClick = {
                    openUrl(context, feedbackFormUrl(appVersion(context)?.first, Build.MODEL, Build.VERSION.RELEASE))
                }) { Text(stringResource(R.string.about_feedback_button)) }
            }

            Section(stringResource(R.string.about_support_title)) {
                Body(stringResource(R.string.about_support_body))
                Button(onClick = {
                    telemetry.log(TelemetryEvent.KofiOpened)
                    openUrl(context, KOFI_URL)
                }) { Text(stringResource(R.string.about_support_button)) }
            }

            Section(stringResource(R.string.about_open_source_title)) {
                Body(stringResource(R.string.about_open_source_body))
                OutlinedButton(onClick = {
                    telemetry.log(TelemetryEvent.LicensesOpened)
                    context.startActivity(Intent(context, OssLicensesMenuActivity::class.java))
                }) {
                    Text(stringResource(R.string.about_open_source_button))
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

/**
 * A section whose [details] are folded away until its heading is tapped. [always] shows whatever the fold, under
 * the details. The heading row is one control for a screen reader: a heading that says whether it is open.
 */
@Composable
private fun CollapsibleSection(
    title: String,
    details: @Composable () -> Unit = {},
    always: @Composable () -> Unit = {},
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val state = stringResource(if (expanded) R.string.about_section_expanded else R.string.about_section_collapsed)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics(mergeDescendants = true) {
                    heading()
                    stateDescription = state
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
            )
        }
        if (expanded) details()
        always()
    }
}

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    // The whole row is the switch: one stop for a screen reader, which reads the title with "on" or "off",
    // and a tap anywhere on it works.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Decoration: without this a screen reader stops on every dot and says "bullet".
        Text("•", modifier = Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LinkButton(label: String, url: String) {
    val context = LocalContext.current
    TextButton(onClick = { openUrl(context, url) }) { Text(label) }
}

private fun openUrl(context: Context, url: String) {
    runCatching { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url)) }
}

/** The version name and build number (versionCode), or null if they can't be read. */
private fun appVersion(context: Context): Pair<String, Long>? = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    info.versionName?.let { it to PackageInfoCompat.getLongVersionCode(info) }
}.getOrNull()
