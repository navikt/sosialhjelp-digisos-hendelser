package no.nav.sosialhjelp.fiks.navenhet

import java.io.Serializable

data class NavEnhet(
    val enhetId: Int,
    val navn: String,
    val enhetNr: String,
    val status: String,
    val antallRessurser: Int,
    val aktiveringsdato: String,
    val nedleggelsesdato: String?,
) : Serializable
