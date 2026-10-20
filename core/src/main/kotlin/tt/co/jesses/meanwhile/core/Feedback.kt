package tt.co.jesses.meanwhile.core

import java.net.URLEncoder

/** The feedback form testers reach from About. It is hosted by Tally and sees only what the tester types. */
const val FEEDBACK_FORM_URL = "https://tally.so/r/o964QM"

/**
 * The form's address with its hidden `app_version` and `device` fields filled in, so the tester doesn't have to look
 * them up. They pre-fill visible questions the tester can still change. A value that can't be read is left out.
 */
fun feedbackFormUrl(appVersion: String?, model: String?, androidVersion: String?): String {
    val device = listOfNotNull(
        model?.trim()?.takeIf { it.isNotEmpty() },
        androidVersion?.trim()?.takeIf { it.isNotEmpty() }?.let { "Android $it" },
    ).joinToString(", ")
    val params = listOfNotNull(
        appVersion?.trim()?.takeIf { it.isNotEmpty() }?.let { "app_version" to it },
        device.takeIf { it.isNotEmpty() }?.let { "device" to it },
    )
    if (params.isEmpty()) return FEEDBACK_FORM_URL
    return FEEDBACK_FORM_URL + params.joinToString("&", prefix = "?") { (key, value) -> "$key=${encode(value)}" }
}

// URLEncoder writes form encoding, where a space is "+"; a query string is clearer with "%20".
private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
