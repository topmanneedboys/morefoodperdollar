# Current state

Updated: 2026-08-28

Branch: `work/valuepilot-android-milestone`

Android version: 101.1.0 (10101)

## Current milestone

5D — Authorized Real Shopping Data Provider Selection / validation.

ValuePilot is a provider-neutral shopping-intelligence platform. Accessibility, OCR, overlays, browser capture and retailer-specific extraction are optional adapters, not the product foundation.

Permanent flow:

`authorized/open/user evidence -> provider adapters -> provenance-preserving claims/import records -> deterministic validation/normalization -> Product identity + multiple Offers -> bounded retrieval -> deterministic ranking -> immutable presentation -> replaceable UI`

Permanent rules:

- Product != Offer.
- Sources contribute separate claims; they never overwrite one shared truth row.
- Stronger same-scope evidence may defeat weaker evidence; unresolved equal-strength conflict blocks Best Value.
- Acceptance policy and factual conflict resolution are separate decisions. A currently valid display-only fact may still contradict or defeat another fact.
- Money, quantity, currency and promotion arithmetic are exact/deterministic.
- AI may classify/explain but may not invent authoritative facts.
- Commission, EPC, payout, sponsorship, affiliate economics and provider preference never affect ranking.
- No unauthorized scraping, private-endpoint reverse engineering or anti-bot circumvention.
- Technical/feed access != publisher authorization != offer geography != production-use rights.
- Dataset recency != per-offer price freshness.
- Currency/context != offer geography.
- Dataset namespace != snapshot; snapshot lifecycle != namespace disposition/deletion.
- Production view != price claim != acceptance rankability != conflict-resolved price eligibility != unit-value eligibility != Best Value ranking != presentation authorization.
- A sale/discounted price does not automatically prove a promotion.
- Shared core owns no hidden clock.
- Provider credentials never belong in source control, Android, fixtures, logs or screenshots.

Primary Android navigation remains Home / Search / Basket / Saved. Compare remains a workflow rather than a primary tab. Built-in Android Search remains explicitly fictional/sample evidence and is never represented as live merchant pricing, inventory, promotion or availability.

## Verified shared-core production chain

- `5bb647a8485f257ec51b3eb0fe39b9c7caccb0a0` — provider-neutral current/reference price relationship validation.
- `a8e98b8ce333a612538841566972d6cab58dde88` — dataset recency separated from per-offer freshness.
- `6aed414bd5f89cf7ac6dfb739464c6f57f5abe78` — fail-closed production authorization profiles/gates.
- `7606ea941f80e3dc6b2ea362bc688c7434215195` — fail-closed offer-country validation; CAD is not Canadian geography proof.
- `f58b400533bdf9a0705fb8e88680e4b56ce9d94e` — staged production offer candidate; no rankability.
- `e546822a448e150674a2769d9899a856124b50fb` — corrected revocable exact-snapshot lifecycle.
- `230b8ae4b6f674979d349320b8e5bd83713db810` — registry-backed namespace disposition/withdrawal boundary.
- `ebb28a4a506232550b62d08370a9c8935d677603` — lifecycle/disposition-coupled point-in-time production price view from raw evidence.
- `5d317810bd6ccf0933ff6432e6e68f88fb865493` — canonical-GTIN/provider-scoped product evidence keys.
- `267cd5aa81c4c3a03110c210ea9b4bfcb203f2ab` — canonical identity integrated with exact unit-value evidence.
- `9a9b5f91948fe505a0ad6b598097bf9b8e50c680` — lifecycle-bound CURRENT_PRICE evidence-claim bridge.
- `97bfa3c353e48f15e26e9576cf06f4fa5e1687d1` — unified evidence acceptance/freshness policy.
- `a1b15cc13df4912fb94893c8952f382f7404db1d` — lifecycle-bound production current-price acceptance.
- `c3dcfab539a2d2ef40fe9ce283533141ea9cd246` — verified bounded current-price acceptance + factual-conflict eligibility. Supersedes failed test-compilation attempt `e5b56e3078c07ff226ba80b4d069da9b3abbcf22`.
- `204c8ae5e0089473f28b7cf6086b73e7a3516ec6` — verified current-price + conflict-resolved PACKAGE_QUANTITY -> existing exact unit-value policy bridge. Supersedes unverified fixture-mismatch attempt `96883bea69a78789d3edeed8465667aa2acab21b`.
- `ad5f91d4eef54e257a6660d291f14558653a1761` — verified bounded production Best Value ranking over exact unit-value candidates.
- `6517fad0ef21daa541d31d0d01d72a5f1980f5d5` — verified immutable point-in-time production Best Value presentation snapshot with auditable price/quantity evidence linkage.

The exact workflows for `204c8ae...`, `ad5f91d4...` and `6517fad0...` all passed browser checks, shared-core/app tests, lint, APK build, JVM result summary, Android privacy verification, release packaging/checksums and artifact upload.

## Current-price and unit-value boundaries

`ProductionCurrentPriceEligibilityEvaluator` evaluates at most 128 raw current-price requests at one caller-supplied instant. Every request re-runs lifecycle-bound production claim creation plus shared evidence acceptance. Currently valid CURRENT_PRICE claims enter factual conflict resolution even if their acceptance disposition is display-only, preventing acceptance filtering from hiding stronger contradictory evidence.

A candidate progresses from the current-price stage only when its request and current production claim exist, its own acceptance is rankable, no same-namespace claim-ID mutation exists, exact-scope CURRENT_PRICE resolution is not conflicted, and the resolved fingerprint is the candidate's exact current-price fingerprint.

`ProductionUnitValueEligibilityEvaluator` re-runs that raw current-price path, then filters PACKAGE_QUANTITY candidates to the exact selected stable product key. It catches same-namespace quantity claim-ID mutation, resolves package quantity through `EvidenceFactResolver`, and delegates materialized supporters to the existing `EvidenceBackedUnitValuePolicy` rather than copying authority or arithmetic rules.

`EvidenceBackedUnitValuePolicy` requires rankable price disposition, valid price/quantity domains, exact product-key equality, exact price and quantity fingerprints and sufficiently strong quantity authority. Arithmetic remains `Money` + `NormalizedQuantity` + `UnitRate` + `DeterministicValueMath`; no production `Double` value math is permitted.

The earlier unit-value fixture failure was caused only by hardcoding raw UPC-A identity. `036000291452` correctly canonicalizes to `0036000291452`; tests now derive the key through `ProductionProductEvidenceKeyResolver` so they follow production identity behavior.

## Verified production Best Value ranking

`ProductionBestValueRanking.kt`, verified at `ad5f91d4eef54e257a6660d291f14558653a1761`, is the authoritative final unit-value comparison boundary currently implemented.

Rules:

- inputs are bounded: price requests <=128, quantity candidates <=128, ranking candidates <=128;
- candidate IDs and referenced price-request IDs must be unique;
- every candidate re-runs `ProductionUnitValueEligibilityEvaluator` from raw evidence and current lifecycle/disposition state; detached `UnitRate` is never trusted as production proof;
- blocked unit-value candidates remain explainable but receive no rank;
- rankable candidates are grouped only by exact `(currencyCode, RateUnit)`;
- different currencies or rate units are never silently cross-ranked;
- lower exact `currencyMicrosPerUnit` wins within one comparable group;
- exact ties share the same dense value rank;
- stable non-economic candidate ID is used only for deterministic display ordering of exact ties;
- singleton groups are rankable/displayable but cannot claim Best Value because no meaningful comparison exists;
- groups with >=2 candidates mark every exact rank-1 tie as co-Best Value;
- provider economics/affiliate preference are absent from the API and algorithm.

Verified regressions cover lower exact rate, exact ties, unit/currency separation, singleton behavior, blocked candidates, input-order independence, duplicate IDs/references and bounds.

## Verified immutable production presentation

`ProductionBestValuePresentation.kt`, verified at `6517fad0ef21daa541d31d0d01d72a5f1980f5d5`, is the shared-core immutable production presentation boundary.

It does **not** accept a detached ranking result as authority. Each call re-runs `ProductionBestValueRankingEvaluator` from raw evidence at the caller-supplied decision instant, so lifecycle, namespace disposition, authorization and freshness are re-established immediately before projection.

Presentation rows carry exact domain values, not formatted strings or floating-point approximations:

- stable production candidate ID and product evidence key;
- product/provider/source display data;
- merchant/location/commerce-channel scope and offer country;
- exact current/reference `Money`;
- exact `NormalizedQuantity` and `UnitRate`;
- availability, current freshness and price-observation timestamp;
- exact value rank, deterministic display order and Best Value flag;
- optional product/image URLs;
- audit linkage to price provider/source/dataset/snapshot/claim, quantity dataset/claim/evidence ID, and lifecycle/disposition revisions.

Blocked candidates are projected separately with unit-value, current-price and underlying unit-value-policy blockers; they never become ranked rows.

The snapshot is explicitly point-in-time and non-durable. A later display decision must re-evaluate it rather than treating an older snapshot as authorization. A regression test mutates an active lifecycle record to a newer REVOKED revision before presentation and verifies that the candidate is blocked.

Shared-core presentation performs no locale formatting, Android work, I/O, AI inference, promotion inference, networking or hidden-clock reads.

## Android / Universal Search boundary decision

The existing Android `ValueEngine.rank()`, `RankingModePolicy`, `DeterministicProductParser`, `DeterministicRankingEngine` and current `UniversalSearchController.receive()` are the legacy/sample/capture search path, not the production-evidence path.

That path intentionally supports raw-text parsing and sample/capture behavior, but it uses `ValueItem`, `Double`, heuristic/parsed quantities, optional local-AI signals and parsed promotion arithmetic. Current Universal Search also formats through `ValueEngine.money` and constructs `UniversalSearchRow` from `RankedItem`.

Therefore production evidence must **not** be converted back into `ValueItem` or sent through `DeterministicProductParser`, `RankingModePolicy`, `ValueEngine.rank()` or legacy promotion parsing. The next Android work must be an additive pure application projector from the verified exact shared-core production presentation snapshot into immutable search UI state. It must not own authorization, parsing, ranking, matching or provider logic.

No real provider data should be wired into Android until the external production-rights/geography/freshness/package-content gates pass.

## Jamieson / Rakuten

Rakuten Product Catalog technical access is enabled. Jamieson partnership, separate Product Feed approval and actual complete catalog-file availability are proven. Proprietary feed data remains outside source control.

Sanitized audit:

- 273 product rows;
- 273/273 documented 38-field shape;
- 273/273 CAD and in-stock;
- 273 unique SKUs/Product IDs;
- GTIN present 271/273; all supplied GTINs checksum-valid;
- Sale < Retail: 48;
- Sale = Retail: 223;
- Sale > Retail: 2;
- Class ID blank all rows; Attribute 1 remains opaque/untyped.

Rakuten generic documented roles are resolved: Sale Price is discounted/current and Retail Price is non-discounted/reference. Sale > Retail is a semantic conflict and must never be swapped/repaired.

Jamieson remains **NOT production-authorized**. Unresolved gates include cache/index/search/display/mobile rights, retention/deletion obligations, installed-software/DSA approval where applicable, trustworthy per-offer price-observation freshness, Canadian offer geography beyond CAD/context and broad package quantity. Current Jamieson rows cannot pass the production path because strong Canadian geography and trustworthy per-offer observation timestamps remain absent.

Rakuten clarification was sent 2026-08-28. No substantive reply after that clarification has been found. Do not resend unless a response creates a new gap.

## Package content / open data

Historical Jamieson x OFF raw-code 0/271 is invalid coverage evidence.

Correct normalized OFF result: 273 rows, 271 valid GTINs, 102 matches, 169 unmatched, 12 exact supplement-count candidates, 2 structured mass/volume-only, 0 quantity conflicts, 88 matched without usable quantity. OFF remains supplemental only; never infer count from Rakuten text or loosen the parser.

Health Canada LNHPD does not provide the needed GTIN-level package net count.

GS1 Canada ECCnet remains the strategic package-content candidate. The Data Recipient inquiry was sent and acknowledged 2026-08-28, but no substantive eligibility/rights response has arrived. Do not implement ECCnet until GS1 confirms eligibility, GTIN/net-content scope, consumer/mobile/search/cache rights, restrictions and commercial/API terms.

## Provider/account checkpoint

Use `PROVIDER_ACCOUNT_STATUS.md` for fast-changing account state. Rakuten/Jamieson production gates remain blocked; GS1 substantive response pending; Well.ca/Bath Depot pending unless newer evidence; Tru Earth/Giant Tiger rejected; CJ applications remain as recorded; Awin active with Skip CA rejected for publisher type; impact.com Marketplace declined; Lowvyn inquiry sent.

## Android privacy boundary

Current Android still has no `INTERNET`, no `ACCESS_NETWORK_STATE`, no account requirement, no telemetry, no remote AI dependency and no ValuePilot server dependency. Provider research/networking remains outside Android.

## Immediate next safe engineering work

External Rakuten and GS1 requests are outstanding. Do not resend them.

Next bounded offline target:

**Add an Android/application-layer production-search projector that consumes only `ProductionBestValuePresentationSnapshot` and creates immutable UI-ready rows without re-parsing, re-ranking or converting the production math to `Double`.**

Requirements:

- additive path; do not destabilize existing sample/capture Universal Search;
- input is the verified shared-core production presentation snapshot only;
- no provider network access and no real Rakuten/GS1 data yet;
- formatting may convert exact integers to locale-neutral strings, but must not use `Double` for value calculations;
- preserve comparison-group semantics so multiple currencies/rate units cannot look like one global ranking;
- exact ties remain co-Best Value; singleton groups receive no Best Value badge;
- blocked candidates remain reference/explanation rows, not ranked rows;
- keep a stable candidate/evidence lookup so product-open or provenance UI can resolve the exact production row later without relying only on formatted text;
- no parsing, acceptance, conflict resolution, quantity authority, unit-value math or ranking logic in the projector;
- add no Android INTERNET/ACCESS_NETWORK_STATE permission, credentials, telemetry or remote AI.

Do not implement production Rakuten/ECCnet adapters, affiliate tracking, checkout/payment, universal cart or subscriptions yet.
