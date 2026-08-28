# Current state

Updated: 2026-08-28

Branch: `work/valuepilot-android-milestone`

Android version: 101.1.0 (10101)

## Current milestone

5D — Authorized Real Shopping Data Provider Selection / validation.

ValuePilot is provider-neutral shopping intelligence. Accessibility, OCR, overlays, browser capture and retailer-specific extraction are optional adapters, not the product foundation.

Permanent flow:

`authorized/open/user evidence -> provider adapters -> provenance-preserving claims/import records -> deterministic validation/normalization -> Product identity + multiple Offers -> bounded retrieval -> deterministic ranking -> immutable presentation -> replaceable UI`

Permanent rules:

- Product != Offer.
- Sources contribute separate claims; they never overwrite one shared truth row.
- Acceptance != factual conflict resolution.
- Stronger same-scope evidence may defeat weaker evidence; unresolved equal-strength conflict blocks Best Value.
- Money, quantity, currency and promotion arithmetic are exact/deterministic.
- AI may classify/explain but may not invent authoritative facts.
- Commission, EPC, payout, sponsorship, affiliate economics and provider preference never affect ranking.
- Feed/technical access != publisher authorization != offer geography != production-use rights.
- Dataset recency != per-offer freshness; currency != geography.
- Dataset namespace != snapshot; snapshot lifecycle != namespace disposition/deletion.
- Shared core owns no hidden clock.
- Provider credentials never belong in source control, Android, fixtures, logs or screenshots.

Primary Android navigation remains Home / Search / Basket / Saved. Compare remains a workflow, not a primary tab. Built-in Search remains fictional/sample evidence and must never be represented as live merchant price, inventory, promotion or availability.

## Verified production chain

Core provider/evidence boundaries:

- `5bb647a8485f257ec51b3eb0fe39b9c7caccb0a0` — provider-neutral current/reference price relationship.
- `a8e98b8ce333a612538841566972d6cab58dde88` — dataset recency separated from per-offer freshness.
- `6aed414bd5f89cf7ac6dfb739464c6f57f5abe78` — fail-closed production authorization.
- `7606ea941f80e3dc6b2ea362bc688c7434215195` — fail-closed geography; CAD is not Canada proof.
- `f58b400533bdf9a0705fb8e88680e4b56ce9d94e` — staged production offer candidate.
- `e546822a448e150674a2769d9899a856124b50fb` — exact-snapshot lifecycle.
- `230b8ae4b6f674979d349320b8e5bd83713db810` — namespace disposition/withdrawal.
- `ebb28a4a506232550b62d08370a9c8935d677603` — point-in-time raw-evidence production price view.
- `5d317810bd6ccf0933ff6432e6e68f88fb865493` — canonical-GTIN/provider-scoped product keys.
- `9a9b5f91948fe505a0ad6b598097bf9b8e50c680` — lifecycle-bound CURRENT_PRICE claims.
- `97bfa3c353e48f15e26e9576cf06f4fa5e1687d1` — unified evidence acceptance/freshness.
- `a1b15cc13df4912fb94893c8952f382f7404db1d` — lifecycle-bound current-price acceptance.
- `c3dcfab539a2d2ef40fe9ce283533141ea9cd246` — current-price factual-conflict eligibility.
- `204c8ae5e0089473f28b7cf6086b73e7a3516ec6` — production price + conflict-resolved PACKAGE_QUANTITY -> exact unit-value policy.
- `ad5f91d4eef54e257a6660d291f14558653a1761` — bounded exact production Best Value ranking.
- `6517fad0ef21daa541d31d0d01d72a5f1980f5d5` — immutable point-in-time production Best Value presentation.

Android/application presentation boundaries:

- `617dc128c9df31bbbd2b1835ac243be93e511d97` — exact Android/application production-search projector.
- `38942d556958b8abdf3b942bcbdc7bce77f1a0da` — synthetic end-to-end raw production evidence -> presentation -> Android projection regression.
- `6e0d3fc0f6c7c6a61926a8c0e85d2b2e12629022` — internal merchant/location/channel keys removed from UI-ready text.
- `1a53d34bfbb2fdf65b7383c489f75b428defe06e` — raw product/image URLs removed from catalog-only UI-ready state.
- `81620dfaeb15aeab48c2e438bd225004190c0a09` — internal blocker enums removed from consumer blocked UI state.
- `7046b725291cc061f1e153e05c4a25539838a28e` — generation-based stale/out-of-order production-refresh protection.
- `3cba4d96eb91e961772dee3c60d86371e61b1137` — independent 128-candidate UI projection ceiling, including manually constructed snapshots.
- `8520af97695fb346710649a80c6d95d422da3ee8` — narrow production Search surface host / renderer-state separation.
- `e2767b31512d8d5b6b9cd7555d452ae3ec0017e2` — inactive-by-default Android production Search renderer.
- `c4661993f9c476e3a44c7d1dc504948e50767d48` — physically separate hidden production Search view attached to `activity_shell.xml`.
- `932838f8d5eedda5d17c7a9dadf76ae38f8bcc9f` — production surface host no longer accepts a public detached presentation snapshot; each display submission re-runs `ProductionBestValuePresentationEvaluator` from raw bounded inputs against current lifecycle/disposition registries.

The exact workflows for the recent boundaries above passed browser checks, shared-core/app tests, lint, APK build, JVM summary, Android privacy verification, release packaging/checksums and artifact upload.

A temporary empty `__noop__` connector artifact was created during the hidden-layout ref update and immediately removed. Net tree comparison from verified renderer `e2767b31...` to hidden-layout `c4661993...` contains only the intended seven-line `ProductionSearchSurfaceView` layout insertion; no stray file remains.

## Production ranking / presentation / Search surface

Production arithmetic remains `Money`, `NormalizedQuantity`, `UnitRate`, `DeterministicValueMath`. Never convert verified production evidence back into legacy `Double` ranking.

`ProductionBestValueRankingEvaluator` is bounded to 128 price requests, 128 quantity candidates and 128 ranking candidates. Only exact same `(currencyCode, RateUnit)` values compare; exact ties co-rank; singleton groups never claim Best Value; provider/affiliate economics are absent.

`ProductionBestValuePresentationEvaluator` re-runs ranking from raw evidence at the supplied instant. A presentation snapshot is point-in-time and explicitly **not** a durable authorization token.

`ProductionSearchUiProjector` formats only exact UI-ready values. UI state excludes internal merchant/location/channel keys, raw provider URLs and blocker enum codes. Exact facts remain only in opaque lookup maps for future authorized actions/diagnostics/provenance. The projector independently rejects more than 128 total ranked+blocked candidates.

`ProductionSearchRefreshGate` owns no clock/I/O. Caller-supplied generations prevent stale/out-of-order state replacement; newer clears prevent late older results from repopulating the surface.

`ProductionSearchSurfaceHost` now exposes `evaluateAndApply(...)` from raw production inputs rather than a public detached-snapshot apply method. It re-runs the shared-core presentation evaluator at the caller-supplied decision instant using the current lifecycle/disposition registries, then applies generation ordering before rendering.

`ProductionSearchSurfaceView` is physically present in the Search layout but starts `GONE`, has state saving disabled, has no link/click/provider behavior and renders only `ProductionSearchUiState`. Blocked results are summarized only as a neutral reference-only count.

**MainActivity still does not wire or drive this production surface.** Current visible Search remains the fictional sample path. Do not add a provider or make the hidden surface visible until provider rights/geography/freshness/package-content gates are actually satisfied.

## Android / legacy Search separation

Current visible Search still uses `UniversalSearchController` + `LocalSampleProductSearchProvider` and legacy `UniversalSearchRow`/`ValueItem` logic.

Never route verified production evidence through:

- `UniversalSearchController.receive()`;
- `DeterministicProductParser`;
- `ValueEngine.analyze()`;
- `RankingModePolicy`;
- `DeterministicRankingEngine`;
- `ValueEngine.rank()`.

The production surface is physically and state-wise separate from legacy `searchResultsContainer`.

## Rakuten / Jamieson

Rakuten Product Catalog technical access is enabled. Jamieson partnership, separate advertiser Product Feed approval and actual complete catalog-file access are proven. Proprietary feed data stays outside source control.

Sanitized feed checkpoint: 273 rows; 273 unique SKUs/Product IDs; GTIN present 271/273 and all supplied GTINs checksum-valid; 273 CAD; 273 in stock; Sale<Retail 48, Sale=Retail 223, Sale>Retail 2; Class ID blank all; Attribute 1 opaque.

Generic Rakuten semantics remain resolved: Sale Price reflects discounts and Retail Price does not. Sale>Retail is a semantic conflict; never swap/repair/infer promotion from those rows.

Latest Rakuten support message only reconfirmed Jamieson Product Feed approval/access. ValuePilot's later questions about Android display/search, cache/index, retention/deletion and applicable Mobile App/DSA approval remain unanswered. Do not resend unless the response is incomplete/new evidence creates another question.

Current public Rakuten agreement/policies (last updated July 13, 2026) establish that mobile applications may be publisher Sites subject to advertiser terms/policies; installable mobile apps are within DSA controls for Rakuten network links; DSA/network-link use requires Rakuten approval/compliance testing and then participating-advertiser approval. Full network termination requires cessation/removal of network-provided links/content/materials. These public rules still do not settle ValuePilot's account-specific catalog-only Android display/cache/index rights or narrower advertiser-feed-revocation retention obligations.

Product Catalog file/update timestamps remain dataset/source recency, not trustworthy per-product price observation timestamps.

Jamieson remains **NOT production-authorized**. Outstanding catalog gates: written Android/mobile Product Catalog display rights, cache/index scope, advertiser-feed removal/retention obligations, strong Canadian offer geography, trustworthy per-offer price freshness and broad package quantity. Link-enabled Android additionally requires DSA/Network Quality, advertiser distribution approval and tracking/privacy readiness.

## Package quantity / GS1 / open data

Historical Jamieson x Open Food Facts raw-code 0/271 is invalid coverage evidence.

Valid normalized OFF result remains: 273 products; 271 valid GTINs; 102 matches; 169 unmatched; 12 exact supplement-count candidates; 2 structured mass/volume-only; 0 quantity conflicts; 88 matched without usable quantity. OFF is supplemental only; never infer package count from Rakuten title/description/opaque attributes/images.

Health Canada LNHPD does not solve GTIN-level package net count.

GS1 Canada ECCnet remains the strategic package-content candidate. Inquiry sent and acknowledged 2026-08-28; no substantive eligibility/rights response has arrived. Do not implement ECCnet until eligibility, GTIN/net-content scope, consumer/mobile/search/cache rights, restrictions and commercial/API terms are confirmed.

## Android privacy boundary

Current Android still has no `INTERNET`, no `ACCESS_NETWORK_STATE`, no account requirement, no telemetry, no remote AI dependency and no ValuePilot server dependency. Provider research/networking remains outside Android.

## Immediate next work

The bounded offline production-search architecture is sufficiently hardened for the current provider-validation milestone. **Do not keep adding speculative Android production plumbing merely to stay busy.**

External provider evidence now determines the next major implementation:

1. Await Rakuten's written account-specific reply. Map every answer independently to display/cache/index/mobile/retention/DSA/link/tracking gates. Feed approval alone still cannot activate production.
2. Await GS1 Canada's substantive ECCnet response. Validate Data Recipient eligibility, GTIN/net-content fields, Jamieson publication scope, consumer comparison/mobile/cache/search rights, retention/attribution restrictions, API/extract options and commercial terms.
3. If either response arrives, update repository status/gate documents first, then implement only the provider-specific adapter/work unlocked by that evidence.
4. Until then, keep the production Search surface hidden and unwired. No real Rakuten/GS1 data, Android networking, permission additions, affiliate links, checkout/payment, telemetry or remote AI.

No user/device action is currently required for this engineering checkpoint.
