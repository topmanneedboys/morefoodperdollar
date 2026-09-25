# Open Prices Canada Coverage — Measured 2026-08-27

Milestone: 5D — Authorized Real Shopping Data Provider Selection

## Measurement method

ValuePilot downloaded the current public Open Prices `prices.parquet` export in GitHub Actions and analyzed it with `tools/analyze-open-prices.py`.

The measurement is reproducible through `.github/workflows/validate-open-prices.yml`. The successful validation run used commit `35b3f8d40dd67084b377fe8911bdd95b01536cbb` and uploaded an aggregate report artifact.

No Android runtime networking, retailer scraping, private credentials, contributor identifiers, or proof-image paths were used or persisted in the repository.

## Exact snapshot

Dataset-wide:

- total price rows: **301,934**
- date range: **2010-07-08 through 2026-08-26**

Canada + CAD:

- rows: **659** (**0.218%** of the dataset)
- date range: **2020-02-01 through 2026-08-11**
- unique locations: **87**
- unique product codes: **482**
- checksum-valid GTINs: **459**
- positive-price rows: **659**
- receipt/price-tag proof-backed rows: **659**
- conservative physical OSM-shop rows: **502**
- strict physical/proof-backed/product rows before package-quantity join: **502**
- strict unique product codes before package-quantity join: **380**
- rows with a product name directly present in the price export: **63**
- discounted rows: **249**
- rows observed in the last 7 days: **0**
- rows observed in the last 30 days: **9**
- rows observed in the last 90 days: **12**

Proof mix:

- receipt: **533**
- price tag: **126**

The price export's `price_per` field is almost entirely absent for Canadian rows: 658 null and 1 `KILOGRAM` row.

## Geographic concentration

The measured Canada/CAD corpus is highly concentrated rather than nationally representative.

Largest city buckets in this snapshot:

- New Westminster: 374 rows / 250 product codes
- Coquitlam: 96 / 76
- Burnaby: 47 / 37
- Montréal: 33 / 33
- Ottawa: 29 / 29
- Ajax: 9 / 9
- Toronto: 8 / 8

Other Canadian cities are represented by only a handful of observations each.

Largest location-name buckets include Walmart, Real Canadian Superstore, London Drugs, Save-On-Foods, Choices Market, Costco, Dollarama, Marché Adonis, No Frills and Metro. These names demonstrate useful retailer diversity but do not imply current or complete chain coverage.

## Product-quantity limitation

The public price Parquet export does **not** expose package size as a dedicated product-quantity field.

Do not misuse `receipt_quantity` as package size. Receipt quantity means how many units were bought, not the mass/volume/count contained in one product.

Open Prices' Product object can expose product metadata such as `quantity`, `product_quantity`, and `product_quantity_unit`, with a product source field such as Open Food Facts. Any future join must preserve that metadata provenance rather than representing the package size as a direct shelf-price observation.

Consequences:

- exact same-GTIN cross-store price comparison can work without a package-size join;
- cross-SKU unit-value comparison requires a separately sourced package quantity;
- source-specific product metadata and price observations must remain independently attributable.

## Decision

**OPEN PRICES = KEEP AS A SUPPLEMENTAL OBSERVED-PRICE RAIL; DO NOT MAKE IT VALUEPILOT'S PRIMARY CURRENT-PRICE PROVIDER.**

Reasons:

1. Every measured Canadian row has useful receipt/price-tag proof, which is a strong positive quality signal.
2. 459 checksum-valid Canadian GTINs and 87 locations prove the data rail is real and technically useful.
3. However, only **12 Canadian/CAD observations are within 90 days**, only **9 within 30 days**, and **none within 7 days** in this snapshot.
4. Coverage is geographically concentrated, especially in a small group of British Columbia cities.
5. Package quantity requires a second provenance-preserving product-metadata join.

Therefore Open Prices is valuable for:

- historical/reference evidence;
- proof-backed user-observation architecture;
- exact same-product historical comparisons when fresh enough;
- validation of ValuePilot's provenance and trust pipeline;
- a future community-contribution rail.

It is not currently sufficient for:

- nationwide current Canadian shopping search;
- claiming named-retailer prices are current without a recent observation;
- current availability/stock;
- broad cross-SKU unit-price ranking by itself.

## Engineering result

A bounded network-free adapter prototype now exists at:

`android/app/src/main/java/com/valuepilot/app/OpenPricesImportedEvidence.kt`

Regression coverage exists at:

`android/app/src/test/java/com/valuepilot/app/OpenPricesImportedEvidenceTest.kt`

The adapter conservatively requires Canada, CAD, physical store, proof, checksum-valid GTIN, positive exact price, explicit deterministic quantity, and an actual source observation timestamp. It emits typed real-world `ShoppingEvidence`, leaves availability unknown, and never substitutes import time for observation time.

GitHub Actions verification for commit `dca8207ed1092764ba1e567022810cb6b4564206` completed successfully, including browser tests, Android shared/app tests, lint, APK build, and the existing no-INTERNET/no-ACCESS_NETWORK_STATE privacy-boundary check.

## Next provider direction

Because the measured Open Prices Canada corpus is too sparse/freshness-limited to be a primary offer rail, ValuePilot should now pursue three paths in parallel:

1. continue merchant-authoritative Rakuten/CJ feeds when approved;
2. retain Open Food Facts / Health Canada / Statistics Canada as independent identity/reference rails;
3. evaluate other explicitly licensed Canadian price-intelligence providers and a proof-backed user-contribution rail, without weakening the no-scraping/provider-rights gate.

Do not add Android runtime networking merely because the open-data prototype passed its tests.
