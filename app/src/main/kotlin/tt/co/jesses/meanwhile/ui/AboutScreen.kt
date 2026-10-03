package tt.co.jesses.meanwhile.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tt.co.jesses.meanwhile.MeanwhileApplication
import tt.co.jesses.meanwhile.core.AnalyticsChoice
import tt.co.jesses.meanwhile.core.TelemetryEvent

private const val KOFI_URL = "https://ko-fi.com/jessescott"
private const val SITE_URL = "https://jesses.co.tt"

@Composable
fun AboutScreen(onBack: () -> Unit) {
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("About", style = MaterialTheme.typography.headlineSmall)
        }
        HorizontalDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Section("Meanwhile") {
                Body("Version ${versionName(context)}")
                Body(
                    "Meanwhile finds the point exactly opposite you on Earth, your antipode, and shows what is " +
                        "happening there: headlines from that country, or from the nearest land if the point is open water. " +
                        "When it is open water you can also look at the sea itself.",
                )
                Body("A hobby project by Jesse Scott. It is free, has no ads, and isn't a business.")
                LinkButton("jesses.co.tt", SITE_URL)
            }

            Section("Where the data comes from") {
                Body(
                    "Headlines: the GDELT Project (gdeltproject.org), a free, open database of world news. " +
                        "Meanwhile shows article titles only and links to the publishers' own sites. " +
                        "Titles appear as published, in their original language, and GDELT can occasionally file an outlet under the wrong country.",
                )
                LinkButton("GDELT Project", "https://www.gdeltproject.org")
                Body(
                    "Sea conditions: the Open-Meteo.com Marine API, with data from DWD and other national weather services, " +
                        "used under the CC BY 4.0 license for non-commercial purposes. Not for navigation.",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinkButton("Open-Meteo", "https://open-meteo.com")
                    LinkButton("CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/")
                }
                Body("Place lookup: your device's built-in geocoder. Location: Google Play services.")
            }

            Section("Privacy") {
                Bullet(
                    "Your location. Meanwhile asks for approximate (coarse) location only, and only to work out the point " +
                        "opposite you. It doesn't store your location, and there is no account to sign up for.",
                )
                Bullet(
                    "What leaves your phone. The coordinates of the point opposite you are sent to your device's geocoder " +
                        "(usually Google Play services) to find the country. Only the country code goes to GDELT for headlines. " +
                        "In open-ocean mode, the opposite point's coordinates go to Open-Meteo for sea conditions.",
                )
                Bullet(
                    "That opposite point is just your location mirrored, so treat those coordinates as approximate-location data. " +
                        "You can search for a place instead of using your location.",
                )
                Bullet("Places you search for are sent to your device's geocoder to look up their coordinates.")
                Bullet(
                    "Usage and crash reports are off unless you turn them on below. If you do, Meanwhile uses Firebase, a Google " +
                        "service, to see how the app is used and to find crashes: which screens are opened, whether a load worked and " +
                        "how long it took, kinds of errors, your phone's model and Android version, the app version, and a random " +
                        "installation ID (a number that isn't tied to you and changes if you reinstall). There are no accounts, so " +
                        "no account details, and the app is built not to put your location, your antipode or your searches in these " +
                        "reports. Turning the switch off again stops the reports and clears the ID.",
                )
                Bullet(
                    "Headlines are kept on your phone for up to three days per country, so something shows straight away while fresh headlines load. " +
                        "Clearing the app's cache removes them. The app also remembers that you have seen its welcome screen, your " +
                        "choice about the reports below, and a count of how often it has worked and asked, so that it doesn't ask too often.",
                )
                Bullet(
                    "No ads. Tapping a headline opens the publisher's own website in your browser, " +
                        "which has its own privacy policy.",
                )

                // The saved choice is the only source of truth; the application turns collection on and off from it.
                val choice by settings.analyticsChoice.collectAsStateWithLifecycle(initialValue = AnalyticsChoice.Unset)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Share usage and crash data", style = MaterialTheme.typography.titleMedium)
                        Text("Off unless you turn it on", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = choice == AnalyticsChoice.Accepted,
                        // Turning it off is an explicit no, so the app won't ask again.
                        onCheckedChange = { on ->
                            scope.launch { settings.setAnalyticsChoice(if (on) AnalyticsChoice.Accepted else AnalyticsChoice.Rejected) }
                        },
                    )
                }
                LinkButton("How Firebase handles data", "https://firebase.google.com/support/privacy")

                // Only in debug builds: lets you check that crash reports arrive in the Firebase console.
                if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                    TextButton(onClick = { throw RuntimeException("Test crash from the About screen (debug build)") }) {
                        Text("Send a test crash (debug builds only)")
                    }
                }
            }

            Section("Support") {
                Body("This project is a labour of love. If you are able and willing to support my work, please buy me a coffee.")
                Button(onClick = {
                    telemetry.log(TelemetryEvent.KofiOpened)
                    openUrl(context, KOFI_URL)
                }) { Text("Buy me a coffee") }
            }

            Section("Open source") {
                Body("Meanwhile is built with open source software. See the licenses for the libraries it uses.")
                OutlinedButton(onClick = {
                    telemetry.log(TelemetryEvent.LicensesOpened)
                    context.startActivity(Intent(context, OssLicensesMenuActivity::class.java))
                }) {
                    Text("Open source licenses")
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", style = MaterialTheme.typography.bodyMedium)
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

private fun versionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "unknown"
