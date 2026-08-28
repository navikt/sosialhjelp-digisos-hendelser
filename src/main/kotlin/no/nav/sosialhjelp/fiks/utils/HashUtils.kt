package no.nav.sosialhjelp.fiks.utils

import java.security.MessageDigest

fun sha256(input: String): String = hashString(input, "SHA-256")

private fun hashString(
    input: String,
    algorithm: String,
): String =
    MessageDigest
        .getInstance(algorithm)
        .digest(input.toByteArray())
        .fold("") { acc, byte -> acc + "%02x".format(byte) }
