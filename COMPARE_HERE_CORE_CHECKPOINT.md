# Compare Here Exact Core Checkpoint

## Latest promoted consumer boundary

- Promoted code commit: `e88db2c722857199e841dd601705d35498ff6860` (`Expose Compare Here price basis selection`)
- Candidate workflow: **33601160548** — success
- Promoted workflow: **33601674447** — success

The manual Compare Here screen now exposes the exact core's existing CURRENT/MEMBER selection instead of silently using CURRENT for every comparison.

- Current shelf prices remain the safe default.
- Member prices require an explicit user selection.
- Changing the basis invalidates the prior rendered comparison while preserving the user's like-for-like confirmation.
- The selection is restored through instance state and the local draft using a bounded enum codec; missing, legacy or unknown values default to CURRENT.
- MEMBER remains strict end to end: a product without an exact member price is blocked, never ranked using its current price.
- The activity passes typed state to the existing route coordinator and does not own comparison arithmetic or ranking authority.
- The prior bounded product-row removal behavior and minimum two-slot editor shape remain intact.

Independent verification from a clean LF clone passed shared-core tests, all app JVM tests, Android lint, debug APK assembly, browser checks, Firefox lint, APK privacy permissions, and APK signature verification. Android still has no `INTERNET` or `ACCESS_NETWORK_STATE` permission.

## Original exact-core boundary

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

## Boundaries intentionally unchanged by the latest UI slice

The exact evaluator and permanent evidence/ranking rules above were not modified. The latest UI slice also does not modify or activate:

- barcode/camera/OCR capture;
- production Search;
- Saved/confirmation flows;
- provider networking, credentials or data feeds;
- Android `INTERNET` / `ACCESS_NETWORK_STATE` permissions;
- accounts, telemetry, remote AI or backend access.

`ComparisonActivity` now routes the explicit selected basis through `CompareHereManualRouteCoordinator`; it still does not invoke `StandaloneComparisonController`, `ValueEngine`, or legacy SMART ranking authority.

## Next safe slice

Inspect the current manual Compare Here input experience for the next smallest consumer correction or evidence-clarity gap. Reuse the existing adapter, exact evaluator, projector and route; do not duplicate them. Keep camera/barcode/OCR work separate until a user-controlled capture handoff can provide exact facts without giving Android Views or capture adapters ranking authority.
