# ValuePilot Open-Data Bootstrap Validation

Research date: 2026-08-27

Milestone:
5D — Authorized Real Shopping Data Provider Selection

Status:
Open-data validation path selected for bounded implementation research.
No production network adapter or Android INTERNET permission is authorized by this document.
Affiliate/merchant feeds remain valuable and continue in parallel.

## Executive decision

Advertiser approval must not be a single point of failure for ValuePilot.

ValuePilot will use multiple independent evidence rails:

1. open product identity/content
2. open observed prices
3. government reference/regulatory data
4. merchant-authoritative offers when authorized

These rails have different authority and must remain provenance-separated.

The first free/no-advertiser-approval price source selected for controlled 5D validation is **Open Prices**, paired with **Open Food Facts** for barcode/product metadata.

Health Canada and Statistics Canada datasets are selected as reference/enrichment rails, not retailer-offer rails.

This is not a decision to replace Rakuten, CJ or future retailer-authorized feeds. Merchant-authoritative offers remain stronger evidence for current retailer pricing when their rights and semantics are valid.

## Provider-role matrix

### Open Prices — selected observed-price validation rail

Role:
Real-world observed price evidence.

Confirmed public capabilities:
- public REST API
- daily Parquet dataset and JSONL exports
- barcode/product code
- exact observed price and currency
- date
- physical or online location
- proof linkage
- proof types including receipt and price tag
- discounted-price metadata
- location comparison endpoint

The public dataset is ODbL licensed.

Canadian evidence exists in the dataset. Research found CAD observations tied to Canadian physical stores including Walmart and Dollarama, including 2026 observations. This proves non-zero Canadian coverage, but does **not** prove sufficient breadth or freshness for a national consumer product.

Decision:
**PASS FOR BOUNDED VALIDATION, NOT YET A PRIMARY PRODUCTION PRICE RAIL.**

Required next measurement:
- Canadian row count
- unique Canadian locations
- unique GTINs
- retailer/chain distribution
- city/province distribution
- observations in last 7/30/90 days
- percentage with receipt/price-tag proof
- percentage with usable product quantity
- duplicate/conflict rate

Until those are measured, Canadian coverage quality remains unknown.

### Open Food Facts — selected product identity/content baseline

Role:
Barcode-oriented product metadata and food-label enrichment.

Useful fields include:
- GTIN/barcode
- product name
- brand
- quantity
- categories
- ingredients
- allergens
- nutrition
- labels

Open Food Facts is community-contributed and explicitly provides no assurance that records are accurate, complete or reliable. It is therefore not merchant-authoritative price or stock evidence.

Its database is ODbL; individual database contents use the Database Contents License; product images are CC BY-SA and may contain other rights.

Operational constraints:
- identify ValuePilot with a custom User-Agent
- product-read rate limit is documented at 15 requests/minute/IP
- search rate limit is documented at 10 requests/minute/IP
- do not implement search-as-you-type against the remote search API
- for more than a few hundred products, use the bulk dataset rather than repeatedly querying the API

Decision:
**PASS AS PRODUCT-METADATA/IDENTITY EVIDENCE, NOT AS A CURRENT OFFER PROVIDER.**

Initial implementation should avoid image reuse until image attribution/licensing is deliberately handled.

### Statistics Canada average retail prices — selected market benchmark

Role:
Canadian market-level reference/benchmark data.

Statistics Canada publishes monthly average retail prices for selected products, based on transaction data from Canadian retailers, with Canada/province/population-centre geography and Canadian-dollar values.

This data is useful for:
- sanity checks
- market context
- category-level trend/reference values
- anomaly detection

It is not suitable for claiming that a named retailer currently sells a particular GTIN at the published average.

Decision:
**REFERENCE ONLY. NEVER TURN A STATCAN AVERAGE INTO A RETAILER OFFER.**

### Health Canada Canadian Nutrient File 2026 — selected food reference enrichment

Role:
Canadian reference food composition and unit/measure enrichment.

The CNF is Health Canada's standard reference food-composition database and is available under the Open Government Licence – Canada.

It is generally reference-food data, not current retailer offers and not a substitute for explicit branded label facts.

Decision:
**REFERENCE/ENRICHMENT ONLY.**

### Health Canada LNHPD — selected supplement regulatory enrichment

Role:
Canadian licensed natural-health-product identity and regulatory facts.

The API exposes licensed product information such as:
- NPN/licence number
- product name
- dosage form
- medicinal ingredients
- non-medicinal ingredients
- dose information
- product purpose
- risk statements

This is highly useful for vitamins/supplements such as Jamieson, but it supplies no current retailer price.

Decision:
**REGULATORY/IDENTITY ENRICHMENT ONLY.**

ValuePilot must not transform sourced regulatory fields into unsupported medical efficacy, treatment or safety recommendations.

## Evidence authority hierarchy

For a current value comparison, the default hierarchy is:

1. authorized merchant/retailer offer with current price and explicit scope
2. recent location/date/proof-backed Open Prices observation
3. other source-asserted observed price with clear provenance
4. community product metadata
5. government category/reference data

A weaker layer may enrich identity or context but must not overwrite a stronger price, quantity, availability or product-label claim.

## Open Prices mapping into existing ShoppingEvidence

The current shared-core provenance model already supports a safe first mapping without retailer-specific core logic.

For an imported Open Prices row that passes adapter validation:

- provider: `Open Prices`
- environment: `REAL_WORLD`
- channel: `IMPORTED` for dump/Parquet ingestion; `AUTHORIZED_API` only for a deliberate API adapter
- observation claim: `DIRECT_OBSERVATION` only when backed by an accepted proof type
- source: stable location identity plus human-readable store/location name
- source product identity: GTIN when valid
- observed timestamp: derived from the actual recorded price date/time only; never substitute download/import time
- availability: `UNKNOWN` unless a separate source explicitly supplies availability
- promotion: only if the row explicitly supplies discount evidence that ValuePilot can map without inference

Initial rankable import gate:
- CAD currency
- Canadian location (`CA`)
- positive exact price
- valid GTIN
- real observation date
- physical-store identity
- accepted proof linkage (`RECEIPT` or `PRICE_TAG` initially)
- enough explicit product quantity evidence for the deterministic value metric being used

Rows that fail the rankable gate may be retained as isolated reference evidence when safe, but must not silently influence Best Value.

## Freshness issue discovered during validation

The current Universal Search default real-world freshness policy is deliberately strict for live provider evidence: 15 minutes fresh and 2 hours stale.

That policy is correct for a live offer feed but is not a truthful semantic fit for receipt/shelf observations whose source resolution is often a calendar date.

ValuePilot must **not** fake freshness by stamping Open Prices rows with import/download time.

Before Open Prices can drive Best Value at useful scale, a later focused change must introduce explicit source/evidence-class freshness policy (for example live offer versus dated shelf observation) while preserving deterministic caller-supplied time.

Until that change exists, old Open Prices rows naturally fall to reference/display-only under the existing trust boundary.

## ODbL isolation rule

Open Food Facts/Open Prices ODbL data must not be blindly merged into a proprietary affiliate-feed database.

The ODbL explicitly distinguishes a Derivative Database from a Collective Database of independent databases, and Open Food Facts documentation specifically warns reusers not to mix its data with external product data in a way that violates ODbL obligations.

Engineering precaution:
- keep ODbL source records in a logically isolated source store/namespace
- keep Rakuten/CJ/retailer-feed records in their own source stores
- retain source licences and provenance per record
- join at the application/evidence layer using stable identifiers such as GTIN
- do not copy proprietary affiliate-feed contents into an ODbL derivative
- do not assume this architecture alone resolves every licensing question; obtain legal review before a commercial production data warehouse materially combines licensed datasets

This separation also improves debugging, deletion, correction, attribution and provider replacement.

## Open Government Licence rule

Health Canada and Statistics Canada data covered by the Open Government Licence – Canada can be used commercially, copied, modified and distributed subject to its conditions, including source attribution and non-endorsement.

Government reference data therefore has a much simpler integration path than proprietary retailer feeds, but its factual scope must still be respected.

## Performance strategy

Do not make every user keystroke call a public API.

Preferred long-term pattern:

```text
source export/API
    -> source-isolated ingest
    -> syntax/schema validation
    -> country/currency/proof filters
    -> GTIN/product identity
    -> deterministic normalization
    -> bounded local/server index
    -> ShoppingEvidence
    -> existing trust/ranking pipeline
```

For Open Food Facts bulk product work, prefer the documented CSV/JSONL dumps.

For Open Prices bulk validation, prefer the daily Parquet/JSONL export so coverage can be measured reproducibly before any runtime API dependency is introduced.

## Explicit non-goals for this 5D substep

Do not yet:
- add Android INTERNET permission
- make Open Prices or Open Food Facts the sole source of truth
- scrape retailer websites
- infer stock from the existence of an old price
- convert a stale observed price into a current price
- synthesize CAD prices from foreign currency
- merge ODbL and proprietary provider databases indiscriminately
- use commission/payout in ranking
- add remote AI
- add checkout/cart/payment logic

## Go/no-go gates for the first open price rail

Open Prices advances from validation to a production candidate only if measured Canadian data demonstrates enough value on all of these axes:

1. non-trivial current Canadian coverage
2. useful product/GTIN coverage
3. useful store/location diversity
4. enough explicit quantities for unit-value comparisons
5. acceptable proof-backed observation rate
6. freshness adequate for the UI claims we make
7. deterministic duplicate/conflict handling
8. ODbL attribution/share-alike architecture reviewed and implementable
9. acceptable import/update cost
10. no need for prohibited retailer scraping

Failing those gates does not invalidate the open-data architecture. It means Open Prices stays a supplemental observed-price rail while authorized merchant feeds remain the main offer source.

## Immediate implementation sequence

1. Add a source-isolated, network-free Open Prices row-to-`ShoppingEvidence` mapper with regression tests.
2. Do not connect it to production UI/network yet.
3. Obtain/download the daily Open Prices dataset outside the Android client and measure Canada/CAD coverage.
4. Add a provider/evidence-class freshness policy only after real age distribution is measured.
5. Continue waiting for Jamieson/Rakuten and pending CJ decisions in parallel.
6. When the first merchant-authoritative feed arrives, compare its identity/price/freshness quality against the open-data rail rather than replacing either source automatically.
