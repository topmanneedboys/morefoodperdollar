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

Rules: Product != Offer; claims stay separate; acceptance != conflict resolution; exact deterministic money/quantity math; affiliate/provider economics never rank; feed access != production rights; dataset recency != offer freshness; currency != geography; shared core owns no hidden clock; no provider credentials in source; no Android networking for provider experiments.

## Current milestone

5D — Authorized Real Shopping Data Provider Selection / validation.

Visible Android Search remains fictional/sample evidence. The production Search path is now architecturally present but hidden/unwired until a real provider passes rights, geography, freshness and package-content gates.

## Latest verified production-search boundaries

Core production ranking/presentation remains verified through:

- `204c8ae5e0089473f28b7cf6086b73e7a3516ec6` — exact production unit value.
- `ad5f91d4eef54e257a6660d291f14558653a1761` — bounded exact Best Value ranking.
- `6517fad0ef21daa541d31d0d01d72a5f1980f5d5` — point-in-time Best Value presentation.
- `38942d556958b8abdf3b942bcbdc7bce77f1a0da` — synthetic raw-evidence -> presentation -> Android projection regression.

Android production-search hardening:

- `6e0d3fc0f6c7c6a61926a8c0e85d2b2e12629022` — internal merchant/location/channel keys excluded from UI-ready text.
- `1a53d34bfbb2fdf65b7383c489f75b428defe06e` — raw product/image URLs excluded from catalog-only UI state.
- `81620dfaeb15aeab48c2e438bd225004190c0a09` — blocker enum diagnostics excluded from consumer blocked UI state.
- `7046b725291cc061f1e153e05c4a25539838a28e` — generation ordering rejects stale/out-of-order refreshes.
- `3cba4d96eb91e961772dee3c60d86371e61b1137` — projector independently caps total rendered candidates at 128.
- `8520af97695fb346710649a80c6d95d422da3ee8` — narrow surface host/renderer-state separation.
- `e2767b31512d8d5b6b9cd7555d452ae3ec0017e2` — inactive Android production renderer.
- `c4661993f9c476e3a44c7d1dc504948e50767d48` — physically separate hidden production Search view in `activity_shell.xml`.
- `932838f8d5eedda5d17c7a9dadf76ae38f8bcc9f` — surface display submissions re-run shared-core presentation evaluation from raw inputs/current registries; no public detached-snapshot apply path.

The exact workflows for these recent code boundaries passed browser checks, shared-core/app tests, lint, APK build, JVM summary, Android privacy verification, packaging/checksums and artifact upload.

A temporary empty `__noop__` connector artifact created during the hidden-layout ref update was removed immediately. Current tree has no `__noop__`; net verified renderer -> hidden-layout change is only the intended seven-line layout insertion.

## Production Search boundary now

`ProductionSearchUiProjector` performs formatting only from exact presentation values; it does not use legacy `ValueItem`/`Double` ranking.

UI-ready state excludes merchant/location/channel internal IDs, raw product/image URLs and blocker enum codes. Exact facts remain in opaque lookup maps for future authorized actions/diagnostics/provenance.

`ProductionSearchRefreshGate` uses caller-supplied monotonically increasing generations, owns no clock/I/O and blocks stale/out-of-order replacement.

`ProductionSearchSurfaceHost.evaluateAndApply(...)` takes raw bounded production inputs + current lifecycle/disposition registries and re-runs `ProductionBestValuePresentationEvaluator` at the supplied decision instant before projection/rendering. It exposes no public method accepting `ProductionBestValuePresentationSnapshot` as authority.

`ProductionSearchSurfaceView` is physically separate from legacy `searchResultsContainer`, starts `GONE`, saves no state, has no click/link/provider behavior and renders only sanitized `ProductionSearchUiState`.

**MainActivity does not wire or drive the production surface.** Keep it that way until external gates pass.

Do not route production evidence through `UniversalSearchController.receive()`, `DeterministicProductParser`, `ValueEngine.analyze()`, `RankingModePolicy`, `DeterministicRankingEngine` or `ValueEngine.rank()`.

## Rakuten / Jamieson

Jamieson partnership + separate Product Feed approval + actual complete Product Catalog feed access are proven. Feed remains outside source control.

Feed checkpoint: 273 rows; 271 valid supplied GTINs; Sale<Retail 48, Sale=Retail 223, Sale>Retail 2. Rakuten generic semantics: Sale reflects discounts, Retail does not; Sale>Retail is a fail-closed semantic conflict.

Latest Rakuten support message only reconfirmed feed approval/access. The later ValuePilot Android display/search, cache/index, retention/deletion and channel-approval clarification remains unanswered. Do not resend unless a response is incomplete.

Public July 13, 2026 Rakuten terms/policies establish mobile apps can be publisher Sites subject to advertiser/network terms, and installable mobile apps are within DSA controls for network-link use. Link-enabled Android therefore remains blocked behind Rakuten DSA/Network Quality approval, participating-advertiser distribution approval and tracking/privacy readiness. Public terms still do not settle account-specific catalog-only Android display/cache/index or narrower advertiser-feed-revocation retention rights.

Product Catalog file/update timestamps remain dataset recency, not per-product price freshness.

Jamieson remains **NOT production-authorized**: unresolved catalog-only Android rights/cache/index/retention, strong Canadian offer geography, trustworthy per-offer price freshness and broad package quantity. Link-enabled Android has the additional DSA/advertiser/tracking gates.

## Package quantity / GS1

Valid normalized Jamieson x Open Food Facts remains: 273 products, 271 valid GTINs, 102 matches, 169 unmatched, 12 exact supplement counts, 2 mass/volume-only, 0 conflicts, 88 matched without usable quantity. OFF is supplemental only. Health Canada LNHPD does not solve GTIN-level package count.

GS1 Canada ECCnet remains strategic. Inquiry sent + acknowledged 2026-08-28; no substantive eligibility/rights response yet. Do not implement until GTIN/net-content scope, consumer/mobile/search/cache rights, restrictions and commercial/API terms are confirmed.

## Immediate next work

**Do not keep extending Android production plumbing without new provider evidence.** The bounded offline pre-provider Search architecture is sufficiently hardened for milestone 5D.

Next decision triggers:

1. Rakuten reply arrives -> record written evidence first; update each production capability gate independently; only then implement the provider-specific use that is actually authorized.
2. GS1 Canada reply arrives -> validate Data Recipient eligibility, Jamieson/GTIN/net-content coverage, consumer/mobile/cache/search rights, retention/attribution and API/commercial terms; only then design an ECCnet adapter.
3. Until either trigger: keep production Search hidden/unwired and preserve current Android privacy boundary.

Do not add real Rakuten/GS1 data, Android INTERNET/ACCESS_NETWORK_STATE, affiliate links, checkout/payment, telemetry, remote AI or provider credentials.

## Security

Never repeat, commit, log, screenshot, embed or request operational provider credentials. Production secrets remain outside source control.
