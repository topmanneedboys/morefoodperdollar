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
- No unauthorized scraping, private-endpoint reverse engineering or anti-bot circumvention.
- Technical/feed access != production authorization.
- Dataset recency != per-offer price freshness.
- Currency/context != offer geography.
- Dataset namespace != dataset snapshot.
- Snapshot lifecycle != namespace-wide retention/deletion disposition.
- Production price view != price claim != acceptance-policy rankability != final Best Value eligibility.
- Conflict resolution and acceptance are separate decisions; never hide a contradictory factual claim merely because that claim is display-only under acceptance policy.
- A discounted/current price field does not automatically prove a promotion.
- Shared core owns no hidden clock.
- Provider credentials never belong in source control, Android, fixtures, logs or screenshots.

Primary Android navigation remains Home / Search / Basket / Saved. Compare remains a workflow rather than a primary tab. Built-in Android Search remains explicitly fictional/sample evidence and is never represented as live merchant pricing, inventory, promotion or availability.

## Verified shared-core production hardening

Verified chain:

- `5bb647a8485f257ec51b3eb0fe39b9c7caccb0a0` — provider-neutral discounted/reference price relationship validation.
- `a8e98b8ce333a612538841566972d6cab58dde88` — dataset/file recency separate from per-offer freshness.
- `6aed414bd5f89cf7ac6dfb739464c6f57f5abe78` — fail-closed production authorization profiles/gates.
- `7606ea941f80e3dc6b2ea362bc688c7434215195` — fail-closed offer-country validation; CAD is not Canadian scope proof.
- `f58b400533bdf9a0705fb8e88680e4b56ce9d94e` — staged production offer candidate; no rankability.
- `e546822a448e150674a2769d9899a856124b50fb` — authoritative corrected revocable snapshot lifecycle.
- `230b8ae4b6f674979d349320b8e5bd83713db810` — authoritative registry-backed namespace disposition/withdrawal boundary.
- `ebb28a4a506232550b62d08370a9c8935d677603` — lifecycle/disposition-coupled point-in-time production price view from raw evidence.
- `5d317810bd6ccf0933ff6432e6e68f88fb865493` — canonical-GTIN/provider-scoped product evidence identity keys.
- `267cd5aa81c4c3a03110c210ea9b4bfcb203f2ab` — integration tests proving canonical GTIN joins through the existing unit-value identity gate and same-SKU cross-provider isolation.
- `9a9b5f91948fe505a0ad6b598097bf9b8e50c680` — lifecycle-bound CURRENT_PRICE evidence-claim bridge.
- `97bfa3c353e48f15e26e9576cf06f4fa5e1687d1` — authoritative evidence-acceptance policy unification with legacy unknown-freshness semantics preserved.
- `a1b15cc13df4912fb94893c8952f382f7404db1d` — lifecycle-bound production current-price acceptance using the unified policy.

The exact code commits above through `a1b15cc13df4912fb94893c8952f382f7404db1d` have passed the full ValuePilot workflow where applicable: browser checks, shared-core/app tests, lint, APK build, JVM result summary, Android privacy-boundary verification, release packaging/checksums and artifact upload.

## Price, authorization, geography and freshness

Provider adapters declare documented current/reference price roles; shared core never derives roles from field names. Missing, malformed, non-positive or incompatible money fails closed. A declared current/discounted value above its reference value is a semantic conflict when the adapter declares `CURRENT_MUST_NOT_EXCEED_REFERENCE`.

Provider production authorization is scoped to exact provider + isolated dataset namespace. The base consumer-mobile-catalog profile requires explicit satisfied gates for data access, consumer display, caching, indexing, mobile app use, retention/deletion, offer geography, price semantics, dataset-recency policy and offer-freshness policy. Network/affiliate use requires additional link/software/advertiser/tracking approvals.

Strong country bases are limited to explicit offer country, explicit dataset country or documented dataset market. Currency-only evidence, advertiser context or inference is unresolved. Permanent rule: **CAD != Canadian offer scope.**

A recent file does not establish a fresh product offer. Dataset timestamps never populate `priceObservedAtEpochMillis`. Production candidates and later lifecycle checks require a genuine per-offer observation time and re-evaluate it under caller-supplied freshness policy.

## Snapshot lifecycle and namespace disposition

`ProductionDatasetLifecycle.kt` provides exact provider + dataset + snapshot binding, profile-scoped `ACTIVE` / `SUSPENDED` / `REVOKED` / `RETIRED` records, monotonic revisions, effective/expiry windows and current authorization/freshness re-evaluation.

Critical lifecycle rules:

- no lifecycle record = inactive;
- suspended/revoked/retired/not-yet-effective/expired = inactive;
- `REVOKED` and `RETIRED` are terminal for the same snapshot/profile key;
- weaker custom activation profiles cannot bypass the base mobile-catalog requirements;
- lifecycle activation creates no canonical `Offer` and no durable rankability/display permission.

`ProductionDatasetDisposition.kt` is separately namespace-wide:

- `RETAINED` — namespace policy alone does not block use;
- `QUARANTINED` — retain but globally block production use;
- `WITHDRAWAL_REQUIRED` — block use and require explicit physical removal;
- `DELETED` — full removal separately confirmed by the caller.

Profile/snapshot revocation never automatically deletes a namespace. Only the registry's current `WITHDRAWAL_REQUIRED` state can invoke exact namespace removal, and in-memory removal is not proof every persisted copy is gone.

## Point-in-time production price view

`ProductionOfferViewEvaluator` starts from the raw `ProviderOfferImportRecord`; it does not trust a detached staged/bound object. It re-runs staged-candidate validation, exact snapshot binding, lifecycle-registry lookup/evaluation and namespace-disposition evaluation.

Only then can `LifecycleBoundProductionOfferView` exist. The view is point-in-time, records lifecycle/disposition revisions, exposes no rankability flag and must be re-evaluated before a later production decision.

Its shared-core arithmetic `Offer` contains current price only. Provider reference/non-discounted price remains separate and is never silently reclassified as historical `Offer.previous`. No member price, promotion, quantity or unit value is invented.

## Product evidence identity keys

`ProductionProductEvidenceKey.kt` uses this deterministic priority:

1. checksum-valid canonical GTIN -> shared `gtin:<canonical>` representation;
2. otherwise provider item id -> provider-scoped key;
3. otherwise SKU -> provider-scoped key;
4. invalid-only GTIN -> no key.

Invalid GTINs are never repaired. Names, descriptions, images and prices never become identity inputs. Equivalent leading-zero GTIN representations resolve together. Provider IDs/SKUs cannot silently join another provider. A canonical GTIN is an identity representation only; it does not prove allocation/current ownership, rights, freshness, quantity authority or rankability.

## Lifecycle-bound current-price claims

Commit `9a9b5f91948fe505a0ad6b598097bf9b8e50c680` added `ProductionCurrentPriceClaim.kt`.

A CURRENT_PRICE claim can be produced only by re-running the raw production-view path and then applying an explicit claim descriptor with merchant scope, commerce-channel scope, optional location, evidence authority and an auditable authority basis.

Supported current-price authorities are explicitly limited to `MERCHANT_AUTHORITATIVE`, `PROOF_BACKED_DIRECT_OBSERVATION` and `SOURCE_ASSERTED_METADATA`. Authority must agree with source claim kind; for example a source-asserted feed row cannot be relabeled as proof-backed direct observation.

The lifecycle-bound wrapper preserves provider/source/dataset/snapshot identity, source product identity, acquisition channel, claim kind, source price field, country, evaluation instant and lifecycle/disposition revisions. The generic `EvidenceClaim` is only an input to conflict handling. Claim creation itself grants no rankability.

## Unified evidence acceptance policy

Commit `97bfa3c353e48f15e26e9576cf06f4fa5e1687d1` is the authoritative acceptance-policy refactor.

`EvidenceAcceptanceEvaluator` now uses an internal shared-core-only `EvidenceAcceptanceFacts` contract. The existing public `ShoppingEvidence` entry point delegates to the same policy facts, preventing production evidence and legacy shopping evidence from drifting into two definitions of freshness/availability/claim trust.

Parity tests cover sample, fresh, aging, stale, unknown, future-dated, weak claim, unknown channel/environment, availability and promotion behavior. Legacy non-positive observation timestamps—including negative timestamps—remain UNKNOWN freshness rather than becoming constructor errors.

The internal facts object is policy input only. Android/UI code cannot use it as a public shortcut around production authorization/lifecycle.

## Lifecycle-bound production current-price acceptance

Commit `a1b15cc13df4912fb94893c8952f382f7404db1d` added `ProductionCurrentPriceAcceptance.kt`.

`ProductionCurrentPriceAcceptanceEvaluator` starts again from the raw provider import and re-runs `ProductionCurrentPriceClaimEvaluator`; therefore current authorization, geography, snapshot lifecycle, namespace disposition, price semantics and freshness are re-established at the same decision point before acceptance policy is evaluated.

If the current-price claim is blocked, acceptance does not run. If it passes, the unified acceptance policy evaluates real environment/channel/claim-kind/timestamp/availability facts.

Important distinction: `acceptanceRankable` means only that this one price evidence item passes the shared acceptance policy. It is **not final Best Value eligibility**. Factual conflict resolution, package-quantity authority, exact product identity, unit-value compatibility and downstream ranking rules still apply.

Tests prove:

- fresh + in-stock current price can be acceptance-policy `RANKABLE`;
- out-of-stock current price remains evidence but becomes `DISPLAY_ONLY`;
- aging evidence obeys the caller-supplied acceptance policy;
- acceptance freshness may be stricter than production-view freshness;
- a revoked lifecycle blocks claim creation before acceptance runs.

No promotion evidence is supplied merely because the source field is called or documented as a discounted/sale price. Promotion provenance remains a separate evidence path.

## Conflict resolution and unit value

`EvidenceFactResolver` / `EvidenceConflictPolicy` remain the factual conflict boundary. `EvidenceBackedUnitValuePolicy` remains the authoritative unit-value gate.

Do not duplicate either system.

A future final ranking bridge must keep the ordering explicit:

1. current production lifecycle/rights/disposition must pass;
2. current-price claim must be valid and scoped;
3. acceptance policy must be evaluated;
4. all relevant factual claims must still participate in conflict resolution—do not discard a stronger contradictory claim merely because it is display-only;
5. the selected current-price fact must correspond to the candidate being considered;
6. final price disposition must be rankable;
7. unit value additionally requires exact matching product identity, exact money/quantity fingerprints, compatible domains and strong package-quantity authority.

Unresolved same-scope conflict blocks Best Value. Never average, vote or guess.

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

Rakuten generic documented field roles are resolved: Sale Price is discounted; Retail Price is non-discounted/reference. The two Sale > Retail rows are source-semantic conflicts and must never be swapped or repaired.

Jamieson remains **NOT production-authorized**. Open gates include Product Catalog cache/persistence/index/search/display/mobile rights, retention/deletion obligations, installed-software/DSA approvals where applicable, trustworthy per-offer price observation freshness, Canadian offer geography beyond CAD/context and broad package-count coverage.

Current Jamieson rows cannot pass the production path because strong Canadian offer scope and trustworthy per-offer price observation timestamps are absent.

The Rakuten Android/feed-use/retention/DSA clarification was sent on 2026-08-28. No substantive reply after that clarification has been found. Do not send a duplicate unless a response creates a genuinely new gap.

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

GS1 Canada ECCnet remains the strategic next package-content candidate. The Data Recipient eligibility/rights inquiry was sent on 2026-08-28; only an acknowledgement has been received, not substantive eligibility/rights terms.

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

External Rakuten and GS1 requests are outstanding. Do not resend them.

Next bounded provider-neutral target:

**Design and test the final current-price conflict/acceptance eligibility boundary without duplicating the existing conflict or ranking systems.**

Requirements:

- re-evaluate current lifecycle/authorization/disposition at the decision instant;
- keep acceptance policy and factual conflict resolution distinct;
- do not pre-filter contradictory claims merely because they are display-only;
- require the resolved CURRENT_PRICE fact to match the lifecycle-bound candidate's exact fingerprint/scope before it may progress;
- unresolved same-scope price conflict must block Best Value;
- keep package quantity separately attributed;
- keep unit value behind `EvidenceBackedUnitValuePolicy` with exact identity/fingerprint/domain/authority checks;
- add no Android networking or real provider credentials.

Do not add production Rakuten integration, affiliate tracking, checkout/payment, universal cart, subscriptions, remote AI, telemetry, unauthorized scraping or private-endpoint reverse engineering yet.
