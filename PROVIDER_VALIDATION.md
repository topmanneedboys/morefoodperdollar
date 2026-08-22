# ValuePilot Provider Validation Record

Updated: 2026-08-22

Milestone:
5D — Authorized Real Shopping Data Provider Selection

Status:
Empirical Awin account/feed validation in progress.
No production provider or production network integration is authorized by this document.

This file records observations from real publisher tooling and downloaded authorized sample feeds so future work does not treat provider documentation or schema labels as sufficient evidence of data quality.

## Safety and repository hygiene

Do not commit:

- publisher credentials
- feed-download credentials or tokenized feed URLs
- account passwords
- private API keys
- raw URLs containing publisher-specific authentication material

The raw sample feeds used for this validation remain external evidence files and are not committed to the repository.

## Current Awin account-level state

Observed through the ValuePilot publisher account on 2026-08-22:

- the publisher account is activated
- the primary promotional type is Comparison Engine
- a Canada + Retail & Shopping + Product Feed = Yes directory filter produced 154 Not Applied programs in the exported advertiser directory
- Create-a-Feed exposes advertiser-specific feed metadata such as format/name, feed ID, last update and product count for feeds currently available to the publisher
- feed-enabled programs are not necessarily selectable before joining the advertiser program

Permanent distinction:

**feed existence != publisher authorization != offer geographic scope**

These are three independent facts and must be represented/validated independently.

## Skip CA advertiser validation

Awin advertiser:
Skip CA

Advertiser ID:
107752

Observed account/program evidence on 2026-08-22:

- ShopWindow Total Products: 50,226
- ShopWindow feed shown as recently updated
- program region shown as Canada
- Mobile Optimised: Yes
- App Tracking: Yes
- program terms explicitly include Comparison Engine among allowed partner types
- program materials describe data-feed and deep-link assets
- restaurants are excluded from participation in the affiliate program

The program terms also contain apparent inherited Just Eat branding language. Treat such boilerplate cautiously and do not infer rights that are not explicit in the current program terms or later written approval.

ValuePilot submitted an application to join Skip CA on 2026-08-22.

Current status at the time of this record:
**PENDING advertiser approval**

Do not submit a duplicate application.

If approved, the next valuable validation is the actual Skip feed, specifically:

- whether the roughly 50k records are useful retail/grocery/convenience product offers
- merchant/retailer identity
- physical store or geographic identity
- commerce-channel scope
- product identity and provider SKU
- GTIN/UPC/EAN quality
- brand/category quality
- current price
- regular/sale price semantics
- promotion terms and effective dates
- pack quantity/size
- unit-price evidence
- availability
- image/product/deep-link fields
- row-level or feed-level freshness
- whether records are marketplace/channel prices rather than physical-store prices

A channel price must never be presented as a retailer's universal in-store price without explicit evidence.

## Empirical feed test 1 — Sportdirect.ca (Canada)

Awin advertiser ID:
25243

Feed ID:
60799

Downloaded sample dimensions:

- 11,614 rows
- 93 columns
- 11,614 unique Awin product IDs
- 11,614 unique merchant product IDs
- 2,843 underlying Shopify product identifiers extracted from the merchant product ID pattern

### Strong/basic coverage

Observed non-empty coverage:

- product_name: 11,614 / 11,614
- aw_product_id: 11,614 / 11,614
- merchant_product_id: 11,614 / 11,614
- search_price: 11,614 / 11,614
- display_price: 11,614 / 11,614
- currency: 11,614 / 11,614, all CAD
- merchant_deep_link: 11,614 / 11,614
- merchant_image_url: 11,604 / 11,614
- in_stock: 11,614 / 11,614
- is_for_sale: 11,614 / 11,614

The feed therefore demonstrates useful basic e-commerce catalog/offer transport.

### Semantic field corruption/mis-mapping

The same feed demonstrates why ValuePilot must not trust provider field labels blindly.

`product_GTIN`:

- non-empty on 10,530 / 11,614 rows
- 0 of the non-empty values are digit-only GTIN candidates
- values include French category-like strings such as `Matelas de réception`, `Gants de DEK Hockey`, `Buts de soccer` and `Natation`

Conclusion:
The field label says GTIN, but the values are not GTIN evidence.

`mpn`:

- populated on all 11,614 rows
- only one distinct value: `Sportdirect.ca`

Conclusion:
A populated MPN field can still carry no item-level MPN information.

`ShoppingNL:size`:

- non-empty on 184 rows
- common values include `Blue`, `Red`, `Gray`, `Black`, `Green`, `Royal` and similar colour names

Conclusion:
The field behaves like colour evidence for these records, not trustworthy size evidence.

`ShoppingNL:material`:

- non-empty on 756 rows
- common values include dimensions such as `32"x24"x21"`, `24"x18"x10"` and `7' x 21'`

Conclusion:
The field behaves like dimensions for many records, not trustworthy material evidence.

`ShoppingNL:google_taxonomy`:

- populated on all 11,614 rows
- values are merchant product URLs

Conclusion:
A URL-shaped value cannot be accepted as taxonomy merely because the column is named taxonomy.

### Missing structured evidence

Observed empty fields across the sample include:

- brand_name
- merchant_category
- category_name
- base_price
- base_price_amount
- base_price_text
- delivery_weight
- last_updated

Useful quantity information can still exist in product names/descriptions and may be recoverable by deterministic parsing, but missing structured fields must remain missing rather than being fabricated.

### Suspicious regular-price semantics

`rrp_price` is populated on 718 rows.

After parsing the numeric amount:

- all 718 RRP values are lower than the corresponding `search_price`
- 0 are higher
- 0 are equal

Example observed relationship:

- Garmin Venu X1 GPS Smartwatch: search price CAD 979.99, `rrp_price` value CAD 839.99

Do not automatically interpret this feed's `rrp_price` as a regular/reference price or derive a discount from it until the advertiser/feed semantics are independently verified.

### Sportdirect conclusion

This feed is useful for basic catalog transport but is not safe for direct universal field-to-domain mapping.

It requires advertiser/feed-specific mapping plus semantic validation before evidence can become trusted ValuePilot Product/Offer data.

## Empirical feed test 2 — Sinocare

Awin advertiser ID:
114180

Feed ID:
104051

Downloaded sample dimensions:

- 83 rows
- 86 columns
- 83 unique Awin product IDs
- 83 unique merchant product IDs

### Strong/basic coverage

Observed non-empty coverage:

- product_name: 83 / 83
- aw_product_id: 83 / 83
- merchant_product_id: 83 / 83
- search_price: 83 / 83
- display_price: 83 / 83
- merchant_deep_link: 83 / 83
- merchant_image_url: 83 / 83
- brand_name: 83 / 83, all Sinocare
- merchant_category: 83 / 83, all Medical
- category_name: 83 / 83, all Health
- product_type: 83 / 83
- in_stock: 83 / 83
- is_for_sale: 83 / 83
- ean: 83 / 83
- mpn: 83 / 83
- delivery_time: 83 / 83

This demonstrates that Awin feeds can contain much more coherent structured identity/category data than the Sportdirect sample.

### EAN validation

The 83 EAN field values were checked as GTIN candidates using:

1. digit-only syntax
2. supported GTIN lengths
3. GTIN check-digit validation

Result:

- 83 / 83 pass those structural checks
- 51 unique EAN values exist across 83 rows
- 13 EAN values are associated with more than one distinct product-name row
- some repeated EANs span different bundle/quantity or measurement-unit variants

Conclusion:

A syntactically valid, check-digit-valid GTIN is stronger identity evidence, but it still does not automatically prove that the identifier uniquely describes the complete offer/variant represented by a row.

Future identity validation must also test cross-row and semantic consistency.

### Geographic/currency validation

All 83 Sinocare rows use:

- currency: GBP
- display prices denominated in GBP

This feed was discoverable from a Canada-oriented Awin feed-selection workflow, yet the actual offer currency is not CAD.

Permanent conclusion:

**A program/feed being visible through a Canada-oriented Awin workflow does not prove that its individual offer records are Canadian consumer price evidence.**

Offer geography/currency must be established from actual offer evidence, not inferred from publisher eligibility or program-region filtering.

### Sinocare conclusion

The sample has substantially better structured identity/category mapping than Sportdirect, but it still requires:

- exact offer-geography validation
- cross-row identifier consistency checks
- advertiser/feed-specific semantic validation

It is not trustworthy Canadian price evidence merely because it was available while working in a Canada-oriented account/feed view.

## Permanent ingestion implications from the empirical tests

A future authorized provider pipeline should conceptually follow:

```text
raw provider feed
    ↓
feed/account authorization metadata
    ↓
advertiser/feed-specific mapping profile
    ↓
field syntax validation
    ↓
semantic validation
    ↓
geography + currency validation
    ↓
cross-row identity consistency
    ↓
normalized Product + Offer evidence
    ↓
ShoppingEvidence / trust policy
    ↓
deterministic ValuePilot ranking
```

Do not implement these conceptual stages merely because this document names them. 5D remains a selection/validation milestone until a later explicit integration milestone authorizes production networking and domain changes.

## Required validator behavior for future provider integration

### Product identifiers

GTIN/EAN/UPC candidates should be evaluated for:

- syntax
- supported length
- check digit
- cross-row consistency
- relationship to exact product/bundle/variant semantics

An invalid or semantically inconsistent value must not silently become strong identity evidence.

### MPN/provider identifiers

A populated field is not automatically useful.

Reject or downgrade values that are obviously merchant-wide constants, category labels, URLs or other non-item evidence when the field claims item identity.

### Prices

Validate:

- numeric amount
- explicit currency
- plausible field semantics
- regular/current/sale relationships
- effective dates when available

Do not calculate a discount merely because a feed supplies two price-like fields.

### Quantity and unit value

Prefer explicit trustworthy quantity evidence.

Where structured quantity fields are absent or mis-mapped, deterministic parsing of product names/descriptions may provide derived evidence under the existing evidence hierarchy.

Do not reinterpret colours, dimensions or arbitrary strings as measurement quantities because of a schema label.

### Geography

Keep separate:

- publisher/account eligibility region
- advertiser/program region
- feed scope
- merchant/store geography
- individual offer geography
- currency
- commerce channel
- fulfillment scope

No one field may silently substitute for all the others.

### Freshness

`last_updated` was empty at the row level in both downloaded samples.

Awin's feed-selection/feed-list tooling can expose feed-level update information, but feed-level freshness and row-level observation freshness are different concepts.

ValuePilot must preserve the strongest truthful freshness granularity available and must not invent row timestamps from weaker metadata.

## Current 5D decision after these tests

Awin remains the first broad authorized catalog/offer rail under validation.

The empirical tests do **not** justify a universal Awin field mapper and do **not** yet justify a production Awin adapter.

Instead, they justify these permanent rules:

1. validate advertiser/feed semantics independently
2. validate field values, not merely field names
3. keep authorization independent from data quality
4. keep geographic scope independent from program visibility
5. preserve provider provenance through normalization
6. keep ValuePilot ranking independent of affiliate economics
7. reject or downgrade semantically invalid evidence rather than guessing

Random low-value feed downloads should stop. The next high-value Awin evidence is the actual Skip CA feed if/when advertiser access is approved.

If Awin cannot supply sufficiently useful authorized Canadian shopping evidence, continue the deliberate validation order already recorded in `PROVIDER_SELECTION.md`:

1. impact.com
2. CJ Affiliate
3. Rakuten Advertising

Flipp and GS1 Canada remain parallel strategic targets for local/store pricing/promotions and trusted product identity respectively.

## International architecture consequence

The Sinocare currency/geography mismatch reinforces that ValuePilot must be multi-country and currency-aware at the architecture level even while Canada remains the first launch/readiness target.

Do not hard-code CAD, Canadian geography or Canadian retailer assumptions into provider-neutral core logic.

Public support for a country should still be gated by sufficient trustworthy merchant/category coverage, correct currency/unit handling, provider reliability and useful consumer search quality.

## Verification status

This is a documentation-only 5D evidence record.

No production code, manifest, permissions, tests or ranking logic were changed by this record.

Android tests were not rerun for this documentation-only change.
