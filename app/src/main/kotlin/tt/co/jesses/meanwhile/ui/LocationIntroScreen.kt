package tt.co.jesses.meanwhile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tt.co.jesses.meanwhile.R

/**
 * Shown once, before the system location prompt, so the prompt isn't a surprise: what Meanwhile does, why it
 * needs an approximate location, and a way to carry on without sharing it.
 */
@Composable
fun LocationIntroScreen(
    onContinue: () -> Unit,
    onSearchInstead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            AntipodeMark(Modifier.size(88.dp))
            Column {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text(tagline(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                stringResource(R.string.intro_what_it_does),
                style = MaterialTheme.typography.bodyLarge,
            )
            Point(
                stringResource(R.string.intro_location_title),
                stringResource(R.string.intro_location_body),
            )
            Point(
                stringResource(R.string.intro_ocean_title),
                stringResource(R.string.intro_ocean_body),
            )
            Point(
                stringResource(R.string.intro_keep_title),
                stringResource(R.string.intro_keep_body),
            )
        }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.intro_continue)) }
        TextButton(onClick = onSearchInstead, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.intro_search_instead)) }
    }
}

@Composable
private fun Point(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The icon's idea, drawn in the theme's colours: a globe with one line through it, from you to the other side. */
@Composable
private fun AntipodeMark(modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.primary
    val you = MaterialTheme.colorScheme.onSurface
    val them = MaterialTheme.colorScheme.tertiary
    Canvas(modifier) {
        val s = size.minDimension
        val center = Offset(size.width / 2, size.height / 2)
        val r = s * 0.40f
        val stroke = s * 0.035f
        drawCircle(outline, radius = r, center = center, style = Stroke(width = stroke))
        drawOval(
            outline.copy(alpha = 0.55f),
            topLeft = Offset(center.x - r * 0.38f, center.y - r),
            size = Size(r * 0.76f, r * 2),
            style = Stroke(width = stroke * 0.7f),
        )
        val d = r * 0.7071f
        val from = Offset(center.x - d, center.y - d)
        val to = Offset(center.x + d, center.y + d)
        drawLine(them, from, to, strokeWidth = stroke * 1.2f, cap = StrokeCap.Round)
        drawCircle(you, radius = s * 0.055f, center = from)
        drawCircle(them, radius = s * 0.055f, center = to)
    }
}
