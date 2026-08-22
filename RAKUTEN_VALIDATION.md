# ValuePilot Rakuten Advertising Validation

Research date: 2026-08-22

Milestone:
5D — Authorized Real Shopping Data Provider Selection

Status:
Publisher account active. Advertiser-discovery evidence collected. Giant Tiger partnership application submitted and pending. No accepted advertiser relationship, product-feed/API authorization, DSA approval, caching/indexing/display right, or production adapter is established yet.

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

#### Authenticated advertiser-profile evidence

The Walmart Canada profile was inspected before any partnership request.

Rakuten currently exposes these Walmart Canada features/policies:

- Product Catalog
- Cross-Device Tracking
- Deep Links
- ITP v2
- DSA: `Allows downloadable software applications`
- real-time tracking
- coupons available through the publisher channel

However, the authenticated profile simultaneously displays:

`This Advertiser requires that you meet a set of terms they have supplied. Currently you do not meet these terms and therefore cannot partner with this Advertiser.`

Therefore ValuePilot currently cannot submit a Walmart Canada partnership request through this Rakuten account.

#### Walmart-specific terms evidence

The Walmart.ca Operating Agreement and approval guidance were reviewed from the advertiser's supplied terms.

Important restrictions/evidence:

- the current website must be live, Canadian-focused and have a clear online privacy policy
- Walmart prohibits scraping/spidering Walmart.ca
- Walmart prohibits cookie stuffing and interception/redirect technology
- Walmart prohibits redistributing, displaying or syndicating Walmart Licensed Materials and/or the Walmart.ca data feed to third-party partners, networks or agencies
- price information may be included only when supplied by Walmart; any displayed Walmart price must carry a statement that Walmart.ca's price governs if there is a difference
- product information, including price and availability, must be updated within 24 hours of changes at Walmart.ca or in LinkShare/Rakuten feed content
- the agreement expressly contemplates data-feed content provided through the LinkShare/Rakuten network, which is useful evidence that an authorized affiliate feed can exist
- the terms do not grant a broad right to scrape, independently reconstruct or redistribute Walmart data
- Walmart's approval guidance says sites with downloads (example: toolbars) are to be permanently rejected
- the agreement separately prohibits software download/technology that attempts to intercept or redirect traffic or referral fees
- the Walmart terms also contain confidentiality, audit/record-retention, trademark, social-network, electronic-communication and other operational restrictions

There is therefore an unresolved policy conflict:

- Rakuten profile metadata says Walmart Canada `Allows downloadable software applications`
- Walmart's supplied approval guidance says sites with downloads (e.g. toolbars) should be permanently rejected
- the agreement prohibits intercept/redirect download technology but does not clearly resolve a user-initiated, non-intercepting product-comparison mobile app

Do not treat Rakuten's DSA feature flag as sufficient Walmart mobile-app authorization. Explicit advertiser clarification or approval would be required before any Walmart affiliate use in the ValuePilot mobile app.

Decision:

**DO NOT APPLY / CANNOT APPLY CURRENTLY.** Walmart Canada remains strategically valuable as a future authorized catalog target, but it is not the first production-path candidate because the account is presently ineligible and the advertiser's DSA/download terms are internally ambiguous. Do not contact support merely to force eligibility while stronger candidates remain available.

If Walmart becomes eligible later, validate the actual authorized Product Catalog before any implementation, including whether grocery/consumables rows exist despite 0% commission, actual currency/geography, quantity/unit fields, identifiers, availability/freshness and exact permitted display/caching behavior.

### Giant Tiger

MID 52823.

Observed discovery metadata:

- Shipping and Availability: CA
- Deeplinks: Y
- Auto Approve: N
- advertiser description positions Giant Tiger as a broad Canadian value retailer
- baseline offer text explicitly shows 0% for groceries

#### Authenticated advertiser-profile evidence

The Giant Tiger profile was inspected before the partnership request.

Rakuten exposes these Giant Tiger features/policies:

- Product Catalog
- explicit offer-language statement: `Product feed available`
- Cross-Device Tracking
- Deep Links
- ITP v2.2
- DSA: `Allows downloadable software applications`
- real-time tracking
- coupons available through the publisher channel
- an active `Apply` control was visible; unlike Walmart Canada, no partnership-eligibility block was shown

The profile categories include Clothing & Accessories, Food & Drink, Games & Toys, Health & Beauty and Home & Living, making Giant Tiger materially relevant to everyday Canadian shopping rather than a narrow specialty catalog.

The baseline offer is 1% with 0% for Gift Cards and Groceries. The grocery commission exclusion is commercially irrelevant to ValuePilot ranking and does not establish that grocery rows are absent from the product feed.

The advertiser's visible approval guidance is comparatively simple: interested sites are encouraged to apply; review focuses mainly on site quality and avoiding pornographic/offensive content; advertiser approval remains discretionary.

Paid-search/trademark restrictions are present, including prohibited Giant Tiger brand terms. These do not affect ValuePilot's deterministic product ranking because paid-search bidding is not part of the product model.

#### Full advertiser-terms review

The downloaded Giant Tiger Terms & Conditions were reviewed before application.

Important conclusions:

- the agreement does not introduce a blanket mobile-app/software prohibition that conflicts with Rakuten's `Allows downloadable software applications` profile flag
- links must be genuine, user-initiated affiliate links; automated click-through generation, hidden frames, popups/robots, automatic redirects, forced clicks and similar attribution manipulation are prohibited
- Giant Tiger controls product prices and sales; ValuePilot must not represent itself as Giant Tiger or make unauthorized claims about Giant Tiger products/policies
- Giant Tiger trademark bidding and related paid-search use are restricted; direct linking in paid search is prohibited
- the license is revocable and limited; the general agreement does not by itself grant blanket indefinite caching, indexing, republication or redistribution rights for the entire product feed
- engagement/offer terms can control over inconsistent general terms
- either party can terminate; terms can be modified with notice
- Canadian affiliate-disclosure requirements are called out for sponsored/promotional content
- a Rakuten-required non-circumvention clause restricts direct web advertising/collaboration arrangements between the parties outside Rakuten during the agreement and for 24 months afterward

The terms therefore support submitting a controlled validation application, but approval must still be followed by actual feed-access and feed-rights validation before any production integration.

#### Application status

On 2026-08-22, after the full terms review, ValuePilot submitted a Giant Tiger partnership application through Rakuten.

Current authenticated status reported by Rakuten:

**PENDING (APPLIED)**

Do not submit a duplicate Giant Tiger application.

Decision:

**APPLICATION PENDING.** Giant Tiger remains the leading Rakuten validation candidate because it combines Canadian serviceability, broad everyday-shopping categories, explicit Product Catalog/product-feed availability, DSA allowance, Deep Links and no pre-application eligibility conflict.

If approved, inspect the real authorized Product Catalog/feed before implementation, especially:

- whether grocery rows exist despite 0% commission
- CAD and Canadian geography semantics
- product identifiers and cross-row identity quality
- quantity/size/weight fields needed for unit-value comparison
- price/sale/reference-price semantics
- availability and freshness fields
- image/deep-link behavior
- feed/API access mechanism and limits
- exact permitted caching, persistence, indexing and display behavior
- whether mobile-app use requires any additional advertiser-specific DSA step beyond Rakuten's network-level DSA approval/testing

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

Do not apply while Giant Tiger is pending unless newer evidence gives a specific reason to add a second Rakuten validation candidate.

### Newegg Canada

MID 44589.

Observed discovery metadata:

- Shipping and Availability: CA
- Deeplinks: Y
- Auto Approve: N
- categories include Consumer and Electronic

Decision:
Useful secondary candidate for electronics/product-identity/variant validation, but less important than broad everyday-shopping candidates for ValuePilot's initial value proposition.

### Other useful secondary candidates

The same filtered export also exposed Canadian-relevant advertisers such as LG Canada, Tru Earth, Bass Pro Shops & Cabela's Canada, Miele Canada, Greenworks Tools Canada, Jamieson Vitamins, CanadaPetCare, FortNine and Bath Depot / Bain Dépôt.

Do not apply to these merely to accumulate advertiser relationships. They are fallback/category-specific validation candidates.

## Permanent interpretation rules

1. `Ships to Canada` is not the same as `advertiser located in Canada`.
2. `Product Catalog` visibility is not proof of actual feed access after partnership.
3. `Product Catalog` visibility is not proof of field quality.
4. `Deeplinks = Y` is not DSA/mobile permission.
5. `DSA allowed` in Rakuten profile metadata is not sufficient when advertiser-specific terms conflict or impose additional restrictions.
6. `Auto Approve = Y` would only describe relationship workflow; it would not authorize product-data caching/indexing/display or DSA use.
7. A 0% commission category can still be strategically valuable product evidence if the advertiser permits catalog use. Commission must not affect ValuePilot ranking or provider-quality scoring.
8. Advertiser feed availability is not permission to scrape the advertiser website.
9. Advertiser approval is not proof of feed/API authorization or feed-field quality.
10. Rakuten publisher account access != advertiser relationship != feed/API authorization != DSA approval != permission to cache/index/display product data.

## Next action

Giant Tiger application is pending. Do not submit a duplicate application and do not add random Rakuten advertiser relationships while this high-information application is under review.

If Giant Tiger is approved, immediately inspect the authorized Product Catalog/feed and any feed-specific terms before implementation. If declined, record the reason if supplied and inspect Well.ca next rather than trying to force approval.

Walmart Canada remains blocked/ambiguous and should not be pursued while better candidates are available.

Do not implement a Rakuten production adapter during 5D.
