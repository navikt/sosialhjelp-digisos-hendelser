package no.nav.sosialhjelp.digisos.hendelser.domain

import java.math.BigDecimal
import java.security.MessageDigest

actual fun sha256(input: String): String =
    MessageDigest
        .getInstance("SHA-256")
        .digest(input.toByteArray())
        .fold("") { acc, byte -> acc + "%02x".format(byte) }

/** JVM convenience — convert the platform-neutral [Utbetaling.belopString] to BigDecimal. */
val Utbetaling.belopAsBigDecimal: BigDecimal
    get() = belopString.toBigDecimalOrNull() ?: BigDecimal.ZERO
