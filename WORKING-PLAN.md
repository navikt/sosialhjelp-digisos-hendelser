# Working plan: rewrite as `sosialhjelp-digisos-hendelser`

> Temporary working document. Delete when the work is done.
>
> **SUPERSEDES `docs/domenetjeneste-plan.md`.** That document describes a shared
> *service* architecture (decisions D1–D22) which has been abandoned. Delete it as part
> of this rewrite. Ignore its contents — following it will produce the wrong thing.

## What changed and why

This repo was a Fiks *domain service* fronting KS Fiks Digisos for innsyn-api and
modia-api. That design was dropped because it:

- forced innsyn-api's fail-open tilgangskontroll to become fail-closed (a regression)
- forced modia-api's 403 responses to become 404 (a regression)
- required the service to accept an ID-porten token whose `aud` is innsyn-api, which
  the old plan itself flagged as *"will be flagged in security review"*
- added a runtime failure point to two user-critical flows
- introduced a cache-isolation hazard: a Valkey key without the caller identity lets a
  cache hit bypass Fiks' own authorization entirely

None of those exist if the shared thing is a **library** instead of a service.

The genuine duplication being solved is the hendelse fold, which exists in two
divergent copies (innsyn-api 1062 lines, modia-api 710 lines).

## New goal

A **Kotlin Multiplatform library** that folds a Digisos hendelse stream into a read
model. Nothing else. Each consumer keeps its own Fiks client, auth, tilgangskontroll,
audit logging and caching.

Consumers: `sosialhjelp-innsyn-api`, `sosialhjelp-modia-api` (Kotlin/JVM, Spring),
`sosialhjelp-adminpanel` (Next.js, Node server-side — hence multiplatform).

## Core principle: the fold must be PURE

No IO, no text, no `suspend`. This is what makes one library serve all three consumers
*and* what makes KMP viable. Concretely:

| Emit facts | NOT display data | Consumer does |
|---|---|---|
| `enhetsnummer` | `enhetsnavn` | NORG lookup post-fold |
| `DokumentRef(id)` | built URL | URL building from own config |
| typed event list | `historikk` with text | maps to own i18n keys / Norwegian strings |

Dropping NORG from the fold removes the `suspend`-vs-blocking colour problem entirely
(innsyn's fold is `suspend`, modia's is blocking — a shared impure fold would have had
to pick one and regress the other).

## API shape

Two outputs from one fold:

- **`Soknad`** (aggregate) — "what is true now": saker, vedtak, utbetalinger, krav, status
- **`List<SoknadHendelse>`** (typed event list) — "what happened, in order, as facts"

Both are needed: the aggregate loses ordering and intermediate states; the event list
cannot answer "current utbetaling status" without replaying.

```kotlin
fun fold(hendelser: List<Hendelse>, metadata: SoknadMetadata): FoldResult

data class FoldResult(
    val soknad: Soknad,
    val hendelser: List<SoknadHendelse>,
)
```

## Salvage vs delete

**Keep and port** (already close to the right shape):

- `domain/Soknad.kt`
- `domain/SoknadHendelse.kt`
- `event/FoldAccumulator.kt`
- `event/*.kt` handler logic
- the existing test suite

**Delete:**
`digisosapi/`, `tilgang/`, `logging/`, `app/auth/`, `app/texas/`, `valkey/`, `routes/`,
`kommuneinfo/`, `navenhet/`, `vedlegg/`, `nais/`, `Dockerfile`, `Application.kt`,
`docs/domenetjeneste-plan.md`

**Refine while porting:**
`SoknadHendelse.TildeltNavKontor` currently carries `tilEnhet: NavEnhet` (with a
resolved name) and `enhetNavnOppslagFeilet`. Both imply the NORG lookup happened inside
the fold. Change to `enhetsnummer: String` only — the consumer resolves the name and
decides the "lookup failed" variant itself.

## Module structure

```
sosialhjelp-digisos-hendelser/
├─ src/commonMain/kotlin/
│   ├─ domain/   Soknad.kt, SoknadHendelse.kt   (port, minus java.time / BigDecimal)
│   ├─ fold/     FoldAccumulator + per-hendelse apply()
│   └─ Fold.kt   public API
├─ src/commonTest/    (port innsyn-api's fold tests — see below)
├─ src/jvmMain/       BigDecimal ext, adapter from Java JsonDigisosSoker
└─ src/jsMain/        npm packaging
```

**The JVM migration surface is small.** Current JVM-only types:

- `java.time.Instant` / `LocalDate` — `SoknadHendelse.kt:3-4`, `Soknad.kt:4-5`
- `java.math.BigDecimal` — `Soknad.kt:105`, one field: `Utbetaling.belop`

Migration:

- dates -> `kotlinx-datetime`
- `belop` -> `String` in commonMain plus a `jvmMain` extension returning `BigDecimal`.
  (The wire format is a JSON number; the existing Java filformat model reads it as
  `Double`, and both consumers convert to `BigDecimal` themselves.)

## Input model

Depends on `soknadsosialhjelp-filformat-kmp` — a new multiplatform artifact being added
in the `soknadsosialhjelp-filformat` repo. That repo has its own working plan.

**Build a `jvmMain` adapter** accepting the existing Java `JsonDigisosSoker` and mapping
to the common model. This lets innsyn-api and modia-api adopt the library **before**
filformat-kmp lands, and sidesteps their version skew (innsyn on filformat
`1.2026.08.10`, modia on `1.2026.05.27`).

## Semantic baseline: innsyn-api

Where innsyn-api and modia-api's fold logic differ, **innsyn-api wins** — it has the
richer model and 3076 lines of fold tests vs modia's 1834. Port innsyn's test suite into
`commonTest` as the regression gate.

Five known divergences reconcile to innsyn. Details, line references and user-visible
consequences are in `sosialhjelp-modia-api/WORKING-PLAN.md`. Summary:

1. `DokumentasjonEtterspurt` `FERDIGBEHANDLET` suppression (modia-only)
2. Dokumentasjonkrav historikk entry (commented out in modia)
3. Comparator third tiebreaker `mottattBeforeUnderBehandling` (innsyn-only)
4. Dokumentasjonkrav `oppdaterFelter` — 6 fields vs modia's 2
5. Utbetaling filter — `!= ANNULLERT` vs modia's `== UTBETALT`

**One exception — `navKontorHistorikk` is a modia *feature*, not a divergence to
remove.** Resolution: emit every `TildeltNavKontor` in the typed event list, use
innsyn's dedup semantics for the aggregate's current-kontor field, and let modia derive
its historikk from the event list post-fold. Nobody loses anything.

## Known model differences to reconcile while porting

The two source repos' models differ in ways the shared model must absorb:

- `Sak.datoOpprettet` — modia-only, non-nullable. Add it (harmless for innsyn).
- `Vedtak` — innsyn has `id` + `vedtaksFilUrl`; modia has neither. Shared model carries
  `DokumentRef` instead, and consumers build URLs.
- `Vilkar.tittel` — innsyn-only.
- `Oppgave` — innsyn 9 fields, modia 5. Shared model is the superset; modia projects.
- `Dokumentasjonkrav.frist` — `LocalDate?` (innsyn) vs `LocalDateTime?` (modia). Use
  innsyn's `LocalDate?`.
- `Oppgavestatus` vs `OppgaveStatus` — identical constants, different identifier casing.

## Sequencing

1. Rename repo to `sosialhjelp-digisos-hendelser`
2. Delete everything outside the salvage list; delete `docs/domenetjeneste-plan.md`
3. Convert to KMP, **JVM target only** initially
4. Port domain + fold + innsyn's tests to `commonMain` / `commonTest`
5. Build the Java `JsonDigisosSoker` adapter in `jvmMain`
6. Publish the JVM artifact; migrate innsyn-api, then modia-api
7. Switch input model to filformat-kmp, add the JS target, wire up adminpanel

Steps 1–6 deliver most of the value and carry no KMP/JS risk.

## Model restructure — `Soknad` is now nested, not flat

The initial port of `domain/Soknad.kt` kept the flat shape from innsyn/modia (parallel
lists linked by `saksReferanse: String?`). It has since been restructured: `Vedtak`,
`Utbetaling`, `Dokumentasjonkrav` and `Vilkar` now nest under `Sak`. Migration notes for
step 6 (innsyn-api, modia-api):

1. **The `"default"` sak is gone.** Both consumers historically materialised a
   `VedtakFattet` with a blank saksreferanse as a sak with referanse `"default"` and a
   hardcoded title (innsyn: `DEFAULT_SAK_TITTEL` i18n key; modia: `"Økonomisk
   sosialhjelp"`). The library exposes these as `Soknad.vedtakUtenSak` instead — read
   that list and build the placeholder sak with your own title constant.
2. **`Sak.tittel` / `Sak.saksStatus` may be null**, on a sak synthesized from a
   `Dokumentasjonkrav`/`Vilkar`/`Vedtak` referencing a saksreferanse with no prior
   `SaksStatusEndret`. Both consumers already have the fallback (`sak.tittel ?:
   DEFAULT_SAK_TITTEL`, `sak.saksStatus ?: UNDER_BEHANDLING`) — no change needed there.
3. **Krav sits on `Sak`, not on `Utbetaling`.** `Sak.dokumentasjonkrav` /
   `Sak.vilkar` keep `utbetalingsReferanser: List<String>` rather than being duplicated
   under each `Utbetaling` they apply to. Rebuild the old per-utbetaling view with:
   ```kotlin
   sak.utbetalinger.associateWith { u ->
       sak.dokumentasjonkrav.filter { u.referanse in it.utbetalingsReferanser }
   }
   ```
   This incidentally fixes a bug present in both consumers, where a `Vilkar` on a
   sak-orphaned `Utbetaling` is silently dropped because the lookup only scans
   sak-nested utbetalinger (`Dokumentasjonkrav` already compensates for this; `Vilkar`
   does not).
4. **`Krav.DokumentasjonEtterspurt` and `Krav.SoknadVedleggKreves` merged** into one
   `DokumentasjonEtterspurt` type at søknadsnivå, discriminated by `kilde: Kilde`.
5. **`gruppeId` is non-null** — `sha256(frist.toString())`, or `sha256("null")` when
   `frist` is absent. Matches innsyn's pinned `oppgaveId`/`dokumentasjonkravId` hashes.
6. **Vedtak no longer cross-bind to a `"default"` sak.** Both consumers' current
   `VedtakFattet` handler binds *every* subsequent vedtak to the first `"default"` sak
   once one exists (`it.referanse == hendelse.saksreferanse || it.referanse ==
   "default"`), regardless of the vedtak's own saksreferanse. This is a bug, not
   behaviour to preserve — the library does not reproduce it.
7. **`Vedtak.referanse` renamed to `Vedtak.dokument`** (`DokumentRef`, unchanged type)
   — it identifies a document, not a lookup key.
8. **`Forvaltningsbrev` and `ForelopigSvar`** (byte-identical shapes) merged into one
   `DatertDokument`.

