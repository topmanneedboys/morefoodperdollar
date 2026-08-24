# ValuePilot Rakuten Advertising Validation

Updated: 2026-08-24

Milestone:
5D — Authorized Real Shopping Data Provider Selection

Status:
Rakuten publisher account active. Giant Tiger application was denied on 2026-08-24. Well.ca is now under pre-application terms/profile review. No accepted Rakuten advertiser relationship, authorized Product Catalog/feed/API, network DSA approval, caching/indexing/display right, or production Rakuten adapter is established yet.

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

### Authenticated profile evidence

Observed on 2026-08-24:

- Canadian delivery/serviceability
- Product Catalog
- Cross-Device Tracking
- Deep Links
- ITP v2.3
- DSA policy flag: `Allows downloadable software applications`
- real-time tracking
- coupon support through the publisher channel
- multi-touch commissioning is allowed
- advertiser description states more than 40,000 products across roughly 2,800 brands in health, baby, beauty, green/natural and related packaged-goods categories
- an active `Apply` control is visible

This makes Well.ca a strong structured-catalog candidate for package-size/unit-value validation and a strategically useful future ingredient/nutrition/quality source if its authorized catalog preserves the required identity and evidence fields.

Paid-search permissions are visible in the profile but are irrelevant to ValuePilot ranking and provider selection.

### Advertiser-terms evidence captured so far

A substantial portion of the Well.ca Advertiser-Publisher Agreement was reviewed from the authenticated dashboard.

Durable conclusions from the supplied portion:

- specific Engagement terms override inconsistent general Agreement terms
- Well.ca can automatically assign future Offers; publisher has seven days to review before deemed acceptance, while early use of the associated Qualifying Link constitutes immediate acceptance
- publisher is responsible for monitoring notices and maintaining current contact information
- the Agreement expressly recognizes digital properties that provide a user-requested benefit, which is compatible with ValuePilot's comparison/search model
- privacy obligations are substantial: a current accessible privacy policy, required cookie/tracking disclosures, legally required consent, opt-out support and Global Privacy Control handling where applicable
- Rakuten/advertiser tracking technologies must not be circumvented or interfered with
- AI-generated promotional content is expressly contemplated, but must be accurate, reviewed before publication, legally/platform compliant and disclosed where required; the publisher remains responsible for it
- false, misleading or unsubstantiated product claims are prohibited
- the general intellectual-property license is revocable and narrow: Rakuten-platform materials designated in an Engagement may be used on the publisher's digital property for referral purposes; the captured general terms do **not** establish blanket rights to persist, cache, index, republish or redistribute the full Product Catalog
- either party may terminate; qualifying links/materials for terminated engagements must be removed and relevant licenses end
- personal-data handling/deletion obligations apply on termination where required
- audit rights can cover Agreement compliance, commission calculations and data-protection practices
- broad legal/compliance obligations include advertising, privacy, anti-spam, accessibility and IP compliance

Important interpretation:

**Well.ca's `Product Catalog` feature plus DSA allowance is promising, but it is not yet proof of feed access or product-data persistence/display rights.** Those remain separate post-approval/feed-specific validation gates.

### Incomplete-document gate

The Terms & Conditions text supplied for review currently ends mid-way through the General section (around Section 9.2). The remainder has not yet been captured. Therefore the advertiser-specific terms review is not complete.

Current decision:

**PROMISING / HOLD APPLICATION UNTIL THE REMAINDER OF THE TERMS IS REVIEWED.**

Do not click Apply yet. Capture the remaining Well.ca Terms & Conditions starting from the point after Section 9.2, plus the current Offers tab/baseline offer if available. Specifically verify any remaining confidentiality, data-use, trademark/search, linking, DSA/software, modification, termination, non-circumvention, attribution, redistribution or special approval provisions.

If the remainder contains no material conflict, Well.ca should become the next deliberate Rakuten application for controlled 5D validation.

If approved later, inspect the real authorized Product Catalog/feed before implementation, including:

- CAD and Canadian geography semantics
- SKU/GTIN/product identity quality
- package size/count/weight/volume fields
- ingredients/nutrition fields if any
- price/sale/reference-price semantics
- availability/freshness timestamps
- image/deep-link behavior
- feed/API access mechanism and limits
- exact permitted caching, persistence, indexing and display behavior
- any extra advertiser-specific DSA requirement beyond Rakuten network DSA approval/testing

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
11. Automatic Offer Assignment requires active monitoring because silence can become acceptance after the stated review window.
12. Rakuten publisher account access != advertiser relationship != feed/API authorization != DSA approval != permission to cache/index/display product data.

## Next action

Well.ca is now the leading Rakuten candidate, but its supplied Terms & Conditions capture is incomplete.

Capture the remainder of the Well.ca terms after Section 9.2 and the current Offers tab/baseline offer if available. Do not submit the application until the remaining terms have been reviewed.

If the complete terms remain compatible, submit one deliberate Well.ca application and stop again for advertiser decision or authorized-feed evidence.

Walmart remains blocked/ambiguous. Giant Tiger remains denied for the current pass.

Do not implement a Rakuten production adapter during 5D.
