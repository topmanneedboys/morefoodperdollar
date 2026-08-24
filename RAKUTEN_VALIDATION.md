# ValuePilot Rakuten Advertising Validation

Updated: 2026-08-24

Milestone:
5D — Authorized Real Shopping Data Provider Selection

Status:
Rakuten publisher account active. Giant Tiger application was denied on 2026-08-24. No accepted Rakuten advertiser relationship, authorized Product Catalog/feed/API, network DSA approval, caching/indexing/display right, or production Rakuten adapter is established yet.

## Empirical discovery evidence

Authenticated Rakuten advertiser discovery exposed filters for partnership status, advertiser location, shipping location, coupons, creatives and features including Product Catalog, Deep Links, Auto-Approve, Cross-Device Tracking and ITP.

The first Canadian catalog pass used Canadian serviceability plus Product Catalog while intentionally ignoring commission economics. The export contained 737 advertiser rows and exposed fields such as MID, advertiser name/description, categories, partnership/status, shipping/availability, creative flags, Deeplinks, ITP, Auto Approve, catalog/product-link-related metadata, baseline offer and commission range.

Commission and payout data are not provider-quality inputs and must never influence ValuePilot ranking.

## Walmart Canada

MID 36751.

Observed authenticated profile evidence:

- Product Catalog
- Cross-Device Tracking
- Deep Links
- ITP v2
- DSA profile flag: `Allows downloadable software applications`
- real-time tracking
- Canadian serviceability

However, Rakuten simultaneously displayed that ValuePilot did not meet Walmart's supplied terms and therefore could not partner with the advertiser.

Walmart-specific terms also created an unresolved software/download ambiguity: Rakuten's DSA metadata allowed downloadable software, while Walmart approval guidance said sites with downloads (example: toolbars) should be permanently rejected and separately prohibited intercept/redirect software.

Other durable Walmart conclusions:

- scraping/spidering Walmart.ca is prohibited
- an authorized LinkShare/Rakuten data feed is contemplated
- redistribution/display/syndication restrictions apply to Licensed Materials/data feed
- price can be displayed only when supplied by Walmart and Walmart.ca governs discrepancies
- product price/availability updates must follow Walmart/feed freshness requirements
- broad independent reconstruction or scraping rights are not granted

Decision:

**DO NOT APPLY / CANNOT APPLY CURRENTLY.** Walmart remains a strategically valuable future target, but not the current path.

## Giant Tiger

MID 52823.

### Pre-application evidence

Authenticated profile evidence included:

- Canadian serviceability
- Product Catalog
- explicit `Product feed available`
- Cross-Device Tracking
- Deep Links
- ITP v2.2
- DSA profile flag: `Allows downloadable software applications`
- real-time tracking
- broad everyday-shopping categories including Food & Drink, Health & Beauty and Home & Living
- active Apply control with no Walmart-style eligibility block

The baseline offer showed 0% for groceries. This was correctly treated as commercially irrelevant to provider quality and not evidence that grocery rows were absent from the feed.

### Full advertiser-terms review

The Giant Tiger advertiser Terms & Conditions were reviewed before application.

Durable conclusions:

- no blanket mobile-app/software prohibition conflicted with the Rakuten DSA profile flag
- affiliate links must be genuine/user-initiated; automated click generation, hidden frames, robots, automatic redirects, forced clicks and similar attribution manipulation are prohibited
- Giant Tiger controls product prices and sales
- ValuePilot must not represent itself as Giant Tiger or make unauthorized product/policy claims
- Giant Tiger trademark bidding and related paid-search use are restricted
- the general license is revocable and limited
- the general agreement does not itself prove blanket indefinite caching, indexing, republication or redistribution rights for the entire product feed
- engagement/offer terms can control over inconsistent general terms
- either party may terminate and terms may be modified with notice
- Canadian affiliate-disclosure requirements are called out
- a Rakuten-required non-circumvention clause restricts direct web advertising/collaboration arrangements outside Rakuten during the agreement and for 24 months afterward

These terms were sufficient for a controlled validation application, but not for production feed integration.

### Application and denial

ValuePilot submitted the Giant Tiger partnership application on 2026-08-22.

On 2026-08-24 Rakuten sent an `Application Denied` notice stating that Giant Tiger chose not to accept ValuePilot into its affiliate program at this time.

Rakuten did **not** provide a specific Giant-Tiger-supplied reason. The notice listed generic possible reasons, including inability to access the website, website not yet live, low traffic, or content mismatch. None of those examples should be recorded as the actual reason without direct advertiser evidence.

Decision:

**DENIED / DO NOT REAPPLY NOW.**

Do not contact or pressure Giant Tiger merely to reverse the decision while other credible candidates remain. Reconsider later only if ValuePilot gains materially stronger public proof, traffic, maturity or provider relationships, or Giant Tiger supplies a concrete reason that can be addressed.

The denial does not invalidate the pre-application technical conclusions. It only means Giant Tiger is not currently an authorized data path.

## Well.ca

MID 53166.

Discovery evidence already observed:

- Shipping and Availability: CA
- Deeplinks: Y
- Auto Approve: N
- advertiser description says it carries more than 40,000 products from 2,800 brands
- categories include Baby, Bath/Body, Children, Gifts and Home

Why it is valuable:

- broad packaged-goods catalog relevant to health, baby, beauty and household comparison
- likely useful for package-size/unit-value validation
- potentially useful later for ingredient/nutrition/quality evidence if the authorized catalog actually supplies those fields or stable identities that can be joined to authorized sources

Current decision:

**NEXT RAKUTEN CANDIDATE TO INSPECT — DO NOT APPLY YET.**

Before any Well.ca application, capture and review:

- Product Catalog/feed availability
- DSA/software/mobile compatibility
- Deep Links
- advertiser-specific terms
- comparison-shopping compatibility
- data-use/display/caching/indexing restrictions
- redistribution restrictions
- privacy/tracking requirements
- trademark/search restrictions
- termination/approval conditions

If compatible, submit one deliberate Well.ca application and then stop again for the advertiser decision or authorized-feed gate.

## Newegg Canada

MID 44589.

Observed discovery evidence includes Canadian serviceability and Deeplinks. It remains a useful secondary candidate for electronics/product-identity/variant validation, but is less important than broad everyday-shopping catalogs for ValuePilot's initial proposition.

## Other secondary candidates

The filtered export also exposed Canadian-relevant advertisers such as LG Canada, Tru Earth, Bass Pro Shops & Cabela's Canada, Miele Canada, Greenworks Tools Canada, Jamieson Vitamins, CanadaPetCare, FortNine and Bath Depot / Bain Dépôt.

Do not apply merely to accumulate advertiser relationships.

## Permanent interpretation rules

1. `Ships to Canada` is not the same as `advertiser located in Canada`.
2. `Product Catalog` visibility is not proof of actual feed access after partnership.
3. `Product Catalog` visibility is not proof of field quality.
4. `Deeplinks = Y` is not DSA/mobile permission.
5. `DSA allowed` in profile metadata is not sufficient when advertiser-specific terms conflict or impose additional restrictions.
6. `Auto Approve = Y` would describe relationship workflow only; it would not authorize product-data caching/indexing/display or DSA use.
7. A 0% commission category can still be valuable product evidence if the advertiser permits catalog use. Commission must never influence ranking or provider-quality scoring.
8. Advertiser feed availability is not permission to scrape the advertiser website.
9. Advertiser approval is not proof of feed/API authorization, feed-field quality, or persistence/display rights.
10. A denial email's generic examples are not evidence of the actual denial reason.
11. Rakuten publisher account access != advertiser relationship != feed/API authorization != DSA approval != permission to cache/index/display product data.

## Next action

Giant Tiger is closed for the current validation pass.

The next Rakuten action is to inspect **Well.ca** before applying. Do not submit the application until its authenticated profile and full advertiser-specific terms have been reviewed.

Walmart remains blocked/ambiguous and should not be pursued while better candidates are available.

Do not implement a Rakuten production adapter during 5D.
