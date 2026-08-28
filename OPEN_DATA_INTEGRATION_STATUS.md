# ValuePilot — Open-Data Integration Status

Updated: 2026-08-27

Milestone: 5D — provider validation and provenance-safe real-data preparation

## External provider status

- Lowvyn rights/technical inquiry: **sent**. Await written clarification before any API adapter, caching, indexing, redistribution, or production use.
- Rakuten/CJ merchant-feed work continues independently.
- No Android runtime network permission or production provider networking is authorized by this status.

## Open Prices

Measured Canadian coverage remains supplemental, not primary-current-price quality.

Implementation now treats an Open Prices row as **price evidence only**:

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

A network-free metadata adapter now exists for the first narrow field set:

- checksum-valid GTIN
- optional product name
- optional brand text
- displayed quantity retained for provenance/cross-checking
- normalized whole-product `product_quantity`
- normalized unit restricted to documented `g` or `ml`
- source last-modified timestamp when supplied

It emits **PACKAGE_QUANTITY only** with `SOURCE_ASSERTED_METADATA` authority.

It cannot emit:

- retailer price
- stock/availability
- promotion
- merchant identity
- market benchmark

Simple displayed quantities such as `1 kg` and `6 x 250 ml` are deterministically cross-checked against structured Open Food Facts quantity fields. If the simple displayed value and structured value disagree, the import fails closed instead of guessing.

## Real Open Prices × Open Food Facts measurement

The first real-data join measurement completed successfully in GitHub Actions using the current public Open Prices Parquet export and the official Open Food Facts v2 bulk-search endpoint with a narrow field projection.

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

The lookup required **5** documented bulk-search calls while staying below the Open Food Facts search-rate limit.

### Measurement decision

**The identity/quantity join is technically useful; the price freshness remains the limiting factor.**

A roughly three-quarter GTIN quantity-join rate is strong enough to justify keeping Open Food Facts as a supplemental package-metadata rail. It does **not** make Open Prices a primary current-price provider because the joined corpus still has no observations in the last seven days and only four joined rows in the last ninety days.

Therefore:

- keep Open Food Facts for source-attributed product/package metadata;
- keep Open Prices for proof-backed observed/historical prices;
- do not call an old joined observation a current retailer offer;
- do not let quantity enrichment upgrade stale/display-only price evidence into Best Value;
- continue merchant-authoritative provider work and the Lowvyn rights inquiry in parallel.

## Cross-source conflict safety

Shared core now has claim-domain and authority-aware conflict handling.

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

Shared core also contains a fail-closed unit-value assembly boundary.

A price from one provider and package quantity from another may produce a deterministic unit rate only when all of the following are true:

1. the price evidence is independently `RANKABLE`;
2. price and quantity claims use the same stable product key (normally validated GTIN);
3. the price claim is `CURRENT_PRICE` or `OBSERVED_PRICE`;
4. the quantity claim is `PACKAGE_QUANTITY`;
5. the exact Money fingerprint matches the price claim;
6. the exact NormalizedQuantity fingerprint matches the quantity claim;
7. quantity authority is strong enough for deterministic ranking.

A stale/display-only Open Prices observation therefore cannot become rankable merely because fresh Open Food Facts metadata exists.

## What this prevents

The current design specifically prevents these failure modes:

- an old receipt price silently becoming the current retailer price;
- Open Food Facts community metadata overwriting an authoritative merchant package size;
- a package size from GTIN A being joined to the price of GTIN B;
- a market average becoming a Walmart/Metro/etc. offer;
- a newer but weaker source automatically defeating a stronger authoritative source;
- conflicting equal-authority values being averaged or chosen arbitrarily;
- source-import time being substituted for source observation time;
- a second source accidentally upgrading stale price evidence to Best Value.

## Next gate

Do not connect these adapters directly to consumer search/ranking UI yet.

The measured quantity-join rate is good enough for the next bounded engineering step: a **source-isolated, network-free imported evidence index/repository prototype** that can hold independently attributed Open Prices and Open Food Facts records and expose resolved evidence only through the existing conflict and unit-value gates.

That prototype must:

- preserve ODbL source separation from proprietary merchant-feed storage;
- index/join only on validated stable identity such as GTIN;
- retain older observations as history rather than overwriting them;
- never infer stock;
- never synthesize freshness;
- never make a display-only/stale price rankable because richer metadata exists;
- make unresolved factual conflicts explicit and non-rankable;
- remain network-free and platform-neutral at the core boundary.

Only after that bounded index is tested should any user-visible open-data search path be considered.

Merchant-authoritative feeds, when approved, will be run through the same conflict and unit-value boundaries rather than bypassing them.
