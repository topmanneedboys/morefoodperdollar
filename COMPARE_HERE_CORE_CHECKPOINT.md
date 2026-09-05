# Compare Here Exact Core Checkpoint

## Latest promoted consumer boundary

- Promoted code commit: `06c37ae4e3c2834bb01dc73d7b85f7e6815c9cd0` (`Preserve Home refinements when shopping again`)
- Candidate workflow: **33974689463** — success
- Promoted code/provenance workflow: **33975020123** — success

Home's `Shop again` action now replays its already-completed session model through the existing deterministic reducer, preserving explicit refinements such as a selected chicken cut instead of resetting the unchanged query into a new draft. Typed request details remain reconciled against stable replayed item keys. This adjacent Home-session correction does not change Compare Here parsing, exact money/quantity, evidence, offer, store, availability, ranking, persistence, provider or Android networking authority.

- Promoted code commit: `cfd4ba6663978dcc2f73447f218b0b4df643c568` (`Prefill complete OCR comparison drafts`)
- Candidate workflow: **33973764156** — success
- Promoted code/provenance workflow: **33974081700** — success

Scan & compare photo review now offers an explicit optional `Add with detected details` action for a row whose bounded parser signals contain one concrete ISO-currency price, an exact non-derived package quantity, a usable name and a promotion shape the exact route can replay. It copies only an ordinary editable draft; multiple/ambiguous prices, unclear currency, estimated/derived quantities and unsupported promotions remain raw OCR. The dialog and status keep the `Review only`/unconfirmed boundary visible, and the existing exact quantity/currency/price, promotion and like-for-like gates remain authoritative. A parser-replayability regression keeps supported promotion terms intact, while the displayed skipped-snippet count is corrected to avoid double-counting. This focused consumer-friction slice adds no OCR, product, offer, store, availability, planner, ranking, persistence, provider or Android networking authority.

- Promoted code commit: `55b1ac9c411444b5285fdcf6dc9740f3f8848cce` (`Explain OCR signals during photo review`)
- Candidate workflow: **33972120095** — success
- Promoted code/provenance workflow: **33972485006** — success

The Scan & compare photo-review dialog now shows bounded parser signals for a possible product name, price text and package size next to each raw OCR suggestion, under an explicit `Review only` notice. Multiple prices, ambiguous currency and estimated or missing quantities remain visible as review requirements. The selected raw OCR block is still the only value appended, and the existing editable-entry, exact quantity/currency/price and like-for-like gates remain authoritative. This presentation-only friction correction adds no OCR, product, offer, store, availability, planner, ranking, persistence, provider or Android networking authority.

- Promoted code commit: `8499e8ad2b566947d6bd6af70d8ab24b47cbf5c2` (`Open Home catalog identities in Compare Here`)
- Candidate workflow: **33970594096** — success
- Promoted workflow/provenance: **33970918134** — success

Home's signed offline identity result now exposes `Use in Scan & compare` for a selected identity when a list word has no exact local catalog match. The existing bounded untrusted text handoff receives only the selected display label; Scan & compare still requires the shopper to enter and review exact package quantity, currency, observed price and like-for-like basis before comparison or private-memory capture. Invalid, oversized, control-character and stale dialog selections fail closed. This does not add Compare Here parser, product, price, package, store, stock, availability, freshness, evidence, planner, ranking, persistence, provider or Android networking authority, and the visible Home fixture remains clearly fictional/demo-only.

- Promoted code commit: `a231f0ecc962737f815dc2bfa9fe894037dd2bf9` (`Cancel stale Compare Here photo work`)
- Candidate workflow: **33969229838** — success
- Promoted workflow/provenance: **33969555817** — success

Scan & Compare now exposes an explicit `Cancel photo reading` action while bounded on-device OCR is active. Draft edits, clear actions and teardown invalidate the photo generation, reject late callbacks, clean temporary camera files and preserve the current editor entries; no stale suggestions can be added. The cancellation and invalidation gate is lifecycle/ergonomics-only and adds no parser, exact money/quantity, evidence, offer, store/availability, planner, ranking, persistence, provider or Android networking authority.

- Promoted code commit: `9318a70d5b6a9a31ba0d4377efdfebd676ccf749` (`Restore Good Price results after recreation`)
- Candidate workflow: **33967685757** — success
- Promoted workflow/provenance: **33967990673** — success

The adjacent Good Price route now restores a previously evaluated answer/share card after activity recreation without appending private memory again. The exact observation fingerprint is excluded from its own history; edits, clear actions and changed/unavailable memory clear the replay marker. This remains separate from Compare Here’s review-first photo/scanner authority and changes no parser, exact money/quantity, evidence, offer, store/availability, planner, ranking, persistence or Android networking behavior.

- Promoted code commit: `8c0f4e843b0c1cce266f17fcceda19f278b5711a` (`Preserve Good Price capture draft safely`)
- Candidate workflow: **33966599555** — success
- Promoted workflow/provenance: **33966897180** — success

The adjacent Good Price capture boundary now restores its bounded typed product draft through activity recreation, preserves a new intent prefill when no recreation state exists, prevents duplicate barcode capture launches, and reports cancellation explicitly. This remains separate from Compare Here’s review-first photo/scanner authority and changes no parser, exact money/quantity, evidence, offer, store/availability, planner, ranking, persistence or Android networking behavior.

- Promoted code commit: `9432604ca89572233e64b8268a0673ed35c6c0ce` (`Announce scanner availability accessibly`)
- Candidate workflow: **33964877138** — success
- Promoted workflow/provenance: **33965190171** — success

Compare Here’s optional scanner status now uses a polite accessibility live region, so availability changes rendered after resume are announced to assistive technology. This renderer-only boundary adds no scanner/product/price authority and does not alter the existing review-first photo route, exact comparison, private memory, planner, ranking, persistence or Android networking behavior.

- Promoted code commit: `60bd0358bc03a50c5180fea1e29a1ee3e65597cd` (`Invalidate stale private memory on resume`)
- Candidate workflow: **33964012615** — success
- Promoted workflow/provenance: **33964271194** — success

Good Price now reloads device-only private memory on resume and returns to its explicit idle projection when the memory state or load issue changes, clearing any result/share card that could retain stale personal context. Compare Here records the state behind its visible private-memory message and hides it when a paused screen detects changed or unreadable storage. These fail-closed lifecycle guards add no parser, exact money/quantity, evidence, offer, store/availability, ranking, planner, persistence or Android networking authority.

- Promoted code commit: `d981fdcd81aa8101c3006f82b8b92744f3fd12ee` (`Announce private memory outcomes accessibly`)
- Candidate workflow: **33963348099** — success
- Promoted workflow/provenance: **33963622041** — success

Good Price and Compare Here private-memory status feedback now uses polite accessibility live regions. Saved, unavailable and clear-failure outcomes are announced without changing the exact comparison route, private-memory store, evidence semantics or any ranking/planner authority.

- Promoted code commit: `d6a0b25ef3e20a7c533cb98fb083c910e734b641` (`Improve Good Price result accessibility`)
- Candidate workflow: **33962568132** — success
- Promoted workflow/provenance: **33962826152** — success

Good Price’s exact answer card and optional personal-history card now each expose a coherent accessibility summary, while decorative child labels are hidden from assistive technology to prevent repeated announcements. This renderer-only formatting reuses already-projected facts and adds no parser, exact money/quantity, evaluator, memory, evidence, ranking, planner, offer, store, availability, persistence or network authority.

- Promoted code commit: `44767d3a89c5fe79e3916c09ef91a589e42230dc` (`Invalidate Good Price result after memory clear`)
- Candidate workflow: **33961384703** — success
- Promoted workflow/provenance: **33961699432** — success

Good Price now clears its rendered result and share action after the existing device-only private history store accepts deletion. A deleted history observation can no longer remain visible in the answer card or be shared; failed deletion still stays on the explicit error path. This is a presentation/lifecycle correction and adds no parser, exact money/quantity, evidence, ranking, planner, offer, store, availability, persistence or network authority.

- Promoted code commit: `9c164ecd2566f26e9805cbcfce1356090cdf9ad6` (`Make photo retry outcomes explicit`)
- Candidate workflow: **33960585727** — success
- Promoted workflow/provenance: **33960929862** — success

Photo recovery now classifies terminal outcomes explicitly. OCR failures, no usable suggestions and recoverable picker/camera creation failures offer `Try another photo`; permission denial, cancellation and unavailable camera hardware do not offer a retry that would repeat an impossible action. The policy remains presentation-only, preserves existing draft entries and adds no parsing, exact money/quantity, evidence, ranking, planner, offer, store, availability, persistence or network authority. Candidate Linux verification passed 402 shared-core tests and 1,501 Android tests with zero failures, lint/build/privacy/single-signer/release-bundle and browser/Firefox checks; local Python/catalog verification passed 79 tests. Physical-device camera/OCR ergonomics and lawful production Home activation remain open.

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
