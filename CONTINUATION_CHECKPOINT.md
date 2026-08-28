# ValuePilot Continuation Checkpoint

Updated: 2026-08-28

Branch: `work/valuepilot-android-milestone`

Purpose: compact durable recovery point. Newer repository/account evidence overrides this file.

## Startup order

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

Rules: Product != Offer; claims stay separate; acceptance != factual conflict resolution; exact deterministic money/quantity math; affiliate/provider economics never rank; feed access != production rights; dataset recency != offer freshness; currency != geography; shared core owns no hidden clock; no provider credentials in source; no Android networking for provider experiments.

## Current milestone

5D — Authorized Real Shopping Data Provider Selection / validation.

Built-in Android Search remains fictional/sample evidence until a real provider path passes production rights, geography, price semantics, per-offer freshness, lifecycle/disposition, conflict/acceptance, quantity/unit-value, Best Value, presentation and UI boundaries.

## Verified production chain

Core boundaries remain verified through:

- `204c8ae5e0089473f28b7cf6086b73e7a3516ec6` — production price + conflict-resolved package quantity -> exact unit value.
- `ad5f91d4eef54e257a6660d291f14558653a1761` — bounded exact Best Value ranking.
- `6517fad0ef21daa541d31d0d01d72a5f1980f5d5` — immutable point-in-time Best Value presentation.
- `617dc128c9df31bbbd2b1835ac243be93e511d97` — Android/application production-search projector.
- `38942d556958b8abdf3b942bcbdc7bce77f1a0da` — synthetic full raw-evidence -> presentation -> Android projection regression.
- `6e0d3fc0f6c7c6a61926a8c0e85d2b2e12629022` — merchant/location/channel scope identifiers removed from UI-ready text.
- `1a53d34bfbb2fdf65b7383c489f75b428defe06e` — raw product/image URLs removed from catalog-only UI state.
- `81620dfaeb15aeab48c2e438bd225004190c0a09` — blocker enum diagnostics removed from consumer blocked UI state.
- `7046b725291cc061f1e153e05c4a25539838a28e` — fail-closed generation-based refresh ordering for future asynchronous production UI.

Recent workflow verification passed browser checks, shared-core/app tests, lint, APK build, JVM summary, Android privacy verification, packaging/checksums and artifact upload.

## Production Android boundary

`ProductionSearchUiProjector` accepts only `ProductionBestValuePresentationSnapshot` and never uses the legacy parser/ranking stack. Exact money/quantity/rates are formatted through `BigDecimal`; no `Double` conversion.

UI-ready state deliberately excludes merchant/location/channel keys, raw provider URLs and blocker enum codes. Exact facts remain in opaque candidate lookup maps for future authorized actions/diagnostics/provenance.

`ProductionSearchRefreshGate` owns no clock/I/O. Caller-supplied monotonically increasing generations prevent stale/out-of-order snapshots from replacing newer UI state. Same-generation different payloads fail closed; newer clear prevents late older results from repopulating.

Current `MainActivity` still uses `UniversalSearchController` + `LocalSampleProductSearchProvider` and one legacy `searchResultsContainer`. Do **not** route production evidence through `UniversalSearchController.receive()`, `DeterministicProductParser`, `ValueEngine.analyze()`, `RankingModePolicy`, `DeterministicRankingEngine` or `ValueEngine.rank()`. Do not mix production rows into the legacy container.

## Rakuten / Jamieson

Jamieson partnership + separate Product Feed approval + actual complete Product Catalog feed access are proven. Feed remains outside source control.

Jamieson feed: 273 rows; 271 valid supplied GTINs; Sale<Retail 48, Sale=Retail 223, Sale>Retail 2. Rakuten generic semantics: Sale reflects discounts, Retail does not; Sale>Retail is a fail-closed semantic conflict.

Latest account-specific support message reconfirmed feed approval/access. ValuePilot's Android display/search, cache/index, retention/deletion and mobile-channel clarification remains unanswered; do not resend unless needed after a response.

Public Rakuten terms/policies rechecked 2026-08-28:

- Publisher Membership Agreement last updated July 13, 2026 defines `Site` to include an application and allows mobile-app promotion subject to advertiser terms/Network Policies.
- Product Catalog is designed for database/search/comparison use, but its implementation guide still describes advertiser feed approval as use on a website/blog. Catalog-only Android rights therefore remain ambiguous.
- July 13, 2026 Network Policies explicitly include installable mobile applications in DSA controls. A DSA with Rakuten advertiser links requires Rakuten approval/compliance testing and then participating advertiser approval. Keep link-enabled Android blocked until those gates plus tracking/privacy readiness pass.
- Full Publisher Agreement termination requires immediate cessation/removal of network-provided links/content/materials and ends relevant licenses. Narrow advertiser-feed-revocation retention/deletion remains unresolved.

Product Catalog file/update timestamps remain dataset recency, not per-product price freshness.

Jamieson remains **NOT production-authorized**: unresolved catalog-only Android rights/cache/index/advertiser-revocation retention, strong Canadian offer geography, trustworthy per-offer freshness and broad package quantity. Link-enabled Android additionally requires DSA/Network Quality + advertiser distribution approval + tracking/privacy readiness.

## Package quantity / GS1

Valid normalized Jamieson x Open Food Facts remains: 273 products, 271 valid GTINs, 102 matches, 169 unmatched, 12 exact supplement-count candidates, 2 mass/volume-only, 0 conflicts, 88 matched without usable quantity. OFF is supplemental only. Health Canada LNHPD does not solve GTIN-level package count.

GS1 Canada ECCnet remains strategic. Inquiry sent and acknowledged 2026-08-28; no substantive response yet. Do not implement until eligibility, GTIN/net-content scope, consumer/mobile/search/cache rights, restrictions and commercial/API terms are confirmed.

## Immediate next safe work

Do not resend Rakuten or GS1 inquiries.

Next bounded offline target: **dedicated production Search surface boundary**, still disabled and separate from the sample container.

Requirements:

- consume production results only through `ProductionSearchRefreshGate` / `ProductionSearchUiProjection`;
- render only `ProductionSearchUiState`;
- keep exact lookup maps non-rendered;
- never insert production rows into legacy `searchResultsContainer`;
- no raw URLs, internal scope identifiers or blocker codes;
- no provider networking, real provider data, Android permission additions, legacy parsing/ranking, telemetry or remote AI;
- do not activate the production surface until provider rights/geography/freshness/package-content gates pass.

## Security

Never repeat, commit, log, screenshot, embed or request operational provider credentials. Production secrets remain outside source control.
