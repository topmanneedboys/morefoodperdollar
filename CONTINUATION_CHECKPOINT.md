# ValuePilot Continuation Checkpoint

Updated: 2026-08-28

Branch: `work/valuepilot-android-milestone`

Purpose: compact durable recovery point. Newer repository/account evidence overrides this file.

## Startup order

Read before changing architecture/provider logic:

1. `AGENTS.md`
2. `CURRENT_STATE.md`
3. `CONTINUATION_CHECKPOINT.md`
4. `PROVIDER_ACCOUNT_STATUS.md`
5. `RAKUTEN_PRICE_AND_ANDROID_RIGHTS_GATE.md`
6. `RAKUTEN_JAMIESON_FEED_AUDIT_2026-08-28.md`
7. `RAKUTEN_OFF_QUANTITY_COVERAGE.md`
8. `OPEN_DATA_INTEGRATION_STATUS.md`
9. `ARCHITECTURE.md`
10. `FUTURE_PRODUCT_VISION.md`

## Permanent architecture

ValuePilot is provider-neutral shopping intelligence, not an Accessibility/OCR/overlay product.

Permanent flow:

`authorized/open/user evidence -> provider adapters -> provenance-preserving claims/import records -> deterministic validation/normalization -> Product identity + Offers -> bounded retrieval -> deterministic ranking -> immutable presentation -> replaceable UI`

Permanent rules:

- Product != Offer.
- Sources contribute claims; they do not overwrite one shared row.
- Exact money/quantity/currency/promotion math only.
- AI may classify/explain but never invent authoritative facts.
- Affiliate/provider economics never affect ranking.
- No unauthorized scraping/private-endpoint reverse engineering.
- Technical/feed access != production authorization.
- Dataset recency != per-offer freshness.
- Currency/context != geography.
- Dataset namespace != snapshot.
- Snapshot/profile lifecycle != namespace-wide retention/deletion.
- Production price view != rankable evidence.
- Shared core owns no hidden clock.
- No Android networking merely for provider experiments.

## Current milestone

5D — Authorized Real Shopping Data Provider Selection / validation.

Built-in Android Search remains fictional/sample evidence until a real provider path passes authorization, geography, price, freshness, lifecycle, namespace-disposition and downstream evidence gates.

## Verified production-hardening chain

- `5bb647a8485f257ec51b3eb0fe39b9c7caccb0a0` — provider-neutral discounted/reference price relationship validation.
- `a8e98b8ce333a612538841566972d6cab58dde88` — dataset recency separate from per-offer freshness.
- `6aed414bd5f89cf7ac6dfb739464c6f57f5abe78` — fail-closed production authorization profiles/gates.
- `7606ea941f80e3dc6b2ea362bc688c7434215195` — fail-closed provider offer-country validation; CAD is not Canadian scope proof.
- `f58b400533bdf9a0705fb8e88680e4b56ce9d94e` — staged production offer candidate; still not canonical `Offer` and no rankability.
- `e546822a448e150674a2769d9899a856124b50fb` — authoritative corrected revocable snapshot lifecycle.
- `230b8ae4b6f674979d349320b8e5bd83713db810` — authoritative registry-backed namespace disposition/withdrawal boundary.
- `ebb28a4a506232550b62d08370a9c8935d677603` — lifecycle/disposition-coupled point-in-time production price view from raw provider evidence.
- `5d317810bd6ccf0933ff6432e6e68f88fb865493` — canonical-GTIN/provider-scoped product evidence identity keys.

The lifecycle, withdrawal and production-view commits each passed the full ValuePilot workflow: browser checks, shared-core/app tests, lint, APK build, JVM summary, Android privacy-boundary verification, release packaging/checksums and artifact upload. The product-key code commit also passed the full workflow; current-branch integration tests additionally exercise it through the existing unit-value identity gate.

## Snapshot lifecycle

`ProductionDatasetLifecycle.kt` provides exact `ProductionDatasetSnapshotRef`, staged-candidate snapshot binding, profile-scoped `ACTIVE` / `SUSPENDED` / `REVOKED` / `RETIRED` state, monotonic revisions, effective/expiry windows and point-in-time activation.

Critical invariants:

- staged candidate != active candidate;
- no lifecycle record = inactive;
- suspended/revoked/retired/not-yet-effective/expired = inactive;
- `REVOKED` and `RETIRED` are terminal for the same snapshot/profile key;
- current authorization is re-evaluated on every activation check;
- original offer timestamp is re-evaluated under current freshness policy;
- weaker custom profile with the same id cannot bypass base mobile-catalog requirements;
- activation creates no canonical `Offer` and grants no durable rankability/display permission.

## Namespace disposition / withdrawal

`ProductionDatasetDisposition.kt` is intentionally separate from snapshot lifecycle.

States:

- `RETAINED` — namespace policy alone does not block use;
- `QUARANTINED` — retain but globally block production use;
- `WITHDRAWAL_REQUIRED` — globally block use and require explicit removal;
- `DELETED` — complete physical removal confirmed separately by caller.

Critical invariants:

- profile/snapshot revocation does not imply deletion;
- `RETAINED <-> QUARANTINED` is reversible;
- withdrawal-required is one-way toward deleted;
- deleted is terminal for the same namespace id;
- use and withdrawal decisions read the registry, not free-standing disposition objects;
- only current `WITHDRAWAL_REQUIRED` can invoke exact namespace removal;
- namespace metadata must match before removal;
- in-memory removal is not proof every persisted copy is gone and does not automatically mark `DELETED`.

## Point-in-time production price view

`ProductionOfferView.kt` does not trust staged or bound intermediate objects as authority.

Every evaluation starts from the raw `ProviderOfferImportRecord` and re-runs:

1. staged candidate validation (price roles, source identity, authorization, geography, freshness);
2. exact snapshot binding;
3. lifecycle-registry lookup;
4. current lifecycle/authorization/freshness evaluation;
5. namespace-disposition-registry evaluation.

Only then can `LifecycleBoundProductionOfferView` exist.

Critical invariants:

- wrong/missing snapshot/lifecycle/disposition, quarantine, revocation, expiry, stale price or current authorization denial all block the view;
- view records evaluation instant plus lifecycle/disposition revisions;
- view is point-in-time and must be re-evaluated for later production decisions;
- no rankability flag is exposed;
- shared-core arithmetic `Offer` contains current price only;
- provider reference/non-discounted price is not mislabeled as historical `Offer.previous`;
- no member price, promotion, quantity or unit value is invented.

## Product evidence identity keys

`ProductionProductEvidenceKey.kt` establishes one production identity representation:

1. valid canonical GTIN -> `gtin:<canonical>` shared representation;
2. otherwise provider item id -> provider-scoped key;
3. otherwise SKU -> provider-scoped key;
4. invalid-only GTIN -> no key.

Critical invariants:

- invalid GTIN is never repaired;
- names/descriptions/images/prices never become identity inputs;
- leading-zero equivalent GTIN representations resolve together;
- provider item IDs and SKUs never silently join different providers;
- delimiter-safe length-prefixed provider components prevent key collisions;
- the key itself does not grant rights, freshness, quantity authority, rankability or proof that a GTIN is allocated/current.

Current-branch integration tests prove equivalent GTIN representations can satisfy `EvidenceBackedUnitValuePolicy`'s exact product-key check, while identical SKU text from different providers fails with `PRODUCT_IDENTITY_MISMATCH`.

## Canonical Offer / unit value

`EvidenceBackedUnitValuePolicy` remains the authoritative unit-value gate. It requires rankable price evidence, correct price/quantity domains, exact same stable product key, exact money/quantity fingerprints and strong quantity authority.

Do not duplicate this logic.

The production price view is deliberately not rankable. The next bridge must preserve explicit merchant/channel scope and price-evidence authority before feeding current price into the existing acceptance/conflict/unit-value engine.

## Jamieson / Rakuten

Jamieson partnership + separate Product Feed approval + actual complete Rakuten catalog-file availability are proven.

Sanitized feed checkpoint:

- 273 rows;
- 273/273 documented 38-field shape;
- 273/273 CAD and in-stock;
- 273 unique SKUs/Product IDs;
- GTIN present 271/273; all supplied GTINs checksum-valid;
- Sale < Retail: 48;
- Sale = Retail: 223;
- Sale > Retail: 2;
- Class ID blank all rows; Attribute 1 remains opaque/untyped.

Rakuten generic field semantics are resolved: Sale Price = discounted field; Retail Price = non-discounted/reference field. The two Sale > Retail rows are semantic conflicts and must fail closed; never swap/repair them.

Jamieson is **NOT production-authorized**. Still unresolved:

- cache/index/search/display/mobile rights;
- retention/deletion obligations;
- installed-software/DSA approvals where applicable;
- trustworthy per-offer price observation freshness;
- Canadian offer geography beyond CAD/context;
- broad package quantity.

Current Jamieson rows cannot pass the production-view path because strong Canadian offer scope and trustworthy per-offer price observation time are absent.

Rakuten clarification was sent on 2026-08-28. No substantive reply after that clarification has been found; do not resend unless a response creates a new gap.

## Jamieson × Open Food Facts

Historical raw-code 0/271 result is invalid coverage evidence.

Correct normalized result:

- 273 product records;
- 271 valid GTINs;
- 102 normalized OFF matches;
- 169 unmatched;
- 12 exact supplement-count candidates;
- 2 structured mass/volume-only;
- 0 quantity conflicts;
- 88 matched but no usable quantity;
- 1 search-fallback batch / 20 direct reads.

OFF is supplemental only. Only 12/271 valid-GTIN identities are exact-count-ready under strict rules. Never infer count from Rakuten text or loosen the parser to inflate coverage.

## Package-content provider path

Health Canada LNHPD does not provide GTIN-level package net count.

GS1 Canada ECCnet remains the strategic GTIN/package-content candidate. The Data Recipient eligibility/rights inquiry was sent on 2026-08-28. An acknowledgement was received, but no substantive eligibility/rights terms yet.

Do not implement ECCnet until GS1 confirms eligibility, GTIN/net-content scope, consumer/mobile/search/cache rights, restrictions and commercial/API terms.

## Provider/account checkpoint

Use `PROVIDER_ACCOUNT_STATUS.md` for fast-changing state.

Current high-level status remains:

- Rakuten/Jamieson: feed approved + actual catalog available; production gates blocked;
- GS1 Canada ECCnet: inquiry acknowledged; substantive response pending;
- Well.ca / Bath Depot: pending unless newer evidence;
- Tru Earth / Giant Tiger: rejected; do not reapply now;
- CJ: TSC, Brother Canada, DAVIDsTEA pending; AOSOM older pending unless newer evidence;
- Awin active; Skip CA rejected due publisher type; never misrepresent publisher type;
- impact.com Marketplace declined; no blind duplicate;
- Lowvyn inquiry sent; await response.

## Immediate next safe work

Do not resend Rakuten or GS1 inquiries.

Next provider-neutral/offline engineering target:

**Define a lifecycle-bound current-price evidence-claim bridge into the existing evidence/conflict engine.**

Requirements:

- start from/re-evaluate the production-view path rather than trusting a detached `Offer`;
- require explicit merchant and commerce-channel scope;
- require explicit evidence authority/claim provenance rather than inventing authority from provider context;
- use the canonical/provider-scoped product evidence key contract;
- allow cross-source package quantity only when exact product identity matches;
- keep unit value behind `EvidenceBackedUnitValuePolicy` and conflict resolution;
- do not add Android networking or real provider credentials.

Do not treat the production view itself as rankable. Do not add affiliate tracking, checkout/payment, universal cart, subscriptions, remote AI or telemetry yet.

## Security

Never repeat, commit, log, screenshot, embed or request operational provider credentials. Production secrets remain outside source control.

## Verification discipline

For Android/shared-core changes:

```bash
cd android
./gradlew --no-daemon :shared-core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Never weaken tests to make a change pass.
