package no.nav.sosialhjelp.fiks.tilgang.pdl

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class PdlResponse(
    val data: PdlData?,
    val errors: List<PdlError>?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PdlData(
    val hentPerson: PdlPerson?,
    val hentIdenter: PdlIdenter?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PdlPerson(
    val adressebeskyttelse: List<Adressebeskyttelse>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Adressebeskyttelse(
    val gradering: Gradering,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PdlIdenter(
    val identer: List<PdlIdent>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PdlIdent(
    val ident: String,
    val historisk: Boolean,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PdlError(
    val message: String?,
)

enum class Gradering {
    STRENGT_FORTROLIG_UTLAND, // kode 6 (utland)
    STRENGT_FORTROLIG, // kode 6
    FORTROLIG, // kode 7
    UGRADERT,
}

fun PdlPerson.erKode6eller7(): Boolean =
    adressebeskyttelse.any {
        it.gradering == Gradering.STRENGT_FORTROLIG ||
            it.gradering == Gradering.STRENGT_FORTROLIG_UTLAND ||
            it.gradering == Gradering.FORTROLIG
    }
