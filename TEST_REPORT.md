# ValuePilot Android v101.1 verification

Updated: 2026-08-20

## Session 3 — 2026-08-21

- Pinned Android/shared-core command attempted first; Gradle 9.5 download failed with `Network is unreachable`, so compilation/tests/lint/APK did not start.
- Static shared-core forbidden-dependency scan: no forbidden production dependency found.
- Browser deterministic/local-model/golden suite: 29 passed, 0 failed.
- Full browser integration and Firefox validation remain blocked by absent locked `jsdom`/`web-ext` dependencies.
- Kotlin changes are not called compiled or build-verified.

## Session 2 — 2026-08-21

- Targeted browser deterministic/local-model/shared-golden suite: **28 passed, 0 failed**.
- Full browser suite: 28 runnable tests passed; integration loading failed because locked `jsdom` is absent.
- Firefox packaging completed; validation did not start because locked `web-ext` is absent.
- Shared-core/Android JVM tests, lint, and APK assembly did not start because pinned Gradle 9.5 was absent and retrieval failed with `Network is unreachable`.
- Static forbidden-dependency scan of `android/shared-core/src` returned no matches.
- No Session 2 Android build or APK is claimed as verified.

## Baseline

Starting commit `ceed4d4dbd83f3636c5e9af858d8c4a42e98b2e5` passed:

```bash
gradle --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The uploaded v101 APK matched the source package/version and bundled local-model asset.

## Current automated suite

The clean current run passes 36 JVM tests with 0 failures, 0 errors, and 0 skipped:

- 13 existing ValueEngine/local-model/promotion/filter regressions
- 7 price/name/relevance/quantity exactness regressions
- 6 SearchContext/query/store/page regressions
- 3 incremental-store regressions
- 5 safe item-matcher/session/off-screen regressions
- 1 multi-size 20/60/100/160/250/500 parse/store/rank fixture
- 1 before/after scanner-operation fixture

Required scenarios include `bananas` → `eggs` invalidation, `milk` excluding banana, member/previous phrases kept out of names, same-name/different-size identity, unit-rate versus total-price parsing, exact reacquisition, ambiguous/stale refusal, and range-derived Estimate labeling.

## Performance evidence

The synthetic architecture fixture reduces scanner node visits from 3,422–104,002 in the v101 shape to 162–4,002 in the v101.1 shape: 21.12×–25.99× less repeated scanner work across 20–500 products.

On the final clean JVM run, 500 synthetic cards measured:

- parse all changed cards: 221.386 ms, background
- apply to incremental store: 17.102 ms
- query filter + rank: 20.427 ms, background
- reject unchanged 500-card batch: 0.378 ms

Full tables and budgets are in `PERFORMANCE_BUDGETS.md`.

## Android build gates

The following clean command passed:

```bash
cd android
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The package was then freshly rebuilt and independently inspected:

- package: `com.valuepilot.app`
- version code/name: `10101` / `101.1.0`
- min/target SDK: 23 / 36
- APK signature: v1 and v2 verified (debug signer)
- testing signer SHA-256: `63f085bd58f2d5de01058545b07cec93569cbd01932a939b6ab601e760c84890`
- uploaded v101 signer SHA-256: `6b3069c96ef1801e3fbcfa1fd0216f3a1dd00abdd9bd37529ac9dfe162b5e4a9`; uninstall v101 before installing v101.1 because Android requires matching update signatures
- INTERNET permission: absent
- ACCESS_NETWORK_STATE permission: absent
- ZIP integrity: 987 entries verified
- final APK SHA-256: `e8463cb0d3fa1b7226d245dbcf39f7bf9e925c56f1c656739b121d76dd95e944`

The first assembled output inspected during development was found truncated despite Gradle reporting success. It was discarded. A forced fresh package run and the final clean build both produced structurally valid, signature-verifiable APKs. Artifact inspection is still required after any future source change.

## Browser and CI scope

No browser source changed. The browser suite and hosted GitHub Actions workflow were not run locally during this Android-only milestone; the available workflow will run them when an authorized push/PR occurs.

## Honest device limit

Automated tests do not prove Motorola Edge 2025 frame health or every live third-party accessibility hierarchy. Real Uber Eats/Walmart search invalidation, collection at scale, visible/off-screen row reopening, ambiguity refusal, and Perfetto/HWUI timing remain the final device gate. The next product milestone must not start before that evidence is recorded.
