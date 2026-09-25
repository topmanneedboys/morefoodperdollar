# ValuePilot Provider Validation — Lowvyn

Research date: 2026-08-27

Milestone: 5D — Authorized Real Shopping Data Provider Selection

## Summary decision

**HOLD / HIGH-INTEREST PROVIDER — REQUEST RIGHTS CLARIFICATION BEFORE ANY INTEGRATION.**

Lowvyn is materially more relevant than a random affiliate advertiser because it exposes a dedicated Canadian price-intelligence API and does not require ValuePilot to obtain approval from each tracked retailer individually.

However, ValuePilot must not treat API availability as sufficient production authorization. The provider's public pages leave important downstream data-use and provenance questions unresolved.

## Positive signals

Lowvyn's public API documentation states that it:

- is intended for third-party developers;
- exposes a read-only REST API/OpenAPI surface;
- tracks Canadian prices across Amazon.ca, Walmart.ca, BestBuy.ca, Canadian Tire, Canada Computers, Newegg.ca and Visions.ca;
- returns current CAD prices;
- exposes up to 365 days of price history;
- supports cross-retailer same-product comparison using UPC matching;
- exposes live retailer counts/freshness through its retailer endpoint;
- offers a free tier described as available for non-commercial and ethical commercial use;
- documents free-tier limits of 600 requests/minute and 10,000 requests/day.

This is a much stronger technical shape for ValuePilot than advertiser-by-advertiser affiliate catalogs.

## Material limitations / unresolved rights

### 1. The underlying retailer data is not described as merchant-authoritative

Lowvyn's public Terms state that its prices are collected from public retailer product pages. Lowvyn also publicly states that it is independent and not affiliated with or endorsed by the retailers whose data it displays.

A Lowvyn engineering post further says it did not have official product APIs from Amazon, Walmart or Best Buy and read product information from pages, with the acknowledged possibility that retailer layout changes can break extraction.

That means ValuePilot must classify Lowvyn as a third-party price-intelligence provider, not as an authorized retailer feed.

### 2. Display/caching/indexing/redistribution rights are not explicit enough

The API page says the free tier may be used for non-commercial and ethical commercial use, but the public Terms do not provide a sufficiently explicit downstream licence covering all of ValuePilot's intended operations, such as:

- persistent caching of price observations;
- indexing for search;
- normalization and derived unit-value calculations;
- displaying retailer-specific current prices inside a mobile application;
- retaining historical price points;
- redistributing product titles or descriptions;
- image use;
- exact attribution/canonical-link obligations.

The Terms also prohibit automated scraping or bulk downloading from Lowvyn. Normal use of the documented API is distinct from scraping, but ValuePilot should obtain explicit confirmation of allowed caching/indexing volume rather than infer it.

### 3. Retailer content rights remain source-specific

Lowvyn's Terms state that product images, titles and descriptions belong to the respective retailers/suppliers and that Lowvyn does not claim ownership of them.

Therefore an API key alone must not be interpreted as a blanket sublicence for ValuePilot to republish retailer images/descriptions.

Initial ValuePilot use, if authorized, should minimize fields to factual price/identifier/link data until field-specific rights are clear.

### 4. Grocery coverage is not the current strength

The documented retailer set is strong for electronics, home, baby and general merchandise but omits major Canadian grocery banners such as Loblaws/No Frills/Real Canadian Superstore, Metro, Sobeys/FreshCo and Save-On-Foods as direct tracked retailer rails.

Lowvyn could still be valuable for ValuePilot's broader shopping future, but it does not by itself solve the Canadian grocery-price bottleneck.

### 5. Product normalization fields need validation

The public API example clearly exposes current price/history and describes UPC-based cheaper-product matching, but the supplied public documentation does not establish the package-size/quantity fields needed for deterministic cross-SKU grocery unit-value comparison.

Actual API response/schema must be inspected before implementing a mapping.

## Required written confirmation before production

Ask Lowvyn to confirm, for ValuePilot specifically:

1. commercial use in a Canadian shopping-comparison mobile/web product;
2. permission to display retailer-specific current prices returned by the API;
3. permission to store/cache historical and current price observations, and any retention limits;
4. permission to index records for search and normalize/derive deterministic value metrics;
5. which returned fields may be redistributed/displayed (title, UPC, retailer name, price, history, URL, image, description);
6. required attribution and canonical-link behavior;
7. whether affiliate/tracking links are required or optional;
8. whether rankings/comparisons may remain commission-independent;
9. source freshness guarantees or update cadence for each retailer;
10. whether the API has stable UPC/GTIN and package-size fields;
11. whether ValuePilot may query/cache in bulk within documented rate limits or whether per-request/on-demand use is required;
12. any retailer-specific restrictions that downstream API consumers must inherit.

## Evidence classification if approved

Until stronger merchant authority is proven:

- provider: `Lowvyn`
- environment: `REAL_WORLD`
- channel: `AUTHORIZED_API`
- claim kind: `SOURCE_ASSERTED`, not `DIRECT_OBSERVATION`
- availability: `UNKNOWN` unless the API explicitly supplies a scoped availability fact
- observed time: use provider-supplied price update timestamp, never ValuePilot request/import time
- retailer identity: preserve provider and retailer separately
- product identity: prefer validated UPC/GTIN if exposed

Lowvyn evidence must remain distinguishable from a Rakuten/CJ merchant-authoritative feed.

## Current action

Do **not** add a Lowvyn network adapter yet and do not add Android INTERNET permission.

Lowvyn is worth one concise provider-level rights inquiry because one approval could potentially unlock current-price intelligence across several Canadian retailers without individual merchant feed applications. If the answers establish commercial display/caching/indexing rights, validate actual API fields and freshness next.
