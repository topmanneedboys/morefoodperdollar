# Current state

Updated: 2026-08-28

Branch: `work/valuepilot-android-milestone`

Android version: 101.1.0 (10101)

## Current milestone

5D — Authorized Real Shopping Data Provider Selection / validation.

ValuePilot remains a provider-neutral shopping-intelligence platform. Accessibility, OCR, overlays, browser capture and retailer-specific extraction are optional adapters, not the product foundation.

Permanent flow:

`authorized/open/user evidence -> provider adapters -> provenance-preserving claims/import records -> deterministic validation/normalization -> Product identity + multiple Offers -> bounded retrieval -> deterministic ranking -> immutable presentation -> replaceable UI`

Permanent rules:

- Product != Offer.
- Sources contribute separate claims; they never overwrite one shared truth row.
- Stronger same-scope evidence may defeat weaker evidence; unresolved equal-strength conflict blocks Best Value.
- Acceptance policy and factual conflict resolution are separate decisions; currently valid display-only facts may still contradict or defeat another fact.
- Money, quantity, currency and promotion arithmetic are exact/deterministic.
- AI may classify/explain but may not invent authoritative facts.
- Commission, EPC, payout, sponsorship, affiliate economics and provider preference never affect ranking.
- No unauthorized scraping, private-endpoint reverse engineering or anti-bot circumvention.
- Technical/feed access != publisher authorization != offer geography != production-use rights.
- Dataset recency != per-offer price freshness. Currency/context != offer geography.
- Dataset namespace != snapshot; snapshot lifecycle != namespace disposition/deletion.
- Production view != price claim != acceptance rankability != conflict-resolved price eligibility != unit-value eligibility != Best Value ranking != presentation authorization.
- Shared core owns no hidden clock.
- Provider credentials never belong in source control, Android, fixtures, logs or screenshots.

Primary Android navigation remains Home / Search / Basket / Saved. Compare remains a workflow, not a primary tab. Built-in Android Search remains fictional/sample evidence and must never be represented as live merchant pricing, inventory, promotion or availability.

## Verified production chain

- `5bb647a8485f257ec51b3eb0fe39b9c7caccb0a0` — provider-neutral current/reference price relationship.
- `a8e98b8ce333a612538841566972d6cab58dde88` — dataset recency separated from per-offer freshness.
- `6aed414bd5f89cf7ac6dfb739464c6f57f5abe78` — fail-closed production authorization.
- `7606ea941f80e3dc6b2ea362bc688c7434215195` — fail-closed geography; CAD is not Canada proof.
- `f58b400533bdf9a0705fb8e88680e4b56ce9d94e` — staged production offer candidate.
- `e546822a448e150674a2769d9899a856124b50fb` — exact-snapshot lifecycle.
- `230b8ae4b6f674979d349320b8e5bd83713db810` — namespace disposition/withdrawal.
- `ebb28a4a506232550b62d08370a9c8935d677603` — point-in-time raw-evidence production price view.
- `5d317810bd6ccf0933ff6432e6e68f88fb865493` — canonical-GTIN/provider-scoped production product keys.
- `267cd5aa81c4c3a03110c210ea9b4bfcb203f2ab` — exact product identity/unit-value integration.
- `9a9b5f91948fe505a0ad6b598097bf9b8e50c680` — lifecycle-bound CURRENT_PRICE claims.
- `97bfa3c353e48f15e26e9576cf06f4fa5e1687d1` — unified evidence acceptance/freshness.
- `a1b15cc13df4912fb94893c8952f382f7404db1d` — lifecycle-bound current-price acceptance.
- `c3dcfab539a2d2ef40fe9ce283533141ea9cd246` — verified current-price factual-conflict eligibility.
- `204c8ae5e0089473f28b7cf6086b73e7a3516ec6` — verified production price + conflict-resolved package quantity -> exact unit-value policy.
- `ad5f91d4eef54e257a6660d291f14558653a1761` — verified bounded production Best Value ranking.
- `6517fad0ef21daa541d31d0d01d72a5f1980f5d5` — verified immutable point-in-time production Best Value presentation.
- `617dc128c9df31bbbd2b1835ac243be93e511d97` — verified Android/application production-search UI projector over exact presentation snapshots.
- `38942d556958b8abdf3b942bcbdc7bce77f1a0da` — verified synthetic end-to-end raw production evidence -> Best Value presentation -> Android production-search projection regression.

The exact workflows for `204c8ae...`, `ad5f91d4...`, `6517fad0...`, `617dc128...` and `38942d55...` passed browser checks, shared-core/app tests, lint, APK build, JVM summary, Android privacy verification, release packaging/checksums and artifact upload.

## Current-price, unit-value and Best Value boundaries

`ProductionCurrentPriceEligibilityEvaluator` evaluates a bounded raw request set at one supplied instant. Every request re-runs production claim creation and shared acceptance; currently valid display-only CURRENT_PRICE facts remain in factual conflict resolution so stronger contradictory evidence cannot be hidden by acceptance filtering.

`ProductionUnitValueEligibilityEvaluator` then re-runs that price path, filters PACKAGE_QUANTITY evidence to the exact selected product key, detects same-namespace claim-ID mutation, resolves quantity through `EvidenceFactResolver`, and delegates materialized supporters to `EvidenceBackedUnitValuePolicy`. It does not copy authority or arithmetic rules.

Exact arithmetic remains `Money`, `NormalizedQuantity`, `UnitRate`, `DeterministicValueMath`. Never convert verified production evidence back into `Double` ranking.

`ProductionBestValueRankingEvaluator`, verified at `ad5f91d4...`, re-runs unit-value eligibility for each candidate. It groups only exact `(currencyCode, RateUnit)`, lower exact `currencyMicrosPerUnit` wins, exact ties share dense rank, candidate ID stabilizes display order only, singleton groups cannot claim Best Value, and provider/affiliate economics are absent from the API.

## Immutable production presentation

`ProductionBestValuePresentationEvaluator`, verified at `6517fad0...`, re-runs ranking from raw evidence at each supplied instant rather than accepting detached ranking authority. Rows preserve exact Money/quantity/rate plus product identity, provider/source, exact merchant/location/channel scope, offer country, availability/freshness/timestamp, rank/order/Best Value state, URLs and auditable price/quantity evidence links. Blocked candidates remain separate explanation objects.

The presentation snapshot is point-in-time/non-durable. A regression mutating lifecycle to newer REVOKED before presentation proves stale ranking/presentation is not trusted.

## Verified Android production-search projector

`ProductionSearchUiProjector.kt`, verified at `617dc128c9df31bbbd2b1835ac243be93e511d97`, is an additive application-layer projector for the production path.

It deliberately does not use `ValueItem`, `RankedItem`, `RankMode`, `DeterministicProductParser`, `RankingModePolicy`, `DeterministicRankingEngine` or `ValueEngine.rank()`. Its only authoritative input is `ProductionBestValuePresentationSnapshot`.

It preserves comparison groups, co-Best-Value ties, singleton no-Best behavior and blocked/reference-only candidates. Money, normalized quantities and unit rates are formatted directly from exact integer representations using `BigDecimal`; no production value calculation or `Double` conversion occurs. Tests include values beyond IEEE-754 exact integer range to catch accidental floating-point conversion.

The projection retains opaque exact candidate lookup maps so later product/provenance actions can resolve the original production presentation item instead of reconstructing facts from formatted text.

**Consumer-display hardening still required:** the current projector forms `merchantSummary` from stable `merchantKey`, optional `locationKey` and `commerceChannelKey`. Those are factual/audit scope keys, not guaranteed consumer display names. Do not render them as merchant/location names. Before any production UI uses these rows, remove internal scope keys from consumer-facing strings unless explicit display metadata exists; keep the exact keys only in the opaque production lookup.

## Verified end-to-end synthetic production-search handoff

`ProductionSearchPipelineIntegrationTest.kt`, verified at `38942d556958b8abdf3b942bcbdc7bce77f1a0da`, exercises the full permanent chain with synthetic data only:

`raw ProviderOfferImportRecord -> production authorization/lifecycle/geography/freshness -> CURRENT_PRICE conflict/acceptance -> PACKAGE_QUANTITY conflict/authority -> exact unit value -> Best Value ranking/presentation -> ProductionSearchUiProjector`

The regression proves:

- two CAD/item products rank exactly at `0.08 CAD/item` and `0.09 CAD/item`;
- a CAD/kg product stays in a separate singleton comparison group and receives no Best Value badge;
- an out-of-stock candidate remains blocked/reference-only and never receives a rank;
- exact presentation/evidence objects remain resolvable through candidate lookup;
- no real provider data, Android networking, raw-text parsing or legacy `ValueEngine` ranking participates.

## Android / Universal Search boundary

Current `MainActivity` still uses `UniversalSearchController` + `LocalSampleProductSearchProvider` and renders legacy `UniversalSearchRow` objects into one `searchResultsContainer`.

That existing path parses raw `ShoppingEvidence` into `ValueItem`, may detect parsed promotion arithmetic, uses legacy `RankingEngine`/`ValueEngine`, and formats through `ValueEngine.money`. It remains valid only for current sample/capture responsibilities.

Do **not** route production evidence through `UniversalSearchController.receive()`, `DeterministicProductParser`, `ValueEngine.analyze()`, `RankingModePolicy`, `DeterministicRankingEngine` or `ValueEngine.rank()`.

There is currently only one Search results container, owned by the legacy sample path. Do not mix verified production rows into that container yet; an additive production surface/state boundary is safer than blurring sample and production pipelines.

## Rakuten / Jamieson

Rakuten Product Catalog technical access is enabled. Jamieson partnership, separate Product Feed approval and actual complete catalog-file access are proven. Proprietary feed data stays outside source control.

Sanitized feed checkpoint: 273 rows; all documented 38-field shape/CAD/in-stock; 273 unique SKU/Product IDs; GTIN present 271/273 and all supplied GTINs checksum-valid; Sale<Retail 48, Sale=Retail 223, Sale>Retail 2; Class ID blank all; Attribute 1 opaque.

Generic Rakuten semantics remain: Sale Price is discounted/current and Retail Price is non-discounted/reference. Sale>Retail is a semantic conflict and must never be swapped/repaired.

Latest incoming Rakuten support message reconfirmed that Jamieson Product Feed approval is active and the feed is present in the Product Catalog account. The later ValuePilot clarification asking about Android display/search, caching/indexing, retention/deletion and Mobile App/DSA approval is still awaiting a substantive reply. Do not resend it.

Current official Rakuten guidance supports Product Catalog/database/product-comparison use generally and recognizes Mobile App as a publisher channel. Mobile App and Downloadable Software Application details are separate in channel settings. DSA software, when applicable, requires Rakuten Network Quality approval followed by participating advertiser approval. These public documents do **not** prove that ValuePilot's Android behavior is a DSA, nor do they settle app-specific Product Catalog display/cache/retention rights. Keep those gates fail-closed until Rakuten answers.

Public Product Catalog file/update timestamps are dataset/source recency signals, not trustworthy per-product price observation timestamps. The permanent dataset-recency != offer-freshness rule remains.

Jamieson therefore remains **NOT production-authorized**. Unresolved gates include Android/mobile display rights, caching/indexing/retention/deletion obligations, applicable Mobile App/DSA approval path, strong Canadian offer geography, trustworthy per-offer price freshness and broad package quantity.

## Package content / GS1 / open data

Historical Jamieson x OFF raw-code 0/271 is invalid coverage evidence.

Valid normalized OFF result remains: 273 products, 271 valid GTINs, 102 matches, 169 unmatched, 12 exact supplement-count candidates, 2 structured mass/volume-only, 0 quantity conflicts, 88 matched without usable quantity. OFF is supplemental only; never infer count from Rakuten text/attributes/images or loosen the parser.

Health Canada LNHPD does not solve GTIN-level package net count.

GS1 Canada ECCnet remains the strategic package-content candidate. The inquiry was sent and acknowledged on 2026-08-28; no substantive eligibility/rights response has arrived. Do not implement ECCnet until eligibility, GTIN/net-content scope, consumer/mobile/search/cache rights, restrictions and commercial/API terms are confirmed.

## Android privacy boundary

Current Android still has no `INTERNET`, no `ACCESS_NETWORK_STATE`, no account requirement, no telemetry, no remote AI dependency and no ValuePilot server dependency. Provider research/networking remains outside Android.

## Immediate next safe engineering work

External Rakuten and GS1 clarifications are outstanding. Do not resend them.

Next bounded offline target:

**Harden `ProductionSearchUiProjector` so internal merchant/location scope keys can never leak into consumer-facing UI-ready text.**

Requirements:

- keep `merchantKey`, `locationKey` and `commerceChannelKey` available only through the exact production presentation lookup unless explicit consumer display metadata is supplied;
- replace/remove current `merchantSummary` consumer string rather than pretending stable keys are merchant/location names;
- do not infer display names from IDs, URLs, provider names or source names;
- preserve exact comparison-group semantics, exact formatting, blocked state and lookup maps;
- add regression tests asserting internal scope keys do not occur in UI-ready strings while remaining intact in the exact lookup object;
- no provider networking, real Rakuten/GS1 data, Android permissions, legacy parsing/ranking, telemetry or remote AI.

After that hardening is verified, the next UI work should still remain additive. Do not wire real providers or merge production rows into the existing sample Search container until rights/geography/freshness/package-content gates pass.
