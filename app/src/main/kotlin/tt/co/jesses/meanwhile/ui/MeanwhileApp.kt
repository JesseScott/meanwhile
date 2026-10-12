package tt.co.jesses.meanwhile.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tt.co.jesses.meanwhile.MainViewModel
import tt.co.jesses.meanwhile.MeanwhileApplication
import tt.co.jesses.meanwhile.R
import kotlinx.coroutines.launch
import tt.co.jesses.meanwhile.Status
import tt.co.jesses.meanwhile.core.AnalyticsChoice
import tt.co.jesses.meanwhile.core.LocationAccess
import tt.co.jesses.meanwhile.core.ModeSwitchVia
import tt.co.jesses.meanwhile.core.TelemetryEvent
import tt.co.jesses.meanwhile.UiEffect
import tt.co.jesses.meanwhile.UiEvent
import tt.co.jesses.meanwhile.UiNotice
import tt.co.jesses.meanwhile.ViewMode

/**
 * Everything around the main screen: the one-time location intro, the permission request, the About page, and
 * turning one-shot effects into snackbars. The screens themselves only render state and send events.
 */
@Composable
fun MeanwhileApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val telemetry = remember { (context.applicationContext as MeanwhileApplication).telemetry }
    val settings = remember { (context.applicationContext as MeanwhileApplication).settings }

    fun hasLocationPermission() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    fun shouldExplainLocation() = context.findActivity()?.let {
        ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.ACCESS_COARSE_LOCATION)
    } ?: false

    val openAppSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
    }

    // null until the saved setting has been read; until then the app asks as if nothing had been refused.
    val locationRefused by produceState<Boolean?>(initialValue = null) { settings.locationRefused.collect { value = it } }
    // The permission can change while the app is away (in system settings), so it is read again on every return.
    var locationGranted by remember { mutableStateOf(hasLocationPermission()) }
    var shouldExplain by remember { mutableStateOf(shouldExplainLocation()) }
    val locationAccess = LocationAccess.of(locationGranted, locationRefused == true, shouldExplain)

    // A grant wipes the slate: when it ends, Android asks again, so an old refusal must not send the user to settings.
    LaunchedEffect(locationGranted, locationRefused) {
        if (locationGranted && locationRefused == true) settings.setLocationRefused(false)
    }

    var askedAt by remember { mutableLongStateOf(0L) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        telemetry.log(TelemetryEvent.PermissionResult(granted))
        locationGranted = granted
        shouldExplain = shouldExplainLocation()
        if (granted) {
            viewModel.onEvent(UiEvent.UseMyLocation)
        } else {
            val elapsed = SystemClock.elapsedRealtime() - askedAt
            if (LocationAccess.refusalCounts(shouldExplain, elapsed)) scope.launch { settings.setLocationRefused(true) }
            // Android answered without showing its dialog (refused twice under an older version of the app, say),
            // so the tap would otherwise do nothing: go where it can be turned on.
            if (elapsed < LocationAccess.NO_DIALOG_MILLIS) openAppSettings()
            viewModel.onEvent(UiEvent.LocationDenied)
        }
    }
    val useLocation = {
        when (locationAccess) {
            LocationAccess.On -> viewModel.onEvent(UiEvent.UseMyLocation)
            LocationAccess.CanAsk -> {
                askedAt = SystemClock.elapsedRealtime()
                permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            LocationAccess.Blocked -> openAppSettings()
        }
    }
    LifecycleResumeEffect(Unit) {
        val wasGranted = locationGranted
        locationGranted = hasLocationPermission()
        shouldExplain = shouldExplainLocation()
        // Turned on in system settings: carry on as if the dialog had been answered yes, unless a place is showing.
        val current = viewModel.state.value
        if (locationGranted && !wasGranted && (current.status == Status.Idle || current.notice == UiNotice.LocationDenied)) {
            viewModel.onEvent(UiEvent.UseMyLocation)
        }
        onPauseOrDispose { }
    }

    // The system prompt never appears out of the blue: first time round, an intro explains it. If it was
    // already answered, the screen's own hint and "Use my location" button take over.
    // null until the saved setting has been read (a few milliseconds), so the screen waits instead of flashing the wrong one.
    val introSeen by produceState<Boolean?>(initialValue = null) { settings.introSeen.collect { value = it } }
    // Off until the saved setting has been read, so a wash the user turned off never flashes up at launch.
    val flagWash by produceState(initialValue = false) { settings.flagWash.collect { value = it } }
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
                        message = resources.getString(
                            R.string.offer_nearest_land_message, effect.country, wholeNumber(effect.distanceKm),
                        ),
                        actionLabel = resources.getString(R.string.offer_nearest_land_action),
                        withDismissAction = true,
                        duration = SnackbarDuration.Long,
                    )
                    if (answer == SnackbarResult.ActionPerformed) {
                        viewModel.onEvent(UiEvent.SetMode(ViewMode.Land, ModeSwitchVia.Snackbar))
                    }
                }
                UiEffect.AskToShareUsage -> {
                    val answer = snackbar.showSnackbar(
                        message = resources.getString(R.string.share_usage_message),
                        actionLabel = resources.getString(R.string.share_usage_action),
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
                showAbout -> AboutScreen(
                    locationAccess = locationAccess,
                    onTurnOnLocation = useLocation,
                    onBack = { showAbout = false },
                )
                else -> MeanwhileScreen(
                    state = state,
                    showFlagWash = flagWash,
                    onUseLocation = useLocation,
                    onEvent = viewModel::onEvent,
                    onOpenAbout = {
                        telemetry.log(TelemetryEvent.AboutOpened)
                        showAbout = true
                    },
                    onOpenLocationSettings = {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    },
                    onOpenSettings = openAppSettings,
                )
            }
            // The action goes under the message: side by side, large text squeezes the message into a thin column.
            CappedFontScale {
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter)) { Snackbar(it, actionOnNewLine = true) }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
