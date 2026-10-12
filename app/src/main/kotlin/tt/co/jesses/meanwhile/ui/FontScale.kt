package tt.co.jesses.meanwhile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** The largest text scale used where room is fixed: the sticky header and the snackbars. */
const val MAX_FIXED_FONT_SCALE = 1.5f

/**
 * Holds text inside [content] to at most [max] times its normal size, whatever the phone's setting. Only for places
 * that can't grow with the text. Anything that is read at length (headlines, the ocean card, About) keeps the full
 * system scale.
 */
@Composable
fun CappedFontScale(max: Float = MAX_FIXED_FONT_SCALE, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    if (density.fontScale <= max) {
        content()
    } else {
        CompositionLocalProvider(LocalDensity provides Density(density.density, max), content = content)
    }
}
