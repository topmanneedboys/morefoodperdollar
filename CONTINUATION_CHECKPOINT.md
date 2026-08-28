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
- Exact deterministic money/quantity/currency/promotion math only.
- AI may classify/explain but never invent authoritative facts.
- Affiliate/provider economics never affect ranking.
- No unauthorized scraping/private-endpoint reverse engineering.
- Technical/feed access != production authorization.
- Dataset recency != per-offer freshness.
- Currency/context != geography.
- Dataset namespace != snapshot.
- Snapshot lifecycle != namespace-wide disposition/deletion.
- Production view != current-price claim != acceptance-policy rankability != final Best Value eligibility.
- Conflict resolution and acceptance remain separate; never hide a contradictory claim merely because acceptance marks it display-only.
- Discounted/sale price does not automatically create promotion evidence.
- Shared core owns no hidden clock.
- No Android networking merely for provider experiments.

## Current milestone

5D — Authorized Real Shopping Data Provider Selection / validation.

Built-in Android Search remains fictional/sample evidence until a real provider path passes authorization, geography, price, freshness, lifecycle, namespace-disposition, acceptance, conflict and downstream unit-value/ranking gates.

## Verified production-hardening chain

- `5bb647a8485f257ec51b3eb0fe39b9c7caccb0a0` — provider-neutral price relationship validation.
- `a8e98b8ce333a612538841566972d6cab58dde88` — dataset recency separate from per-offer freshness.
- `6aed414bd5f89cf7ac6dfb739464c6f57f5abe78` — fail-closed production authorization.
- `7606ea941f80e3dc6b2ea362bc688c7434215195` — fail-closed offer geography; CAD is not Canadian scope proof.
- `f58b400533bdf9a0705fb8e88680e4b56ce9d94e` — staged production offer candidate; no rankability.
- `e546822a448e150674a2769d9899a856124b50fb` — authoritative corrected revocable snapshot lifecycle.
- `230b8ae4b6f674979d349320b8e5bd83713db810` — authoritative registry-backed namespace disposition/withdrawal.
- `ebb28a4a506232550b62d08370a9c8935d677603` — point-in-time production price view from raw evidence.
- `5d317810bd6ccf0933ff6432e6e68f88fb865493` — canonical-GTIN/provider-scoped product evidence keys.
- `267cd5aa81c4c3a03110c210ea9b4bfcb203f2ab` — product-key/unit-value integration checkpoint.
- `9a9b5f91948fe505a0ad6b598097bf9b8e50c680` — lifecycle-bound CURRENT_PRICE evidence claims.
- `97bfa3c353e48f15e26e9576cf06f4fa5e1687d1` — unified evidence acceptance policy, preserving legacy non-positive timestamp -> UNKNOWN behavior.
- `a1b15cc13df4912fb94893c8952f382f7404db1d` — lifecycle-bound production current-price acceptance.

The exact corrected acceptance refactor and current-price acceptance commits passed the full ValuePilot workflow including shared-core/app tests, lint, APK build, privacy verification, packaging and artifact upload.

## Lifecycle / disposition / view

Snapshot lifecycle is exact provider + dataset + snapshot + activation-profile state. No lifecycle record means inactive. Suspended/revoked/retired/pre-effective/expired snapshots cannot activate. Revoked/retired are terminal for the same snapshot/profile. Current authorization and original per-offer freshness are re-evaluated on every activation check.

Namespace disposition is separately `RETAINED`, `QUARANTINED`, `WITHDRAWAL_REQUIRED`, or `DELETED`. Profile/snapshot revocation never automatically means deletion. Only the registry's current withdrawal-required state may invoke exact namespace removal.

`ProductionOfferViewEvaluator` starts from raw import evidence and re-runs staged-candidate validation, snapshot binding, lifecycle registry evaluation and namespace disposition. A resulting view is point-in-time, records lifecycle/disposition revisions, exposes no rankability and must be re-evaluated later.

Provider reference/non-discounted price remains separate from historical `Offer.previous`. No member price, promotion, quantity or unit value is invented.

## Product identity

Production evidence key priority:

1. valid canonical GTIN -> shared `gtin:<canonical>`;
2. provider item ID -> provider-scoped key;
3. SKU -> provider-scoped key;
4. invalid-only GTIN -> no key.

Never repair invalid GTINs or use name/description/image/price as identity. Equivalent leading-zero GTIN representations join. Provider ID/SKU never cross providers. A product key grants no rights/freshness/quantity/rankability.

Integration tests prove canonical GTIN identity can join through `EvidenceBackedUnitValuePolicy`, while identical SKU text from different providers fails product identity matching.

## Current-price evidence claims

`ProductionCurrentPriceClaimEvaluator` starts from the raw provider import and re-runs the entire production-view path. Claim descriptors require explicit merchant/channel scope plus explicit evidence authority and auditable authority basis.

Supported current-price authorities are `MERCHANT_AUTHORITATIVE`, `PROOF_BACKED_DIRECT_OBSERVATION`, and `SOURCE_ASSERTED_METADATA`. Authority must agree with source claim kind; a source-asserted feed row cannot masquerade as proof-backed direct observation.

The lifecycle-bound wrapper preserves provider/source/dataset/snapshot/source-identity/channel/claim-kind/source-field/country/evaluation/lifecycle/disposition provenance. Generic `EvidenceClaim` is conflict-engine input only. Claim creation grants no rankability.

## Unified acceptance policy

`EvidenceAcceptanceEvaluator` now delegates both legacy `ShoppingEvidence` and shared-core production policy inputs through the same internal `EvidenceAcceptanceFacts` logic.

Important invariants:

- public `ShoppingEvidence` behavior is preserved;
- sample/fresh/aging/stale/unknown/future-dated, environment/channel, weak claim, availability and promotion behavior stay unified;
- legacy non-positive timestamps, including negative values, remain UNKNOWN freshness;
- common acceptance facts are internal to shared core and cannot be a public Android/UI authorization shortcut.

## Production current-price acceptance

`ProductionCurrentPriceAcceptanceEvaluator` re-runs `ProductionCurrentPriceClaimEvaluator` from raw provider inputs before applying the unified acceptance policy.

If claim creation is blocked, acceptance does not run. If claim creation passes, acceptance uses the real environment, acquisition channel, source claim kind, price observation timestamp and availability.

`acceptanceRankable` means only that the current-price evidence passes the shared acceptance policy at that instant. It does **not** mean final Best Value eligibility.

Verified cases:

- fresh/in-stock -> acceptance may be `RANKABLE`;
- out-of-stock -> claim remains factual evidence but acceptance is `DISPLAY_ONLY`;
- aging obeys caller policy;
- acceptance freshness may be stricter than production-view freshness;
- revoked lifecycle blocks the claim before acceptance runs.

Promotion is deliberately null in this bridge: a discounted/sale price field does not prove a promotion claim.

## Conflict + unit-value ordering

`EvidenceConflictPolicy` / `EvidenceFactResolver` remain the factual conflict system. `EvidenceBackedUnitValuePolicy` remains the unit-value system. Do not duplicate either.

Do not filter all display-only evidence before conflict resolution. A stronger contradictory factual claim may still be necessary to block or defeat a weaker accepted claim.

Future final current-price eligibility must require, at minimum:

1. current lifecycle/authorization/disposition passes;
2. current-price claim exists with exact scope/fingerprint;
3. unified acceptance decision is evaluated;
4. relevant CURRENT_PRICE facts are conflict-resolved at exact product/merchant/location/channel/currency scope;
5. unresolved same-scope conflict blocks Best Value;
6. the resolved value must match the lifecycle-bound candidate's exact current-price fingerprint;
7. the candidate must be acceptance-policy rankable;
8. unit value additionally requires exact identity, exact money/quantity fingerprints, compatible domains and strong package-quantity authority.

Never average, vote or guess through a factual conflict.

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
- Class ID blank all; Attribute 1 remains opaque/untyped.

Rakuten generic field semantics: Sale Price = discounted; Retail Price = non-discounted/reference. The two Sale > Retail rows are semantic conflicts and must fail closed.

Jamieson is **NOT production-authorized**. Still unresolved:

- cache/index/search/display/mobile rights;
- retention/deletion obligations;
- installed-software/DSA approval where applicable;
- trustworthy per-offer price observation freshness;
- Canadian offer geography beyond CAD/context;
- broad package quantity.

Current Jamieson rows cannot pass the production path because strong Canadian offer scope and trustworthy per-offer price observation time are absent.

Rakuten clarification was sent on 2026-08-28. No substantive reply after that clarification has been found. Do not resend unless a response creates a new gap.

## Jamieson × Open Food Facts

Historical raw-code 0/271 is invalid coverage evidence.

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

OFF remains supplemental only. Only 12/271 valid-GTIN identities are exact-count-ready. Never infer count from Rakuten text or loosen the parser.

## Package-content provider path

Health Canada LNHPD does not provide the needed GTIN-level package net count.

GS1 Canada ECCnet remains the strategic package-content candidate. The Data Recipient inquiry was sent 2026-08-28 and acknowledged, but no substantive eligibility/rights response has arrived.

Do not implement ECCnet until GS1 confirms eligibility, GTIN/net-content scope, consumer/mobile/search/cache rights, restrictions and commercial/API terms.

## Provider/account checkpoint

Use `PROVIDER_ACCOUNT_STATUS.md` for fast-changing state.

Current high-level status:

- Rakuten/Jamieson: feed approved + actual catalog available; production gates blocked;
- GS1 Canada ECCnet: inquiry acknowledged; substantive response pending;
- Well.ca / Bath Depot pending unless newer evidence;
- Tru Earth / Giant Tiger rejected; do not reapply now;
- CJ advertiser applications pending as previously recorded;
- Awin active; Skip CA rejected due publisher type; never misrepresent publisher type;
- impact.com Marketplace declined; no blind duplicate;
- Lowvyn inquiry sent; await response.

## Immediate next safe work

Do not resend Rakuten or GS1 inquiries.

Next provider-neutral/offline target:

**Design the final current-price conflict/acceptance eligibility boundary.**

Keep acceptance and conflict resolution distinct. Re-evaluate lifecycle/authorization/disposition at the decision instant. Do not pre-filter contradictory display-only claims. Require the resolved CURRENT_PRICE fact to match the lifecycle-bound candidate's exact fingerprint/scope. Unresolved same-scope conflict blocks Best Value.

Keep package quantity separately attributed and unit value behind `EvidenceBackedUnitValuePolicy`.

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
