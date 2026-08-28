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

`authorized/open/user evidence -> provider adapters -> provenance-preserving claims/import records -> deterministic validation/normalization -> Product identity + Offers -> bounded retrieval -> deterministic ranking -> immutable presentation -> replaceable UI`

Permanent rules:

- Product != Offer.
- Sources contribute separate claims; never overwrite one shared truth row.
- Stronger same-scope evidence may defeat weaker evidence; unresolved equal-strength conflict blocks Best Value.
- Acceptance and factual conflict resolution are separate; currently valid display-only evidence may still contradict another claim.
- Exact deterministic money/quantity/currency/promotion math only.
- AI may classify/explain but never invent authoritative facts.
- Affiliate/provider economics never affect ranking.
- No unauthorized scraping/private-endpoint reverse engineering.
- Technical/feed access != production authorization.
- Dataset recency != per-offer freshness; currency/context != geography.
- Namespace != snapshot; snapshot lifecycle != namespace disposition/deletion.
- View != price claim != acceptance rankability != conflict-resolved price eligibility != unit-value eligibility != Best Value ranking != presentation authorization.
- Shared core owns no hidden clock.
- No Android networking merely for provider experiments.

## Current milestone

5D — Authorized Real Shopping Data Provider Selection / validation.

Built-in Android Search remains fictional/sample evidence until a real provider path passes production rights, geography, price semantics, per-offer freshness, lifecycle, namespace disposition, acceptance, conflict, quantity/unit-value, ranking and presentation gates.

## Verified production chain

- `5bb647a8485f257ec51b3eb0fe39b9c7caccb0a0` — provider-neutral current/reference price relationship.
- `a8e98b8ce333a612538841566972d6cab58dde88` — dataset recency separate from offer freshness.
- `6aed414bd5f89cf7ac6dfb739464c6f57f5abe78` — fail-closed production authorization.
- `7606ea941f80e3dc6b2ea362bc688c7434215195` — fail-closed geography; CAD is not Canada proof.
- `f58b400533bdf9a0705fb8e88680e4b56ce9d94e` — staged production offer candidate.
- `e546822a448e150674a2769d9899a856124b50fb` — exact-snapshot lifecycle.
- `230b8ae4b6f674979d349320b8e5bd83713db810` — namespace disposition/withdrawal.
- `ebb28a4a506232550b62d08370a9c8935d677603` — point-in-time raw-evidence production price view.
- `5d317810bd6ccf0933ff6432e6e68f88fb865493` — canonical-GTIN/provider-scoped product keys.
- `267cd5aa81c4c3a03110c210ea9b4bfcb203f2ab` — exact product identity/unit-value integration.
- `9a9b5f91948fe505a0ad6b598097bf9b8e50c680` — lifecycle-bound CURRENT_PRICE claims.
- `97bfa3c353e48f15e26e9576cf06f4fa5e1687d1` — unified evidence acceptance/freshness.
- `a1b15cc13df4912fb94893c8952f382f7404db1d` — lifecycle-bound current-price acceptance.
- `c3dcfab539a2d2ef40fe9ce283533141ea9cd246` — verified bounded current-price acceptance + factual-conflict eligibility; supersedes failed `e5b56e...` compile attempt.
- `204c8ae5e0089473f28b7cf6086b73e7a3516ec6` — verified price + conflict-resolved package quantity -> exact unit-value policy; supersedes unverified `96883bea...` fixture mismatch.
- `ad5f91d4eef54e257a6660d291f14558653a1761` — verified bounded exact production Best Value ranking.
- `6517fad0ef21daa541d31d0d01d72a5f1980f5d5` — verified immutable point-in-time production Best Value presentation.

The exact workflows for `204c8ae...`, `ad5f91d4...` and `6517fad0...` passed browser checks, shared-core/app tests, lint, APK build, JVM summary, Android privacy verification, release packaging/checksums and artifact upload.

## Current-price + unit-value facts

`ProductionCurrentPriceEligibilityEvaluator` evaluates at most 128 raw requests at one supplied instant and re-runs production claim + acceptance for every request. Display-only currently valid facts remain in conflict resolution. Candidate progression requires current claim, own rankable acceptance, no same-namespace claim-ID mutation, resolved exact-scope CURRENT_PRICE fact and exact fingerprint equality.

`ProductionUnitValueEligibilityEvaluator` re-runs that raw price path, filters PACKAGE_QUANTITY to the exact product key, detects quantity claim mutation, resolves quantity with `EvidenceFactResolver`, then delegates supporting materialized quantities to `EvidenceBackedUnitValuePolicy`. It does not duplicate authority or unit math.

Exact arithmetic remains `Money`, `NormalizedQuantity`, `UnitRate`, `DeterministicValueMath`. Never convert verified production evidence back into `Double` ranking.

The historical unit-value test failure was fixture-only: raw UPC `036000291452` canonicalizes to `0036000291452`. Tests now derive identity with `ProductionProductEvidenceKeyResolver`.

## Verified Best Value ranking

`ProductionBestValueRanking.kt` at `ad5f91d4...`:

- raw-evidence re-evaluation for every candidate;
- <=128 price requests, <=128 quantity candidates, <=128 ranking candidates;
- unique stable non-economic candidate IDs and unique price-request references;
- blocked candidates get no rank but remain explainable;
- comparison only inside exact same currency + exact same `RateUnit`;
- lower exact `currencyMicrosPerUnit` wins;
- exact ties share dense value rank;
- candidate ID only stabilizes display order for equal value;
- singleton group gets no Best Value claim;
- groups with >=2 mark every exact rank-1 tie as co-Best Value;
- no commission/EPC/payout/sponsorship/provider-preference input.

## Verified immutable production presentation

`ProductionBestValuePresentation.kt` at `6517fad0...`:

- re-runs `ProductionBestValueRankingEvaluator` from raw evidence at the supplied instant; detached ranking results are not authority;
- exact `Money`, `NormalizedQuantity` and `UnitRate` are preserved;
- row carries product key/name, provider/source, merchant/location/channel, country, exact current/reference price, exact quantity/rate, availability, freshness, price timestamp, value rank/order, Best Value flag and optional URLs;
- audit link retains price provider/source/dataset/snapshot/claim, quantity dataset/claim/evidence ID and lifecycle/disposition revisions;
- blocked candidates are separate explanation objects, never ranked rows;
- singleton groups cannot present a Best Value badge;
- snapshot is point-in-time/non-durable; a later display must re-evaluate;
- lifecycle REVOKED-before-presentation regression proves stale ranking/presentation is not trusted;
- no formatting, I/O, Android dependency, hidden clock, AI/promotion inference or provider economics.

## Android / Universal Search decision

Do **not** route production evidence through:

- `DeterministicProductParser`
- `ValueEngine.analyze()`
- `RankingModePolicy`
- `DeterministicRankingEngine`
- `ValueEngine.rank()`
- current legacy `UniversalSearchController.receive()` ranking path.

Those paths use `ValueItem`, `Double`, raw-text parsing, heuristic quantities/promotion arithmetic and optional local AI. They remain valid for current sample/capture responsibilities but are not the production evidence chain.

The safe next Android boundary is additive: pure application projection from `ProductionBestValuePresentationSnapshot` into immutable UI-ready state, without parsing or ranking again.

## Jamieson / Rakuten

Rakuten publisher + Product Catalog technical access active. Jamieson PARTNERED + separate Product Feed approved + complete actual feed available.

Sanitized feed: 273 rows; all documented 38-field shape/CAD/in-stock; 273 unique SKU/Product IDs; GTIN 271/273 and all supplied valid; Sale<Retail 48, Sale=Retail 223, Sale>Retail 2; Class ID blank all; Attribute1 opaque.

Generic Rakuten semantics: Sale Price discounted/current; Retail Price non-discounted/reference. Sale>Retail is a semantic conflict and never repaired/swapped.

Jamieson remains NOT production-authorized. Missing/unclear: cache/index/search/display/mobile rights, retention/deletion, installed-software/DSA where applicable, trustworthy per-offer price observation time, strong Canadian geography, broad package quantity. Current feed rows cannot pass production geography/freshness gates.

Rakuten clarification already sent 2026-08-28; no substantive reply. Do not resend unless a new response creates a new gap.

## Package content / open data

Historical raw-code OFF 0/271 is invalid.

Valid normalized Jamieson x OFF: 273 records, 271 valid GTINs, 102 matches, 169 unmatched, 12 exact supplement counts, 2 structured mass/volume only, 0 quantity conflicts, 88 matched without usable quantity. OFF remains supplemental; never infer count from Rakuten text/attributes/images or loosen parser.

Health Canada LNHPD does not solve GTIN-level package net count.

GS1 Canada ECCnet is strategic package-content target. Inquiry sent + acknowledged 2026-08-28; no substantive eligibility/rights response. Do not implement until eligibility, GTIN/net-content scope, consumer/mobile/search/cache rights, restrictions and commercial/API terms are confirmed.

## Immediate next safe work

Do not resend Rakuten or GS1 inquiries.

Next offline/provider-neutral target:

**Create an additive Android/application production-search projector over `ProductionBestValuePresentationSnapshot`.**

Requirements:

- no real provider networking/data yet;
- no new Android INTERNET/ACCESS_NETWORK_STATE permission;
- no parsing, acceptance, conflict resolution, quantity authority, unit math or ranking in the projector;
- no `Double` value calculation;
- preserve comparison groups so currencies/rate units cannot look globally comparable;
- exact ties remain co-Best Value; singleton groups get no Best Value badge;
- blocked candidates remain explanation/reference state;
- retain stable candidate/evidence lookup for later product/provenance actions;
- locale-neutral exact formatting may occur only in the application presentation adapter;
- synthetic tests only until provider rights/geography/freshness/package-content gates pass.

Do not add production Rakuten/ECCnet integration, affiliate tracking, checkout/payment, universal cart, subscriptions, remote AI or telemetry yet.

## Security

Never repeat, commit, log, screenshot, embed or request operational provider credentials. Production secrets remain outside source control.

## Verification discipline

For Android/shared-core changes:

```bash
cd android
./gradlew --no-daemon :shared-core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Never weaken tests to make a change pass.
