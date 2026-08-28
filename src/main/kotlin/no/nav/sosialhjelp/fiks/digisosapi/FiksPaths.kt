package no.nav.sosialhjelp.fiks.digisosapi

object FiksPaths {
    // Citizen (ID-porten) paths
    const val SOKNAD = "/digisos/api/v1/soknader/{digisosId}"
    const val ALLE_SOKNADER = "/digisos/api/v1/soknader/soknader"
    const val DOKUMENT = "/digisos/api/v1/soknader/{digisosId}/dokumenter/{dokumentlagerId}"

    /** Bulk innsynsfil fetch — POST, Accept: multipart/mixed */
    const val DOKUMENTER_BULK = "/digisos/api/v1/soknader/dokumenter"

    // Saksbehandler (Maskinporten/nav) paths
    const val NAV_SOKNAD = "/digisos/api/v1/nav/soknader/{digisosId}"
    const val NAV_ALLE_SOKNADER = "/digisos/api/v1/nav/soknader/soknader"
    const val NAV_DOKUMENT = "/digisos/api/v1/nav/soknader/{digisosId}/dokumenter/{dokumentlagerId}"

    // Kommuneinfo (always Maskinporten)
    const val KOMMUNEINFO = "/digisos/api/v1/nav/kommuner/{kommunenummer}"
}
