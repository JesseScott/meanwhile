package tt.co.jesses.meanwhile

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * User-facing wording belongs in res/values/strings.xml, where it can be translated. Android Lint's own checks for
 * hard-coded text don't look inside Compose, so this reads the UI sources and fails on a string literal with words
 * in it that is handed straight to a composable, a content description or a snackbar.
 */
class HardcodedTextTest {
    /** Text that stays in code on purpose: it only exists in debug builds. */
    private val allowed = setOf(
        "Test crash from the About screen (debug build)",
        "Send a test crash (debug builds only)",
    )

    // A literal as the first argument of a call that starts with a capital (composables do), or given to one of the
    // parameters that carry wording.
    private val wording = Regex(
        """(?:\b[A-Z]\w*\(\s*|\b(?:contentDescription|title|detail|text|message|actionLabel|placeholder)\s*=\s*)"((?:[^"\\]|\\.)*)"""",
    )

    @Test
    fun uiSourcesHaveNoHardcodedWording() {
        val sources = File("src/main/kotlin/tt/co/jesses/meanwhile/ui").walkTopDown().filter { it.extension == "kt" }.toList()
        assertTrue(sources.isNotEmpty(), "No UI sources found from ${File("").absolutePath}")
        val found = sources.flatMap { file ->
            wording.findAll(file.readText())
                .map { it.groupValues[1] }
                .filter { literal -> literal.any(Char::isLetter) && literal !in allowed }
                .map { "${file.name}: \"$it\"" }
        }
        assertTrue(found.isEmpty(), "Move these to res/values/strings.xml:\n" + found.joinToString("\n"))
    }
}
