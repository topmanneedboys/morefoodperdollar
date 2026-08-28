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
- Sources contribute claims; they never overwrite one shared truth row.
- Exact deterministic money/quantity/currency/promotion math only.
- AI may classify/explain but never invent authoritative facts.
- Affiliate/provider economics never affect ranking.
- No unauthorized scraping/private-endpoint reverse engineering.
- Technical/feed access != production authorization.
- Dataset recency != per-offer freshness.
- Currency/context != geography.
- Dataset namespace != snapshot.
- Snapshot lifecycle != namespace-wide disposition/deletion.
- Production view != current-price claim != acceptance rankability != conflict-resolved current-price eligibility != final Best Value.
- Acceptance and factual conflict resolution are separate; display-only but currently valid evidence may still contradict another claim.
- Discounted/sale price does not automatically create promotion evidence.
- Shared core owns no hidden clock.
- No Android networking merely for provider experiments.

## Current milestone

5D — Authorized Real Shopping Data Provider Selection / validation.

Built-in Android Search remains fictional/sample evidence until a real provider path passes authorization, geography, price, freshness, lifecycle, namespace-disposition, acceptance, conflict, quantity/unit-value and final ranking gates.

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
- `267cd5aa81c4c3a03110c210ea9b4bfcb203f2ab` — product-key/unit-value identity integration checkpoint.
- `9a9b5f91948fe505a0ad6b598097bf9b8e50c680` — lifecycle-bound CURRENT_PRICE evidence claims.
- `97bfa3c353e48f15e26e9576cf06f4fa5e1687d1` — unified evidence acceptance policy preserving legacy unknown-freshness behavior.
- `a1b15cc13df4912fb94893c8952f382f7404db1d` — lifecycle-bound production current-price acceptance.
- `c3dcfab539a2d2ef40fe9ce283533141ea9cd246` — verified bounded current-price acceptance + factual-conflict eligibility. Supersedes failed test-compilation attempt `e5b56e3078c07ff226ba80b4d069da9b3abbcf22`.

Exact `c3dcfab539a2d2ef40fe9ce283533141ea9cd246` passed the full ValuePilot workflow: browser checks, shared-core/app tests, lint, APK build, JVM summary, Android privacy verification, release packaging/checksums and artifact upload.

## Lifecycle / disposition / view

Lifecycle is exact provider + dataset + snapshot + activation profile. Missing/suspended/revoked/retired/pre-effective/expired lifecycle state blocks use. Revoked/retired are terminal for the same snapshot/profile. Current authorization and original price freshness are re-evaluated.

Namespace disposition remains separately `RETAINED`, `QUARANTINED`, `WITHDRAWAL_REQUIRED`, or `DELETED`. Snapshot revocation does not imply deletion. Only current withdrawal-required state may invoke exact namespace removal.

`ProductionOfferViewEvaluator` always starts from raw provider import evidence and re-runs staging, exact snapshot binding, lifecycle and namespace disposition. Its output is point-in-time and grants no rankability. Reference price is not historical `Offer.previous`; member price, promotion, quantity and unit value are not invented.

## Product identity

Production product key priority:

1. valid canonical GTIN -> shared `gtin:<canonical>`;
2. provider item ID -> provider-scoped key;
3. SKU -> provider-scoped key;
4. invalid-only GTIN -> no key.

Never repair invalid GTINs or use name/description/image/price as identity. Equivalent leading-zero GTIN representations join; provider-scoped IDs never cross providers. Product identity itself grants no rights/freshness/quantity/rankability.

## Current-price evidence / acceptance

`ProductionCurrentPriceClaimEvaluator` re-runs the raw production-view path. Claim descriptors require explicit merchant/channel scope plus explicit evidence authority and auditable basis. Authority must match source claim kind.

`ProductionCurrentPriceAcceptanceEvaluator` re-runs claim creation before the unified acceptance policy. Claim failure prevents acceptance evaluation. `acceptanceRankable` is only one evidence item's acceptance status, not final Best Value eligibility.

Promotion remains null in this bridge; a discounted price field does not prove a promotion.

## Verified current-price conflict eligibility

`ProductionCurrentPriceEligibility.kt` is verified at `c3dcfab539a2d2ef40fe9ce283533141ea9cd246`.

At one supplied decision instant it evaluates at most 128 raw current-price requests. Each request re-runs the complete production claim + acceptance path. Only currently valid production claims enter CURRENT_PRICE fact resolution.

Important: acceptance `DISPLAY_ONLY` does not remove a valid factual claim from conflict resolution. This preserves stronger contradictory evidence instead of hiding it.

The candidate progresses from this stage only if:

- candidate request exists;
- current lifecycle-bound claim exists;
- candidate's own acceptance is rankable;
- same-product relevant price claims are conflict-resolved by the existing `EvidenceFactResolver` at exact merchant/location/channel/currency scope;
- no same-namespace claim-ID mutation/collision exists;
- resolution is not an unresolved conflict;
- resolved fingerprint equals the candidate's exact current-price fingerprint.

Verified regression cases:

- single fresh/in-stock candidate progresses;
- out-of-stock candidate stays factual but cannot progress;
- equal-authority disagreement blocks unresolved;
- stronger merchant-authoritative display-only contradiction still participates and defeats a weaker candidate;
- revoked competitor contributes no production claim;
- different merchant scope coexists;
- same namespace/claim ID mutation fails closed;
- missing candidate fails closed;
- request set is bounded.

This is current-price-stage eligibility only. It creates no final Best Value, package quantity or unit value.

## Unit value

`EvidenceBackedUnitValuePolicy` is authoritative; do not duplicate it.

It requires:

- `RANKABLE` price disposition;
- CURRENT_PRICE or OBSERVED_PRICE price domain;
- PACKAGE_QUANTITY quantity domain;
- exact same stable product key;
- exact price fingerprint matching supplied `Offer` money;
- exact quantity fingerprint matching supplied normalized quantity;
- strong quantity authority.

The policy does not choose conflicting quantity claims. Quantity conflict resolution must happen before the selected quantity reaches it.

## Jamieson / Rakuten

Jamieson partnership + separate Product Feed approval + actual complete Rakuten catalog-file availability are proven.

Sanitized feed checkpoint: 273 rows; all documented 38-field shape/CAD/in-stock; 273 unique SKUs/Product IDs; GTIN present 271/273 and all supplied GTINs checksum-valid; Sale<Retail 48, Sale=Retail 223, Sale>Retail 2; Class ID blank all and Attribute 1 remains opaque.

Rakuten generic semantics: Sale Price = discounted; Retail Price = non-discounted/reference. Sale>Retail fails closed.

Jamieson remains **NOT production-authorized**. Unresolved: cache/index/search/display/mobile rights, retention/deletion, installed-software/DSA where applicable, trustworthy per-offer price observation time, Canadian geography beyond CAD/context, broad package quantity. Current Jamieson rows cannot pass production geography/freshness gates.

Rakuten clarification was sent 2026-08-28. No substantive reply after that clarification has been found. Do not resend unless a response creates a new gap.

## Jamieson x OFF / package content

Historical raw-code 0/271 is invalid coverage evidence.

Correct normalized result: 273 records, 271 valid GTINs, 102 OFF matches, 169 unmatched, 12 exact supplement-count candidates, 2 structured mass/volume-only, 0 quantity conflicts, 88 matched without usable quantity. OFF is supplemental only; never infer count from Rakuten text or loosen the parser.

Health Canada LNHPD does not provide the needed GTIN-level package net count.

GS1 Canada ECCnet remains the strategic package-content candidate. Inquiry sent and acknowledged 2026-08-28; no substantive eligibility/rights terms yet. Do not implement ECCnet until GS1 confirms eligibility, GTIN/net-content scope, consumer/mobile/search/cache rights, restrictions and commercial/API terms.

## Immediate next safe work

Do not resend Rakuten or GS1 inquiries.

Next provider-neutral/offline target:

**Bridge the verified conflict-resolved current-price candidate into existing `EvidenceBackedUnitValuePolicy` with separately attributed, conflict-resolved PACKAGE_QUANTITY evidence.**

Requirements:

- re-run `ProductionCurrentPriceEligibilityEvaluator` from raw price requests rather than trusting detached price evidence;
- keep quantity as a separate attributed claim/value;
- select quantity only after exact PACKAGE_QUANTITY factual conflict resolution;
- require exact stable product-key match before cross-source price + quantity combination;
- pass selected price claim, selected quantity claim, exact `Offer`, exact `NormalizedQuantity`, and rankable price disposition into `EvidenceBackedUnitValuePolicy`;
- do not duplicate acceptance, conflict, identity, fingerprint, authority or deterministic unit-value math;
- fail closed on unresolved quantity conflict, product mismatch, value mismatch, weak authority or non-rankable price;
- keep request/input paths bounded;
- add no Android networking or credentials.

Do not add production Rakuten integration, affiliate tracking, checkout/payment, universal cart, subscriptions, remote AI or telemetry yet.

## Security

Never repeat, commit, log, screenshot, embed or request operational provider credentials. Production secrets remain outside source control.

## Verification discipline

For Android/shared-core changes:

```bash
cd android
./gradlew --no-daemon :shared-core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Never weaken tests to make a change pass.
