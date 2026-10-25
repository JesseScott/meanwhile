package tt.co.jesses.meanwhile.core

import java.net.URLEncoder

/** The address shown under "Contact us". Google Play asks news apps for a clearly labelled in-app contact email. */
const val CONTACT_EMAIL = "js@jesses.co.tt"

/**
 * A `mailto:` address for [address] with [subject] filled in, for an ACTION_SENDTO intent. Mail apps read the subject
 * from the query, which must be percent-encoded (a space is "%20", not "+").
 */
fun mailtoUri(address: String = CONTACT_EMAIL, subject: String? = null): String {
    val base = "mailto:$address"
    val trimmed = subject?.trim()?.takeIf { it.isNotEmpty() } ?: return base
    // URLEncoder writes form encoding, where a space is "+"; mail apps expect "%20".
    return "$base?subject=" + URLEncoder.encode(trimmed, Charsets.UTF_8).replace("+", "%20")
}
