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
| CJ Affiliate publisher account | ACTIVE | Wait on the deliberately selected advertiser applications; no random additional applications |
| Rakuten Advertising publisher account | ACTIVE / DASHBOARD ACCESS CONFIRMED | Wait on Giant Tiger advertiser review; no random additional applications |
| Giant Tiger on Rakuten (MID 52823) | PENDING / APPLIED | If approved, inspect the actual authorized Product Catalog/feed and feed-specific rights before implementation |
| Flipp | PARTNERSHIP TARGET, NOT YET VALIDATED | Commercial partnership inquiry later |
| GS1 Canada ECCnet | DATA/IDENTITY TARGET, NOT YET VALIDATED | Commercial/data-recipient rights validation later |

## Rakuten Advertising checkpoint

Observed during the real Rakuten publisher onboarding and advertiser-validation flow on 2026-08-22:

- publisher registration completed successfully
- Rakuten Publisher Dashboard access is active
- ValuePilot is shown as the publisher profile/site in the dashboard
- the public ValuePilot website is the declared primary web property
- advertiser discovery exposes Product Catalog, Deep Links and Canadian shipping/service filters and can export results as CSV
- Walmart Canada was inspected but is currently ineligible for partnership and has conflicting advertiser-specific download/software guidance, so no Walmart application was submitted
- Giant Tiger was inspected in detail before application
- Giant Tiger exposes Product Catalog, an explicit `Product feed available` statement, Deep Links, broad Canadian everyday-shopping categories and `Allows downloadable software applications`
- Giant Tiger's full advertiser-supplied Terms & Conditions were reviewed before application and did not introduce a blanket mobile/software prohibition, although feed persistence/caching/indexing/display rights remain unproven
- a deliberate Giant Tiger partnership application was submitted and Rakuten currently shows `Pending (applied)`
- do not submit a duplicate Giant Tiger application
- the account dashboard still indicates additional publisher-profile/payment-related setup remains incomplete; this does not itself authorize any advertiser, product feed, API, caching, indexing, display, or mobile-app use
- no Rakuten account identifier, payment information, tax information, password, or other sensitive account data is stored in repository documentation

Rakuten policy conclusions relevant to ValuePilot:

1. Rakuten permits publisher promotion through websites and mobile applications subject to its agreement, network policies, and advertiser-specific terms.
2. Rakuten classifies AI-powered shopping assistants, recommendation tools, browser extensions, and mobile applications as downloadable software applications (DSAs).
3. Before a ValuePilot app contains Rakuten network links, Rakuten DSA approval/compliance testing is required and individual advertisers may impose additional DSA requirements.
4. ValuePilot must never force clicks, cookie-stuff, automatically redirect users through affiliate links, overwrite another publisher's attribution, or drop tracking merely because an offer was displayed.
5. Product/feed rights remain advertiser- and feature-specific. Rakuten publisher access or advertiser approval does not establish permission to persist, normalize, cache, index, redistribute, or display a merchant catalog in ValuePilot.
6. Rakuten can automatically assign Offers to a publisher account and may deem an assigned Offer accepted after the stated review period. Rakuten notices therefore require deliberate review rather than unattended acceptance.
7. Production tracking/privacy disclosures must reflect actual behavior before Rakuten tracking is enabled; do not add speculative tracking code or disclosures prematurely.

Permanent distinction for Rakuten:

**Rakuten publisher account access != advertiser relationship != product-feed/API access != DSA approval != permission to cache/index/display product data in ValuePilot**

Each stage requires separate evidence.

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

The highest-value pending gates are now advertiser/provider decisions rather than more signup activity.

1. Do not submit duplicate Giant Tiger, Skip, CJ, or impact.com applications.
2. Do not add random Rakuten advertisers while Giant Tiger is pending.
3. If Giant Tiger is approved, inspect the actual authorized Product Catalog/feed plus any feed-specific terms before downloading, caching or integrating anything.
4. If Giant Tiger is declined, record the reason if supplied and inspect Well.ca next rather than trying to force approval.
5. If Skip is approved, inspect the actual authorized Skip feed before implementation.
6. If impact.com Marketplace is approved, inspect advertiser/catalog permissions rather than treating marketplace approval as catalog authorization.
7. Stop before production integration; implementation remains unauthorized until 5D selects a provider deliberately.

Do not implement a Rakuten, CJ, Awin or impact.com production adapter during 5D.
