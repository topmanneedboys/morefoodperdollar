# ValuePilot Rakuten Advertising Validation

Research date: 2026-08-22

Milestone:
5D — Authorized Real Shopping Data Provider Selection

Status:
Publisher account active. Advertiser-discovery evidence collected. No advertiser relationship, product-feed/API authorization, DSA approval, caching/indexing/display right, or production adapter is established yet.

## Empirical discovery evidence

Observed in the authenticated Rakuten Publisher Dashboard on 2026-08-22:

- advertiser discovery exposes filters for partnership status, advertiser location, shipping location, coupons, creatives and feature flags
- available feature filters include Product Catalog, Deep Links, Auto-Approve, Cross-Device Tracking, ITP and Media Optimization Reporting
- no DSA/software-compatibility filter was visible in the advertiser-discovery UI; DSA compatibility must therefore be validated separately from advertiser profile/terms or a supported API surface
- the advertiser directory can export filtered results as CSV

For the first broad Canadian catalog pass, the directory was filtered around Canadian serviceability plus Product Catalog while intentionally leaving advertiser headquarters unrestricted and leaving commission economics out of selection.

The resulting CSV contained 737 advertiser rows. Relevant exported fields included:

- MID
- Advertiser Name
- Advertiser Description
- Categories
- Partnership
- Advertiser Status
- Shipping and Availability
- product/text/banner creative flags
- Deeplinks
- ITP
- Auto Approve
- product-link/catalog-related feature flag
- Baseline Offer
- Commission Range

Commission and payout data are not provider-quality inputs and must never influence ValuePilot value ranking.

## High-information Canadian candidates discovered

### Walmart Canada

MID 36751.

Observed discovery metadata:

- Shipping and Availability: CA
- categories include Bath/Body, Equipment, Hardware and Improvement
- Deeplinks: Y
- Auto Approve: N
- advertiser description explicitly references groceries plus non-essentials, delivery and pickup on Walmart.ca
- baseline offer text shows 0% for Consumables, Video Games, Electronics and Grocery

The 0% affiliate payout for some categories is commercially irrelevant to ValuePilot ranking. It does not establish whether those categories are absent from Product Catalog, nor does it establish permission to ingest/display them.

Decision:
HIGHEST-VALUE RAKUTEN PROFILE TO INSPECT FIRST because it can test broad Canadian retail plus grocery/household relevance.

Before applying, inspect advertiser profile and offer/program terms for:

- Product Catalog availability and scope
- whether grocery/consumables appear in the feed despite 0% payout
- DSA/mobile-app permission or approval requirements
- comparison-shopping compatibility
- deep-linking restrictions
- geographic/currency semantics
- caching/indexing/display rights
- trademark/search restrictions
- feed freshness and availability fields

### Giant Tiger

MID 52823.

Observed discovery metadata:

- Shipping and Availability: CA
- Deeplinks: Y
- Auto Approve: N
- advertiser description positions Giant Tiger as a broad Canadian value retailer
- baseline offer text explicitly shows 0% for groceries

Decision:
SECOND broad Canadian retail candidate. Valuable as an independent test of grocery/general-merchandise coverage and whether zero-commission product categories are still available as authorized catalog evidence.

### Well.ca

MID 53166.

Observed discovery metadata:

- Shipping and Availability: CA
- Deeplinks: Y
- Auto Approve: N
- advertiser description says it carries more than 40,000 products from 2,800 brands
- categories include Baby, Bath/Body, Children, Gifts and Home

Decision:
HIGH-VALUE structured-catalog candidate for health, baby, beauty, household and packaged-goods comparison. It is likely to provide useful package-size/unit-value test cases if the actual catalog preserves quantities and identifiers.

### Newegg Canada

MID 44589.

Observed discovery metadata:

- Shipping and Availability: CA
- Deeplinks: Y
- Auto Approve: N
- categories include Consumer and Electronic

Decision:
Useful secondary candidate for electronics/product-identity/variant validation, but less important than Walmart Canada, Giant Tiger and Well.ca for ValuePilot's everyday-shopping value proposition.

### Other useful secondary candidates

The same filtered export also exposed Canadian-relevant advertisers such as LG Canada, Tru Earth, Bass Pro Shops & Cabela's Canada, Miele Canada, Greenworks Tools Canada, Jamieson Vitamins, CanadaPetCare, FortNine and Bath Depot / Bain Dépôt.

Do not apply to these merely to accumulate advertiser relationships. They are fallback/category-specific validation candidates.

## Permanent interpretation rules

1. `Ships to Canada` is not the same as `advertiser located in Canada`.
2. `Product Catalog` visibility is not proof of actual feed access after partnership.
3. `Product Catalog` visibility is not proof of field quality.
4. `Deeplinks = Y` is not DSA/mobile permission.
5. `Auto Approve = Y` would only describe relationship workflow; it would not authorize product-data caching/indexing/display or DSA use.
6. A 0% commission category can still be strategically valuable product evidence if the advertiser permits catalog use. Commission must not affect ValuePilot ranking or provider-quality scoring.
7. Rakuten publisher account access != advertiser relationship != feed/API authorization != DSA approval != permission to cache/index/display product data.

## Next action

Inspect Walmart Canada's Advertiser Profile first, not `View offer` blindly and not a random application.

Capture the profile and program/offer terms before applying. Specifically resolve software/DSA rules, comparison-shopping fit, Product Catalog scope, grocery/consumables availability, data-use rights and any advertiser-specific restrictions.

If Walmart Canada is unsuitable, inspect Giant Tiger next, then Well.ca.

Do not implement a Rakuten production adapter during 5D.
