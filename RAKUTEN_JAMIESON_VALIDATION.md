# ValuePilot Rakuten Jamieson Vitamins Validation

Updated: 2026-08-28

Milestone:
5D — Authorized Real Shopping Data Provider Selection

Status:
Jamieson Vitamins has accepted ValuePilot's Rakuten advertiser partnership. Rakuten Customer Support has now explicitly confirmed that ValuePilot is approved for the Jamieson advertiser Product Feed and that the Jamieson feed is present in the authorized Product Catalog SFTP account. The complete pipe-delimited TXT catalog was downloaded and empirically inspected offline. This establishes advertiser-level feed access and actual file availability. It does **not** by itself establish blanket caching, persistence, indexing, redistribution, mobile-display, or production-use rights, and it does not establish Rakuten DSA approval for production app links.

No file-transfer username, password, publisher/account identifier, token, or other credential belongs in this repository.

## Why Jamieson is high-information for ValuePilot

Jamieson is a strong packaged-product validation source because its catalog exercises exact identity, price, availability, links, images and GTINs across many supplement variants. It is also strategically useful for the future ingredient/label/quality evidence layer.

ValuePilot must not convert supplement marketing into medical efficacy claims. Health/wellness claims are not ranking evidence unless separately authorized, sourced and deliberately modeled under an appropriate future policy. Current validation is about product identity, price, package/count/quantity evidence, ingredients/label evidence where authorized, and deterministic value comparisons.

## Authenticated Rakuten profile and Offer evidence

Observed before feed approval:

- active advertiser partnership
- Product Catalog
- Cross-Device Tracking
- Deep Links
- ITP v2.2
- DSA policy flag: `Allows downloadable software applications`
- real-time tracking
- coupon support through the publisher channel
- Offer permits standard banner/text/product/search/storefront-style affiliate links subject to advertiser authorization requirements
- paid-search policy includes a specific restriction against language suggesting health claims and requires advertiser-provided logos for that context

Commission/cookie-window economics are not provider-quality inputs and must not influence ValuePilot ranking or provider selection.

## Advertiser agreement review

The advertiser-specific agreement was reviewed before application.

Durable conclusions:

- Offer/Engagement-specific terms can override inconsistent general agreement terms
- automatic Offer assignment exists, with a seven-day review period before deemed acceptance; notices must therefore be monitored
- the digital property carrying Qualifying Links must provide a user-requested benefit, compatible with ValuePilot's comparison/search model
- privacy, cookie/tracking consent, opt-out and Global Privacy Control obligations are substantial and must be implemented before production tracking use where applicable
- AI/automated promotional content is contemplated only if accurate, reviewed, compliant and disclosed where required
- false, misleading or unsubstantiated product claims are prohibited
- the general intellectual-property/content license is narrow and referral-oriented; it does not establish blanket rights to persist, cache, index, republish or redistribute the full Product Catalog
- termination ends relevant link/material licenses and can trigger personal-data handling obligations
- audit, confidentiality, security and breach-notification obligations apply
- no blanket mobile-app/software prohibition or anti-comparison restriction was found that conflicts with the authenticated DSA policy allowing downloadable software applications

## Health-claim guardrail

Jamieson's paid-search policy explicitly restricts wording that suggests health claims, and the general agreement independently prohibits false, misleading or unsubstantiated claims.

ValuePilot therefore must not infer or generate statements such as one supplement being healthier, more effective, safer, clinically better, or medically preferable merely from product names, ingredients, marketing copy, ratings or AI analysis.

Acceptable first-stage comparison dimensions remain objective and source-grounded, such as price, count, package quantity, strength as printed on the product, ingredient/label facts as supplied, and transparent unit-value calculations. Any future health-oriented interpretation requires a separate deliberate product/safety/legal design review.

## Product Catalog approval and actual file availability

On 2026-08-26 Rakuten Customer Support created ValuePilot's Product Catalog file-transfer account. ValuePilot then requested Jamieson advertiser-level Product Catalog access under `Links -> Product Feeds`.

On 2026-08-28 Rakuten Customer Support explicitly confirmed both of the following:

1. ValuePilot is approved for Jamieson Vitamins' advertiser Product Feed.
2. The Jamieson feed is visible in ValuePilot's authorized Product Catalog SFTP account.

ValuePilot then connected to the Product Catalog SFTP account and observed the expected root-level complete catalog plus delta/template-related files and merchant/category folders. The complete `.txt.gz` catalog was downloaded for offline inspection.

Decision:

**JAMIESON PRODUCT CATALOG = ADVERTISER APPROVED + ACTUAL COMPLETE FEED AVAILABLE.**

This is the first ValuePilot provider for which the milestone has advanced through publisher access, advertiser partnership, advertiser Product Catalog approval and actual authorized file availability.

It is still **not production-authorized** until the remaining data-use-rights and semantic gates below are deliberately cleared.

## First real-feed empirical audit — 2026-08-28

The downloaded complete TXT feed was decompressed and parsed offline. The raw proprietary catalog itself is not committed to this repository.

Observed structural results:

- header/trailer structure valid
- trailer declares **273** products and exactly **273** product records were present
- all **273** product rows have the documented 38-field shape
- **273 / 273** rows use `CAD`
- **273 / 273** rows report `in-stock`
- **273 / 273** rows have positive Retail Price
- **273 / 273** rows also contain a positive Sale Price
- **273 / 273** product URLs are syntactically valid HTTP(S) URLs
- **273 / 273** image URLs are syntactically valid HTTP(S) URLs
- **273** unique SKUs
- **273** unique Product IDs
- **271 / 273** rows contain a UPC/GTIN and all **271 / 271** supplied codes pass checksum validation
- **2** rows have no supplied UPC/GTIN
- manufacturer name is present as Jamieson across all 273 rows
- short/long description coverage is 272 / 273
- all 273 Class ID values are blank

Price relationship observed in the real feed:

- **48** rows: Sale Price < Retail Price
- **223** rows: Sale Price = Retail Price
- **2** rows: Sale Price > Retail Price

The two `Sale Price > Retail Price` rows are concrete evidence that ValuePilot must not blindly interpret any positive `Sale Price` as a valid discount or preferred current price. Sale/reference-price semantics require deterministic validation. A malformed or counter-intuitive price relationship must never create a false savings claim.

The current offline qualifier can structurally recognize all 273 rows as CAD offer candidates, but that is only qualification coverage. It is not permission to rank those rows as current production offers.

## Quantity/unit-value gate

The real Jamieson feed exposed the most important remaining data limitation:

- the primary Rakuten schema has no universal structured package-quantity field
- all 273 Jamieson Class ID values are blank, so no validated class-specific Size field is available through the documented class mechanism
- package count/quantity must therefore **not** be guessed from product title, description, image filename, SKU, price or neighboring variants

Decision:

**CURRENT STRUCTURAL OFFER CANDIDATES = 273. CURRENT AUTHORITATIVE UNIT-VALUE CANDIDATES = 0 UNTIL QUANTITY IS ESTABLISHED BY A VALIDATED SOURCE.**

A future quantity join may use another authorized or appropriately licensed source matched by strong product identity such as checksum-valid GTIN. Provenance must remain separate: the source contributing package quantity must not be represented as the source of Jamieson's merchant price.

## Variant identity finding

Repeated product names occur in the feed, but distinct rows can have different Product IDs, SKUs, GTINs and prices. Name-only deduplication is therefore unsafe.

Permanent rule:

**Product name is not identity. Product and Offer/variant identity must preserve strong source identifiers and GTIN evidence where available.**

## Remaining production gates

Before a production Rakuten/Jamieson adapter or consumer ranking use, validate:

- exact Sale Price versus Retail Price meaning, including equal and inverted relationships
- whether the feed represents the intended Canadian consumer offer geography beyond the observed CAD currency and advertiser context
- package/count/strength/dosage-form evidence from a validated source
- per-product freshness semantics; the file HDR timestamp is file-generation/deposit evidence and must not be promoted to per-product freshness
- exact caching/persistence/indexing/search/display rights
- exact mobile-app/software/catalog-use rights for ValuePilot's intended presentation
- any required Rakuten network DSA submission/testing before affiliate links are used in the Android app
- disclosure/privacy/consent requirements before production tracking links are enabled

Do not infer these rights from feed availability alone.

## Engineering direction after the first real feed

Use the existing offline `tools/qualify_rakuten_product_catalog.py` boundary for deterministic feed research. Keep it network-free and separate from the Android production runtime.

Next engineering work should harden the qualifier around the real-feed findings, especially explicit `sale < retail`, `sale == retail`, and `sale > retail` counts, then design a provider-neutral import mapping that preserves raw source identity and price semantics without inventing quantity or freshness.

Do not commit the proprietary full catalog as a test fixture. Use small synthetic fixtures for parser/regression tests unless the governing terms explicitly permit redistribution of catalog rows.

Do not implement SFTP credentials, automatic downloading, Android networking, or production affiliate-link behavior merely because the feed is now accessible.