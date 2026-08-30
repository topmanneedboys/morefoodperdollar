# Compare Here Exact Core Checkpoint

## Verified boundary

- Verified code commit: `389fb72d34d4a214407f826b8093d9beb0aabef2` (`Add exact Compare Here core evaluator`)
- Workflow: `Build ValuePilot v101 release`
- Workflow run: `163` / `33283876298`
- Job: `99183553384`
- Result: full workflow success, including browser tests/package, shared-core/app tests, Android lint/APK, JVM summary, Android privacy boundary, release/checksums, verified release upload, cleanup, and Complete job.

## What this boundary adds

`android/shared-core/src/main/kotlin/com/valuepilot/core/CompareHere.kt` is the first permanent Compare Here decision boundary.

It is platform-neutral, network-free, provider-neutral and contains no Android/UI/capture dependency.

The evaluator accepts at most 32 candidates. Each candidate carries only:

- a bounded opaque candidate id for deterministic linkage;
- an explicit opaque comparison-intent key supplied by an upstream identity/capture boundary;
- exact `Offer` money/promotion facts;
- an exact `NormalizedQuantity`, or explicit unknown quantity.

The comparison-intent key is intentionally not inferred from product names, prices, barcodes, embeddings, package units or UI text. Same currency/rate unit alone is never treated as semantic product equivalence.

## Exact decision rules

- Reuses `DeterministicValueMath.pricePerBaseUnit`; no duplicate or `Double` arithmetic.
- CURRENT and MEMBER price selection are explicit.
- MEMBER selection is strict: missing member price is blocked and never falls back to current price.
- Explicit `PromotionTerms` are honored; promotions are never inferred.
- Unknown quantity is blocked rather than estimated.
- Candidates from another semantic comparison-intent key are blocked rather than cross-ranked.
- Non-positive selected prices are blocked.
- Arithmetic overflow is blocked explicitly.
- A positive price whose fixed-precision representable unit rate is not positive is blocked separately rather than misreported as a bad sticker price.
- At least two exact candidates are required before any Best Value claim.
- Mixed currencies or mixed `RateUnit` dimensions make the single requested comparison incompatible; the evaluator does not split one consumer intent into unrelated winners.
- Lower deterministic unit rate is better.
- Exact equal represented rates co-rank.
- Candidate id is only a stable tie/display ordering key.
- Input order cannot change the result.

Affiliate commission, EPC, payout, sponsorship, provider priority and source economics are absent from the API.

## Verified regressions

Focused shared-core tests cover:

- a higher sticker price winning because its exact per-kilogram rate is lower;
- explicit promotion math;
- exact represented-rate ties;
- unknown quantity blocking while other exact candidates remain comparable;
- strict member-price behavior with no current-price fallback;
- wrong semantic group blocking;
- mixed currency incompatibility;
- mixed rate-unit incompatibility;
- simultaneous currency/rate-unit incompatibility;
- zero/negative selected price blocking;
- arithmetic overflow blocking;
- positive price whose representable rate rounds to zero blocking;
- single-candidate no-Best-Value behavior;
- input-order independence;
- duplicate candidate-id rejection;
- 32-candidate bound;
- stable/bounded/control-free comparison-intent keys.

## Boundaries intentionally unchanged

This slice does not modify or activate:

- `ComparisonActivity` or the legacy `StandaloneComparisonController`;
- any Android layout/View/route;
- barcode/camera/OCR capture;
- production Search;
- Saved/confirmation flows;
- provider networking, credentials or data feeds;
- Android `INTERNET` / `ACCESS_NETWORK_STATE` permissions;
- accounts, telemetry, remote AI or backend access.

The existing standalone comparison remains a legacy/manual-capture reference and must not become the permanent Compare Here ranking authority because it still routes through legacy `ValueItem` / SMART ranking.

## Next safe slice

Add a pure Android-application presentation projector around `CompareHereComparisonResult` before changing a View.

The projector should:

1. accept separately supplied human display labels keyed by opaque candidate id;
2. never fall back to candidate ids or technical/source identifiers;
3. format exact money/quantity/unit-rate values only, without recalculating ranking;
4. distinguish READY, not-enough-data, incompatible-dimensions and blocked-candidate states with simple consumer copy;
5. preserve typed opaque lookups/actions outside renderer strings if future capture/edit flows need them;
6. remain bounded and JVM-tested;
7. leave `ComparisonActivity` untouched until the projector itself passes full CI.
