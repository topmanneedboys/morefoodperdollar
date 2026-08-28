# Current state

Updated: 2026-08-28

Branch: `work/valuepilot-android-milestone`

Android version: 101.1.0 (10101)

## Current milestone

5D — Authorized Real Shopping Data Provider Selection / validation.

ValuePilot is a provider-neutral shopping-intelligence platform. Accessibility, OCR, overlays, browser capture and retailer-specific extraction remain optional adapters, not the product foundation.

Permanent flow:

`authorized/open/user evidence -> provider adapters -> provenance-preserving claims/import records -> deterministic validation/normalization -> Product identity + multiple Offers -> bounded retrieval -> deterministic ranking -> immutable presentation -> replaceable UI`

Permanent rules:

- Product != Offer.
- Sources contribute separate claims; they never overwrite one shared truth row.
- Acceptance policy != factual conflict resolution.
- Stronger same-scope evidence may defeat weaker evidence; unresolved equal-strength conflict blocks Best Value.
- Money, quantity, currency and promotion arithmetic are exact/deterministic.
- AI may classify/explain but may not invent authoritative facts.
- Commission, EPC, payout, sponsorship, affiliate economics and provider preference never affect ranking.
- Technical/feed access != publisher authorization != offer geography != production-use rights.
- Dataset recency != per-offer freshness; currency != geography.
- Dataset namespace != snapshot; snapshot lifecycle != namespace disposition/deletion.
- Shared core owns no hidden clock.
- Provider credentials never belong in source control, Android, fixtures, logs or screenshots.

Primary Android navigation remains Home / Search / Basket / Saved. Compare remains a workflow, not a primary tab. Built-in Android Search remains fictional/sample evidence and must never be represented as live merchant pricing, inventory, promotion or availability.

## Verified production chain

- `5bb647a8485f257ec51b3eb0fe39b9c7caccb0a0` — current/reference price relationship.
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
- `ad5f91d4eef54e257a6660d291f14558653a1761` — bounded exact production Best Value ranking.
- `6517fad0ef21daa541d31d0d01d72a5f1980f5d5` — immutable point-in-time production Best Value presentation.
- `617dc128c9df31bbbd2b1835ac243be93e511d97` — Android/application production-search UI projector.
- `38942d556958b8abdf3b942bcbdc7bce77f1a0da` — synthetic end-to-end raw production evidence -> Best Value presentation -> Android projection regression.
- `6e0d3fc0f6c7c6a61926a8c0e85d2b2e12629022` — internal merchant/location/channel scope keys removed from UI-ready strings while retained in exact lookup.
- `1a53d34bfbb2fdf65b7383c489f75b428defe06e` — raw provider product/image URLs removed from catalog-only UI-ready state while retained in exact lookup.
- `81620dfaeb15aeab48c2e438bd225004190c0a09` — internal blocker enum diagnostics removed from consumer UI-ready blocked state while retained in exact diagnostic lookup.
- `7046b725291cc061f1e153e05c4a25539838a28e` — generation-based fail-closed production-search refresh ordering; stale/out-of-order results cannot replace newer UI state.

The GitHub workflows for the recent production boundaries above passed browser checks, shared-core/app tests, lint, APK build, JVM summary, Android privacy verification, release packaging/checksums and artifact upload.

## Production ranking / presentation boundary

Production arithmetic remains `Money`, `NormalizedQuantity`, `UnitRate`, `DeterministicValueMath`. Never convert verified production evidence back into `Double` ranking.

`ProductionBestValueRankingEvaluator` re-runs unit-value eligibility for each candidate and is hard-bounded at 128 price requests, 128 quantity candidates and 128 ranking candidates. It groups only exact `(currencyCode, RateUnit)`, lower exact unit rate wins, exact ties share dense rank, candidate ID stabilizes display order only, singleton groups cannot claim Best Value, and provider/affiliate economics are absent.

`ProductionBestValuePresentationEvaluator` re-runs ranking from raw evidence at the supplied instant. Presentation snapshots are point-in-time/non-durable and preserve exact evidence/provenance links. A later lifecycle revocation must cause re-evaluation rather than trusting an older presentation.

## Verified Android production-search boundary

`ProductionSearchUiProjector` consumes only `ProductionBestValuePresentationSnapshot`; it does not use `ValueItem`, `RankedItem`, `RankMode`, `DeterministicProductParser`, `RankingModePolicy`, `DeterministicRankingEngine` or `ValueEngine.rank()`.

UI-ready ranked rows contain exact formatted price, reference price, normalized quantity, unit rate, rank/Best Value, explicit offer country, provider/source display labels, availability and freshness. Formatting is based on exact integer representations through `BigDecimal`; tests include integers beyond IEEE-754 exact range.

UI-ready state deliberately excludes:

- `merchantKey`, `locationKey`, `commerceChannelKey`;
- raw provider `productUrl` and `imageUrl`;
- internal blocker enum/reason codes.

Those exact facts remain available only through `rankedByCandidateId` / `blockedByCandidateId` lookup maps for future authorized actions, diagnostics and provenance.

`ProductionSearchRefreshGate` is the immutable app-layer ordering boundary for future asynchronous production refreshes. Generation is caller-supplied request order, not wall-clock time. Newer generations apply; older generations are stale; identical same-generation replays are idempotent; different same-generation payloads fail closed; and a newer clear prevents a late older result from repopulating the surface.

## Android / Universal Search boundary

Current `MainActivity` still uses `UniversalSearchController` + `LocalSampleProductSearchProvider` and renders legacy `UniversalSearchRow` into the single `searchResultsContainer`.

Do **not** route production evidence through:

- `UniversalSearchController.receive()`;
- `DeterministicProductParser`;
- `ValueEngine.analyze()`;
- `RankingModePolicy`;
- `DeterministicRankingEngine`;
- `ValueEngine.rank()`.

There is still no physical production results container in `activity_shell.xml`. Do not mix verified production rows into the existing sample container. Any future production renderer must be additive and physically/state-wise separate from the sample/capture path.

## Rakuten / Jamieson

Rakuten Product Catalog technical access is enabled. Jamieson partnership, separate advertiser Product Feed approval and actual complete catalog-file access are proven. Proprietary feed data stays outside source control.

Sanitized Jamieson feed checkpoint: 273 rows; 273 unique SKUs/Product IDs; GTIN present 271/273 and all supplied GTINs checksum-valid; 273 CAD; 273 in stock; Sale<Retail 48, Sale=Retail 223, Sale>Retail 2; Class ID blank all; Attribute 1 opaque.

Generic Rakuten field semantics remain resolved: Sale Price reflects discounts and Retail Price does not. Sale>Retail is therefore a semantic conflict; never swap/repair/infer a promotion from those rows.

Latest account-specific Rakuten support evidence reconfirmed Jamieson Product Feed approval and feed availability. ValuePilot's later clarification about Android display/search, caching/indexing, retention/deletion and channel approval is still awaiting a substantive reply. Do not resend it unless the answer is incomplete.

Current public Rakuten evidence was rechecked against the Publisher Membership Agreement and Network Policies last updated July 13, 2026:

- the Publisher Membership Agreement defines a consumer-facing `Site` broadly enough to include an application and permits promotion through mobile applications, subject to advertiser engagement terms and Network Policies;
- Product Catalog is designed for database/search/comparison use and daily-updated product information, but its implementation guide still describes advertiser Product Feed approval as use on a website or blog;
- this leaves catalog-only Android display/cache/index rights ambiguous for ValuePilot and keeps those production gates fail-closed;
- the current Network Policies expressly include installable mobile applications in the DSA policy and require Rakuten approval/compliance testing before a DSA launches with Rakuten network links; participating advertisers must then approve the DSA distribution method;
- therefore the link-enabled Android profile must require `AFFILIATE_LINK_USE_AUTHORIZED`, `INSTALLED_SOFTWARE_NETWORK_APPROVED`, `ADVERTISER_DISTRIBUTION_APPROVED` and `TRACKING_PRIVACY_READY` before any Rakuten/Jamieson network links are exposed;
- on full Publisher Agreement/network termination, Rakuten requires immediate cessation/removal of Qualifying Links and other network-provided content/materials and the relevant licenses end. This gives a full-account termination baseline, but does not yet resolve the narrower advertiser-feed-approval removal/partnership-end retention case.

Product Catalog file generation/update timestamps remain source/dataset recency signals, not trustworthy per-product price observation timestamps.

Jamieson therefore remains **NOT production-authorized**. Outstanding catalog gates include account-specific Android/mobile Product Catalog display rights, cache/index scope, advertiser-feed removal retention/deletion behavior, strong Canadian offer geography, trustworthy per-offer price freshness and broad package quantity. Link-enabled Android additionally requires DSA/Network Quality, advertiser distribution approval and tracking/privacy readiness.

## Package content / GS1 / open data

Historical Jamieson x Open Food Facts raw-code 0/271 is invalid coverage evidence.

Valid normalized OFF result remains: 273 products, 271 valid GTINs, 102 matches, 169 unmatched, 12 exact supplement-count candidates, 2 structured mass/volume-only, 0 quantity conflicts, 88 matched without usable quantity. OFF is supplemental only; never infer count from Rakuten text/attributes/images or loosen the parser.

Health Canada LNHPD does not solve GTIN-level package net count.

GS1 Canada ECCnet remains the strategic package-content candidate. The inquiry was sent and acknowledged on 2026-08-28; no substantive eligibility/rights response has arrived. Do not implement ECCnet until eligibility, GTIN/net-content scope, consumer/mobile/search/cache rights, restrictions and commercial/API terms are confirmed.

## Android privacy boundary

Current Android still has no `INTERNET`, no `ACCESS_NETWORK_STATE`, no account requirement, no telemetry, no remote AI dependency and no ValuePilot server dependency. Provider research/networking remains outside Android.

## Immediate next safe engineering work

External Rakuten and GS1 clarifications remain outstanding. Do not resend them.

Next bounded offline target:

**Design the dedicated production Search surface boundary without activating it or reusing the legacy sample results container.**

Requirements:

- production state must enter only through `ProductionSearchRefreshGate` / `ProductionSearchUiProjection`;
- any renderer must consume only UI-ready `ProductionSearchUiState` for display;
- exact lookup maps remain non-rendered action/diagnostic/provenance data;
- production results must not be inserted into the current `searchResultsContainer` owned by `UniversalSearchController`;
- keep production surface disabled/empty until a provider passes rights, geography, freshness and quantity gates;
- do not expose raw URLs, internal scope keys or blocker enum names;
- no provider networking, real Rakuten/GS1 data, Android permission additions, legacy parsing/ranking, telemetry or remote AI.

Do not add checkout/payment, universal cart, subscriptions or affiliate-link behavior during this gate.
