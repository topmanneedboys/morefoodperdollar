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

The shared deterministic foundation includes ShoppingEvidence, source-isolated namespaces, evidence conflict resolution, evidence-backed unit-value gating, checksum-valid/canonical GTIN handling, and provider-neutral staged offer import.

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
- Attribute 1 populated 273/273 while Attributes 2–10 are blank; without Class ID it remains opaque/untyped.

Conclusions:

- advertiser feed/file access proven;
- production caching/persistence/indexing/display/mobile rights unresolved;
- Retail/Sale semantics unresolved;
- Rakuten feed alone does not establish package count;
- structural offer candidates = 273;
- authoritative unit-value candidates from Rakuten alone = 0;
- never infer quantity from title/description/SKU/image/price/untyped attribute/neighboring row.

## Open Food Facts identity correction

The historical first Jamieson × Open Food Facts run reported 0 / 271 matches. That result is invalid as coverage evidence because the old tool compared normalized Open Food Facts response codes against raw provider GTIN representations.

Sanitized Jamieson identity distribution:

- 12-digit GTINs: 248
- 13-digit GTINs: 1
- 14-digit GTINs: 22
- documented leading-zero canonicalization changes 267 / 271 source representations
- canonical unique identities remain 271
- canonical collisions: 0

Never recover the project by concluding “Jamieson has 0 Open Food Facts matches” from that first run.

## Valid normalized Jamieson × OFF result

The corrected normalized run completed successfully on 2026-08-28.

Authoritative aggregate result:

- product records: 273
- valid GTINs: 271
- normalized OFF matches: 102
- unmatched GTINs: 169
- exact supplement-count candidates: 12
- structured mass/volume-only candidates: 2
- quantity conflicts: 0
- matched but no usable quantity: 88
- matches containing expected Jamieson brand text: 90
- matches with source modification timestamp: 102
- successful batch-search requests: 13
- fallback batches after repeated search 5xx: 1
- direct product reads: 20
- response codes ignored after canonical validation: 0

This result supersedes the historical invalid 0-match run.

Open Food Facts is useful supplemental metadata but insufficient as the package-count foundation: only 12 valid-GTIN Jamieson identities are exact-count-ready under the strict current rules; 259 are not.

Do not relax the quantity parser or infer count from Rakuten text to increase coverage.

## OFF transport hardening

`tools/run_rakuten_off_quantity_coverage.py` delegates to the normalized v2 implementation.

Commit `5ffa11d6492129cabc33dd0d73816ae86454469b` makes batch search an optimization only: repeated Open Food Facts search HTTP 5xx failures fall back to rate-limited direct product-by-barcode reads while preserving canonical identity, aggregate-only reporting and privacy boundaries.

The merchant-feed qualification workflow passed for that commit.

## Shared-core GTIN identity

`GtinValidation.kt` has deterministic `canonicalOrNull()` handling for equivalent leading-zero GTIN representations.

`ProviderOfferImport.kt` separates:

- `suppliedGtin`: exact provider representation
- `validatedGtin`: exact checksum-valid provider representation
- `canonicalGtin`: cross-source identity

Canonical GTIN is promoted for matching while the raw provider value remains preserved for provenance/audit.

## Strict supplement-count semantics

`OpenFoodFactsImportedMetadata.kt` can emit `PACKAGE_QUANTITY` only from:

- structured positive whole-product `g` / `ml`; or
- exact source-displayed supplement count when the complete `quantity` field is integer + narrow allow-listed dose-form noun.

Titles/descriptions are never parsed as authoritative quantity. Strength/range/multiplier/mixed expressions remain unknown. Open Food Facts quantity remains `SOURCE_ASSERTED_METADATA`.

## Next package-content source decision

Health Canada's Licensed Natural Health Products Database can contribute regulatory identity, licence, dosage form, ingredients and recommended-dose facts, but its public schema does not carry GTIN-level package net content/count. It cannot close the current Jamieson exact-count gap by itself.

GS1 Canada ECCnet is now the strategic next package-content/identity validation target because it is GTIN-centric and carries standardized net-content/product-content data, including count-style net content.

ECCnet access is controlled for Data Recipients. No production ECCnet integration is authorized yet.

The next external gate is to establish:

- whether ValuePilot can qualify as a GS1 Canada ECCnet Data Recipient;
- whether the relevant Jamieson/product scope contains GTIN-level net content/count;
- consumer comparison display rights;
- caching/indexing/search/retention rights;
- mobile/software/API use rights;
- attribution/redistribution restrictions;
- subscription/commercial terms;
- available API/extract synchronization options.

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

## Immediate next action

**Validate GS1 Canada ECCnet Data Recipient feasibility and rights for ValuePilot.**

Do not create canonical production Offers while Rakuten Retail/Sale semantics and production data-use rights remain unresolved. Do not automate provider credentials or add Android networking yet.

Health Canada LNHPD may later be added as a separately attributed regulatory metadata rail, but not as a substitute for package count.

## Security

Operational provider credentials have appeared in conversational material. Never repeat, commit, log, screenshot, embed, or request them again. Future production automation must keep secrets outside source control.

## Verification discipline

For Android/shared-core changes:

```bash
cd android
./gradlew --no-daemon :shared-core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

For Python provider-research changes, run the focused `tools/tests` suite. Never weaken tests to make changes pass.
