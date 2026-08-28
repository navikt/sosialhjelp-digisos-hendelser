# Plan: sosialhjelp-fiks-service as a shared Fiks domain service

**Status:** design agreed, not yet implemented.
**Written:** 2026-08-28.
**Audience:** whoever (human or agent) picks this up next. This document is intended to be self-contained — you should not need the original conversation.

---

## 1. Goal

`sosialhjelp-fiks-service` becomes the single place that talks to KS Fiks Digisos, parses the
`hendelser` list from the innsynsfil (`JsonDigisosSoker`) and folds it into one coherent domain
model. Three consumers:

| Consumer | Inbound token | Fiks endpoint family | Fiks credential |
|---|---|---|---|
| `sosialhjelp-innsyn-api` | raw ID-porten token (passthrough) | `/digisos/api/v1/soknader/*` | the user's own ID-porten token |
| `sosialhjelp-modia-api` | Entra ID (Azure AD) OBO | `/digisos/api/v1/nav/soknader/*` | Maskinporten (`ks:fiks`) + `sporingsId` |
| `sosialhjelp-adminpanel` | Entra ID (Azure AD) OBO | `/digisos/api/v1/nav/soknader/*` | Maskinporten (`ks:fiks`) + `sporingsId` |

Consumers keep their own presentation logic, filtering and text. This service owns fetching,
parsing, folding, and access control.

---

## 2. Situation as of writing

### 2.1 This repo

Only two commits. It already contains roughly 80 % of `innsyn-api`'s `EventService` copied in:

- `domain/DigisosSoker.kt` — mutable `InternalDigisosSoker` aggregate
- `event/*.kt` — one `apply()` extension function per hendelse type, plus `EventService.kt`
- `digisosapi/FiksClient.kt` — currently hits the **ID-porten** paths (`/digisos/api/v1/soknader/*`)
- `valkey/ValkeyClient.kt` — Lettuce cache
- Ktor 3.2, Kotlin 2.3.20, `ktor-server-di` (not Koin), Java 21
- `app/texas/TexasClient.kt` exists but is **never instantiated**

Known defects in the current code (fix as part of this work):

- `Application.kt` `configureAuth()` has `skipWhen { true }` when `TOKEN_X_JWKS_URI` is unset — a
  missing env var silently disables all authentication.
- `vedlegg/NoopVedleggService` returns `emptyList()`, so `applySoknadKrav` never fires and
  `oppgaver` is silently wrong.
- `FiksService` removes the per-key mutex in `finally`, making the request-dedup racy.
- `ValkeyClient` ignores `VALKEY_USERNAME`/`VALKEY_PASSWORD` from the nais binding and uses
  `redis://` without TLS.
- `FIKS_SVARUT_ENDPOINT_URL` is not set in either nais manifest, so SvarUt URLs are malformed.
- `nais/prod/prod.yaml` lists `sosialhjelp-innsyn-api` twice under `accessPolicy.inbound`.
- No Prometheus scrape config for `/internal/metrics`.
- `logback.xml` has a single STDOUT appender — no `sporingslogg` appender exists yet.
- `FiksClient` has a dead shadowing `companion object { private val log by logger() }` and a
  `log.debug("Hentet ${0} DigisosSaker fra Fiks")` with a literal `0`.
- No ktor-client retry/timeout plugin installed despite the dependency being declared.

### 2.2 modia-api (reference for the OBO path)

- `digisossak/fiks/FiksPaths.kt` — `/digisos/api/v1/nav/soknader/...`, `?sporingsId={uuid}` on every
  call, `POST` body `{"fnr": "..."}` for the list endpoint.
- Headers `IntegrasjonId` / `IntegrasjonPassord` (from nais secret `fiks-integrasjon-secret`), plus
  `Authorization: Bearer <maskinporten>` via Texas M2M (`identity_provider=maskinporten`,
  `target=ks:fiks`).
- `tilgang/TilgangskontrollService.kt` — PDL kode 6/7 **fail-closed**, then
  `skjermede-personer-pip` **fail-closed**. Called at the top of every controller method.
- `logging/{AuditService,AuditLogger,AuditLoggingConstants}.kt` + `logging/cef/CommonEventFormat.kt`
  — CEF to logger `sporingslogg`, syslog TCP `audit.nais:6514`.
  Format: `CEF:0|sosialhjelp-modia-api|sporingslogg|1.0|audit:access|Fiks|INFO|end=... suid=<NAVident>
  duid=<fnr> sproc=<callId> dproc=srvsosialhjelp-mod request=<url> requestMethod=GET cs5=<sporingsId>
  cs5Label=fiksRequestId`
- **Known gap:** the audit entry lives in `FiksClientImpl` *after* the HTTP call, so a cache hit
  produces no audit entry.
- Cache key is session-scoped: `<sosialhjelp-modia sessionId>_<digisosId>`.
- PDL `behandlingsnummer` = **B117**.

### 2.3 innsyn-api (reference for the citizen path)

- `digisosapi/FiksPaths.kt` — `/digisos/api/v1/soknader/...`; the raw ID-porten token is forwarded
  verbatim (`TokenUtils.getToken().withBearer()`), no exchange.
- `POST /digisos/api/v1/soknader/dokumenter` returns `multipart/mixed` — used to fetch all
  innsynsfiler for a person in one round-trip. Part name is `"${fiksDigisosId}_${dokumentlagerId}"`.
  See `MultipartMixedReader`.
- `tilgang/Tilgangskontroll.kt` — PDL kode 6/7 but **fail-open** (a `PdlException` yields `null`,
  and the check only denies when non-null).
- `verifyDigisosSakIsForCorrectUser` — checks `digisosSak.sokerFnr` against PDL `hentIdenter`
  (historical idents included), run on every `getSoknad` and on each element of the list.
- Suppresses the whole innsynsfil fetch when the kommune has `kanOppdatereStatus == false`.
- PDL `behandlingsnummer` = **B478**.
- Inbound `AudienceValidator` checks the **`client_id`** claim, not `aud`. Also validates
  `acr in {Level4, idporten-loa-high}`.

### 2.4 adminpanel

Not cloned locally at time of writing. Confirmed to need the same thing as modia (søknad lookup by
fnr / digisosId). Do not design for it beyond keeping the caller allowlist extensible.

---

## 3. Decisions

All of these were explicitly agreed. Where a decision reverses current behaviour in a consumer, that
is called out.

| # | Area | Decision |
|---|---|---|
| D1 | Auth | Multi-issuer. `Authorization` is either an ID-porten token (citizen path) or an Entra OBO token (saksbehandler path). **The issuer selects the mode.** |
| D2 | Citizen path | Raw ID-porten token forwarded verbatim to Fiks `/digisos/api/v1/soknader/*`. fnr taken from the `pid` claim. |
| D3 | OBO path | Maskinporten (Texas M2M, `ks:fiks`) to `/digisos/api/v1/nav/soknader/*`. fnr supplied by the caller in the request body/header. |
| D4 | Gating | PDL kode 6/7 on **all** paths. `skjermede-personer-pip` on **OBO paths only**. |
| D5 | Fail mode | **Fail closed** everywhere. Reverses innsyn-api's current fail-open behaviour. Mitigated by a short-TTL positive cache. |
| D6 | Denial | **404 everywhere**, reason in logs only. Reverses modia's current 403 `tilgang_error`. |
| D7 | Eier-verifisering | Moves into this service. `sokerFnr` checked against PDL `hentIdenter` on the citizen path. |
| D8 | Audit | CEF to `sporingslogg`, product `sosialhjelp-fiks-service`, emitted on **every inbound OBO request, before the upstream call**. Fixes modia's cache-hit gap. |
| D9 | Citizen audit | **No** sporingslogg entry when a person reads their own data. Metric only. |
| D10 | sporingsId | Derived from the **OTel trace id**. `Nav-Call-Id` is legacy and is not used. |
| D11 | behandlingsnummer | Caller sends it as a header, **validated against a per-`client_id` allowlist** so it cannot be forged. |
| D12 | Model | Pure facts. **No display text, no i18n keys.** Consumers map to their own text. |
| D13 | Response | Folded aggregate **and** normalized typed event list. |
| D14 | Shape | **One DTO for all callers.** No per-caller field suppression. |
| D15 | Types | `Instant`/`OffsetDateTime` on the wire, `BigDecimal` for beløp, opaque `DokumentRef` instead of built URLs. |
| D16 | Krav | Unify `oppgaver` / `dokumentasjonkrav` / `vilkar` into one sealed `Krav` hierarchy with a `kilde` discriminator. |
| D17 | Sak/vedtak | Drop the synthetic `"default"` sak. `Vedtak.saksReferanse` is nullable; consumers group. |
| D18 | Status | Expose both the folded `status` and the derived `avledetStatus`. |
| D19 | Scope | Read-only: DigisosSak, folded model, bulk oversikt, kommuneinfo, vedleggspesifikasjon. **No writes** (ettersendelse, klage, mellomlagring stay in innsyn-api). |
| D20 | Innsyn deaktivert | Not this service's concern. Consumers read kommuneinfo and decide whether to call at all. |
| D21 | Bulk | Yes — a bulk endpoint returning folded models for all of a person's søknader, using Fiks' `multipart/mixed` dokument endpoint. |
| D22 | Migration | Parallel run; consumers migrate endpoint by endpoint. innsyn-api first (lowest parity risk), then modia, then adminpanel. |

### 3.1 Rationale worth preserving

**Why skjerming only on OBO paths (D4).** `skjermede-personer-pip` answers "is this person a
skjermet (egen ansatt) person". It is a control on *saksbehandlere looking up others*. A skjermet
person viewing their **own** søknad in innsyn is not a leak, and blocking it would break
self-service for Nav employees who need sosialhjelp.

**Why fiks-service accepts a token whose `aud` is not itself (D1/D2).** Fiks' `/soknader/*` family
requires an actual ID-porten token; nothing else is accepted. Since innsyn-api must hand that token
over, and we chose to carry it as `Authorization` rather than in a second header, this service
validates a token issued for innsyn-api. **This will be flagged in security review.** The
mitigations are: the `client_id` allowlist, the `acr` check, and `accessPolicy.inbound`. Document it
in the code.

---

## 4. Blocking spike (do this first)

### S1 — Can TokenX exchange a foreign ID-porten token?

Because the raw ID-porten token was chosen as `Authorization` (D2), this service holds a token whose
`client_id` is `innsyn-api`. tokendings binds the `subject_token` to the requesting client via the
`client_id` claim — Nav's docs phrase it as "the subject token must be an ID-porten or TokenX token
*issued for your application*". If that holds, this service **cannot** exchange it to reach PDL.

**Test:** from fiks-service in dev, call Texas `BEHALF_OF` with `identity_provider=tokenx`,
`user_token=<raw ID-porten token issued to innsyn-api>`, `target=<pdl scope>`.

- **Succeeds** → citizen-path PDL calls use the exchanged user token. Done.
- **Rejected** (expected) → fall back to fiks-service's own **Entra client-credentials (M2M)** token
  to PDL. This works, but PDL then sees a machine rather than the user, so **get personvern
  sign-off** that a machine-token kode 6/7 lookup is acceptable.

Design so the outcome is a one-line swap and blocks nothing else:

```kotlin
interface PdlTokenStrategy { suspend fun tokenFor(caller: Caller): String }
class TokenXOnBehalfOf(...) : PdlTokenStrategy
class EntraM2M(...)         : PdlTokenStrategy
```

Only §6 (tilgangskontroll) depends on the outcome.

---

## 5. Auth layer

Replace `Application.kt::configureAuth()` entirely. **Delete the `skipWhen { true }` bypass.**

```
authentication {
  jwt("idporten") {
    issuer = IDPORTEN_ISSUER; verifier(idportenJwks)
    validate { require client_id in allowlist; require acr in {Level4, idporten-loa-high} }
  }
  jwt("entra") {
    issuer = AZURE_OPENID_CONFIG_ISSUER; verifier(azureJwks)
    validate { require aud == AZURE_APP_CLIENT_ID; require NAVident present }
  }
}
```

New files under `app/auth/`:

- **`CallerRegistry.kt`** — config-driven map: `client_id` → `Caller(navn, type, tillatteBehandlingsnummer)`.
  Backs both the allowlist (D1) and the behandlingsnummer validation (D11).
- **`Caller.kt`** — sealed:
  ```kotlin
  sealed interface Caller {
      val appNavn: String
      data class Citizen(val pid: Fnr, val rawIdportenToken: String, override val appNavn: String) : Caller
      data class Saksbehandler(val navIdent: String, val oboToken: String, override val appNavn: String) : Caller
  }
  ```
- **`CallerPlugin.kt`** — Ktor plugin resolving `Caller` from the principal into call attributes.

Routes use `authenticate("idporten", "entra")` and branch on `call.caller`.

Note: `JwkProviderBuilder` should be configured with cache and rate limiting; the current code
builds it bare.

---

## 6. Tilgangskontroll — new package `tilgang/`

- **`PdlClient.kt`** — GraphQL. Needs `adressebeskyttelse(historikk: false){ gradering }` and
  `hentIdenter`. `behandlingsnummer` header from the *validated* caller value (D11). Token from
  `PdlTokenStrategy` (see S1).
- **`SkjermedePersonerClient.kt`** — `POST /skjermet` with body `{"personident": "<fnr>"}`.
  Port modia's fail-closed semantics **but fix the response parsing**: modia does
  `"false" != response`, which treats any transport oddity as "skjermet". Parse a boolean explicitly
  and throw on anything else.
- **`TilgangskontrollService.kt`**:
  ```kotlin
  suspend fun sjekkTilgang(fnr: Fnr, caller: Caller) {
      val person = pdl.hentPerson(fnr, caller)          // throws -> deny (D5)
      if (person.harGradering()) deny(KODE_6_7)
      if (caller is Caller.Saksbehandler && skjerming.erSkjermet(fnr, caller)) deny(SKJERMET)
  }
  ```
  `deny()` audit-logs `Access.DENY` (OBO path only), logs the reason, and throws
  `IkkeTilgangException` → **404** in `StatusPages` (D6).

Graderinger that deny: `FORTROLIG` (kode 7), `STRENGT_FORTROLIG` and `STRENGT_FORTROLIG_UTLAND`
(kode 6).

### Ordering

- **OBO path**: fnr is caller-supplied, so gate **before** any Fiks call. After fetching, verify
  `fnr == digisosSak.sokerFnr` and 404 on mismatch.
- **Citizen path**: gate on `pid` before the call, then run eier-verifisering against
  `hentIdenter` on the response (D7).

### Caching of verdicts

Negative results are **not** cached. Positive results ("ikke gradert" / "ikke skjermet") cached
~10 min in Valkey keyed on a **hashed** fnr; skjerming keeps modia's 2 h TTL. Access verdicts are
cached separately from payloads and are never bundled with them.

---

## 7. Domain model — `domain/`

Replace `domain/DigisosSoker.kt`. Immutable data classes; no `var`, no `MutableList` on the wire.

```kotlin
data class Soknad(
    val fiksDigisosId: String,
    val navEksternRefId: String?,
    val kommunenummer: String,
    val fagsystem: Fagsystem?,
    val erPapirsoknad: Boolean,               // originalSoknadNAV == null
    val tidspunktSendt: Instant?,
    val sistEndret: Instant,

    val status: SoknadsStatus,                // folded value
    val avledetStatus: SoknadsStatus,         // after the aktive-saker override (D18)

    val mottaker: NavEnhet?,                  // current
    val navKontorHistorikk: List<NavKontorTildeling>,   // modia needs this

    val saker: List<Sak>,
    val vedtak: List<Vedtak>,                 // flat; saksReferanse nullable (D17)
    val utbetalinger: List<Utbetaling>,
    val krav: List<Krav>,                     // unified (D16)
    val forvaltningsbrev: List<Dokument>,
    val forelopigSvar: ForelopigSvar?,
    val originalSoknad: DokumentRef?,
)

sealed interface Krav {
    val referanse: String
    val tittel: String?
    val beskrivelse: String?
    val status: KravStatus
    val frist: LocalDate?
    val saksReferanse: String?
    val utbetalingsReferanser: List<String>
    val gruppeId: String?   // the sha256(frist) that both apps group on today — keep it

    data class DokumentasjonEtterspurt(...) : Krav   // was: Oppgave, erFraInnsyn = true
    data class SoknadVedleggKreves(...)     : Krav   // was: Oppgave, erFraInnsyn = false
    data class Dokumentasjonkrav(...)       : Krav
    data class Vilkar(...)                  : Krav
}

sealed interface DokumentRef {
    data class Dokumentlager(val id: String) : DokumentRef
    data class SvarUt(val id: String, val nr: Int) : DokumentRef
}
```

- `Utbetaling.belop: BigDecimal` (D15).
- **Keep the `kontonummer = null when annenMottaker` rule verbatim**, including the detail that
  `annenMottaker == null` counts as true. This is a deliberate fail-safe in both apps today.
- `KravStatus` should collapse the deprecated `OPPFYLT`/`IKKE_OPPFYLT` the same way
  `getOppgaveStatus()` does today — or expose the raw value and let consumers collapse. Prefer
  exposing raw; consumers already have the collapse logic.

### Event list — `domain/Hendelse.kt`

Sealed hierarchy carrying **facts only** (D12):

```kotlin
sealed interface Hendelse { val tidspunkt: Instant }

data class SoknadSendt(...) : Hendelse
data class SoknadsStatusEndret(val status: SoknadsStatus, val mottakerNavn: String?) : Hendelse
data class TildeltNavKontor(
    val fraEnhet: NavEnhet?,
    val tilEnhet: NavEnhet?,
    val enhetOppslagFeilet: Boolean,
    val erForsteTildeling: Boolean,
) : Hendelse
data class SaksStatusEndret(...) : Hendelse
data class VedtakFattet(...) : Hendelse
data class DokumentasjonEtterspurt(...) : Hendelse
data class ForelopigSvarMottatt(...) : Hendelse
data class UtbetalingEndret(...) : Hendelse
data class KravEndret(...) : Hendelse
```

The nullable/boolean fields are load-bearing: `mottakerNavn` lets innsyn reconstruct
`SOKNAD_MOTTATT_MED_KOMMUNENAVN` vs `..._UTEN_KOMMUNENAVN`, and
`enhetOppslagFeilet` + `erForsteTildeling` + `erPapirsoknad` reconstruct the four
`SOKNAD_VIDERESENDT_*` variants — **without** this service owning any text.

Response envelope:

```kotlin
data class SoknadResponse(val soknad: Soknad, val hendelser: List<Hendelse>)
```

---

## 8. Fold engine — `event/`

Keep the existing `apply()`-per-hendelse structure. Internally keep a mutable accumulator (the fold
is genuinely imperative) and map to the immutable `Soknad` at the end, emitting the `Hendelse` list
as a side output of the same pass.

**Keep `hendelseComparator` semantics exactly:**

1. by `hendelsestidspunkt`
2. `UTBETALING` before `VILKAR`/`DOKUMENTASJONKRAV` (so krav can attach to the utbetaling)
3. `MOTTATT` before `UNDER_BEHANDLING`

One change: compare the timestamp as a **parsed instant**, not as a raw string. Both apps currently
rely on Fiks' ISO-8601 formatting being lexicographically sortable.

Changes vs. today:

- Drop URL construction (`Utils.hentUrlFraFilreferanse`) → emit `DokumentRef` (D15).
- Delete `HendelseTekstType`, and do not port `Titler.kt` / `Tekster.kt` from modia (D12).
- `VedtakFattet`: no synthetic `"default"` sak, and no
  `referanse == saksreferanse || referanse == "default"` loose match — that can attach a vedtak to
  the wrong sak (D17).
- `overrideSoknadsstatusIfActivesakerExists` computes `avledetStatus` and leaves `status` intact (D18).
- **Implement `VedleggService` for real** — fetch `originalSoknadNAV.vedleggMetadata` →
  `JsonVedleggSpesifikasjon` — so `applySoknadKrav` works. Delete `NoopVedleggService`.
- `Rammevedtak` stays a no-op, but emit nothing rather than logging per hendelse.

### Tests

Port the existing suite: `EventTestData.kt`, `SaksStatusTest`, `UtbetalingTest`, `VilkarTest`,
`VedtakFattetTest`, `TildeltNavKontorTest`, `ForelopigSvarTest`.

**Add the missing ones** (no coverage exists today): `SoknadsStatus`, `Dokumentasjonkrav`,
`DokumentasjonEtterspurt`, `SoknadDokumentasjonskrav`.

---

## 9. Fiks client — `digisosapi/`

```kotlin
object FiksPaths {
    // citizen (ID-porten)
    const val SOKNAD          = "/digisos/api/v1/soknader/{digisosId}"
    const val ALLE_SOKNADER   = "/digisos/api/v1/soknader/soknader"
    const val DOKUMENT        = "/digisos/api/v1/soknader/{digisosId}/dokumenter/{dokumentlagerId}"
    const val DOKUMENTER_BULK = "/digisos/api/v1/soknader/dokumenter"       // POST, multipart/mixed

    // saksbehandler (Maskinporten)
    const val NAV_SOKNAD        = "/digisos/api/v1/nav/soknader/{digisosId}"
    const val NAV_ALLE_SOKNADER = "/digisos/api/v1/nav/soknader/soknader"   // POST {"fnr": "..."}
    const val NAV_DOKUMENT      = "/digisos/api/v1/nav/soknader/{digisosId}/dokumenter/{dokumentlagerId}"

    const val KOMMUNEINFO       = "/digisos/api/v1/nav/kommuner/{kommunenummer}"
}
```

One `FiksClient` with a `Caller`-driven strategy picking the path prefix and the token (raw
ID-porten vs Texas Maskinporten — **wire up the currently-unused `TexasClient`**).
`IntegrasjonId` / `IntegrasjonPassord` on all calls. `sporingsId` (= OTel trace id, D10) appended as
a query param on `/nav/*` calls.

Fixes to make while here:

- Install the ktor-client **retry** plugin: 5 attempts, exponential backoff from 100 ms, **5xx
  only**. Also set timeouts (modia uses 30 s connect/socket, 2 min response).
- Mask fnr in all error messages: `\b[0-9]{11}\b` → `[FNR]`.
- Remove the dead shadowing `companion object { private val log by logger() }`.
- Fix `log.debug("Hentet ${0} DigisosSaker fra Fiks")`.
- Never forward upstream error bodies to clients.

**Bulk oversikt** (D21) uses `POST /digisos/api/v1/soknader/dokumenter` with `Accept:
multipart/mixed`; port innsyn-api's `MultipartMixedReader`. Part `Content-Disposition; name` is
`"${fiksDigisosId}_${dokumentlagerId}"`. This makes the oversikt one round-trip instead of N.

### Caching — `FiksService`

Global keys (safe because the access gate runs on **every** request, D4/§6):

| Key | TTL | Contents |
|---|---|---|
| `fiks:sak:{digisosId}` | 60 s | raw `DigisosSak` |
| `fiks:dok:{dokumentlagerId}_{timestampSistOppdatert}` | 1 h | raw `JsonDigisosSoker` |
| `fiks:model:{digisosId}_{timestampSistOppdatert}` | 1 h | folded model — folding is the expensive part |

Access verdicts live under their own keys and are never bundled with payloads.

Fix the racy `requestLocks.remove(key)` in `finally`. Fix `ValkeyClient` to use the
`VALKEY_USERNAME`/`VALKEY_PASSWORD` from the nais binding and `rediss://`.

---

## 10. Audit logging — `logging/`

Port modia's `AuditService` / `AuditLogger` / `CommonEventFormat` in shape, with:

- CEF product `sosialhjelp-fiks-service`; `dproc` = calling app name; `suid` = NAVident;
  `duid` = fnr; `sproc` = OTel trace id; `cs5` = sporingsId.
- Emitted on **every inbound OBO request, before the upstream call** (D8) — this fixes modia's
  cache-hit gap — plus an `Access.DENY` entry on every gate denial.
- **No entry on the citizen path** (D9). Count it as a metric instead.
- `logback.xml` needs a `sporingslogg` appender → syslog TCP `audit.nais:6514`,
  `maxMessageLength 128000`, pattern `%msg%n`, `additivity="false"`. Without this the audit lines
  leak into normal application logs.
- Note that modia's `Extension.toString()` puts `ACCESS` in the map but never renders it. Decide
  whether to render it; if you do, tell whoever owns the ArcSight queries.

---

## 11. API surface

All routes under `authenticate("idporten", "entra")`:

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/v1/soknader/{digisosId}` | citizen path; fnr from `pid` |
| `POST` | `/api/v1/soknader/{digisosId}` | OBO path; body `{"fnr": "..."}` |
| `GET` | `/api/v1/soknader` | citizen oversikt (bulk) |
| `POST` | `/api/v1/soknader/sok` | OBO oversikt (bulk); body `{"fnr": "..."}` |
| `GET` | `/api/v1/kommuner/{kommunenummer}` | kommuneinfo |
| `GET` | `/internal/health`, `/internal/metrics` | unchanged, unauthenticated |

`POST` is used for the OBO variants purely to keep the fnr out of URLs, access logs and traces.

`StatusPages` mapping:

| Exception | Status |
|---|---|
| `IkkeTilgangException` | **404** (D6) |
| `FiksNotFoundException` | 404 |
| `FiksClientException` | 502 |
| `FiksServerException` | 502 |
| anything else | 500 |

Never include upstream error text in the response body.

---

## 12. nais changes

- `azure.application.enabled: true` with `claims.groups` for modia and adminpanel. Keep `tokenx`
  and `maskinporten` (`ks:fiks`).
- `accessPolicy.inbound`: add `sosialhjelp-modia-api` and `sosialhjelp-adminpanel`.
  **Fix the duplicated `sosialhjelp-innsyn-api` entry in `nais/prod/prod.yaml`.**
- `accessPolicy.outbound`: add `skjermede-personer-pip` (namespace `nom`) and PDL
  (external `pdl-api.prod-fss-pub.nais.io`, dev equivalent in dev).
- Add `FIKS_SVARUT_ENDPOINT_URL` — currently unset in both manifests. Even though we no longer build
  URLs here, confirm who owns the base URL now that consumers construct them.
- Add Prometheus scrape config for `/internal/metrics`.

---

## 13. Sequencing

1. **S1 spike** (§4). Unblocks §6 only — do not let it block anything else.
2. Auth layer + `CallerRegistry`; remove the auth bypass (§5).
3. `FiksClient` dual-path + Maskinporten via Texas (§9).
4. Domain model + fold rewrite + full test suite (§7, §8).
   **Parity gate:** shadow-compare output against innsyn-api's `EventService` over a corpus of real
   `JsonDigisosSoker` files. This is what makes the parallel run safe — do not skip it.
5. Tilgangskontroll (§6) + audit logging (§10).
6. Bulk oversikt + kommuneinfo (§9, §11).
7. Parallel run and migration (D22): innsyn-api first (its code is already largely here, so parity
   risk is lowest), then modia-api, then adminpanel.

---

## 14. Consequences for consumers

- **modia-api** loses the distinction between "mangler tilgang" (403) and "finnes ikke" (404).
  Accepted. Its frontend must degrade gracefully.
- **innsyn-api** changes from fail-open to fail-closed on PDL errors. A PDL outage will now deny
  access rather than grant it. Mitigated by the positive-verdict cache.
- **Both** must map the fact-only `Hendelse` types to their own text, and build dokument URLs from
  their own config.
- **Both** keep: presentation filtering, synthetic timeline entries (vedlegg-uploads,
  utbetalings-grouping), age cutoffs (15 months), fagsystem-gating, and the innsyn-deaktivert
  decision (D20).

---

## 15. Still open

Nothing blocking. The only unresolved item is the **S1 spike outcome**, which decides the
`PdlTokenStrategy` implementation and whether personvern sign-off is needed for a machine-token
kode 6/7 lookup.
