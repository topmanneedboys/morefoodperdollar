# ValuePilot Rakuten Jamieson Vitamins Validation

Updated: 2026-08-26

Milestone:
5D — Authorized Real Shopping Data Provider Selection

Status:
Jamieson Vitamins has accepted ValuePilot's Rakuten advertiser partnership. Rakuten has also completed the publisher-side technical Product Catalog setup, and ValuePilot has submitted the separate Jamieson Product Catalog access request from `Links -> Product Feeds`. The Product Feeds row now shows `Remove`, which establishes that the request/enrollment is active, but it must not be treated as proof of advertiser-level feed approval until Rakuten exposes an approved status or the Jamieson feed appears in the SFTP account. No feed-specific caching/indexing/display right, network DSA approval, or production integration is established.

## Why Jamieson is high-information for ValuePilot

Jamieson is a strong packaged-product candidate because its catalog can exercise exact variant identity across count, strength, dosage form and related package attributes. It is also strategically useful for the future ingredient/nutrition/quality evidence layer.

ValuePilot must not convert supplement marketing into medical efficacy claims. Health/wellness claims are not ranking evidence unless separately authorized, sourced and deliberately modeled under an appropriate future policy. Current validation is about product identity, price, package/count/quantity evidence, ingredients/label evidence where authorized, and deterministic value comparisons.

## Authenticated Rakuten profile and Offer evidence

Observed on 2026-08-26:

- active public Offer with generic site-approval guidance and no ValuePilot-specific eligibility block observed
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

If Jamieson data is ever authorized for production use, acceptable first-stage comparison dimensions should remain objective and source-grounded, such as price, count, package quantity, strength as printed on the product, ingredient/label facts as supplied, and transparent unit-value calculations. Any future health-oriented interpretation would require a separate deliberate product/safety/legal design review.

## Product Catalog technical setup and request

On 2026-08-26 Rakuten Customer Support created ValuePilot's Product Catalog file-transfer account and confirmed that publisher-side technical setup was complete. Credentials are operational secrets and must never be committed to this repository, embedded in the Android application, logged, or copied into documentation.

Rakuten instructed ValuePilot to request Product Catalog access separately for partnered advertisers through `Links -> Product Feeds`. The dashboard was subsequently unlocked. Feed settings showed automatic enrollment enabled and `.txt` selected as the preferred feed format.

ValuePilot then clicked `Apply` for Jamieson Vitamins. The Jamieson Product Feeds row changed from `Apply` to `Remove`. Rakuten's Product Catalog documentation distinguishes technical approval from advertiser Product Catalog approval and states that the Product Feeds interface is used to apply, remove access, and view application status.

Decision:

**JAMIESON PRODUCT CATALOG REQUEST = SUBMITTED / ACTIVE; ADVERTISER-LEVEL FEED APPROVAL NOT YET PROVEN.**

The strongest confirmation of feed approval will be either an explicit approved status in Rakuten or appearance of Jamieson's catalog file/folder in the authorized SFTP account. Do not infer approval solely from the `Remove` control.

## Next validation gate

Once advertiser-level Product Catalog approval is confirmed and the authorized feed becomes available, inspect the real feed before implementation, including:

- Canadian/CAD semantics
- SKU/GTIN/product identity quality
- count/strength/dosage-form/package fields
- ingredient/nutrition/label fields if any
- price/sale/reference-price semantics
- availability/freshness timestamps
- image/deep-link behavior
- exact permitted caching, persistence, indexing and consumer display behavior
- whether catalog/datafeed use is allowed in the ValuePilot mobile app or only on approved digital properties
- any additional advertiser-specific DSA requirement beyond Rakuten network approval/testing

Start with the pipe-delimited `.txt` feed for empirical inspection because it is easier to validate deterministically before building a permanent provider adapter.

Do not implement a production Rakuten adapter merely because the advertiser partnership is active or the Product Catalog request has been submitted.