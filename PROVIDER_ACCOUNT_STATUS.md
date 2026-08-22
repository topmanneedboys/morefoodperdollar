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
| CJ Affiliate | NOT STARTED | Begin publisher/account validation while Awin Skip and impact.com are pending |
| Rakuten Advertising | NOT STARTED | Validate after CJ unless newer evidence changes priority |
| Flipp | PARTNERSHIP TARGET, NOT YET VALIDATED | Commercial partnership inquiry later |
| GS1 Canada ECCnet | DATA/IDENTITY TARGET, NOT YET VALIDATED | Commercial/data-recipient rights validation later |

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

While Skip and impact.com are both pending, continue the deliberate validation order with CJ Affiliate.

The purpose of CJ validation is to establish:

- publisher account feasibility
- Canadian advertiser/catalog availability
- advertiser-level authorization requirements
- real product-feed/API access
- actual field quality for price, sale price, country, currency, availability, product identity, weight/dimensions and unit-pricing evidence
- mobile-app/comparison-publisher compatibility
- caching/indexing/display restrictions
- cost, rate and operational constraints

Do not implement a CJ production adapter during 5D.
