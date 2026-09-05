# Compare Here Exact Core Checkpoint

## Latest promoted consumer boundary

- Promoted code commit: `58c8732f1fe5dcb1a450fb9c0d28aa743f5dfb56` (`Add retry action for failed comparison photos`)
- Candidate workflow: **33959540807** — success
- Promoted workflow/provenance: **33959812269** — success

When a user-triggered camera/import OCR attempt fails or yields no usable price-tag suggestion, Scan & Compare now offers `Try another photo` for the same method. The retry is a bounded presentation action, hidden while another photo/review is active and cleared after a successful review or a new request; every failure/cancellation path preserves the existing editor blocks. It reuses the existing review-first OCR boundary and adds no parser, exact money/quantity, evidence, ranking, planner, offer, store, availability, persistence or network authority.

- Promoted code commit: `42f86e7dbd6a1e03a694c85032f30f21b1ab752e` (`Let saved products reopen Good Price`)
- Candidate workflow: **33957008185** — success
- Promoted workflow/provenance: **33957294707** — success

Saved product rows now have a typed `Check price` handoff into the existing Good Price Activity. It carries only a bounded display label as an untrusted prefill; exact package quantity, currency, observed price and the existing evaluator/private-memory rules remain required downstream. The saved identity is not treated as a comparison fact, current offer, store, stock or availability claim, and busy Saved lifecycle states suppress the action. This repeat-use navigation path adds no Compare Here parser/ranking/evidence authority, Android networking or provider economics.

- Promoted code commit: `37cc19cb72cb231831dfecef8fd5eb0c0e5bca2b` (`Add review gate for OCR comparison suggestions`)
- Candidate workflow: **33955298400** — success
- Promoted workflow/provenance: **33955607275** — success

Scan & Compare’s camera/photo OCR route now stops at a bounded, clearly labelled untrusted review. Raw snippets are filtered for blank/control/overlong/duplicate text and the 32-entry capacity before display; the shopper explicitly selects which suggestions to add, and only then does the existing editable draft helper insert them. Nothing is parsed, ranked, persisted or remembered by OCR itself. The existing exact parser, package/price/currency rules, like-for-like confirmation, comparison evaluator and private-memory capture remain authoritative; cancel/dismiss, stale lifecycle callbacks, unsafe snippets and capacity overflow leave existing entries unchanged.

- Promoted code commit: `ba1effcc34e0acefecedd2e4aed7dfc534324d59` (`Add review-first price photo import to Compare Here`)
- Candidate workflow: **33840519086** — success
- Promoted workflow/provenance: **33840982046** — success

Compare Here now includes a user-triggered `Import a price photo` action. The image is bounded before on-device ML Kit OCR, OCR snippets are capped at the existing 32-entry comparison limit and rejected when blank, unsafe-control or overlong, and accepted snippets fill empty editor slots before appending. The activity only inserts suggestions; the shopper reviews every entry, can edit or remove it, confirms like-for-like alternatives and then invokes the existing exact route. Images are never persisted, OCR never creates a product identity or price authority, and cancellation/error leaves existing entries unchanged.

The Home secondary entry is now labelled `Scan & compare prices` and still opens the same manual comparison route. The optional Accessibility scanner remains a separate opt-in adapter.

The prior editor-draft boundary remains recorded in the historical verification list below.

## Previous promoted consumer boundary (superseded)

- Promoted code commit: `e0c98dcd2cccd161b8ad25a762f0a846e74f6165` (`Bound Compare Here editor drafts`)
- Candidate workflow: **33629519482** — success
- Promoted workflow: **33630064672** — success

The manual Compare Here screen now exposes the exact core's existing CURRENT/MEMBER selection instead of silently using CURRENT for every comparison.

- Current shelf prices remain the safe default.
- Member prices require an explicit user selection.
- Changing the basis invalidates the prior rendered comparison while preserving the user's like-for-like confirmation.
- The selection is restored through instance state and the local draft using a bounded enum codec; missing, legacy or unknown values default to CURRENT.
- MEMBER remains strict end to end: a product without an exact member price is blocked, never ranked using its current price.
- Manual-entry instructions demonstrate the parser's exact `Current price` and optional `Member price` labels.
- Insufficient-evidence guidance names the selected basis; member mode explicitly says current prices are not substitutes.
- The primary Compare action is disabled until two entries contain text and the user explicitly confirms like-for-like substitutability.
- Action readiness is pure and bounded but does not parse facts; the exact route remains the sole authority for price, quantity, currency, promotion and ranking outcomes.
- Each physical editor block uses the evidence adapter's existing 4,096-character limit, bounding paste, lifecycle and persistence work before parsing.
- Oversized restored blocks fail closed as empty/error entries; they are never silently truncated into partial evidence.
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

Return to the Practical Shopping Home surface for the next smallest verified consumer-usability gap, while keeping the existing one-store planner/projector authoritative. Keep camera/barcode/OCR work separate until a user-controlled capture handoff can provide exact facts without giving Android Views or capture adapters ranking authority.
