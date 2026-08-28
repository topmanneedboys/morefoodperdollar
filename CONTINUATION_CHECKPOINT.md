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

Permanent rules: Product != Offer; claims stay separate; acceptance != factual conflict resolution; exact deterministic money/quantity/currency math; affiliate/provider economics never rank; technical access != production rights; dataset recency != offer freshness; currency != geography; namespace != snapshot; shared core owns no hidden clock; no Android networking for provider experiments.

## Current milestone

5D — Authorized Real Shopping Data Provider Selection / validation.

Built-in Android Search remains fictional/sample evidence until a real provider path passes production rights, geography, price semantics, per-offer freshness, lifecycle, namespace disposition, acceptance, factual conflict, quantity/unit-value, Best Value, presentation and UI boundaries.

## Verified production chain

- `5bb647a8485f257ec51b3eb0fe39b9c7caccb0a0` — current/reference price relationship.
- `a8e98b8ce333a612538841566972d6cab58dde88` — dataset recency != offer freshness.
- `6aed414bd5f89cf7ac6dfb739464c6f57f5abe78` — fail-closed production authorization.
- `7606ea941f80e3dc6b2ea362bc688c7434215195` — fail-closed geography; CAD != Canada proof.
- `f58b400533bdf9a0705fb8e88680e4b56ce9d94e` — staged production offer.
- `e546822a448e150674a2769d9899a856124b50fb` — exact-snapshot lifecycle.
- `230b8ae4b6f674979d349320b8e5bd83713db810` — namespace disposition.
- `ebb28a4a506232550b62d08370a9c8935d677603` — point-in-time production price view.
- `5d317810bd6ccf0933ff6432e6e68f88fb865493` — canonical-GTIN/provider-scoped product keys.
- `267cd5aa81c4c3a03110c210ea9b4bfcb203f2ab` — product identity/unit-value integration.
- `9a9b5f91948fe505a0ad6b598097bf9b8e50c680` — lifecycle-bound CURRENT_PRICE claims.
- `97bfa3c353e48f15e26e9576cf06f4fa5e1687d1` — unified evidence acceptance/freshness.
- `a1b15cc13df4912fb94893c8952f382f7404db1d` — current-price acceptance.
- `c3dcfab539a2d2ef40fe9ce283533141ea9cd246` — current-price factual-conflict eligibility.
- `204c8ae5e0089473f28b7cf6086b73e7a3516ec6` — price + conflict-resolved PACKAGE_QUANTITY -> exact unit-value policy.
- `ad5f91d4eef54e257a6660d291f14558653a1761` — bounded exact production Best Value ranking.
- `6517fad0ef21daa541d31d0d01d72a5f1980f5d5` — immutable point-in-time production Best Value presentation.
- `617dc128c9df31bbbd2b1835ac243be93e511d97` — exact Android/application production-search UI projector.
- `38942d556958b8abdf3b942bcbdc7bce77f1a0da` — synthetic end-to-end raw production evidence -> presentation -> Android projector regression.

Workflows for the last five production boundaries (`204c8ae...`, `ad5f91d4...`, `6517fad0...`, `617dc128...`, `38942d55...`) passed browser checks, shared-core/app tests, lint, APK build, JVM summary, Android privacy verification, packaging/checksums and artifact upload.

## Best Value + presentation + Android projector

Production value math stays exact: `Money`, `NormalizedQuantity`, `UnitRate`, `DeterministicValueMath`. Never route verified production evidence back through legacy `Double` ranking.

`ProductionBestValueRankingEvaluator` re-runs unit-value eligibility from raw evidence for every candidate. It compares only exact same `(currencyCode, RateUnit)`, lower exact rate wins, exact ties co-rank, singleton groups get no Best Value, and provider economics are absent.

`ProductionBestValuePresentationEvaluator` re-runs ranking at the supplied instant. It preserves exact values and auditable evidence links. It is point-in-time/non-durable; lifecycle revocation before presentation blocks stale results.

`ProductionSearchUiProjector` at `617dc128...` is additive and consumes only `ProductionBestValuePresentationSnapshot`. It formats exact integer domain values through `BigDecimal`, preserves comparison groups and blocked state, and retains exact ranked/blocked candidate lookup maps. Tests include values beyond IEEE-754 exact integer range to guard against `Double` conversion.

`ProductionSearchPipelineIntegrationTest` at `38942d55...` verifies the entire synthetic chain from raw provider imports to Android projection. It proves two CAD/item products compare at exact 0.08 vs 0.09 CAD/item, a CAD/kg product remains a separate singleton group, an out-of-stock product remains blocked/reference-only, and exact evidence lookup survives projection. No real provider data, networking or legacy parser/ranking participates.

## Android / Universal Search decision

Do NOT route production evidence through:

- `UniversalSearchController.receive()`
- `DeterministicProductParser`
- `ValueEngine.analyze()`
- `RankingModePolicy`
- `DeterministicRankingEngine`
- `ValueEngine.rank()`

Current `MainActivity` still uses `UniversalSearchController` + `LocalSampleProductSearchProvider` and one legacy `searchResultsContainer`. That sample/capture path may parse raw text, infer promotion arithmetic and use `ValueItem`/`Double`. Do not merge production rows into that container yet.

Important projector hardening: current UI-ready `merchantSummary` is built from stable `merchantKey`, optional `locationKey` and `commerceChannelKey`. These are audit/scope identifiers, not guaranteed display names. Before consumer rendering, remove internal keys from UI-ready strings unless explicit display metadata exists. Keep exact keys in the opaque production lookup.

## Rakuten / Jamieson

Jamieson partnership + separate Product Feed approval + actual complete Product Catalog feed access are proven. Latest Rakuten support message reconfirmed feed approval/access. A later ValuePilot clarification asks about Android display/search, cache/index, retention/deletion and Mobile App/DSA approval; no substantive reply yet. Do not resend.

Official Rakuten guidance supports Product Catalog/database/product-comparison use generally and recognizes Mobile App as a publisher channel. Mobile App and Downloadable Software Application details are separate. DSA software, if applicable, needs Network Quality approval then advertiser approval. Public docs do not establish that ValuePilot Android is a DSA or settle app-specific Product Catalog display/cache/retention rights.

Product Catalog file/update timestamps remain dataset/source recency, not trustworthy per-product price observation timestamps.

Jamieson remains NOT production-authorized: unresolved Android/mobile rights, cache/index/retention/deletion, applicable Mobile App/DSA path, strong Canadian offer geography, trustworthy per-offer price freshness, and broad package quantity.

## Package content / GS1 / open data

Historical raw-code OFF 0/271 is invalid.

Valid normalized Jamieson x OFF remains: 273 rows, 271 valid GTINs, 102 matches, 169 unmatched, 12 exact supplement counts, 2 structured mass/volume only, 0 quantity conflicts, 88 matched without usable quantity. OFF is supplemental only.

Health Canada LNHPD does not solve GTIN-level package count.

GS1 Canada ECCnet remains strategic package-content target. Inquiry sent + acknowledged 2026-08-28; no substantive eligibility/rights response yet. Do not implement until GTIN/net-content scope, consumer/mobile/search/cache rights, restrictions and commercial/API terms are confirmed.

## Immediate next safe work

Do not resend Rakuten or GS1 inquiries.

Next bounded offline target:

**Harden `ProductionSearchUiProjector` so internal merchant/location scope keys cannot leak into consumer-facing UI-ready strings.**

Requirements:

- keep exact merchant/location/channel keys only in the production presentation lookup unless explicit display metadata is supplied;
- remove/replace current `merchantSummary` consumer string without inferring names from IDs, URLs, provider names or source names;
- preserve comparison groups, exact formatting, co-best ties, singleton behavior, blocked state and exact lookup;
- add regression tests proving internal scope keys do not appear in UI-ready strings while remaining intact in the exact lookup;
- no provider networking, real Rakuten/GS1 data, Android permissions, legacy parsing/ranking, telemetry or remote AI.

After this is verified, keep future production UI additive. Do not wire real providers until rights/geography/freshness/package-content gates pass.

## Security

Never repeat, commit, log, screenshot, embed or request operational provider credentials. Production secrets remain outside source control.

## Verification discipline

For Android/shared-core changes:

```bash
cd android
./gradlew --no-daemon :shared-core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Never weaken tests to make a change pass.
