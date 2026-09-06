# Known Issues

Updated: 2026-09-06

## Current verification status

The latest promoted Practical Shopping Home consumer slice is code SHA `7b002bf5ee193ef122ef6b30c9c61230fd2b988c` (`Retain shared image while OCR is pending`), following `078e8b6946183e74ce1de26b4223e054f3aedcf1` (`Accept shared images for local comparison`). Candidate workflow **34060944264** passed (Android job **101561238171**, browser job **101561238457**, release-bundle job **101562062418**) and recorded release artifact digest `sha256:a60cabe2618ba3db935695965bd72e006d772c70e2c071729c5e5d2c8fa4df7a`. Milestone workflow **34061301327** and provenance job **101562207724** verified the promoted milestone ref and exact candidate artifact lineage (provenance-copy artifact digest `sha256:2e22e508359ed963ff2e06996ca1ea1e769cd6d84277f8c6a7ee62ff6bde65e0`). Share-to-ValuePilot accepts bounded user-selected `content:` image URIs, hands them to the existing Compare Here photo/OCR review path with temporary read permission, and retains the URI only in transient instance state while OCR is pending. Unsupported or malformed inputs fail closed; completion, cancellation, invalidation and finish clear the handoff. No image is uploaded, scraped, persisted as evidence or used for ranking; OCR remains editable and unconfirmed, and all exact package, currency, price, promotion, evidence, private-memory and planner gates remain unchanged. Local verification passed 405 shared-core tests, 1,606 Android tests, 80 Python/catalog tests and 30 browser tests with zero failures or skips, plus lint, debug/release assembly, APK privacy, single-signer, signed offline-catalog and store-directory checks. Physical-device share/OCR, barcode/camera/accessibility/latency validation and lawful production Home activation remain open; provider/current-offer activation remains blocked pending explicit rights.

### Superseded prior verification status

The previously recorded promoted Practical Shopping Home consumer slice was code SHA `7f4126932713f6844363cf81d9f907d8089cb18f` (`Surface saved exact product context on Home`) with candidate workflow **34056970343** and milestone provenance workflow **34058400728**.

The latest promoted code tree at `32184ca0782dc7f82ac7583364edee6660b79236` has passed candidate
workflow **34047828847** (Android job **101525964170**, browser job **101525964196**, release-bundle
job **101526604524**) and milestone provenance workflow **34048136063** (job **101526778010**).
With an empty Compare Here slot or blank Good Price field, one exact offline barcode identity now
flows directly into the existing editable name field without a redundant choice dialog; exact
quantity, currency, price, promotion and evidence remain manual and unconfirmed. Multiple matches
and replacement/data-loss cases retain explicit confirmation. Local verification passed 402
shared-core tests, 1,591 Android tests, 80 Python/catalog tests and 30 browser tests, plus lint,
debug/release assembly, APK privacy, single-signer, signed offline-catalog and store-directory
checks. Physical-device barcode/camera/accessibility/latency validation and lawful production Home
activation remain open; provider/current-offer activation remains blocked pending explicit rights.
The preceding promoted Good Price tree at `7229879d9fe1df9bec6d4b2d5dd25e360c8242f2` remains in
history with candidate workflow **34046055514** and milestone provenance workflow **34046391345**.

The latest promoted code tree at `7229879d9fe1df9bec6d4b2d5dd25e360c8242f2` has passed candidate
workflow **34046055514** (Android job **101521179647**, browser job **101521179493**, release-bundle
job **101521994885**) and milestone provenance workflow **34046391345** (job **101522085801**).
When no exact personal history matches a Good Price check, its result now states that no matching
history is available and its disclosure uses only the exact entered price; it no longer promises a
save before storage success is known. The preceding promoted Scan & Compare tree at
`a441190602a2968033c85c9d0d324294b2394499` remains in history with its candidate workflow
**34044501200** and milestone provenance workflow **34044815415**.
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
