package tt.co.jesses.meanwhile.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tt.co.jesses.meanwhile.MainViewModel
import tt.co.jesses.meanwhile.MeanwhileApplication
import kotlinx.coroutines.launch
import tt.co.jesses.meanwhile.Status
import tt.co.jesses.meanwhile.core.AnalyticsChoice
import tt.co.jesses.meanwhile.core.ModeSwitchVia
import tt.co.jesses.meanwhile.core.TelemetryEvent
import tt.co.jesses.meanwhile.UiEffect
import tt.co.jesses.meanwhile.UiEvent
import tt.co.jesses.meanwhile.ViewMode

/**
 * Everything around the main screen: the one-time location intro, the permission request, the About page, and
 * turning one-shot effects into snackbars. The screens themselves only render state and send events.
 */
@Composable
fun MeanwhileApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val telemetry = remember { (context.applicationContext as MeanwhileApplication).telemetry }
    val settings = remember { (context.applicationContext as MeanwhileApplication).settings }

    fun hasLocationPermission() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        telemetry.log(TelemetryEvent.PermissionResult(granted))
        viewModel.onEvent(if (granted) UiEvent.UseMyLocation else UiEvent.LocationDenied)
    }
    val useLocation = {
        if (hasLocationPermission()) viewModel.onEvent(UiEvent.UseMyLocation) else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    // The system prompt never appears out of the blue: first time round, an intro explains it. If it was
    // already answered, the screen's own hint and "Use my location" button take over.
    // null until the saved setting has been read (a few milliseconds), so the screen waits instead of flashing the wrong one.
    val introSeen by produceState<Boolean?>(initialValue = null) { settings.introSeen.collect { value = it } }
    var introDismissed by rememberSaveable { mutableStateOf(false) }
    val showIntro = introSeen == false && !introDismissed && !hasLocationPermission()
    var showAbout by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showAbout) { showAbout = false }

    LaunchedEffect(Unit) {
        if (hasLocationPermission() && viewModel.state.value.status == Status.Idle) viewModel.onEvent(UiEvent.UseMyLocation)
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UiEffect.OfferNearestLand -> {
                    val answer = snackbar.showSnackbar(
                        message = "Your antipode is open water. Read headlines from ${effect.country}, about ${"%,d".format(effect.distanceKm)} km away?",
                        actionLabel = "Show headlines",
                        withDismissAction = true,
                        duration = SnackbarDuration.Long,
                    )
                    if (answer == SnackbarResult.ActionPerformed) {
                        viewModel.onEvent(UiEvent.SetMode(ViewMode.Land, ModeSwitchVia.Snackbar))
                    }
                }
                UiEffect.AskToShareUsage -> {
                    val answer = snackbar.showSnackbar(
                        message = "Help improve Meanwhile? You can share anonymous usage and crash reports.",
                        actionLabel = "Turn on",
                        withDismissAction = true,
                        duration = SnackbarDuration.Long,
                    )
                    // Dismissing is not a no: it just means "not now", and the view model stops asking after two tries.
                    if (answer == SnackbarResult.ActionPerformed) settings.setAnalyticsChoice(AnalyticsChoice.Accepted)
                }
            }
        }
    }
    // A different place makes any offer about the last one stale.
    LaunchedEffect(state.antipode) { snackbar.currentSnackbarData?.dismiss() }

    CompositionLocalProvider(LocalTelemetry provides telemetry) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            when {
                introSeen == null -> Unit
                showIntro -> LocationIntroScreen(
                    onContinue = {
                        scope.launch { settings.setIntroSeen() }
                        introDismissed = true
                        useLocation()
                    },
                    onSearchInstead = {
                        scope.launch { settings.setIntroSeen() }
                        introDismissed = true
                    },
                )
                showAbout -> AboutScreen(onBack = { showAbout = false })
                else -> MeanwhileScreen(
                    state = state,
                    onUseLocation = useLocation,
                    onEvent = viewModel::onEvent,
                    onOpenAbout = {
                        telemetry.log(TelemetryEvent.AboutOpened)
                        showAbout = true
                    },
                    onOpenLocationSettings = {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    },
                    onOpenSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                )
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}
