# ValuePilot — Canadian Price Data Landscape

Research date: 2026-08-27

Milestone: 5D — Authorized Real Shopping Data Provider Selection

## Question

Can ValuePilot obtain useful Canadian product/price data without waiting for every retailer or affiliate advertiser to approve a product feed?

## Answer

**Yes for product identity, reference data, historical/observed prices and user-contributed evidence. No free source found provides broad, fresh, merchant-authoritative, store-specific Canadian grocery offers with unrestricted commercial reuse and no approval.**

That means the durable architecture should not choose between open data and merchant feeds. It should combine independent evidence rails without conflating their authority.

## Candidate matrix

### Open Prices — free / no merchant approval

Status: **SUPPLEMENTAL ONLY after measured Canada coverage**

Strengths:
- public API and bulk exports
- ODbL
- receipt/price-tag proof model
- product code, price, currency, date and location
- accepts contributions from users and third-party apps

Measured Canadian snapshot:
- 659 Canada/CAD rows
- 87 locations
- 482 product codes
- 459 checksum-valid GTINs
- all 659 rows receipt/price-tag proof-backed
- only 9 observations within 30 days and 12 within 90 days
- no observations within 7 days

Conclusion:
Useful independent evidence rail and a strong model for proof-backed crowdsourcing, but far too sparse/freshness-limited to power nationwide current shopping by itself.

See `OPEN_PRICES_CANADA_COVERAGE.md`.

### Open Food Facts — free / no merchant approval

Status: **PASS FOR PRODUCT IDENTITY/LABEL METADATA**

Strengths:
- barcode-oriented product database
- brand/name/quantity/ingredients/nutrition/categories
- open API and bulk downloads

Limits:
- community data, not merchant-authoritative price/stock
- ODbL/database-content licensing and image-specific CC BY-SA/other-rights considerations
- API rate limits make bulk/local indexing preferable for substantial catalog work

Conclusion:
Permanent product-identity/content rail, not a current retailer-offer rail.

### Statistics Canada average retail prices — free / no merchant approval

Status: **REFERENCE ONLY**

Strengths:
- monthly Canadian retail-price benchmarks
- based on transaction/scanner data from Canadian retailers
- Canada/province/population-centre geography

Limits:
- aggregate representative products, not named-retailer GTIN offers
- cannot be represented as a current price at Walmart, Loblaws, Metro, etc.

Conclusion:
Excellent market benchmark/anomaly context; never convert it into a retailer offer.

### Health Canada CNF / LNHPD — free / no merchant approval

Status: **REFERENCE/REGULATORY ENRICHMENT**

Strengths:
- CNF: Canadian food composition and measure data
- LNHPD: licensed natural-health-product identity, NPN, dosage form, ingredients, dose, purpose/risk label information
- government source

Limits:
- no current retailer prices

Conclusion:
High-quality Canadian factual enrichment independent of affiliate approvals.

### Lowvyn — provider-level access, not retailer-by-retailer approval

Status: **HIGH-INTEREST HOLD PENDING WRITTEN DATA-USE RIGHTS**

Public API claims:
- current CAD prices
- daily tracking
- Amazon.ca, Walmart.ca, BestBuy.ca, Canadian Tire, Canada Computers, Newegg.ca, Visions.ca
- price history
- cross-retailer UPC matching
- free tier described as supporting non-commercial and ethical commercial use

Material caveats:
- Lowvyn states its underlying prices are collected from public retailer product pages and that it is not affiliated with those retailers
- public terms do not clearly grant all caching/indexing/normalization/redistribution rights ValuePilot needs
- retailer titles/images/descriptions are expressly identified as belonging to retailers/suppliers
- package-size fields needed for grocery unit-value comparison are not established in the public API example
- retailer coverage is much stronger for electronics/home/general merchandise than grocery

Conclusion:
Potentially useful broad-shopping price provider, but not production-authorized until Lowvyn explicitly confirms downstream commercial display/caching/indexing rights and field semantics.

See `LOWVYN_VALIDATION.md`.

### Vynn.AI — strong grocery coverage, but not a free production rail

Status: **NOT A FREE PRODUCTION SOLUTION; REVISIT IF BUDGET/LICENCE JUSTIFIES IT**

Positive signals:
- Canada-only grocery focus
- current/latest offers, product lookup/search and price history documented
- broad retailer/store coverage claims
- explicit observation/freshness semantics

Blocking facts for the current free-first path:
- public API documentation says authenticated responses are `private, no-store` and must not be put in a shared cache
- public pricing distinguishes personal/internal tiers from a commercial-use tier
- the site's general Terms say data-product use is governed by separate subscription/data licence agreements and free samples are evaluation-only
- public pages expose changing/overlapping product plans, so exact commercial terms must come from a current agreement rather than assumptions from marketing pages

Conclusion:
Potential future paid provider, not the free independent rail sought in this milestone.

### GroceryPulse — free aggregate index, licensed product/store microdata

Status: **FREE REFERENCE ONLY / PAID FOR OFFER-LEVEL USE**

The free surface is an aggregate grocery-price index. Observation-level prices, product catalog and shrinkflation data are licensed subscription products, with onward distribution negotiated separately.

Conclusion:
Useful benchmark/research source; does not provide free offer-level data for ValuePilot production.

### Instacart Developer Platform — rich grocery ecosystem, but approval required

Status: **NOT A NO-APPROVAL RAIL**

Instacart publicly describes a large North American retailer catalog and developer shopping integrations, but applicants are reviewed/approved. Its own developer FAQ also distinguishes public developer experiences from direct access to underlying Instacart data.

Conclusion:
Potential strategic partnership later, but it does not eliminate the provider-approval bottleneck.

### Walmart Canada Marketplace APIs — not a public shopper-comparison feed

Status: **NOT APPLICABLE**

The Canada Marketplace APIs are for sellers/approved solution providers managing their own items, prices and inventory and require marketplace credentials. They do not provide an unrestricted public feed of Walmart Canada's consumer catalog/prices.

### Best Buy public developer API

Status: **US-ORIENTED / CANADIAN PRODUCTION RIGHTS NOT ESTABLISHED**

Best Buy's current public developer portal exposes BestBuy.com catalog, pricing and availability through an API key. Historical Best Buy Canada public API documentation also exists, but it is old and does not establish a current, supported Canadian commercial data rail with the rights ValuePilot needs.

Do not build a production adapter from undocumented/stale Canada endpoints or reverse-engineered site APIs.

## Direct scraping / private web APIs

Status: **REJECT**

A technically accessible retailer website or internal GraphQL endpoint is not equivalent to an authorized reusable data feed.

ValuePilot should not make retailer scraping/reverse-engineering its production foundation. It is brittle, creates terms/rights uncertainty and violates the permanent provider-validation gate.

## The strongest no-merchant-approval path: proof-backed user observations

Open Prices' architecture confirms a viable model that does not require retailer feed approval:

```text
user scans barcode
    + photographs shelf tag or receipt
    + identifies store/location/date
    -> local validation / PII redaction
    -> product identity resolution
    -> proof-backed price observation
    -> trust/freshness policy
    -> ValuePilot comparison evidence
```

Open Prices explicitly supports user and third-party-app contributions and associates prices with proofs, locations and products.

This path has important advantages:
- no dependency on an affiliate manager approving a feed;
- evidence can be location-specific and auditable;
- can improve precisely where open-data coverage is weak;
- creates a defensible first-party/community evidence rail;
- can optionally contribute compatible observations back to Open Prices under its rules.

But it also has real costs:
- cold-start coverage;
- receipt/shelf-image privacy and PII handling;
- moderation/fraud/conflict handling;
- location accuracy;
- freshness expiration;
- package-size/product matching;
- contributor consent and licensing;
- incentives must not distort truthfulness.

Therefore this should be designed as a **future bounded capture/provider adapter**, not rushed into the current Android UI merely to bypass merchant approval.

## Durable provider strategy

ValuePilot should operate four independent layers:

```text
OPEN IDENTITY / LABEL DATA
Open Food Facts + Health Canada

OPEN / COMMUNITY OBSERVED PRICES
Open Prices + future proof-backed ValuePilot observations

MARKET REFERENCE
Statistics Canada (+ optional aggregate indexes)

MERCHANT / COMMERCIAL CURRENT OFFERS
Rakuten + CJ + future retailer/provider partnerships
```

All layers feed provenance-preserving `ShoppingEvidence`. Stronger evidence may supersede weaker evidence for a particular claim, but provenance is never erased.

## Current bottleneck after this research

The bottleneck is no longer “getting any product data.”

The remaining hard problem is:

**broad, fresh, store-specific Canadian offer prices and availability with rights that permit commercial display, normalization, search/indexing and appropriate caching.**

Open data solves identity/reference and some observed-price evidence. Merchant or explicitly licensed provider rails are still needed for dependable current offers at scale.

## Recommended next actions

1. Keep Rakuten Jamieson and the pending CJ applications moving; they remain potentially high-authority offer rails.
2. Keep Open Prices as supplemental evidence; do not spend runtime engineering effort pretending its current Canadian coverage is sufficient.
3. Preserve Open Food Facts, Health Canada and Statistics Canada as independent reusable enrichment/reference rails.
4. Send one provider-level rights inquiry to Lowvyn before considering its API for ValuePilot.
5. Do not pay for Vynn/GroceryPulse yet; first validate what the approved affiliate feeds and Lowvyn can provide.
6. Design the future proof-backed user-observation rail only after the first merchant feed/API schema establishes what gaps actually remain.
7. Do not add Android INTERNET permission or production backend/networking until a provider has passed the rights/data-quality gate.
