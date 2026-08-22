# Current state

Updated: 2026-08-21

Branch: `work/valuepilot-android-milestone`

Android version: `101.1.0` (`10101`)

## PERMANENT CORE

- `android/shared-core` contains exact `Money`, normalized quantities/units, offers/promotions, observations, opaque IDs, stable identity evidence, product equivalence, semantic-enrichment contracts, and deterministic value math.
- Shared core has no Android, UI, capture, OCR, retailer, lifecycle, filesystem, network, JSON, model-loading, or hidden-clock dependency.
- `ValueEngine` exact parsing works with `NoSemanticEnricher`; Android injects `LocalModelSemanticEnricher` explicitly when optional estimates are wanted.
- Search-session creation uses supplied observation time; repository/dedupe identity uses scaled numeric fields and invariant canonical text rather than display-locale formatting.
- Product equivalence is core evidence; Android node paths/bounds/view IDs/card fingerprints remain navigation evidence.
- Cross-client expectations live in `shared-fixtures/valuepilot-golden-v1.json` schema v2.

## EXPERIMENTAL

Accessibility live capture, overlay/bubble, OCR, screenshot capture, and Android card reacquisition remain optional legacy adapters/test harnesses. They are not the product foundation and are not device-verified on the reported Motorola Edge 2025.

## VERIFICATION (GREEN)

- Build ValuePilot v101 release: SUCCESS
- Browser: 30 tests passed, Firefox packaging/lint passed
- Android shared-core: 10 tests, 0 failures
- Android app JVM tests: 47 tests, 0 failures
- Android: lintDebug passed, assembleDebug passed, APK produced
- Privacy boundary: Verified no INTERNET or ACCESS_NETWORK_STATE
- Release: Chromium, Firefox, and Android packages produced

## NEXT MILESTONE

FIRST PERMANENT STANDALONE ANDROID VALUEPILOT APPLICATION

This app must operate independently of:
- Accessibility
- overlay/bubble
- OCR
- Uber
- any particular retailer

Initial standalone direction:
normal Android application
    ↓
manual / fixture-backed product input
    ↓
existing deterministic parsing/normalization
    ↓
comparison / ranking
    ↓
clear best-value results
