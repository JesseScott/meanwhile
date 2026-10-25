package tt.co.jesses.meanwhile.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import tt.co.jesses.meanwhile.core.FLAG_WASH_ALPHA_DARK
import tt.co.jesses.meanwhile.core.FLAG_WASH_ALPHA_LIGHT
import tt.co.jesses.meanwhile.core.FlagLayout
import tt.co.jesses.meanwhile.core.FlagStripes
import tt.co.jesses.meanwhile.core.flagStripesFor

private const val CROSSFADE_MS = 300

/** The wash is at full strength at the fly end, and this much of it is left at the hoist end, where the text starts. */
private const val HOIST_END_STRENGTH = 0.55f

/**
 * The wash is at full strength at the top, under the status bar, and fades towards the bottom of the header until
 * this much of it is left. Without the fade, a flag of one dark colour (New Zealand's, as the wash draws it) turned
 * into a flat grey-blue block with a hard lower edge.
 */
private const val BOTTOM_STRENGTH = 0.2f

/**
 * The country's flag as a soft wash of colour, to sit behind the header. Purely decorative: it has no semantics,
 * takes whatever size it is given (so it can't change the header's), and draws nothing for a null or unknown
 * [iso]. The flag is drawn as blurred bands and then covered with the surface colour until only a tint is left,
 * so the header's text stays on what is very nearly the plain surface.
 *
 * It runs past the top and sides of the space it is given, under the status bar and any cutout the app keeps its
 * content clear of, so the colour doesn't stop short at the status bar. That part is drawn only, never laid out.
 */
@Composable
fun FlagWash(iso: String?, modifier: Modifier = Modifier) {
    val flag = remember(iso) { iso?.let(::flagStripesFor) }
    val surface = MaterialTheme.colorScheme.surface
    val strength = if (surface.luminance() < 0.5f) FLAG_WASH_ALPHA_DARK else FLAG_WASH_ALPHA_LIGHT
    val insets = WindowInsets.safeDrawing
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val top = insets.getTop(density)
    val left = insets.getLeft(density, direction)
    val right = insets.getRight(density, direction)
    val bleed = Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth + left + right, constraints.maxHeight + top))
        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-left, -top) }
    }
    Crossfade(targetState = flag, modifier = modifier.then(bleed), animationSpec = tween(CROSSFADE_MS), label = "flagWash") { shown ->
        if (shown != null) {
            Canvas(Modifier.fillMaxSize()) {
                drawFlag(shown)
                drawRect(
                    Brush.horizontalGradient(
                        0f to surface.copy(alpha = 1f - strength * HOIST_END_STRENGTH),
                        0.75f to surface.copy(alpha = 1f - strength),
                    ),
                )
                // A second layer of surface over the lower part: what was left of the wash there drops to
                // BOTTOM_STRENGTH of itself by the bottom edge.
                drawRect(
                    Brush.verticalGradient(
                        0f to surface.copy(alpha = 0f),
                        1f to surface.copy(alpha = 1f - BOTTOM_STRENGTH),
                    ),
                )
            }
        }
    }
}

private fun rgb(colour: Int) = Color(0xFF000000L or colour.toLong())

private fun DrawScope.drawFlag(flag: FlagStripes) {
    val colours = flag.colors.map(::rgb)
    val shorter = minOf(size.width, size.height)
    when (flag.layout) {
        FlagLayout.Horizontal -> drawBands(colours, vertical = true)
        FlagLayout.Vertical -> drawBands(colours, vertical = false)
        FlagLayout.NordicCross, FlagLayout.CenteredCross -> {
            drawRect(colours[0])
            val centre = Offset(size.width * if (flag.layout == FlagLayout.NordicCross) 0.36f else 0.5f, size.height / 2)
            drawSoftCross(colours[1], centre, thickness = shorter * 0.3f)
            colours.getOrNull(2)?.let { drawSoftCross(it, centre, thickness = shorter * 0.13f) }
        }
        FlagLayout.Disc -> {
            drawRect(colours[0])
            drawSoftDisc(colours[1], center, radius = shorter * 0.5f)
            colours.getOrNull(2)?.let { drawSoftDisc(it, center, radius = shorter * 0.28f) }
        }
        FlagLayout.Hoist -> {
            drawBands(colours.drop(1), vertical = true)
            drawRect(
                Brush.horizontalGradient(0f to colours[0], 0.18f to colours[0], 0.42f to colours[0].copy(alpha = 0f)),
            )
        }
        FlagLayout.Canton -> {
            drawBands(colours.drop(1), vertical = true)
            drawSoftDisc(colours[0], Offset.Zero, radius = size.height * 0.8f)
        }
    }
}

/** Equal bands that melt into each other: each keeps its own colour in the middle and blends towards its neighbours. */
private fun DrawScope.drawBands(colours: List<Color>, vertical: Boolean) {
    if (colours.size == 1) {
        drawRect(colours[0])
        return
    }
    val n = colours.size
    val stops = colours.flatMapIndexed { i, colour ->
        listOf((i + 0.3f) / n to colour, (i + 0.7f) / n to colour)
    }.toTypedArray()
    drawRect(if (vertical) Brush.verticalGradient(*stops) else Brush.horizontalGradient(*stops))
}

private fun DrawScope.drawSoftCross(colour: Color, centre: Offset, thickness: Float) {
    val clear = colour.copy(alpha = 0f)
    val stops = arrayOf(0f to clear, 0.3f to colour, 0.7f to colour, 1f to clear)
    drawRect(
        Brush.verticalGradient(*stops, startY = centre.y - thickness / 2, endY = centre.y + thickness / 2),
        topLeft = Offset(0f, centre.y - thickness / 2),
        size = Size(size.width, thickness),
    )
    drawRect(
        Brush.horizontalGradient(*stops, startX = centre.x - thickness / 2, endX = centre.x + thickness / 2),
        topLeft = Offset(centre.x - thickness / 2, 0f),
        size = Size(thickness, size.height),
    )
}

private fun DrawScope.drawSoftDisc(colour: Color, centre: Offset, radius: Float) {
    drawCircle(
        Brush.radialGradient(0f to colour, 0.55f to colour, 1f to colour.copy(alpha = 0f), center = centre, radius = radius),
        radius = radius,
        center = centre,
    )
}
