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
| CJ Affiliate publisher account | ACTIVE | Begin advertiser/catalog/feed-rights validation; no production adapter yet |
| Rakuten Advertising | NOT STARTED | Validate after CJ unless newer evidence changes priority |
| Flipp | PARTNERSHIP TARGET, NOT YET VALIDATED | Commercial partnership inquiry later |
| GS1 Canada ECCnet | DATA/IDENTITY TARGET, NOT YET VALIDATED | Commercial/data-recipient rights validation later |

## CJ Affiliate checkpoint

Observed during the real CJ publisher onboarding flow on 2026-08-22:

- CJ explicitly reports `Your Account Has Been Activated`
- CJ states the account is `ACTIVE` and may begin applying to join advertiser programs
- the truthful Promotional Property setup is complete
- property type: `Services and Tools`
- primary promotional model: `Product Comparison, Reviews, or Discovery`
- ValuePilot has a dedicated public marketing site at `https://topmanneedboys.github.io/morefoodperdollar/`
- the marketing site identifies ValuePilot as pre-launch and does not claim live retailer data or advertiser relationships that have not been established
- the site states that ranking is independent of affiliate commission rates, advertiser payouts, and sponsorship
- a public privacy notice is present
- the required individual non-US tax-information flow was completed and submitted through CJ
- payment onboarding was completed sufficiently for the account to reach active status
- no tax identifier, bank-account number, routing number, home address, or other sensitive financial identity data is stored in repository documentation

Do not:

- invent a browser-extension store URL
- invent an App Store or Google Play listing
- classify ValuePilot as a coupon, cashback, influencer, or paid-search property unless the product actually adopts that model
- claim advertiser partnerships before advertiser-level acceptance exists
- assume an active CJ publisher account grants advertiser, catalog, product-feed, API, caching, indexing, or display rights
- implement a CJ production adapter during 5D

Permanent distinction for CJ:

**CJ publisher account access != advertiser relationship != catalog/feed/API access != permission to cache/index/display catalog data in ValuePilot**

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

CJ account activation removes the onboarding gate. Continue CJ validation in this order:

1. open CJ advertiser discovery and search specifically for Canada-relevant merchants with broad consumer-retail/product catalogs rather than applying randomly
2. prioritize advertisers whose program profile explicitly permits product-comparison, shopping-discovery, services/tools, mobile-app, or similar publisher models
3. before applying, inspect each candidate's program terms for product-feed/API access, deep linking, geographic scope, allowed promotional methods, trademark/search rules, data-use restrictions, and any software-specific approval requirement
4. distinguish `advertiser visible` from `advertiser joined` from `catalog/feed/API authorized`
5. apply only to a small, high-information set of candidates likely to expose useful Canadian product data; avoid duplicate or low-value applications
6. for any accepted advertiser, inspect real catalog/product-feed/API access and authorization requirements before downloading or integrating anything
7. validate actual feed fields for price, sale price, country, currency, availability, product identity, weight/dimensions, quantity/unit evidence, timestamps, and geography
8. record caching, indexing, display, attribution, mobile-app/tool, deep-linking, API/rate-limit, cost, and termination constraints
9. stop before production integration; implementation remains unauthorized until 5D selects a provider deliberately

Do not implement a CJ production adapter during 5D.
