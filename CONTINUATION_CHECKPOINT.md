# ValuePilot Continuation Checkpoint

Updated: 2026-08-28

Branch: `work/valuepilot-android-milestone`

Purpose: compact durable recovery point. Newer repository/account evidence overrides this file.

## Startup order

Read before changing architecture/provider logic:

1. `AGENTS.md`
2. `CONTINUATION_CHECKPOINT.md`
3. `CURRENT_STATE.md`
4. `PROVIDER_ACCOUNT_STATUS.md`
5. `RAKUTEN_JAMIESON_VALIDATION.md`
6. `RAKUTEN_JAMIESON_FEED_AUDIT_2026-08-28.md`
7. `RAKUTEN_OFF_QUANTITY_COVERAGE.md`
8. `OPEN_DATA_INTEGRATION_STATUS.md`
9. `ARCHITECTURE.md`
10. `FUTURE_PRODUCT_VISION.md`

## Permanent architecture

ValuePilot is a provider-neutral shopping-intelligence platform, not an Accessibility/overlay/OCR product.

Permanent flow:

authorized/open/user evidence -> provider adapters -> provenance-preserving claims/import records -> deterministic validation/normalization -> Product identity + Offers -> bounded retrieval -> deterministic ranking -> immutable presentation -> replaceable UI

Permanent rules:

- Product != Offer.
- Sources contribute claims; they do not overwrite one shared product row.
- Stronger same-scope evidence may defeat weaker evidence; unresolved equal-strength conflicts block Best Value.
- Money, quantity and currency are exact/deterministic.
- AI may classify/explain but must not invent authoritative facts.
- Commission, EPC, payout, sponsorship and provider preference never affect ranking.
- No unauthorized scraping/reverse engineering.
- Technical/feed access != production authorization.
- No Android `INTERNET` / `ACCESS_NETWORK_STATE` merely for provider experiments.

## Current milestone

5D — Authorized Real Shopping Data Provider Selection / validation.

The shared deterministic foundation includes ShoppingEvidence, source-isolated namespaces, evidence conflict resolution, evidence-backed unit-value gating, checksum-valid GTIN handling, and provider-neutral staged offer import.

Built-in Android Search remains explicitly fictional/sample evidence until a production-authorized provider path exists.

## Jamieson / Rakuten — first actual authorized merchant feed

Rakuten technical Product Catalog access is enabled. Rakuten Customer Support explicitly confirmed Jamieson Product Feed approval and actual file presence on 2026-08-28.

The complete proprietary TXT.gz feed was downloaded and audited offline; it is never committed.

Sanitized feed facts:

- 273 product rows; trailer count matches
- 273/273 documented 38-field shape
- 273/273 CAD
- 273/273 in-stock
- 273 unique SKUs and 273 unique source Product IDs
- GTIN present 271/273; all 271 supplied GTINs checksum-valid
- product/image URL syntax valid 273/273
- manufacturer Jamieson 273/273
- descriptions 272/273
- Class ID blank 273/273
- Sale < Retail: 48
- Sale = Retail: 223
- Sale > Retail: 2
- Attribute 1 populated 273/273 while Attributes 2–10 are blank; without Class ID it remains opaque/untyped and must not be reverse-engineered.

Conclusions:

- advertiser feed/file access proven;
- production caching/persistence/indexing/display/mobile rights unresolved;
- Retail/Sale semantics unresolved;
- Rakuten feed alone does not establish package count;
- structural offer candidates = 273;
- authoritative unit-value candidates from Rakuten alone = 0;
- never infer quantity from title/description/SKU/image/price/untyped attribute/neighboring row.

## Critical 2026-08-28 Open Food Facts measurement correction

The first real local Rakuten × Open Food Facts quantity-coverage run completed and reported:

- 273 products
- 271 valid GTINs
- 4 Open Food Facts search calls
- 0 matched GTINs
- 0 exact counts

**That 0-match result is INVALID AS COVERAGE EVIDENCE.**

A concrete barcode-representation bug was identified immediately afterward.

Open Food Facts normalizes equivalent barcode representations with leading zeroes. The first tool queried raw provider GTINs and then compared returned normalized API `code` strings against the raw provider strings. Legitimate responses could therefore be discarded.

Sanitized actual-feed identity distribution:

- 12-digit GTINs: 248
- 13-digit GTINs: 1
- 14-digit GTINs: 22
- documented leading-zero canonicalization changes 267 / 271 source representations
- canonical unique identities remain 271
- canonical collisions: 0

Therefore never recover the project by concluding “Jamieson has 0 Open Food Facts matches” from the first run.

## Corrected barcode-normalized research path

`tools/run_rakuten_off_quantity_coverage.py` now delegates to `tools/measure_rakuten_off_quantity_coverage_v2.py` with `tools/open_facts_barcode.py`.

The corrected tool:

- validates GTIN first;
- canonicalizes only documented leading-zero equivalent representations;
- never repairs invalid GTINs;
- queries canonical identities;
- canonicalizes returned response codes before matching;
- maps accepted responses back to source identity in memory;
- explicitly reports canonical collisions;
- emits aggregate reports only;
- never emits GTINs, source rows, URLs, credentials or private provider identifiers.

Synthetic regression coverage includes normalized API response mapping and privacy checks. Merchant-feed qualification CI passed the normalized lookup suite at commit `3a949e14be5cdcd10f523aa3a8d20fe463b91d4f`.

## Shared-core GTIN identity correction

`GtinValidation.kt` now has deterministic `canonicalOrNull()` handling for equivalent leading-zero GTIN representations.

`ProviderOfferImport.kt` now deliberately separates:

- exact provider representation: `suppliedGtin`
- exact checksum-valid provider representation: `validatedGtin`
- canonical cross-source identity: `canonicalGtin`

`validatedSourceProductIdentity()` promotes only the canonical GTIN while the raw provider value remains preserved for provenance/audit.

Tests cover UPC-A/GTIN-12, equivalent GTIN-13, equivalent leading-zero GTIN-14, EAN-8, non-zero-indicator GTIN-14, and invalid GTIN non-repair.

GitHub Actions run for commit `be96095e6634b28f93e9add932bf67ac98bb66a3` has already completed the browser checks, Android/shared-core tests, lint/assemble, APK privacy-boundary verification, release assembly and artifact upload successfully; only workflow post-job cleanup was still running at the last checkpoint.

## Strict supplement-count semantics

`OpenFoodFactsImportedMetadata.kt` can emit `PACKAGE_QUANTITY` as either:

- structured positive whole-product `g` / `ml`; or
- exact source-displayed supplement count when the complete `quantity` field is integer + narrow allow-listed dose-form noun.

Titles/descriptions are never parsed as authoritative quantity. Strength/range/multiplier/mixed expressions remain unknown. Open Food Facts quantity remains `SOURCE_ASSERTED_METADATA`, never merchant-authoritative Jamieson metadata.

## Provider-neutral staged offer import

`ProviderOfferImport.kt` remains staging, not a canonical `Offer` constructor.

It preserves provider item ID, SKU, exact source GTIN, canonical cross-source GTIN, raw/parsed price fields, availability, URLs and dataset-generation provenance. Retail/Sale fields remain `UNRESOLVED_SOURCE_FIELDS`; dataset generation time is not per-offer freshness.

## Provider/account checkpoint

Use `PROVIDER_ACCOUNT_STATUS.md` as the fast-changing authority. Current checkpoint:

- Rakuten/Jamieson: feed approved + actual catalog available
- Well.ca: pending unless newer evidence
- Bath Depot: pending unless newer evidence
- Tru Earth: rejected; do not reapply now
- Giant Tiger: rejected; do not reapply now
- CJ: TSC, Brother Canada, DAVIDsTEA pending; AOSOM older pending unless newer evidence
- Awin active; Skip CA rejected due publisher type; do not misrepresent publisher type
- impact.com Marketplace declined; no blind duplicate application
- Lowvyn inquiry sent; await written rights/technical response

## Immediate next engineering action

**Rerun the stable Rakuten × Open Food Facts launcher locally after pulling the corrected branch.**

The corrected aggregate result becomes the first valid Jamieson OFF coverage measurement. Measure:

- normalized matched GTINs
- exact supplement-count candidates
- mass/volume-only metadata
- unmatched GTINs
- conflicts
- matched-but-no-usable-quantity
- normalization sanity/collision counts

If normalized coverage remains poor, then investigate the next source/domain fallback. Do not infer quantity from Rakuten text to compensate.

Do not create canonical production Offers while Retail/Sale semantics and production data-use rights remain unresolved. Do not automate SFTP credentials or add Android networking yet.

## Security

Operational provider credentials have appeared in conversational material. Never repeat, commit, log, screenshot, embed, or request them again. Future production automation must keep secrets outside source control.

## Verification discipline

For Android/shared-core changes:

```bash
cd android
./gradlew --no-daemon :shared-core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

For Python provider-research changes, run the focused `tools/tests` suite. Never weaken tests to make changes pass.
