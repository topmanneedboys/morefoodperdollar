# ValuePilot Provider Account Status

Updated: 2026-08-25

Milestone:
5D — Authorized Real Shopping Data Provider Selection

Purpose:
Keep fast-changing external account/application status separate from architectural provider research and empirical feed-quality evidence.

This file is an operational checkpoint only. It does not authorize production networking, credentials in the app, backend deployment, affiliate-driven ranking, or any provider adapter.

## Status summary

| Provider / program | Current status | Next meaningful action |
| --- | --- | --- |
| Awin publisher account | ACTIVE / APPROVED | Wait on selected advertiser-level validation; no duplicate applications |
| Skip CA on Awin (advertiser 107752) | PENDING advertiser approval unless newer evidence arrives | If approved, inspect the actual authorized Skip product feed before implementation |
| impact.com partner account | ACCOUNT EXISTS / MARKETPLACE APPLICATION DECLINED | Do not create a duplicate account or resubmit blindly; inspect any dashboard-specific reason before deciding whether support/reapplication is worthwhile |
| impact.com Marketplace application | DECLINED on 2026-08-25 | Preserve the denial as evidence; do not infer a specific cause from the generic email |
| CJ Affiliate publisher account | ACTIVE | Wait on the deliberately selected advertiser applications; no random additional applications |
| Rakuten Advertising publisher account | ACTIVE / DASHBOARD ACCESS CONFIRMED | Well.ca application is pending advertiser approval |
| Well.ca on Rakuten (MID 53166) | PENDING APPROVAL / APPLIED on 2026-08-25 | Wait for advertiser decision; if approved, inspect actual Product Catalog/datafeed and feed-specific rights before implementation |
| Giant Tiger on Rakuten (MID 52823) | DENIED / APPLICATION NOT ACCEPTED | Do not reapply now; reason was not specifically provided; preserve as future reconsideration candidate |
| Flipp | PARTNERSHIP TARGET, NOT YET VALIDATED | Commercial partnership inquiry later |
| GS1 Canada ECCnet | DATA/IDENTITY TARGET, NOT YET VALIDATED | Commercial/data-recipient rights validation later |

## Rakuten Advertising checkpoint

Rakuten publisher registration and dashboard access are active. The public ValuePilot website is the declared primary web property.

Giant Tiger was deliberately selected for a first Rakuten validation attempt because its authenticated profile showed Product Catalog, `Product feed available`, Deep Links, broad Canadian everyday-shopping categories and `Allows downloadable software applications`. Its full advertiser-supplied Terms & Conditions were reviewed before application and did not introduce a blanket mobile/software prohibition, although feed persistence/caching/indexing/display rights remained unproven.

A Giant Tiger partnership application was submitted on 2026-08-22. On 2026-08-24 Rakuten sent an `Application Denied` notice stating that Giant Tiger chose not to accept ValuePilot into its affiliate program at this time. The notice did not provide a Giant-Tiger-specific reason, so generic examples in the notice must not be treated as the actual cause.

Decision:

**GIANT TIGER = DENIED / DO NOT REAPPLY NOW.**

Well.ca was then selected as the next controlled Rakuten candidate. Its authenticated profile, full advertiser agreement, and current baseline Offer were reviewed before application. Relevant evidence included Canadian delivery/serviceability, Product Catalog, Deep Links, real-time tracking, `Allows downloadable software applications`, and an Offer statement that a datafeed is available. The general agreement remains a narrow referral/marketing license and does not by itself establish blanket catalog caching/indexing/display/redistribution rights.

On 2026-08-25 ValuePilot submitted the Well.ca partnership application. The authenticated Rakuten dashboard now shows:

**`Partnership pending approval`**

Decision:

**WELL.CA = APPLIED / PENDING ADVERTISER APPROVAL.**

Do not submit a duplicate Well.ca application. If approved, first inspect the actual authorized Product Catalog/datafeed, feed-specific terms, Canadian/CAD semantics, product identity, package-size fields, ingredients/nutrition fields if any, price semantics, freshness/availability, image/deep-link behavior, and exact permitted caching/indexing/display/mobile-app use before any implementation.

Rakuten permanent distinctions remain:

1. Publisher account access != advertiser relationship.
2. Advertiser relationship != product-feed/API access.
3. Product Catalog visibility != field quality or data-use rights.
4. DSA allowed in profile metadata != network-level DSA approval or unrestricted app use.
5. Advertiser approval != permission to cache/index/display/redistribute catalog data.
6. Feed availability != permission to scrape the advertiser website.
7. Commission/payout must never influence provider-quality scoring or ValuePilot ranking.
8. ValuePilot must never force clicks, cookie-stuff, auto-redirect, overwrite attribution, or drop tracking merely because an offer was displayed.

## CJ Affiliate checkpoint

CJ publisher account is active. The truthful Promotional Property setup is complete as `Services and Tools` with `Product Comparison, Reviews, or Discovery`. The public ValuePilot marketing site states that the product is pre-launch and that ranking is independent of affiliate commissions, advertiser payouts and sponsorship.

The deliberately selected CJ advertiser applications remain pending unless newer evidence arrives. Do not submit duplicates or add random CJ advertisers merely to accumulate relationships.

Permanent distinction:

**CJ publisher access != advertiser relationship != catalog/feed/API access != permission to cache/index/display catalog data.**

## impact.com checkpoint

The impact.com partner account was created and the Marketplace/media-partner application was submitted on 2026-08-22.

On 2026-08-25 impact.com sent an `Application Update` email stating that the application to join Impact as a media partner **has been declined**. The available notification did not identify a concrete reason, so do not infer a cause without direct dashboard/support evidence.

Decision:

**IMPACT.COM MARKETPLACE APPLICATION = DECLINED.**

Do not create a duplicate impact.com account or submit a blind duplicate application. First inspect the authenticated impact.com dashboard for any specific rejection reason or remediation path. If no concrete reason is available there, a targeted support inquiry may be justified later.

Permanent distinction:

**impact.com account access != Marketplace approval != advertiser relationship != catalog access != permission to cache/index/display catalog data.**

## Awin / Skip checkpoint

Awin publisher access is active. The deliberate Skip CA advertiser application remains pending unless newer evidence arrives.

Do not submit a duplicate Skip application.

If approved, inspect the actual authorized Skip product feed before implementation, including merchant/store/channel/geography semantics, product identity, quantity and unit fields, price semantics, availability/freshness and data-use rights.

Detailed Awin feed-quality evidence remains in `PROVIDER_VALIDATION.md`.

## Current next action

1. Well.ca is now applied and pending advertiser approval; do not submit a duplicate.
2. Continue waiting for material updates from Awin/Skip and the selected CJ advertisers.
3. Preserve impact.com as declined; do not blindly reapply.
4. Do not reapply to Giant Tiger now.
5. If Well.ca approves, validate the real authorized datafeed and rights before production integration.
6. Additional Rakuten applications should be deliberately pre-screened for high information value rather than submitted randomly.
7. Do not implement a Rakuten, CJ, Awin or impact.com production adapter until 5D has validated and deliberately selected a provider.
