# Known Issues

Updated: 2026-08-21

## Crash evidence

- The latest Motorola crash is not root-caused: the repository contains no AndroidRuntime, ANR, tombstone, or bugreport evidence. Confirmed lifecycle/concurrency risks and the exact next-run capture commands are in `CRASH_ROOT_CAUSE.md`.
- The legacy overlay still owns domain results/filter/ranking presentation decisions. New immutable state/intents/contracts exist, but full migration is intentionally incomplete in Session 1.
- Android Live Accessibility/overlay/OCR behavior is experimental and is no longer the permanent product foundation.
- Session 1 source changes are not yet build-verified. Gradle distribution retrieval failed with `Network is unreachable`; browser integration/Firefox validation require missing npm dependencies. Restore the documented JDK 17/API 36/Gradle/npm toolchain, then rerun every checkpoint command before device installation.

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
