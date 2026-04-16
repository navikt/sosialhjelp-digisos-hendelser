package no.nav.sosialhjelp.fiks.app.exceptions

class NorgException(
    override val message: String?,
    override val cause: Throwable?,
) : RuntimeException(message, cause)
