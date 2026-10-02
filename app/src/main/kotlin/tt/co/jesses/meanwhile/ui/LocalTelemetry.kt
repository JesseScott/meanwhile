package tt.co.jesses.meanwhile.ui

import androidx.compose.runtime.staticCompositionLocalOf
import tt.co.jesses.meanwhile.core.NoTelemetry
import tt.co.jesses.meanwhile.core.Telemetry

/** The app's telemetry, for screens that report a tap. Does nothing in previews and tests. */
val LocalTelemetry = staticCompositionLocalOf<Telemetry> { NoTelemetry }
