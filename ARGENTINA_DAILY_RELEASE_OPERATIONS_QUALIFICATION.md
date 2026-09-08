# Argentina daily release operations V1

Status: **qualified and activated locally**. The Tuesday release is active in the local content-addressed workspace; cloud deployment and Android networking remain out of scope.

## Exact release evidence

- Starting repository SHA: `6ea860f50444da14485d3fe49ba2168003041e23`
- Previous official release: 2026-09-06, SHA-256 `e6c08be6a36e5e5b90e6eb0b6f54a07c8bccded929fab2a28ad7f000ed08b305`
- New operator-supplied release: `sepa_martes.zip`, 2026-09-08, 325,118,492 bytes, SHA-256 `aeef3399fa20e751e80cda46cbb3a38349e378b51832ecffe1f779b986479815`
- Internal dated ZIP evidence and CRC checks passed. It contains 17 nested retailer packages; one explicit zero-byte package is quarantined as `EMPTY_PROVIDER_PACKAGE`.
- Attribution is preserved as **Precios Claros - Base SEPA**, Creative Commons Attribution 4.0, source host `datos.produccion.gob.ar`.

`SECOND_OFFICIAL_RELEASE_PROVEN = true`.

## Qualification and backend

The existing M1 authority qualified 14,560,394 rows. It accepted 14,456,334 current-price rows and quarantined 104,060. Availability remains `UNKNOWN`; stale packages, implausible positive prices, malformed rows, orphan stores, invalid prices, and the empty package remain explicit diagnostics. No raw national archive is published or committed.

The exact production counts are **14,387,849 offers**, **2,909 stores**, **1,480,719 product-evidence records**, **886,017 promotions**, and 24 publishable regions. The online profile is **1024 logical partitions / 32 physical packs**. The existing offline/mobile contract, **128 / 32**, is preserved rather than rebuilt.

Compared with the verified Sunday backend: stores `+372`, product-evidence records `+33,000`, valid unique GTINs `+2,259`, offers `+247,852`, promotions `-791,245`, provinces `0`, commerce providers `+2`, banner keys `+3`, unknown-quantity rows `+79,397`, and quarantine rows `-38`. These are diagnostics, not claims that a changed count is erroneous.

Schema drift is `NO_DECLARED_DRIFT`.

## Publication and safety

The release state advanced atomically through `DISCOVERED → STRUCTURALLY_VALIDATED → QUALIFIED → NORMALIZED → BACKEND_BUILT → INDEXED → VERIFIED → PUBLISHED → ACTIVATED`. Stage outputs are temporary/checksummed and resumable. The active pointer was written last.

Content-addressed accounting measured 898 new-release object references, one unchanged object (129 reusable bytes), 897 new objects (476,238,390 new bytes), 487,762,842 previous-release bytes, and 964,001,232 bytes retained after activation. The measured logical deduplication rate for the new release is `0.000027%`; identical bytes are reused and the previous generation remains available.

The real publication-failure injection preserved the Sunday pointer. Rollback to Sunday succeeded with an audit record, and Tuesday was then restored as active. The focused operations suite also covers corrupt objects, schema drift, stage crash/resume, pointer-last behavior, and the single-writer lock.

The full new-source run took 9,607,194.555 ms (about 160.12 minutes). Later report-only/resume work reused all seven completed stages and did not reread the source-wide national data. Observed candidate scratch usage was 2,861,662,528 bytes; peak memory was not measured.

## Consumer regressions

Bounded cross-release requests for CABA, Buenos Aires, Córdoba, and Jujuy covered arroz, leche, manteca, Coca-Cola, and Sprite. All eight requests passed with release provenance pinned, no release mixing, and availability still `UNKNOWN`. Exact Decimal M6 math passed for both generations; M7 release pinning passed for both generations; M8 input safety passed for tomate/tomte, tomato forms, aroz, sprit, coka cola, and buter. Changed outcomes remain explicit clarification/correction states rather than silent substitutions.

`AUTOMATED_OFFICIAL_ACQUISITION_PROVEN = false`<br>
`LIVE_CLOUD_DEPLOYMENT_VERIFIED = false`<br>
`ANDROID_NETWORKING_AUTHORIZED = false`<br>
`PRODUCTION_ANDROID_UI_AUTHORIZED = false`

## Verification record

- Full Python tools suite: 181 tests passed.
- Backend suite: 11 tests passed.
- Android shared-core/app tests, `lintDebug`, `assembleDebug`, offline-permission check, and single-signer APK check passed.
- Browser extension: 30 tests and Firefox `web-ext lint` passed.
- Candidate and promoted SHAs plus exact CI run IDs are recorded in the final handoff after exact-head CI; no promotion is considered complete before those checks are green.
