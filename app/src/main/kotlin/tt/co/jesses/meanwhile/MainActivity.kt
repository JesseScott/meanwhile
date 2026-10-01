package tt.co.jesses.meanwhile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tt.co.jesses.meanwhile.ui.MeanwhileScreen

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface {
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                        if (granted) viewModel.useDeviceLocation()
                    }
                    val requestLocation = {
                        val granted = ContextCompat.checkSelfPermission(
                            this, Manifest.permission.ACCESS_COARSE_LOCATION,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) viewModel.useDeviceLocation() else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }

                    LaunchedEffect(Unit) {
                        if (viewModel.state.value.status == Status.Idle) requestLocation()
                    }

                    Box(Modifier.safeDrawingPadding()) {
                        MeanwhileScreen(
                            state = state,
                            onUseLocation = requestLocation,
                            onRefresh = viewModel::refresh,
                            onSearch = viewModel::search,
                            onPickPlace = viewModel::usePlace,
                        )
                    }
                }
            }
        }
    }
}
