# Current state

Updated: 2026-08-28

Branch: `work/valuepilot-android-milestone`

Android version: 101.1.0 (10101)

## Completed Android / product foundation

Completed milestones:

- 5B1 standalone comparison application layer
- 5B2 standalone comparison screen
- 5B2A real-device comparison hardening
- 5C1 immutable Android application shell state
- parser regression fix preserving names beginning with `reg`
- 5C2 permanent Android app shell
- 5C3A Universal Search application foundation
- 5C3B first consumer Universal Search experience
- SMART ranking fix preferring explicit measurable evidence over heuristic portion fallback
- 5C4A permanent shopping evidence provenance contract
- 5C4B Universal Search migration to typed shopping evidence
- 5C4C deterministic evidence acceptance/freshness policy
- 5C4D Universal Search evidence-trust enforcement
- 5C4E promotion-provenance ranking hardening

Primary navigation remains Home / Search / Basket / Saved. Compare remains a workflow rather than a primary tab.

The first Android Search experience is physically verified on-device. Built-in Search data remains explicitly fictional/sample evidence and is never presented as live merchant pricing, inventory, promotions or availability.

## Permanent architecture

ValuePilot is a provider-neutral shopping-intelligence platform.

Permanent flow:

authorized/open/user evidence -> provider adapters -> provenance-preserving claims/import records -> deterministic validation/normalization -> Product identity + multiple Offers -> bounded retrieval -> deterministic ranking -> immutable presentation -> replaceable UI

Permanent rules:

- Product != Offer.
- Sources contribute claims; they do not overwrite one shared row.
- Stronger same-scope evidence may defeat weaker evidence; unresolved equal-strength conflict blocks Best Value.
- Money, quantity, currency and promotion arithmetic are exact/deterministic.
- AI may classify/explain but may not invent authoritative facts.
- Commission, EPC, sponsorship, payout and provider preference never influence rank.
- No unauthorized scraping or private-endpoint reverse engineering.
- Feed/account access != production authorization.
- Shared core owns no hidden clock.
- Provider credentials never belong in source control, Android, fixtures, logs or screenshots.

## Real Shopping Evidence / shared-core hardening

The deterministic shared layer now includes:

- typed `ShoppingEvidence`
- explicit sample / real-world / unknown environment
- explicit acquisition channel and claim kind
- caller-supplied evidence freshness evaluation
- rankable / display-only / rejected dispositions
- checksum-aware GTIN validation and canonical cross-source GTIN representation
- source-isolated evidence namespaces and storage boundaries
- deterministic conflict policy and N-source fact resolution
- evidence-backed unit-value gating
- provider-neutral staged offer import preserving unresolved source fields
- provider-neutral discounted/reference price-pair structural validation
- provider dataset-recency classification separate from offer freshness
- fail-closed provider production-authorization profiles/gates
- fail-closed provider dataset offer-country validation
- staged production offer candidate boundary that still creates no canonical `Offer` and grants no rankability

Permanent invariant: historical observed price, merchant price, package quantity, benchmark, regulatory fact, dataset recency, geography and authorization remain distinct evidence domains. None is silently promoted into another.

### Provider price relationship boundary

Commit `5bb647a8485f257ec51b3eb0fe39b9c7caccb0a0` added adapter-declared discounted/reference price-pair assessment.

The shared core contains no Rakuten-specific field names and never chooses a current price from this assessment.

Deterministic outcomes:

- discounted < reference -> structurally consistent discount pair
- discounted = reference -> no savings claim
- discounted > reference -> semantic-conflict state
- missing/malformed/non-positive values -> unavailable
- currency or exact-money-scale mismatch -> incomparable

This layer does not create an `Offer`, establish rights/freshness/geography, or grant rankability. Its full Android workflow passed.

### Dataset recency != offer freshness

Commit `a8e98b8ce333a612538841566972d6cab58dde88` added `ProviderDatasetRecency.kt`.

Dataset/file timestamps can classify as `UNKNOWN`, `FUTURE_DATED`, `RECENT`, `AGING`, or `STALE`, but they never populate `priceObservedAtEpochMillis`, never become per-offer freshness and never make evidence rankable.

The full Android workflow passed for that exact commit.

### Fail-closed production authorization

Commit `6aed414bd5f89cf7ac6dfb739464c6f57f5abe78` added `ProviderProductionAuthorization.kt` and focused tests.

Production activation is machine-gated per provider + isolated dataset namespace. Every gate required by the selected activation profile must be explicitly `SATISFIED`; missing, `PENDING`, `DENIED`, `UNKNOWN`, or incorrectly `NOT_REQUIRED` required gates block activation.

The consumer mobile offer-catalog profile requires explicit evidence for:

- data access authorization
- consumer display authorization
- caching authorization
- indexing authorization
- mobile-app authorization
- retention/deletion policy
- offer geography validation
- price semantics validation
- dataset-recency policy
- offer-freshness policy

When network/affiliate links are enabled, the stronger profile additionally requires:

- affiliate-link use authorization
- installed-software/network approval
- advertiser distribution approval
- tracking/privacy readiness

A feed-access approval alone therefore cannot authorize mobile production. Network/DSA gates are conditional on actually enabling that capability, not global blockers for a catalog-only profile.

The exact commit passed browser checks, shared-core/app tests, lint, APK build, JVM summary, Android privacy-boundary verification, release packaging/checksums and artifact upload.

### Offer-country / Canadian market boundary

Commit `7606ea941f80e3dc6b2ea362bc688c7434215195` added `ProviderOfferGeography.kt` and focused tests.

Country scope is evidence with provenance. Strong bases are limited to:

- explicit offer country
- explicit dataset country
- documented dataset market

Weak bases remain unresolved even when they suggest `CA`:

- currency-only evidence such as CAD
- advertiser context alone
- inferred country
- unknown basis

A strong explicit/documented match to Canada can satisfy the production `OFFER_GEOGRAPHY_VALIDATED` gate. A strong explicit different country denies that gate. Currency/context/inference maps to `UNKNOWN` and therefore blocks production activation.

Permanent rule:

**CAD != proof that an offer is Canadian.**

The exact geography commit also passed browser checks, shared-core/app tests, lint, APK build, JVM summary, Android privacy-boundary verification, release packaging/checksums and artifact upload.

### Staged production offer candidate boundary

Commit `f58b400533bdf9a0705fb8e88680e4b56ce9d94e` added `ProductionOfferCandidate.kt` and focused tests.

A provider row can become a `StagedProductionOfferCandidate` only after all of the following survive the same deterministic evaluation:

- the selected activation profile contains at least the full consumer-mobile-catalog gate set;
- authorization is scoped to the same provider + dataset namespace as the row;
- geography evidence is scoped to the same provider + dataset and independently re-evaluated rather than trusting a stale geography gate;
- the effective production authorization decision is fully authorized;
- the row is real-world evidence from a known non-fixture channel with a non-inferred claim;
- at least one validated source identity remains after invalid-GTIN filtering;
- the adapter explicitly declares the current-price field and optional reference-price field;
- the current price is exact/positive and any reference price is exact/positive/compatible;
- an optional current-must-not-exceed-reference rule fails closed on inverted price semantics;
- `priceObservedAtEpochMillis` is actually present;
- caller-supplied per-offer freshness classifies the price as `FRESH` or `AGING`, not unknown/future/stale.

Permanent rules:

- a dataset timestamp cannot substitute for per-offer price observation time;
- CAD/context cannot substitute for Canadian offer scope;
- an authorization decision cannot be reused across a different provider/dataset;
- a deliberately weaker custom activation profile cannot bypass the base mobile-catalog requirements;
- the candidate is still **not** a canonical `Offer` and contains no rankable flag;
- quantity, unit value, promotion arithmetic and Best Value participation remain downstream evidence-gated decisions.

The exact staged-candidate commit passed browser checks, shared-core/app tests, lint, APK build, JVM summary, Android privacy-boundary verification, release packaging/checksums and artifact upload.

## GTIN identity representation

`GtinValidation.kt` distinguishes checksum validation from canonical cross-source representation.

Provider staging preserves:

- `suppliedGtin`: exact provider source string
- `validatedGtin`: exact checksum-valid source representation
- `canonicalGtin`: deterministic cross-source identity

Canonical representation handles documented leading-zero equivalents while refusing invalid-GTIN repair.

## Source-isolated evidence index

`SourceIsolatedEvidenceIndex` already supports coarse-grained source withdrawal through `removeNamespace(namespaceId)`, removing exactly one dataset namespace and its claims without mutating other provider datasets. Do not duplicate this mechanism; future production activation/revocation should use the existing source-isolation boundary.

## First authorized real merchant feed — Jamieson / Rakuten

Rakuten technical Product Catalog access is enabled. Rakuten Customer Support explicitly confirmed Jamieson advertiser Product Feed approval and actual catalog-file presence.

The complete authorized Jamieson TXT.gz feed was downloaded and audited offline. The proprietary file is never committed.

Sanitized checkpoint:

- 273 product rows; trailer count matches
- 273/273 documented 38-field shape
- 273/273 CAD
- 273/273 in-stock
- 273 unique SKUs and 273 unique source Product IDs
- GTIN present 271/273; all 271 supplied values checksum-valid
- product/image URL syntax valid 273/273
- manufacturer Jamieson 273/273
- description 272/273
- Class ID blank 273/273
- Attribute 1 populated 273/273 while Attributes 2–10 are blank; without Class ID this field remains opaque/untyped
- Sale Price < Retail Price: 48
- Sale Price = Retail Price: 223
- Sale Price > Retail Price: 2

Rakuten's documented generic schema resolves the field meanings:

- `Sale Price` reflects discounts
- `Retail Price` does not reflect discounts

Therefore:

- Sale < Retail is structurally consistent
- Sale = Retail creates no savings claim
- Sale > Retail is a semantic conflict and fails closed
- the 2 inverted rows must never be swapped, repaired or interpreted as markup

Still unresolved for production:

- Product Catalog cache/persistence/index/search/display/mobile rights
- retention/deletion obligations
- Android/installed-software distribution approval
- DSA/network-link approvals if affiliate links are enabled
- per-offer/current-price freshness
- Canadian offer geography beyond CAD/context
- broad package-count coverage

The Rakuten Android/feed-use/retention/DSA clarification request was sent in the existing support case on 2026-08-28. Do not send a duplicate unless their response leaves a new gap.

The production authorization evaluator therefore keeps Jamieson blocked from production activation today. In particular, the geography gate remains `UNKNOWN`: 273/273 CAD is useful currency evidence but is not Canadian offer-scope proof. Because the feed also lacks a trustworthy per-offer price observation timestamp, current Jamieson rows cannot pass the staged production candidate boundary either.

## Jamieson × Open Food Facts — valid normalized measurement

The historical raw-code result of 0/271 matches is invalid coverage evidence because it compared normalized OFF response codes against raw provider GTIN representations.

The corrected normalized run completed successfully on 2026-08-28:

- product records: 273
- valid GTINs: 271
- normalized OFF matches: 102
- unmatched: 169
- exact supplement-count candidates: 12
- structured mass/volume-only candidates: 2
- quantity conflicts: 0
- matched but no usable quantity: 88
- expected Jamieson brand text: 90
- source modification timestamp: 102
- successful batch-search requests: 13
- search fallback batches: 1
- direct product reads: 20
- response codes ignored after canonical validation: 0

Open Food Facts is useful supplemental metadata but not the package-count foundation. Only 12 of the 271 valid-GTIN identities are exact-count-ready under the strict rules; 259 remain not exact-count-ready.

Do not relax the parser or infer count from Rakuten titles/descriptions/attributes to increase coverage.

## Package-content source decision

Health Canada's LNHPD is useful for regulatory identity, licence, dosage form, ingredients and recommended-dose facts, but its public schema does not provide GTIN-level package net content/count.

GS1 Canada ECCnet remains the strategic next package-content/identity candidate because it is GTIN-centric and supports standardized net-content/product-content data including count-style content.

The ValuePilot ECCnet Data Recipient eligibility/rights inquiry was sent to GS1 Canada on 2026-08-28. Await the written response. Do not implement or assume ECCnet production rights before eligibility, scope, usage rights and commercial/API terms are established.

## Provider/account checkpoint

Use `PROVIDER_ACCOUNT_STATUS.md` for fast-changing external status.

Current high-level state:

- Rakuten/Jamieson: partnership + Product Feed approved + actual catalog available; production rights/channel/geography/freshness/count gates still open
- GS1 Canada ECCnet: eligibility/rights inquiry sent; awaiting response
- Well.ca: pending unless newer evidence
- Bath Depot: pending unless newer evidence
- Tru Earth: rejected; do not reapply now
- Giant Tiger: rejected; do not reapply now
- CJ: TSC, Brother Canada, DAVIDsTEA pending; AOSOM older pending unless newer evidence
- Awin active; Skip CA rejected for publisher type; do not misrepresent publisher type
- impact.com Marketplace declined; no blind duplicate application
- Lowvyn inquiry sent; await written rights/technical response
- Open Prices: supplemental historical/proof-backed price rail
- Open Food Facts: supplemental product/package metadata rail

## Privacy boundary

Current Android build still has:

- no `INTERNET` permission
- no `ACCESS_NETWORK_STATE` permission
- no account requirement
- no telemetry
- no remote AI dependency
- no ValuePilot server dependency

Provider research/networking remains outside Android.

## Current milestone

5D — Authorized Real Shopping Data Provider Selection / validation.

The milestone is now beyond first-feed acquisition and includes machine-enforced authorization, offer-country and staged-production-candidate gates.

## Immediate next work

External responses are already requested. Do not resend them.

While awaiting Rakuten and GS1 Canada written responses, continue only provider-neutral/offline work that cannot accidentally authorize or expose real provider data.

Next safe engineering targets, in order:

1. add an activation/revocation lifecycle around accepted staged candidates so a dataset can become enabled only from an authorized snapshot and can be disabled/withdrawn by namespace using the existing source-isolation boundary;
2. keep package quantity as separately attributed evidence and do not make unit value available unless exact compatible quantity evidence exists;
3. keep canonical `Offer` creation and ranking downstream of both activation and evidence acceptance;
4. do not add Android networking until a real provider path actually passes the required activation profile.

5D still does not authorize production Rakuten integration, affiliate-link tracking, checkout/payment, universal cart, subscriptions, remote AI, telemetry, unauthorized scraping or private-endpoint reverse engineering.
