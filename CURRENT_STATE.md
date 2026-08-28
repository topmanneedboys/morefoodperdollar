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
- Money, quantity, currency and promotion arithmetic are exact/deterministic.
- AI may classify/explain but may not invent authoritative facts.
- Commission, EPC, payout, sponsorship, affiliate economics and provider preference never affect ranking.
- No unauthorized scraping or private-endpoint reverse engineering.
- Technical/feed access != production authorization.
- Dataset recency != per-offer price freshness.
- Currency/context != offer geography.
- Dataset namespace != dataset snapshot.
- Snapshot lifecycle != namespace-wide retention/deletion disposition.
- Production-available price view != rankable evidence.
- Shared core owns no hidden clock.
- Provider credentials never belong in source control, Android, fixtures, logs or screenshots.

Primary Android navigation remains Home / Search / Basket / Saved. Compare remains a workflow rather than a primary tab. Built-in Android Search remains explicitly fictional/sample evidence and is never represented as live merchant pricing, inventory, promotion or availability.

## Verified shared-core production hardening

The deterministic foundation now includes:

- typed `ShoppingEvidence` and evidence acceptance/freshness policy;
- checksum-valid and canonical GTIN identity handling;
- source-isolated dataset namespaces and conflict resolution;
- evidence-backed unit-value gating;
- provider-neutral staged offer import;
- provider-neutral discounted/reference price relationship validation;
- dataset/file recency classification separate from offer freshness;
- fail-closed provider production authorization profiles;
- fail-closed offer-country validation;
- staged production offer candidate boundary;
- exact dataset-snapshot binding;
- revocable/suspendable/expiring snapshot lifecycle;
- separate namespace-wide retain/quarantine/withdraw/delete disposition;
- registry-backed physical namespace withdrawal boundary;
- lifecycle/disposition-coupled point-in-time production price view;
- canonical-GTIN/provider-scoped product evidence key contract.

### Price relationship boundary

Commit `5bb647a8485f257ec51b3eb0fe39b9c7caccb0a0` added provider-neutral discounted/reference price-pair assessment.

The shared core never derives field roles from provider field names. Discounted > reference becomes a semantic conflict. Missing/malformed/non-positive/incompatible values fail closed. The relationship assessment never chooses a current price, creates an `Offer`, grants rankability or proves rights/freshness/geography.

### Dataset recency boundary

Commit `a8e98b8ce333a612538841566972d6cab58dde88` added provider dataset/file recency classification.

Permanent rule: **a recent dataset does not mean a fresh offer.** Dataset timestamps never populate or substitute for `priceObservedAtEpochMillis`.

### Production authorization boundary

Commit `6aed414bd5f89cf7ac6dfb739464c6f57f5abe78` added fail-closed production authorization per provider + isolated dataset namespace.

The base consumer mobile catalog profile requires explicit `SATISFIED` gates for data access, consumer display, caching, indexing, mobile-app use, retention/deletion policy, offer geography, price semantics, dataset-recency policy and offer-freshness policy.

Missing, pending, denied, unknown, or incorrectly-not-required required gates block production. A stronger network/affiliate profile additionally requires affiliate-link use, installed-software/network approval, advertiser distribution approval and tracking/privacy readiness.

Feed access alone can never authorize production.

### Offer-country boundary

Commit `7606ea941f80e3dc6b2ea362bc688c7434215195` added provider dataset offer-country validation.

Strong country bases are limited to explicit offer country, explicit dataset country, or documented dataset market. Currency-only evidence, advertiser context, inference and unknown basis remain unresolved.

Permanent rule: **CAD != Canadian offer scope.**

### Staged production offer candidate

Commit `f58b400533bdf9a0705fb8e88680e4b56ce9d94e` added `StagedProductionOfferCandidate`.

A provider row can reach this boundary only when authorization/geography scope matches, effective authorization is fully satisfied, the row is real-world non-fixture/non-inferred evidence, validated source identity exists, adapter-declared current/reference price roles are exact/positive/compatible, declared price relationship rules pass, a real per-offer observation timestamp exists, and current caller-supplied freshness is `FRESH` or `AGING`.

This object is still not a canonical `Offer`, has no rankability flag and cannot bypass quantity, promotion, conflict or unit-value evidence gates.

### Revocable production dataset lifecycle

Commits:

- `8ad4bb29a3e61d8931cedb3abe48c87ea2ba074a` — initial lifecycle implementation;
- `e546822a448e150674a2769d9899a856124b50fb` — authoritative corrected lifecycle with weak-profile bypass closed.

`ProductionDatasetLifecycle.kt` provides exact provider + dataset + snapshot identity, snapshot binding, profile-scoped `ACTIVE` / `SUSPENDED` / `REVOKED` / `RETIRED` records, monotonic revisions, effective/expiry windows and point-in-time activation.

Permanent lifecycle rules:

- a staged candidate is not active merely because it once passed validation;
- no lifecycle record means inactive;
- suspension, revocation, retirement, pre-effective state and expiry all block activation;
- `REVOKED` and `RETIRED` are terminal for the same snapshot/profile key;
- stale lifecycle revisions and same-revision mutations are rejected;
- current production authorization is re-evaluated on every activation check;
- activation re-checks the original per-offer price observation against current freshness policy;
- a weaker custom profile cannot bypass the base mobile-catalog gates even if it reuses the same profile id;
- one dataset lifecycle cannot disable another dataset;
- lifecycle activation creates no canonical `Offer` and grants no durable rankability/display permission.

The exact corrected lifecycle commit passed browser checks, shared-core/app tests, lint, APK build, JVM summary, Android privacy verification, release packaging/checksums and artifact upload.

### Namespace-wide disposition and explicit withdrawal

Commits:

- `948f72305d370d65e1f327d8666141a958c6f363` — initial namespace disposition/withdrawal layer;
- `230b8ae4b6f674979d349320b8e5bd83713db810` — authoritative registry-backed withdrawal boundary.

`ProductionDatasetDisposition.kt` separates namespace-wide storage/use state from snapshot/profile lifecycle:

- `RETAINED` — namespace policy alone does not block use;
- `QUARANTINED` — keep for audit/review but block production use globally;
- `WITHDRAWAL_REQUIRED` — block use and require explicit physical removal;
- `DELETED` — full physical removal has been separately confirmed by the caller.

Permanent withdrawal rules:

- profile/snapshot revocation never automatically means namespace deletion;
- `RETAINED <-> QUARANTINED` is reversible;
- `RETAINED`/`QUARANTINED -> WITHDRAWAL_REQUIRED -> DELETED` is the deletion path;
- `DELETED` is terminal for the same namespace identity;
- the use evaluator reads the disposition registry rather than trusting a free-standing record;
- the withdrawal executor also reads only the registry's current state;
- only current `WITHDRAWAL_REQUIRED` can invoke `SourceIsolatedEvidenceIndex.removeNamespace()`;
- exact namespace metadata must match before anything is removed;
- removing zero claims may still mean an empty registered namespace was removed;
- absence from the in-memory index is not proof that every persistent copy is deleted;
- successful in-memory removal does not itself mutate the disposition to `DELETED`.

The exact authoritative withdrawal commit `230b8ae4b6f674979d349320b8e5bd83713db810` passed the full ValuePilot workflow including privacy verification and release artifact upload.

### Lifecycle-bound production price view

Commit `ebb28a4a506232550b62d08370a9c8935d677603` added `ProductionOfferView.kt`.

This is deliberately stronger than accepting a previously staged/bound object. `ProductionOfferViewEvaluator` starts again from the raw `ProviderOfferImportRecord` and re-runs:

1. staged-candidate evaluation including price roles, source identity, authorization, geography and current freshness;
2. exact snapshot binding;
3. current lifecycle record lookup from `ProductionDatasetLifecycleRegistry`;
4. current lifecycle/authorization/freshness evaluation;
5. current namespace-wide use disposition from `ProductionDatasetDispositionRegistry`.

Only when all five layers pass can shared core create a point-in-time `LifecycleBoundProductionOfferView`.

Permanent view rules:

- staged/bound intermediates are not accepted as authority by this production boundary;
- missing lifecycle, wrong snapshot, revoked/suspended/expired lifecycle, stale price, denied current authorization, missing namespace disposition, quarantine, withdrawal-required or deleted state all fail closed;
- the view records the evaluation instant plus lifecycle/disposition revisions used;
- the view is not a durable authorization token and must be re-evaluated before a later production decision;
- the view exposes no rankability flag;
- its shared-core-only arithmetic `Offer` contains only the validated current price;
- provider reference/non-discounted price remains separate and is **not** silently reclassified as historical `Offer.previous`;
- no member price, promotion, quantity or unit value is invented.

The exact commit `ebb28a4a506232550b62d08370a9c8935d677603` passed browser checks, shared-core/app tests, lint, APK build, JVM summary, Android privacy verification, release packaging/checksums and artifact upload.

### Product evidence identity keys

Commit `5d317810bd6ccf0933ff6432e6e68f88fb865493` added `ProductionProductEvidenceKey.kt`.

Deterministic identity priority:

1. checksum-valid canonical GTIN -> shared `gtin:<canonical>` representation;
2. otherwise provider item id -> provider-scoped key;
3. otherwise SKU -> provider-scoped key;
4. invalid-only GTIN -> no key.

Permanent identity rules:

- invalid GTIN is never repaired;
- names, descriptions, images and prices are never product identity inputs;
- equivalent leading-zero GTIN representations resolve to the same canonical key;
- provider item ID/SKU keys include provider identity and cannot silently join another provider;
- provider-scoped key components are length-prefixed to avoid delimiter collisions;
- a canonical GTIN key is only an identity representation: it does not by itself prove allocation/product ownership, rights, freshness, quantity authority or rankability.

The exact code commit passed the full ValuePilot workflow. Integration tests on the current branch additionally prove that equivalent canonical GTIN keys can satisfy the existing exact-product-key unit-value join, while the same SKU text from different providers is rejected as a product-identity mismatch.

## Canonical Offer / unit-value boundary

`EvidenceBackedUnitValuePolicy` remains the authoritative cross-source unit-value gate. It requires rankable price evidence, compatible price domain, package-quantity domain, identical stable product key, exact money/quantity fingerprints and strong quantity authority.

Do not duplicate this logic.

The lifecycle-bound production view deliberately does not grant rankability. A later production price-evidence bridge must preserve merchant/channel scope and explicit evidence authority before the existing acceptance/conflict/unit-value engine is allowed to rank it.

Do not infer a cross-source join from product name, description, image, price, SKU, or provider item id.

## GTIN identity

Provider staging preserves:

- `suppliedGtin`: exact provider representation;
- `validatedGtin`: checksum-valid exact source representation;
- `canonicalGtin`: deterministic cross-source representation.

Canonicalization handles documented leading-zero equivalent representations and never repairs invalid GTINs.

## Jamieson / Rakuten — first actual merchant feed

Rakuten technical Product Catalog access is enabled. Jamieson partnership, separate advertiser Product Feed approval and actual complete catalog-file availability are proven. The proprietary feed remains outside source control.

Sanitized audit:

- 273 product rows; trailer count matches;
- 273/273 documented 38-field shape;
- 273/273 CAD and in-stock;
- 273 unique SKUs and source Product IDs;
- GTIN present 271/273; all supplied GTINs checksum-valid;
- product/image URL syntax valid 273/273;
- manufacturer Jamieson 273/273;
- description 272/273;
- Class ID blank 273/273;
- Attribute 1 populated all rows but remains opaque/untyped without Class ID;
- Sale Price < Retail Price: 48;
- Sale Price = Retail Price: 223;
- Sale Price > Retail Price: 2.

Rakuten's generic documented schema resolves the field roles: `Sale Price` reflects discounts; `Retail Price` is non-discounted/reference. The two Sale > Retail rows are semantic conflicts and must never be swapped/repaired to manufacture a discount.

Jamieson remains **NOT production-authorized**. Open gates include Product Catalog cache/persistence/index/search/display/mobile rights, retention/deletion obligations, installed-software/DSA approvals where applicable, trustworthy per-offer/current-price observation freshness, Canadian offer geography beyond CAD/context and broad package-count coverage.

Because the feed still lacks strong Canadian offer-scope evidence and trustworthy per-offer price observation timestamps, Jamieson rows cannot pass the production view today.

The Rakuten Android/feed-use/retention/DSA clarification was sent on 2026-08-28. No substantive reply after that clarification has been found yet; do not send a duplicate unless a reply creates a genuinely new gap.

## Jamieson × Open Food Facts

The historical first 0/271 result is invalid coverage evidence because it compared normalized OFF codes against raw provider representations.

Correct normalized result from 2026-08-28:

- product records: 273;
- valid GTINs: 271;
- normalized OFF matches: 102;
- unmatched: 169;
- exact supplement-count candidates: 12;
- structured mass/volume-only candidates: 2;
- quantity conflicts: 0;
- matched but no usable quantity: 88;
- expected Jamieson brand text: 90;
- source modification timestamps: 102;
- successful batch-search requests: 13;
- search fallback batches: 1;
- direct product reads: 20;
- response codes ignored after canonical validation: 0.

Open Food Facts is supplemental only. Only 12 of 271 valid-GTIN identities are exact-count-ready under strict rules. Never relax the parser or infer count from Rakuten titles/descriptions/attributes to inflate coverage.

## Package-content provider path

Health Canada LNHPD can contribute regulatory identity/licence/dosage/ingredient facts but does not provide the needed GTIN-level package net count.

GS1 Canada ECCnet remains the strategic next package-content candidate. The Data Recipient eligibility/rights inquiry was sent on 2026-08-28; only an acknowledgement has been received so far, not substantive eligibility/rights terms.

Await written confirmation of eligibility, GTIN/net-content scope, consumer/mobile/search/cache rights, restrictions and commercial/API terms before implementing any production ECCnet adapter.

## Provider/account checkpoint

Use `PROVIDER_ACCOUNT_STATUS.md` for fast-changing external account status.

High-level state remains:

- Rakuten/Jamieson: feed approved + actual catalog available; production rights/geography/freshness/count gates blocked;
- GS1 Canada ECCnet: inquiry acknowledged; substantive response pending;
- Well.ca / Bath Depot: pending unless newer evidence;
- Tru Earth / Giant Tiger: rejected; do not reapply now;
- CJ: TSC, Brother Canada, DAVIDsTEA pending; AOSOM older pending unless newer evidence;
- Awin active; Skip CA rejected for publisher type; never misrepresent publisher type;
- impact.com Marketplace declined; no blind duplicate application;
- Lowvyn inquiry sent; await written rights/technical response;
- Open Prices: supplemental historical/proof-backed price rail;
- Open Food Facts: supplemental product/package metadata rail.

## Android privacy boundary

Current Android still has:

- no `INTERNET` permission;
- no `ACCESS_NETWORK_STATE` permission;
- no account requirement;
- no telemetry;
- no remote AI dependency;
- no ValuePilot server dependency.

Provider research/networking remains outside Android.

## Immediate next safe engineering work

External Rakuten and GS1 requests are already outstanding. Do not resend them.

Next bounded provider-neutral target:

1. define a lifecycle-bound **current-price evidence-claim bridge** from the production view into the existing evidence/conflict engine;
2. require explicit merchant/channel scope and evidence authority rather than inventing either from provider context;
3. use the canonical/provider-scoped product key contract, allowing cross-source quantity joins only when the identity representation truly matches;
4. keep package quantity separately attributed and keep unit value unavailable unless exact compatible quantity evidence survives conflict/authority gates;
5. do not add Android networking until a real provider path passes all required production gates.

Do not treat the production view itself as rankable. Do not add production Rakuten integration, affiliate tracking, checkout/payment, universal cart, subscriptions, remote AI, telemetry, unauthorized scraping or private-endpoint reverse engineering.
