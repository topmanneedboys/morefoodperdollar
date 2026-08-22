# ValuePilot Provider Account Status

Updated: 2026-08-22

Milestone:
5D — Authorized Real Shopping Data Provider Selection

Purpose:
Keep fast-changing external account/application status separate from architectural provider research and empirical feed-quality evidence.

This file is an operational checkpoint only. It does not authorize production networking, credentials in the app, backend deployment, affiliate-driven ranking, or any provider adapter.

## Status summary

| Provider / program | Current status | Next meaningful action |
| --- | --- | --- |
| Awin publisher account | ACTIVE / APPROVED | Continue advertiser-level validation only |
| Skip CA on Awin (advertiser 107752) | PENDING advertiser approval | If approved, inspect the actual authorized Skip product feed before implementation |
| impact.com partner account | ACCOUNT CREATED | No duplicate account |
| impact.com Marketplace application | IN REVIEW | Wait for approval/request; do not resubmit |
| CJ Affiliate | ACCOUNT / ONBOARDING IN PROGRESS | Complete truthful promotional-property setup, then validate advertiser/catalog/feed rights |
| Rakuten Advertising | NOT STARTED | Validate after CJ unless newer evidence changes priority |
| Flipp | PARTNERSHIP TARGET, NOT YET VALIDATED | Commercial partnership inquiry later |
| GS1 Canada ECCnet | DATA/IDENTITY TARGET, NOT YET VALIDATED | Commercial/data-recipient rights validation later |

## CJ Affiliate checkpoint

Observed during the real CJ publisher onboarding flow on 2026-08-22:

- CJ publisher signup has progressed beyond the earlier `NOT STARTED` checkpoint
- the account is far enough through onboarding to configure Promotional Properties
- CJ requires a truthful promotional property describing where advertiser promotion will occur
- `Services and Tools` is the current truthful property type for the pre-launch ValuePilot technology because the browser extension and mobile app do not yet have legitimate public store listings
- primary promotional model: `Product Comparison, Reviews, or Discovery`
- ValuePilot now has a dedicated public marketing site for this property at `https://topmanneedboys.github.io/morefoodperdollar/`
- the marketing site is reported live by the product owner and clearly identifies ValuePilot as pre-launch
- the marketing site does not claim live retailer pricing, inventory, promotions, merchant relationships, user counts, or public store distribution that has not been established
- the site states that ValuePilot ranking is independent of affiliate commission rates, advertiser payouts, and sponsorship
- a public privacy notice is present on the marketing site

Do not:

- invent a browser-extension store URL
- invent an App Store or Google Play listing
- classify ValuePilot as a coupon, cashback, influencer, or paid-search property unless the product actually adopts that model
- claim advertiser partnerships before advertiser-level acceptance exists
- assume CJ account creation or promotional-property approval grants product-feed/API rights
- implement a CJ production adapter during 5D

Permanent distinction for CJ:

**CJ publisher account access != promotional-property acceptance != advertiser relationship != catalog/feed/API access != permission to cache/index/display catalog data in ValuePilot**

Each stage requires separate evidence.

## impact.com account/application checkpoint

Observed during the real signup flow on 2026-08-22:

- correct account type: Partner / Publisher
- operating as: individual
- country: Canada
- timezone: Eastern Time (US & Canada)
- preferred account currency: CAD
- publisher categorization selected for a consumer app/site that compares products and services
- primary promotion channel: mobile app
- current connected media property: public ValuePilot GitHub repository website
- GitHub website ownership was successfully verified through impact.com's edit-content verification method
- the temporary site-verification token was removed from both the main branch and the authoritative development branch after verification
- pre-launch Mobile App channel was not added because impact.com required an App Store URL and ValuePilot does not yet have a legitimate public store listing
- Marketplace application was successfully submitted and currently shows `In Review`

Do not:

- submit a duplicate Marketplace application
- create another impact.com account to bypass review
- invent an App Store URL
- add fake social/newsletter/browser-extension channels merely to improve profile completion

If impact.com requests clarification, tax information, identity verification, media-property changes, or contractual acceptance, capture the exact request before responding.

## impact.com agreement conclusions

The uploaded 2025 Master Program Agreement and Partner User Agreement were reviewed during signup.

Operational conclusions for ValuePilot:

1. Platform membership is not blanket permission to use every advertiser/catalog.
2. Advertiser-specific Partner Contracts can add, replace, or restrict permitted promotional/data use.
3. Mobile-app promotion is contemplated, subject to truthful disclosure and applicable standards.
4. Unauthorized scraping, fake redirects, automated action generation, hidden frames, cookie manipulation, malware/adware and similar abusive methods are prohibited and remain outside ValuePilot's provider strategy.
5. Advertiser content/data can carry confidentiality and use restrictions.
6. Production caching, indexing, comparison display and redistribution rights must be validated per advertiser/catalog rather than inferred from Marketplace access.
7. impact.com and advertiser relationships can terminate; therefore impact.com must never become ValuePilot's sole provider dependency.
8. The Partner User Agreement incorporates a separate Data Protection Agreement. Review that agreement before production tracking or processing visitor/personal data through impact.com.

Permanent distinction for impact.com:

**impact.com account access != Marketplace approval != advertiser relationship != catalog access != permission to cache/index/display catalog data in ValuePilot**

Each stage requires separate evidence.

## Awin / Skip checkpoint

Awin publisher account is already active.

Skip CA advertiser application was submitted on 2026-08-22 and remains pending at the time of this update.

Do not submit a duplicate Skip application.

If approved, the Skip feed remains the highest-value Awin feed to inspect because the advertiser profile showed approximately 50,226 ShopWindow products and expressly allowed Comparison Engine partner type, while actual product/merchant/store/channel/geography/feed-field quality remains unverified.

Detailed Awin feed-quality evidence is recorded in `PROVIDER_VALIDATION.md`.

## Next action

Complete the CJ Promotional Property using the live ValuePilot marketing site, then continue CJ validation in this order:

1. finish the truthful `Services and Tools` promotional property with `Product Comparison, Reviews, or Discovery` as the primary model
2. inspect any CJ publisher-network terms, software/tool policy, privacy/data requirements, and onboarding gates presented before accepting or submitting them
3. determine whether the property/account is accepted, pending review, or blocked by an additional verification step
4. search for Canadian advertisers relevant to ValuePilot and distinguish advertiser discovery from actual advertiser acceptance
5. inspect real catalog/product-feed/API access and authorization requirements
6. validate actual feed fields for price, sale price, country, currency, availability, product identity, weight/dimensions, quantity/unit evidence, timestamps, and geography
7. record caching, indexing, display, attribution, mobile-app/tool, deep-linking, API/rate-limit, cost, and termination constraints
8. stop before production integration; implementation remains unauthorized until 5D selects a provider deliberately

Do not implement a CJ production adapter during 5D.
