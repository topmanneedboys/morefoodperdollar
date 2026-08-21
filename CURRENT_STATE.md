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

## NEXT BLOCKER

The pinned Gradle 9.5 distribution, JDK compiler, Android API 36 SDK, `jsdom`, and `web-ext` are absent from this workspace and network retrieval is blocked. Restore those exact dependencies and compile/test this checkpoint before any further core migration.

## VERIFICATION

- Targeted browser deterministic/golden suite: 29/29 passed locally.
- Shared-core forbidden-dependency scan and `git diff --check`: run at each checkpoint.
- Shared-core compilation/tests, Android JVM tests/lint/APK, browser integration, and Firefox validation: not run successfully in this workspace because required tooling is unavailable.
- Last fully built pre-extraction Android APK evidence belongs to the earlier v101.1 baseline; no newer APK is claimed.
