package tt.co.jesses.meanwhile.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity
import kotlinx.coroutines.launch
import tt.co.jesses.meanwhile.MeanwhileApplication
import tt.co.jesses.meanwhile.R
import tt.co.jesses.meanwhile.core.AnalyticsChoice
import tt.co.jesses.meanwhile.core.CONTACT_EMAIL
import tt.co.jesses.meanwhile.core.LocationAccess
import tt.co.jesses.meanwhile.core.TelemetryEvent
import tt.co.jesses.meanwhile.core.feedbackFormUrl
import tt.co.jesses.meanwhile.core.mailtoUri

private const val KOFI_URL = "https://ko-fi.com/jessescott"
private const val SITE_URL = "https://jesses.co.tt"
private const val PRIVACY_URL = "https://www.jesses.co.tt/privacy-meanwhile.html"

private enum class AboutTab { Settings, About }

/**
 * Settings and About, as two tabs. Every section is an accordion whose heading also says, in one line, how things
 * stand, so the screen can be read folded. [onNoMailApp] runs when there is no mail app for the contact email; the
 * address has been copied by then.
 */
@Composable
fun AboutScreen(
    locationAccess: LocationAccess,
    onTurnOnLocation: () -> Unit,
    onNoMailApp: () -> Unit,
    onBack: () -> Unit,
) {
    // Opened from the header, so About first: it holds Contact us, which Play reviewers look for.
    var tab by rememberSaveable { mutableStateOf(AboutTab.About) }
    // Keeps each tab's open sections and scroll position while the other tab is showing.
    val tabStates = rememberSaveableStateHolder()
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
                stringResource(R.string.app_name),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        PrimaryTabRow(selectedTabIndex = tab.ordinal) {
            AboutTab.entries.forEach { each ->
                Tab(
                    selected = tab == each,
                    onClick = { tab = each },
                    text = {
                        Text(
                            stringResource(if (each == AboutTab.Settings) R.string.about_tab_settings else R.string.about_tab_about),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    },
                )
            }
        }
        tabStates.SaveableStateProvider(tab) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                when (tab) {
                    AboutTab.Settings -> SettingsTab(locationAccess, onTurnOnLocation)
                    AboutTab.About -> AboutTabContent(onNoMailApp)
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(locationAccess: LocationAccess, onTurnOnLocation: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { (context.applicationContext as MeanwhileApplication).settings }
    val scope = rememberCoroutineScope()
    val flagWash by settings.flagWash.collectAsStateWithLifecycle(initialValue = true)
    // The saved choice is the only source of truth; the application turns collection on and off from it.
    val choice by settings.analyticsChoice.collectAsStateWithLifecycle(initialValue = AnalyticsChoice.Unset)
    val sharing = choice == AnalyticsChoice.Accepted

    Accordion(
        title = stringResource(R.string.about_location_title),
        summary = stringResource(if (locationAccess == LocationAccess.On) R.string.about_summary_on else R.string.about_summary_off),
    ) {
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
    SectionDivider()

    Accordion(
        title = stringResource(R.string.about_appearance_title),
        summary = stringResource(if (flagWash) R.string.about_appearance_summary_on else R.string.about_appearance_summary_off),
    ) {
        SwitchRow(
            title = stringResource(R.string.about_flag_wash_title),
            detail = stringResource(R.string.about_flag_wash_detail),
            checked = flagWash,
            onCheckedChange = { on -> scope.launch { settings.setFlagWash(on) } },
        )
    }
    SectionDivider()

    Accordion(
        title = stringResource(R.string.about_privacy_title),
        summary = stringResource(if (sharing) R.string.about_privacy_summary_on else R.string.about_privacy_summary_off),
    ) {
        SwitchRow(
            title = stringResource(R.string.about_share_usage_title),
            detail = stringResource(R.string.about_share_usage_detail),
            checked = sharing,
            // Turning it off is an explicit no, so the app won't ask again.
            onCheckedChange = { on ->
                scope.launch { settings.setAnalyticsChoice(if (on) AnalyticsChoice.Accepted else AnalyticsChoice.Rejected) }
            },
        )
        Bullet(stringResource(R.string.about_privacy_location))
        Bullet(stringResource(R.string.about_privacy_leaves_phone))
        Bullet(stringResource(R.string.about_privacy_reports))
        Bullet(stringResource(R.string.about_privacy_stored))
        Bullet(stringResource(R.string.about_privacy_no_ads))
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
    SectionDivider()

    val version = remember(context) { appVersion(context) }
    Accordion(
        title = stringResource(R.string.about_version_title),
        summary = version?.let { (name, code) -> stringResource(R.string.about_version_summary, name, code) }
            ?: stringResource(R.string.about_version_summary_unknown),
    ) {
        Body(
            version?.let { (name, code) -> stringResource(R.string.about_version, name, code) }
                ?: stringResource(R.string.about_version_unknown),
        )
    }
}

@Composable
private fun AboutTabContent(onNoMailApp: () -> Unit) {
    val context = LocalContext.current
    val telemetry = LocalTelemetry.current

    Accordion(
        title = stringResource(R.string.about_meanwhile_title),
        summary = stringResource(R.string.about_meanwhile_summary),
        initiallyExpanded = true,
    ) {
        Body(stringResource(R.string.about_what_it_does))
        Body(stringResource(R.string.about_byline))
        LinkButton(stringResource(R.string.about_link_site), SITE_URL)
    }
    SectionDivider()

    Accordion(
        title = stringResource(R.string.about_contact_title),
        summary = stringResource(R.string.about_contact_summary),
        initiallyExpanded = true,
    ) {
        Body(stringResource(R.string.about_contact_body))
        val subject = stringResource(R.string.app_name)
        EmailRow(onClick = { writeEmail(context, subject, onNoMailApp) })
        Body(stringResource(R.string.about_feedback_body))
        OutlinedButton(onClick = {
            openUrl(context, feedbackFormUrl(appVersion(context)?.first, Build.MODEL, Build.VERSION.RELEASE))
        }) { Text(stringResource(R.string.about_feedback_button)) }
        LinkButton(stringResource(R.string.about_link_site), SITE_URL)
    }
    SectionDivider()

    Accordion(title = stringResource(R.string.about_sources_title)) {
        Body(stringResource(R.string.about_sources_headlines))
        LinkButton(stringResource(R.string.about_link_gdelt), "https://www.gdeltproject.org")
        Body(stringResource(R.string.about_sources_sea))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LinkButton(stringResource(R.string.about_link_open_meteo), "https://open-meteo.com")
            LinkButton(stringResource(R.string.about_link_cc_by), "https://creativecommons.org/licenses/by/4.0/")
        }
        Body(stringResource(R.string.about_sources_places))
    }
    SectionDivider()

    Accordion(title = stringResource(R.string.about_support_title)) {
        Body(stringResource(R.string.about_support_body))
        Button(onClick = {
            telemetry.log(TelemetryEvent.KofiOpened)
            openUrl(context, KOFI_URL)
        }) { Text(stringResource(R.string.about_support_button)) }
    }
    SectionDivider()

    Accordion(title = stringResource(R.string.about_open_source_title)) {
        Body(stringResource(R.string.about_open_source_body))
        OutlinedButton(onClick = {
            telemetry.log(TelemetryEvent.LicensesOpened)
            context.startActivity(Intent(context, OssLicensesMenuActivity::class.java))
        }) {
            Text(stringResource(R.string.about_open_source_button))
        }
    }
}

/**
 * A section that opens and closes under its heading, in the style of Moonlight's AccordionSection. The heading row is
 * one control for a screen reader: a heading that reads the title and [summary] and says whether it is open.
 * Whether it is open survives rotation.
 */
@Composable
private fun Accordion(
    title: String,
    summary: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val state = stringResource(if (expanded) R.string.about_section_expanded else R.string.about_section_collapsed)
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val bringIntoView = remember { BringIntoViewRequester() }
    // Opening a section scrolls it into view, so its text isn't left below the bottom edge. Not on first show:
    // sections that start open stay where they are.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded && shown) {
            withFrameNanos { }
            bringIntoView.bringIntoView()
        }
        shown = true
    }
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics(mergeDescendants = true) {
                    heading()
                    stateDescription = state
                }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                summary?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.rotate(chevron))
        }
        if (expanded) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** The contact address, written out so it can be read and copied by hand, and a tap away from the mail app. */
@Composable
private fun EmailRow(onClick: () -> Unit) {
    val action = stringResource(R.string.about_contact_email_action)
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClickLabel = action, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.about_contact_email_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(CONTACT_EMAIL, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    // The whole row is the switch: one stop for a screen reader, which reads the title with "on" or "off",
    // and a tap anywhere on it works.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
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

/** Opens the mail app on a new email to the contact address. Without one, copies the address and says so. */
private fun writeEmail(context: Context, subject: String, onNoMailApp: () -> Unit) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(mailtoUri(subject = subject)))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.about_contact_email_label), CONTACT_EMAIL))
        onNoMailApp()
    }
}

/** The version name and build number (versionCode), or null if they can't be read. */
private fun appVersion(context: Context): Pair<String, Long>? = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    info.versionName?.let { it to PackageInfoCompat.getLongVersionCode(info) }
}.getOrNull()
