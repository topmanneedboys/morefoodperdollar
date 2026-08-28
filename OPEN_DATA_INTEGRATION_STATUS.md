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

Do not connect these adapters to consumer search/ranking UI yet.

Next engineering step after CI is green:

- exercise the actual Open Prices + Open Food Facts path with real rows from their public exports;
- measure join success by validated GTIN and usable package quantity;
- inspect disagreement/corruption rates;
- keep failed/conflicting rows out of Best Value;
- only then decide whether a bounded import/index deserves a user-visible path.

Merchant-authoritative feeds, when approved, will be run through the same conflict and unit-value boundaries rather than bypassing them.
