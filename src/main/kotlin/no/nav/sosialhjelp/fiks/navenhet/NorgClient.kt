package no.nav.sosialhjelp.fiks.navenhet

interface NorgClient {
    suspend fun hentNavEnhet(enhetsnr: String): NavEnhet
}
