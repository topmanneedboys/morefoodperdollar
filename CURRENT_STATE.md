# Current state

Updated: 2026-08-28

Branch: `work/valuepilot-android-milestone`

Android version: 101.1.0 (10101)

## Current milestone

5D — Authorized Real Shopping Data Provider Selection / validation.

ValuePilot remains a provider-neutral shopping-intelligence platform. The permanent foundation is not Accessibility, OCR, overlays, browser capture, or any one merchant/provider.

Permanent flow:

`authorized/open/user evidence -> provider adapters -> provenance-preserving claims/import records -> deterministic validation/normalization -> Product identity + multiple Offers -> bounded retrieval -> deterministic ranking -> immutable presentation -> replaceable UI`

Permanent rules:

- Product != Offer.
- Sources contribute claims; they never overwrite one shared truth row.
- Stronger same-scope evidence may defeat weaker evidence; unresolved equal-strength conflicts block Best Value.
- Money, quantity, currency and promotion arithmetic are exact/deterministic.
- AI may classify/explain but may not invent authoritative facts.
- Commission, EPC, payout, sponsorship, affiliate economics and provider preference never affect ranking.
- No unauthorized scraping or private-endpoint reverse engineering.
- Technical/feed access != production authorization.
- Dataset recency != per-offer price freshness.
- Currency/context != offer geography.
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
- revocable/suspendable/expiring production dataset lifecycle.

### Price relationship boundary

Commit `5bb647a8485f257ec51b3eb0fe39b9c7caccb0a0` added provider-neutral discounted/reference price-pair assessment.

The shared core never derives field roles from provider field names. Discounted > reference becomes a semantic conflict. Missing/malformed/non-positive/incompatible values fail closed. The relationship assessment never chooses a current price, creates an `Offer`, grants rankability or proves rights/freshness/geography.

### Dataset recency boundary

Commit `a8e98b8ce333a612538841566972d6cab58dde88` added provider dataset/file recency classification.

Permanent rule: **a recent dataset does not mean a fresh offer.** Dataset timestamps never populate or substitute for `priceObservedAtEpochMillis`.

### Production authorization boundary

Commit `6aed414bd5f89cf7ac6dfb739464c6f57f5abe78` added fail-closed production authorization per provider + isolated dataset namespace.

The base consumer mobile catalog profile requires explicit `SATISFIED` gates for:

- data access;
- consumer display;
- caching;
- indexing;
- mobile app use;
- retention/deletion policy;
- offer geography;
- price semantics;
- dataset-recency policy;
- offer-freshness policy.

Missing, pending, denied, unknown, or incorrectly-not-required required gates block production. A stronger network/affiliate profile additionally requires affiliate-link use, installed-software/network approval, advertiser distribution approval, and tracking/privacy readiness.

Feed access alone can never authorize production.

### Offer-country boundary

Commit `7606ea941f80e3dc6b2ea362bc688c7434215195` added provider dataset offer-country validation.

Strong country bases are limited to explicit offer country, explicit dataset country, or documented dataset market. Currency-only evidence, advertiser context, inference and unknown basis remain unresolved.

Permanent rule: **CAD != Canadian offer scope.**

### Staged production offer candidate

Commit `f58b400533bdf9a0705fb8e88680e4b56ce9d94e` added `StagedProductionOfferCandidate`.

A provider row can reach this boundary only when:

- at least the complete base consumer-mobile-catalog profile is required;
- authorization and geography are scoped to the same provider + dataset;
- effective authorization is fully satisfied;
- evidence is real-world and not fixture/unknown/inferred;
- validated source identity exists;
- the adapter explicitly supplies documented current/reference price roles;
- current/reference money is exact, positive and compatible;
- declared price relationship rules pass;
- a real per-offer price observation timestamp exists; and
- caller-supplied freshness classifies the offer price as `FRESH` or `AGING`.

This object is still not a canonical `Offer`, has no rankability flag, and cannot bypass quantity, promotion, conflict or unit-value evidence gates.

### Revocable production dataset lifecycle

Commits:

- `8ad4bb29a3e61d8931cedb3abe48c87ea2ba074a` — initial lifecycle implementation;
- `e546822a448e150674a2769d9899a856124b50fb` — closes the weak-profile reactivation bypass and is the authoritative verified lifecycle code.

`ProductionDatasetLifecycle.kt` now adds:

- `ProductionDatasetSnapshotRef` — exact provider + dataset namespace + opaque adapter-supplied snapshot identity;
- `ProductionDatasetSnapshotBinder` — a staged candidate must match the exact provider/dataset scope before snapshot binding;
- `ProductionDatasetLifecycleRecord` — explicit profile-scoped `ACTIVE`, `SUSPENDED`, `REVOKED`, or `RETIRED` state with caller-supplied revision/effective/expiry data and an auditable basis;
- `ProductionDatasetLifecycleRegistry` — deterministic monotonic lifecycle state keyed by snapshot + activation profile;
- `ProductionDatasetActivationEvaluator` — point-in-time fail-closed activation.

Permanent lifecycle rules:

- a dataset namespace is not a snapshot;
- a staged candidate is not active merely because it once passed validation;
- no lifecycle record means inactive;
- suspension, revocation, retirement, pre-effective state and expiry all block activation;
- `REVOKED` and `RETIRED` are terminal for the same snapshot/profile key; re-enabling requires a new snapshot identity;
- stale lifecycle revisions are rejected and same-revision mutations are rejected as collisions;
- current production authorization is re-evaluated on every activation check;
- authorization from another provider/dataset cannot be reused;
- activation re-checks the original per-offer price observation against the current caller-supplied freshness policy, so an old staged `FRESH` value cannot remain active after becoming stale;
- a custom weaker activation profile cannot bypass the base consumer-mobile-catalog gates even if it reuses the same profile ID;
- revoking dataset A does not disable dataset B;
- activation creates no canonical `Offer` and grants no durable rankability/display permission.

The full ValuePilot workflow for exact corrected commit `e546822a448e150674a2769d9899a856124b50fb` completed successfully: browser checks, shared-core/app tests, lint, APK build, JVM result summary, Android privacy-boundary verification, release packaging/checksums and artifact upload all passed.

## Source withdrawal / deletion boundary

`SourceIsolatedEvidenceIndex.removeNamespace(namespaceId)` already supports explicit removal of one evidence namespace and its claims without mutating other namespaces.

Do **not** automatically call `removeNamespace()` merely because one activation profile is `REVOKED` or `RETIRED`. Profile-level authorization/lifecycle and dataset-level retention/deletion are distinct decisions. One use profile may be revoked while another permitted use or retention obligation remains.

Current invariant:

**revocation blocks use immediately; physical deletion remains a separate explicit dataset-level withdrawal/retention decision.**

## Canonical Offer / unit-value boundary

`EvidenceBackedUnitValuePolicy` already fails closed after a canonical `Offer` exists. It requires rankable price evidence, compatible price domain, package-quantity domain, identical stable product key, exact money/quantity fingerprints and strong quantity authority.

Do not duplicate this logic.

Do not yet materialize production canonical `Offer` objects from lifecycle-active candidates. `Offer` itself carries no lifecycle/provenance handle, so premature materialization could allow downstream code to detach price data from later revocation/freshness checks. The lifecycle-to-Offer coupling must be designed before production Offer creation is enabled.

## GTIN identity

Provider staging preserves:

- `suppliedGtin`: exact provider representation;
- `validatedGtin`: checksum-valid exact source representation;
- `canonicalGtin`: deterministic cross-source identity.

Canonicalization handles documented leading-zero equivalent representations and never repairs invalid GTINs.

## Jamieson / Rakuten — first actual merchant feed

Rakuten technical Product Catalog access is enabled. Jamieson partnership, separate advertiser Product Feed approval and actual complete catalog-file availability are proven.

The proprietary feed remains outside source control.

Sanitized audit:

- 273 product rows; trailer count matches;
- 273/273 documented 38-field shape;
- 273/273 CAD;
- 273/273 in-stock;
- 273 unique SKUs and source Product IDs;
- GTIN present 271/273; all 271 supplied GTINs checksum-valid;
- product/image URL syntax valid 273/273;
- manufacturer Jamieson 273/273;
- description 272/273;
- Class ID blank 273/273;
- Attribute 1 populated 273/273 while Attributes 2–10 are blank; Attribute 1 remains opaque/untyped without Class ID;
- Sale Price < Retail Price: 48;
- Sale Price = Retail Price: 223;
- Sale Price > Retail Price: 2.

Rakuten's generic documented schema resolves the field roles:

- `Sale Price` reflects discounts;
- `Retail Price` does not reflect discounts.

Therefore the two Sale > Retail rows are source-semantic conflicts. Never swap, repair, or reinterpret them to manufacture a current price or discount.

Jamieson remains **NOT production-authorized**. Open gates include:

- Product Catalog cache/persistence/index/search/display/mobile rights;
- retention/deletion obligations;
- Android/installed-software distribution approval;
- DSA/network-link approvals if affiliate links are enabled;
- trustworthy per-offer/current-price observation freshness;
- Canadian offer geography beyond CAD/context;
- broad package-count coverage.

Because the current feed lacks both strong Canadian offer-scope evidence and trustworthy per-offer price observation timestamps, Jamieson rows cannot pass the staged production candidate/lifecycle path today.

The Rakuten Android/feed-use/retention/DSA clarification request was already sent on 2026-08-28. Do not send a duplicate unless Rakuten's response leaves a genuinely new unresolved issue.

## Jamieson × Open Food Facts

The historical first 0/271 result is invalid coverage evidence because it compared normalized OFF codes against raw provider representations.

Correct normalized authoritative result from 2026-08-28:

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

GS1 Canada ECCnet remains the strategic next package-content candidate because it is GTIN-centric and supports standardized net-content/product-content data including count-style content.

The ECCnet Data Recipient eligibility/rights inquiry was already sent on 2026-08-28. Await written confirmation of eligibility, GTIN/net-content scope, consumer/mobile/search/cache rights, restrictions and commercial/API terms before implementing any production ECCnet adapter.

## Provider/account checkpoint

Use `PROVIDER_ACCOUNT_STATUS.md` for fast-changing external account status.

High-level state:

- Rakuten/Jamieson: feed approved + actual catalog available; production rights/geography/freshness/count gates remain blocked;
- GS1 Canada ECCnet: eligibility/rights inquiry sent; awaiting response;
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

Next bounded provider-neutral targets:

1. define a **dataset-level withdrawal/quarantine decision** separate from profile-level lifecycle, so explicit removal can safely use the existing source-isolated namespace boundary without deleting data merely because one use profile was revoked;
2. design lifecycle-coupled canonical Offer materialization so an `Offer` cannot be detached from current activation/freshness/revocation state before ranking/display;
3. keep package quantity separately attributed and keep unit value unavailable unless exact compatible quantity evidence survives conflict/authority gates;
4. add no Android networking until a real provider path passes the required activation profile and lifecycle checks.

5D still does not authorize production Rakuten integration, affiliate tracking, checkout/payment, universal cart, subscriptions, remote AI, telemetry, unauthorized scraping or private-endpoint reverse engineering.
