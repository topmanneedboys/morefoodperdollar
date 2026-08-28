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
- Shared core owns no hidden clock.
- No Android networking merely for provider experiments.

## Current milestone

5D — Authorized Real Shopping Data Provider Selection / validation.

Built-in Android Search remains fictional/sample evidence until a real provider path passes authorization, geography, price, freshness, lifecycle and downstream evidence gates.

## Verified production-hardening chain

- `5bb647a8485f257ec51b3eb0fe39b9c7caccb0a0` — provider-neutral discounted/reference price relationship validation.
- `a8e98b8ce333a612538841566972d6cab58dde88` — dataset recency separate from per-offer freshness.
- `6aed414bd5f89cf7ac6dfb739464c6f57f5abe78` — fail-closed production authorization profiles/gates.
- `7606ea941f80e3dc6b2ea362bc688c7434215195` — fail-closed provider offer-country validation; CAD is not Canadian scope proof.
- `f58b400533bdf9a0705fb8e88680e4b56ce9d94e` — staged production offer candidate; still not canonical `Offer` and no rankability.
- `e546822a448e150674a2769d9899a856124b50fb` — authoritative corrected revocable production dataset lifecycle.

The exact lifecycle commit passed browser checks, shared-core/app tests, lint, APK build, JVM summary, Android privacy-boundary verification, release packaging/checksums and artifact upload.

## Revocable production dataset lifecycle

`ProductionDatasetLifecycle.kt` provides:

- exact `ProductionDatasetSnapshotRef` = provider + dataset namespace + opaque adapter-supplied snapshot id;
- snapshot binding for staged candidates;
- profile-scoped lifecycle states `ACTIVE`, `SUSPENDED`, `REVOKED`, `RETIRED`;
- monotonic caller-supplied lifecycle revisions;
- effective/expiry windows;
- terminal same-snapshot/profile revocation/retirement;
- point-in-time activation that rechecks current authorization and current offer freshness.

Critical invariants:

- dataset namespace != dataset snapshot;
- staged candidate != active candidate;
- no lifecycle record = inactive;
- suspended/revoked/retired/not-yet-effective/expired = inactive;
- current authorization is re-evaluated on every activation check;
- authorization from another provider/dataset cannot be reused;
- old staged `FRESH` evidence becomes inactive when its original price observation becomes stale under the current caller-supplied freshness policy;
- weaker custom profile with the same profile ID cannot bypass the base consumer-mobile-catalog requirements;
- revoking one dataset does not disable another;
- activation creates no canonical `Offer` and grants no durable rankability/display permission.

## Withdrawal/deletion distinction

`SourceIsolatedEvidenceIndex.removeNamespace(namespaceId)` already removes one evidence namespace without mutating others.

Do not automatically delete a namespace when one activation profile becomes revoked/retired. Profile lifecycle and dataset-level retention/deletion are distinct.

Permanent rule:

**revocation blocks use immediately; physical deletion requires a separate explicit dataset-level withdrawal/retention decision.**

This avoids deleting data when one use profile is revoked but another permitted use or retention obligation remains.

## Canonical Offer / unit value

`EvidenceBackedUnitValuePolicy` already requires rankable price evidence, correct price/quantity domains, exact same stable product key, exact money/quantity fingerprints and strong quantity authority.

Do not duplicate it.

Do not yet materialize production canonical `Offer` objects from active candidates. `Offer` itself has no lifecycle/provenance handle, so lifecycle-to-Offer coupling must be designed before production Offer creation/ranking can be enabled.

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

Rakuten generic field semantics are resolved:

- Sale Price = discounted field;
- Retail Price = non-discounted/reference field.

The two Sale > Retail rows are semantic-invalid and must fail closed; never swap/repair them.

Jamieson is **NOT production-authorized**. Still unresolved:

- cache/index/search/display/mobile rights;
- retention/deletion obligations;
- installed-software/DSA approvals where applicable;
- trustworthy per-offer price observation freshness;
- Canadian offer geography beyond CAD/context;
- broad package quantity.

Current Jamieson rows cannot pass the staged/lifecycle production path because strong Canadian offer scope and trustworthy per-offer price observation time are absent.

Rakuten clarification was already sent on 2026-08-28. Do not resend unless the response creates a new gap.

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

GS1 Canada ECCnet remains the strategic GTIN/package-content candidate. The Data Recipient eligibility/rights inquiry was already sent on 2026-08-28.

Do not implement ECCnet until GS1 confirms eligibility, GTIN/net-content scope, consumer/mobile/search/cache rights, restrictions and commercial/API terms.

## Provider/account checkpoint

Use `PROVIDER_ACCOUNT_STATUS.md` for fast-changing account state.

Current high-level status:

- Rakuten/Jamieson: feed approved + actual catalog available; production gates blocked;
- GS1 Canada ECCnet: inquiry sent; await response;
- Well.ca / Bath Depot: pending unless newer evidence;
- Tru Earth / Giant Tiger: rejected; do not reapply now;
- CJ: TSC, Brother Canada, DAVIDsTEA pending; AOSOM older pending unless newer evidence;
- Awin active; Skip CA rejected due publisher type; never misrepresent publisher type;
- impact.com Marketplace declined; no blind duplicate;
- Lowvyn inquiry sent; await response.

## Immediate next safe work

Do not resend Rakuten or GS1 inquiries.

Next provider-neutral/offline engineering target:

**Define a dataset-level withdrawal/quarantine decision separate from profile-level activation lifecycle.**

It must allow explicit namespace removal only when the dataset-level retention/withdrawal state requires it, while proving that revoking one profile does not automatically delete data needed or permitted by another profile.

After that, design lifecycle-coupled canonical Offer materialization so active data cannot be detached from current revocation/freshness state before ranking/display.

Keep package quantity separately attributed and unit value gated by exact compatible quantity evidence.

Do not add Android networking, production credentials, affiliate tracking, checkout/payment, universal cart, subscriptions, remote AI or telemetry yet.

## Security

Never repeat, commit, log, screenshot, embed or request operational provider credentials. Production secrets remain outside source control.

## Verification discipline

For Android/shared-core changes:

```bash
cd android
./gradlew --no-daemon :shared-core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Never weaken tests to make a change pass.
