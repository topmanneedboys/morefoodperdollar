# ValuePilot — Open-Data Integration Status

Updated: 2026-08-28

Milestone: 5D — provider validation and provenance-safe real-data preparation

## External provider status

- Lowvyn rights/technical inquiry: **sent**. Await written clarification before any API adapter, caching, indexing, redistribution, or production use.
- Rakuten/Jamieson merchant-feed validation is now the highest-value real-provider path.
- CJ/Rakuten/Awin provider screening continues independently.
- No Android runtime network permission or production provider networking is authorized by this status.

## Open Prices

Measured Canadian coverage remains supplemental, not primary-current-price quality.

Implementation treats an Open Prices row as **price evidence only**:

- validated GTIN
- Canada + CAD
- proof-backed physical observation
- exact positive price
- source location
- source observation time
- availability remains unknown

Package quantity is deliberately absent from the Open Prices price observation. The public price export does not establish package size, and injecting a quantity from another provider into the same observation would erase provenance.

The adapter emits both:

1. typed `ShoppingEvidence`; and
2. a typed `OBSERVED_PRICE` `EvidenceClaim` with proof-backed-direct-observation authority.

## Open Food Facts

A strict network-free metadata mapper exists in `OpenFoodFactsImportedMetadata.kt`.

Supported source fields remain narrow:

- checksum-valid GTIN
- optional product name
- optional brand text
- displayed source quantity retained for provenance/cross-checking
- normalized structured whole-product `product_quantity`
- normalized structured unit restricted to documented `g` or `ml`
- source last-modified timestamp when supplied

The mapper now supports **two explicit quantity bases**:

1. `STRUCTURED_MASS_OR_VOLUME` for positive normalized `g` / `ml`; and
2. `DISPLAYED_SUPPLEMENT_COUNT` for an exact source quantity such as `100 tablets`, `60 capsules`, `90 gummies`, or another deliberately allow-listed dose-form noun.

Count promotion is intentionally strict:

- the complete source `quantity` value must match the count syntax;
- titles and descriptions are never parsed as authoritative count;
- ranges, strengths, multipliers and mixed expressions remain unknown;
- ambiguous text such as `60 capsules x 500 mg` is not converted into count evidence.

It emits **PACKAGE_QUANTITY only** with `SOURCE_ASSERTED_METADATA` authority.

It cannot emit:

- retailer price
- stock/availability
- promotion
- merchant identity
- market benchmark

Simple displayed mass/volume values such as `1 kg` and `6 x 250 ml` are still deterministically cross-checked against structured Open Food Facts quantity fields. If a parseable displayed value and structured value disagree, the import fails closed instead of guessing.

Open Food Facts metadata never becomes merchant-authoritative merely because it shares a GTIN with a merchant feed.

## Real Open Prices × Open Food Facts measurement

The first real-data join measurement completed successfully in GitHub Actions using the then-current public Open Prices Parquet export and the official Open Food Facts structured-search API with a narrow field projection.

The analysis remained research-only and aggregate-only. It did not add Android networking, scrape retailers, merge source records, persist proof-image paths, or publish product/contributor identifiers.

Measured strict Open Prices input:

- **478** Canadian CAD proof-backed physical-store price rows
- **358** checksum-valid GTINs
- observation date range: **2020-02-01 through 2026-08-11**
- recent strict rows: **0 / 9 / 11** in the last **7 / 30 / 90 days**

Open Food Facts lookup results:

- **323 / 358** GTINs found
- **266** GTINs had valid structured `g`/`ml` whole-product quantity
- **0** parseable raw-vs-structured quantity disagreements in this measured set
- **0** conflicting duplicate structured quantities in this measured set
- **266 / 358 = 74.3%** usable quantity joins after fail-closed checks

Price-row coverage after the quantity join:

- **371 / 478 = 77.6%** of strict Open Prices rows had a usable OFF package quantity
- joined rows in the last **7 / 30 / 90 days**: **0 / 4 / 4**

The lookup required **5** structured-search calls while staying below the documented Open Food Facts search-rate limit.

### Measurement decision

**The identity/quantity join is technically useful; Open Prices freshness remains the limiting factor.**

Keep Open Food Facts as a supplemental product/package metadata rail and Open Prices as proof-backed observed/historical price evidence. Do not call an old joined observation a current retailer offer, and do not let richer metadata upgrade stale/display-only price evidence into Best Value.

## Source-isolated evidence index

The bounded platform-neutral in-memory prototype now exists in `android/shared-core/src/main/kotlin/com/valuepilot/core/SourceIsolatedEvidenceIndex.kt`.

It preserves dataset namespace and storage-boundary metadata instead of flattening all claims into one shared row. It supports bounded lookup by stable product key, delegates factual resolution to the existing resolver, rejects claim-ID collisions, and can remove one dataset namespace without touching another provider.

This is an engineering/source-isolation boundary, not a legal conclusion about any dataset licence.

## Cross-source conflict safety

Important invariants:

- current merchant price != historical/proof-backed observed price;
- package quantity != price;
- market benchmark != retailer offer;
- regulatory fact != retailer offer;
- different merchants/locations/channels/currencies coexist instead of overwriting each other;
- stronger authority wins a same-scope factual conflict before recency is considered;
- equal-scope unresolved conflicts block Best Value instead of guessing.

Canonical exact fingerprints are used for money and normalized quantity so formatting differences cannot masquerade as factual differences.

## Cross-source unit-value gate

A price from one provider and package quantity from another may produce a deterministic unit rate only when all of the following are true:

1. the price evidence is independently `RANKABLE`;
2. price and quantity claims use the same stable product key, normally validated GTIN;
3. the price claim is `CURRENT_PRICE` or `OBSERVED_PRICE`;
4. the quantity claim is `PACKAGE_QUANTITY`;
5. the exact Money fingerprint matches the selected price;
6. the exact NormalizedQuantity fingerprint matches the selected quantity;
7. quantity authority is strong enough for deterministic ranking.

A stale/display-only Open Prices observation therefore cannot become rankable merely because fresh Open Food Facts metadata exists.

A Jamieson merchant price likewise cannot receive per-tablet/capsule arithmetic merely because a title appears to contain a count. Count must be separately established and pass the same identity/conflict/unit-value gates.

## Rakuten × Open Food Facts supplement-count coverage research

The privacy-safe research tool `tools/measure_rakuten_off_quantity_coverage.py` now exists for the next empirical Jamieson step.

It reads the local authorized Rakuten feed, keeps valid GTINs in memory, performs bounded Open Food Facts metadata lookups, fails closed on conflicting quantity candidates, and emits only aggregate JSON/Markdown. It does not emit GTINs, source product rows, URLs, credentials or provider account identifiers.

Synthetic regression coverage is included in the merchant-feed qualification test suite. The workflow compiled the tool and passed the suite on commit `3c4dfa8cfe2f8fd6de4c3a503983de52c26bed7a`.

The **real Jamieson feed has not yet been run through this new count-coverage tool**, so no real exact-count coverage percentage is established yet.

See `RAKUTEN_OFF_QUANTITY_COVERAGE.md`.

## What the current design prevents

The current design specifically prevents:

- an old receipt price silently becoming the current retailer price;
- Open Food Facts community metadata overwriting an authoritative merchant package size;
- a package size from GTIN A being joined to the price of GTIN B;
- a market average becoming a retailer-specific offer;
- a newer but weaker source automatically defeating a stronger authoritative source;
- conflicting equal-authority values being averaged or chosen arbitrarily;
- source-import time being substituted for source observation time;
- a second source accidentally upgrading stale price evidence to Best Value;
- supplement count being guessed from a title, description or dosage strength.

## Next gate

Do not connect these research adapters directly to consumer search/ranking UI yet.

The next bounded empirical step is:

1. run the privacy-safe Rakuten × Open Food Facts coverage tool against the existing authorized complete Jamieson feed locally;
2. retain only aggregate output;
3. determine exact supplement-count coverage, mass/volume-only coverage, unmatched products, conflicts and unresolved quantity rows;
4. only if coverage is useful, assemble separately attributed quantity claims through the existing source-isolated index, conflict resolver and evidence-backed unit-value gate;
5. keep production rights, price semantics and Android networking as separate unresolved gates.

Merchant-authoritative feeds and open metadata must continue through the same provenance/conflict boundaries rather than bypassing them.
