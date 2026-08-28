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
- Sources contribute claims; they never overwrite one shared truth row.
- Stronger same-scope evidence may defeat weaker evidence; unresolved equal-strength conflict blocks Best Value.
- Acceptance policy and factual conflict resolution are separate decisions.
- A display-only but currently valid factual claim may still contradict or defeat another claim.
- Money, quantity, currency and promotion arithmetic are exact/deterministic.
- AI may classify/explain but may not invent authoritative facts.
- Commission, EPC, payout, sponsorship, affiliate economics and provider preference never affect ranking.
- No unauthorized scraping, private-endpoint reverse engineering or anti-bot circumvention.
- Technical/feed access != production authorization.
- Dataset recency != per-offer price freshness.
- Currency/context != offer geography.
- Dataset namespace != dataset snapshot.
- Snapshot lifecycle != namespace-wide retention/deletion disposition.
- Production price view != price claim != acceptance rankability != conflict-resolved current-price eligibility != final Best Value eligibility.
- A discounted/current price field does not automatically prove a promotion.
- Shared core owns no hidden clock.
- Provider credentials never belong in source control, Android, fixtures, logs or screenshots.

Primary Android navigation remains Home / Search / Basket / Saved. Compare remains a workflow rather than a primary tab. Built-in Android Search remains explicitly fictional/sample evidence and is never represented as live merchant pricing, inventory, promotion or availability.

## Verified shared-core production-hardening chain

- `5bb647a8485f257ec51b3eb0fe39b9c7caccb0a0` — provider-neutral discounted/reference price relationship validation.
- `a8e98b8ce333a612538841566972d6cab58dde88` — dataset/file recency separate from per-offer freshness.
- `6aed414bd5f89cf7ac6dfb739464c6f57f5abe78` — fail-closed production authorization profiles/gates.
- `7606ea941f80e3dc6b2ea362bc688c7434215195` — fail-closed offer-country validation; CAD is not Canadian scope proof.
- `f58b400533bdf9a0705fb8e88680e4b56ce9d94e` — staged production offer candidate; no rankability.
- `e546822a448e150674a2769d9899a856124b50fb` — authoritative corrected revocable snapshot lifecycle.
- `230b8ae4b6f674979d349320b8e5bd83713db810` — authoritative registry-backed namespace disposition/withdrawal boundary.
- `ebb28a4a506232550b62d08370a9c8935d677603` — lifecycle/disposition-coupled point-in-time production price view from raw evidence.
- `5d317810bd6ccf0933ff6432e6e68f88fb865493` — canonical-GTIN/provider-scoped product evidence identity keys.
- `267cd5aa81c4c3a03110c210ea9b4bfcb203f2ab` — canonical-GTIN unit-value identity integration and same-SKU cross-provider isolation.
- `9a9b5f91948fe505a0ad6b598097bf9b8e50c680` — lifecycle-bound CURRENT_PRICE evidence-claim bridge.
- `97bfa3c353e48f15e26e9576cf06f4fa5e1687d1` — unified evidence acceptance policy with legacy non-positive timestamp -> UNKNOWN behavior preserved.
- `a1b15cc13df4912fb94893c8952f382f7404db1d` — lifecycle-bound production current-price acceptance using the unified policy.
- `c3dcfab539a2d2ef40fe9ce283533141ea9cd246` — verified bounded current-price acceptance + factual-conflict eligibility boundary. This supersedes the failed test-compilation attempt `e5b56e3078c07ff226ba80b4d069da9b3abbcf22`.

The exact `c3dcfab539a2d2ef40fe9ce283533141ea9cd246` workflow passed browser checks, shared-core/app tests, lint, APK build, JVM result summary, Android privacy-boundary verification, release packaging/checksums and artifact upload.

## Price, authorization, geography and freshness

Provider adapters declare documented current/reference roles; shared core never derives roles from provider field names. Missing, malformed, non-positive or incompatible money fails closed. A declared current/discounted value above its reference value is a semantic conflict when the adapter declares `CURRENT_MUST_NOT_EXCEED_REFERENCE`.

Production authorization is scoped to exact provider + isolated dataset namespace. The base consumer-mobile-catalog profile requires explicit satisfied gates for access, consumer display, caching, indexing, mobile-app use, retention/deletion, offer geography, price semantics, dataset-recency policy and offer-freshness policy. Network/affiliate use requires additional link/software/advertiser/tracking approvals.

Strong country bases are explicit offer country, explicit dataset country or documented dataset market. Currency-only evidence, advertiser context or inference remains unresolved. Permanent rule: **CAD != Canadian offer scope.**

A recent file does not establish a fresh offer. Dataset timestamps never substitute for `priceObservedAtEpochMillis`. Current per-offer freshness is re-evaluated with caller-supplied policy at every production decision boundary.

## Lifecycle / namespace disposition / production view

`ProductionDatasetLifecycle.kt` binds exact provider + dataset + snapshot + activation profile. Missing, suspended, revoked, retired, pre-effective or expired lifecycle state blocks use. `REVOKED` and `RETIRED` are terminal for the same snapshot/profile. Current authorization and original price freshness are re-evaluated.

Namespace disposition remains separate: `RETAINED`, `QUARANTINED`, `WITHDRAWAL_REQUIRED`, `DELETED`. Snapshot revocation never automatically deletes a namespace. Only the registry's current withdrawal-required state may invoke exact namespace removal, and in-memory removal is not proof every persisted copy is gone.

`ProductionOfferViewEvaluator` starts from raw `ProviderOfferImportRecord` and re-runs staged candidate validation, snapshot binding, lifecycle evaluation and namespace disposition. Its point-in-time view is not rankability. Reference/non-discounted price is not silently mapped to historical `Offer.previous`; no member price, promotion, quantity or unit value is invented.

## Product evidence identity

Production product-key priority:

1. valid canonical GTIN -> shared `gtin:<canonical>`;
2. otherwise provider item ID -> provider-scoped key;
3. otherwise SKU -> provider-scoped key;
4. invalid-only GTIN -> no key.

Invalid GTINs are never repaired. Names, descriptions, images and prices never become identity inputs. Equivalent leading-zero GTIN representations join. Provider-scoped IDs never silently join different providers. A product key grants no rights, freshness, quantity authority or rankability.

## Lifecycle-bound current-price claim and acceptance

`ProductionCurrentPriceClaimEvaluator` re-runs the raw production-view path. Descriptors require explicit merchant/channel scope, optional location, evidence authority and auditable authority basis. Supported current-price authorities are `MERCHANT_AUTHORITATIVE`, `PROOF_BACKED_DIRECT_OBSERVATION`, and `SOURCE_ASSERTED_METADATA`, and the authority must agree with source claim kind.

`ProductionCurrentPriceAcceptanceEvaluator` re-runs that claim path before applying the shared acceptance policy. If claim creation is blocked, acceptance does not run. If it passes, acceptance uses actual environment/channel/claim-kind/timestamp/availability facts.

`acceptanceRankable` is only the acceptance result for one evidence item. It is not final Best Value eligibility. No promotion evidence is manufactured merely because a provider field is called or documented as sale/discounted price.

## Verified current-price conflict eligibility

`ProductionCurrentPriceEligibility.kt`, verified at `c3dcfab539a2d2ef40fe9ce283533141ea9cd246`, is the current price-stage boundary.

It evaluates a bounded set of raw provider requests at one caller-supplied decision instant and, for every request, re-runs lifecycle-bound production claim creation plus unified acceptance. Only claims whose current production claim path still passes enter factual conflict resolution.

Crucially, a claim does **not** need acceptance-policy rankability to participate in factual conflict resolution. A currently valid display-only claim can still contradict or defeat another price claim. This prevents acceptance filtering from hiding stronger factual evidence.

A candidate progresses from the current-price stage only when:

1. its request exists;
2. its current production claim exists;
3. its own acceptance decision is rankable;
4. relevant CURRENT_PRICE claims are resolved through the existing `EvidenceFactResolver` at exact product/merchant/location/channel/currency scope;
5. no relevant same-namespace claim-ID mutation/collision exists;
6. the fact is resolved rather than an unresolved conflict;
7. the resolved exact value fingerprint equals the candidate's lifecycle-bound current-price fingerprint.

Verified cases include:

- one fresh/in-stock candidate progresses;
- out-of-stock candidate remains factual evidence but cannot progress;
- equal-authority same-scope disagreement remains unresolved and blocks;
- a stronger merchant-authoritative but display-only/out-of-stock contradictory price still participates and defeats the weaker candidate;
- a currently revoked competitor creates no production claim and therefore cannot block;
- different merchant scopes coexist rather than conflict;
- same namespace + same claim ID with mutated content fails closed;
- missing candidate request fails closed;
- request count is explicitly bounded at 128.

This boundary still creates no canonical production offer for ranking, no package quantity, no unit value and no final Best Value result.

## Unit-value boundary

`EvidenceBackedUnitValuePolicy` remains authoritative and must not be duplicated. It already requires:

- price disposition `RANKABLE`;
- price domain CURRENT_PRICE or OBSERVED_PRICE;
- quantity domain PACKAGE_QUANTITY;
- exact identical stable product key;
- exact money fingerprint matching the supplied price/Offer;
- exact quantity fingerprint matching the supplied normalized quantity;
- strong quantity authority.

It never chooses among conflicting claims. Conflict resolution must happen before a selected quantity claim reaches it.

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

Rakuten generic documented roles are resolved: Sale Price is discounted; Retail Price is non-discounted/reference. Sale > Retail is a semantic conflict and must never be swapped/repaired.

Jamieson remains **NOT production-authorized**. Unresolved gates include cache/index/search/display/mobile rights, retention/deletion obligations, installed-software/DSA approval where applicable, trustworthy per-offer price-observation freshness, Canadian offer geography beyond CAD/context and broad package quantity. Current Jamieson rows cannot pass the production path because strong Canadian offer scope and trustworthy per-offer price observation timestamps are absent.

Rakuten clarification was sent 2026-08-28. No substantive reply after that clarification has been found; do not resend unless a response creates a new gap.

## Package content / open data

Historical Jamieson x OFF raw-code 0/271 is invalid coverage evidence.

Correct normalized OFF result: 273 rows, 271 valid GTINs, 102 matches, 169 unmatched, 12 exact supplement-count candidates, 2 structured mass/volume-only, 0 quantity conflicts, 88 matched without usable quantity. OFF remains supplemental only; never infer count from Rakuten text or loosen the parser.

Health Canada LNHPD does not provide the needed GTIN-level package net count.

GS1 Canada ECCnet remains the strategic package-content candidate. The Data Recipient inquiry was sent 2026-08-28 and acknowledged, but no substantive eligibility/rights response has arrived. Do not implement ECCnet until GS1 confirms eligibility, GTIN/net-content scope, consumer/mobile/search/cache rights, restrictions and commercial/API terms.

## Provider/account checkpoint

Use `PROVIDER_ACCOUNT_STATUS.md` for fast-changing state. Rakuten/Jamieson production gates remain blocked; GS1 substantive response pending; Well.ca/Bath Depot pending unless newer evidence; Tru Earth/Giant Tiger rejected; CJ applications remain as recorded; Awin active with Skip CA rejected for publisher type; impact.com Marketplace declined; Lowvyn inquiry sent.

## Android privacy boundary

Current Android still has no `INTERNET`, no `ACCESS_NETWORK_STATE`, no account requirement, no telemetry, no remote AI dependency and no ValuePilot server dependency. Provider research/networking remains outside Android.

## Immediate next safe engineering work

External Rakuten and GS1 requests are outstanding. Do not resend them.

Next bounded provider-neutral/offline target:

**Bridge a conflict-resolved, acceptance-rankable production current-price candidate into the existing `EvidenceBackedUnitValuePolicy` using separately attributed, conflict-resolved package-quantity evidence.**

Requirements:

- start from/re-evaluate the verified `ProductionCurrentPriceEligibilityEvaluator`; do not trust detached price evidence;
- do not duplicate price conflict, acceptance, product-key, fingerprint or unit-value policy logic;
- package quantity must remain a separate attributed claim and must itself be selected only after appropriate exact factual conflict handling;
- require exact same stable product key before cross-source price/quantity combination;
- feed the selected price claim, exact price `Offer`, selected quantity claim, normalized quantity and rankable disposition into `EvidenceBackedUnitValuePolicy`;
- fail closed on unresolved quantity conflict, weak quantity authority, product mismatch, value mismatch or non-rankable price;
- create no Android networking and use no real provider credentials.

Do not add production Rakuten integration, affiliate tracking, checkout/payment, universal cart, subscriptions, remote AI, telemetry, unauthorized scraping or private-endpoint reverse engineering yet.
