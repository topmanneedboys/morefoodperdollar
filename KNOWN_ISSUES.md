# Known Issues

Updated: 2026-09-06

## Current verification status

The latest promoted code tree at `a441190602a2968033c85c9d0d324294b2394499` has passed candidate
workflow **34044501200** (Android job **101517028979**, browser job **101517029114**, release-bundle
job **101517762235**) and milestone provenance workflow **34044815415** (job **101517879027**).
The old local-toolchain blockers described below are historical baseline notes, not the current
build state. The preceding promoted documentation tree at `dc9811ee92d054fe5033e7af073b313f1d145370` had passed the
candidate workflow **34042210768** (Android job **101510898036**, browser job **101510897877**,
release-bundle job **101511704903**) and milestone provenance workflow **34042551442** (job
**101511807873**). Local verification also passes 402 shared-core tests, 1,586 Android tests, 80
Python/catalog tests and 30 browser tests, plus lint, debug/release assembly, APK privacy,
single-signer, signed offline-catalog and store-directory checks. The remaining milestone issue is
physical-device validation on the Motorola Edge 2025; provider/current-offer activation remains
blocked pending explicit lawful rights.

## Crash evidence

- The latest Motorola crash is not root-caused: the repository contains no AndroidRuntime, ANR, tombstone, or bugreport evidence. Confirmed lifecycle/concurrency risks and the exact next-run capture commands are in `CRASH_ROOT_CAUSE.md`.
- The legacy overlay still owns domain results/filter/ranking presentation decisions. New immutable state/intents/contracts exist, but full migration is intentionally incomplete in Session 1.
- Android Live Accessibility/overlay/OCR behavior is experimental and is no longer the permanent product foundation.
- Historical baseline (superseded): Session 1 source changes were not yet build-verified when Gradle distribution retrieval failed with `Network is unreachable` and browser integration/Firefox validation lacked npm dependencies. The pinned JDK 17/API 36/Gradle/npm toolchain and current hosted/local gates now pass; retain this note only for incident history.
- Historical baseline (superseded): Session 2 `shared-core` Gradle wiring and Kotlin tests were once uncompiled locally. The platform-neutral source is now covered by the promoted shared-core and Android verification gates above.
- Legacy `ValueEngine`, session detection, relevance, repository, and application-state code remain in the app module because they still depend on app-local parsed models, JVM canonicalization/display formatting, `Double` calculations, or Android-backed model loading. See `PLATFORM_DEPENDENCY_MAP.md`; do not copy them wholesale into shared core.
- Session 3 removed hidden session clocks, global model calls from `ValueEngine`, and locale-formatted identity keys; those Kotlin changes are now build-verified by the current gates. Legacy promotion/budget/ranking money still uses `Double`; convert only behind golden compatibility tests.

## Milestone blockers

- Physical-device validation is pending. The automated 20–500 fixtures and operation-count evidence pass, but the reported Motorola Edge 2025 has not run this v101.1 APK. Sustained responsiveness, frame timing, memory, and real Uber Eats/Walmart navigation remain not-yet-device-verified.

## Verified platform limits

- Some apps omit product text, expose unstable accessibility paths, or use canvas/custom surfaces. ValuePilot reports missing evidence and will not coordinate-click an uncertain row.
- Exact row reopening can fail safely if the search/store changes, the app changes a price or size, the card fingerprint changes, duplicate cards are ambiguous, or the list cannot scroll far enough. A failed search can leave the underlying list at a different scroll position.
- Off-screen reacquisition searches downward and then upward, up to 90 steps per direction. Very large or nonstandard pagers may exceed that bound.
- Secure windows can reject screenshot capture. OCR then reports the limitation and leaves Accessibility results intact.
- Search-field/store/page detection is heuristic because third-party apps control their accessibility semantics. Strong text-change sessions, explicit IDs/headings, store/page fingerprints, and final query relevance provide layered protection.
- The supplied APK is debug-signed for testing, not a Play-distribution release.
- The uploaded v101 APK and this locally built v101.1 testing APK use different Android debug certificates (`6b3069…e4a9` versus `63f085…4890`). Android will reject an in-place update; uninstall the old v101 app before installing this test build. That clears ValuePilot's local settings.

## Build maintenance

- The project targets/compiles API 36. The installed lint version reports that API 37 is available; upgrade only after adding SDK 37 and completing compatibility testing.
- RecyclerView 1.2.1 is selected consistently with the current Material dependency graph. Lint reports 1.4.0 as available; upgrading requires a clean dependency build and regression run.
- Android lint passes but retains version-availability warnings noted above.

No iOS, backend, Supabase, cross-store, Universal Cart, or Basket Optimizer implementation has started.
