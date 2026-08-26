# ValuePilot Provider Account Status

Updated: 2026-08-26

Milestone:
5D — Authorized Real Shopping Data Provider Selection

Purpose:
Keep fast-changing external account/application status separate from architectural provider research and empirical feed-quality evidence.

This file is an operational checkpoint only. It does not authorize production networking, credentials in the app, backend deployment, affiliate-driven ranking, or any provider adapter.

## Status summary

| Provider / program | Current status | Next meaningful action |
| --- | --- | --- |
| Awin publisher account | ACTIVE / APPROVED | Pre-screen any additional advertisers for publisher-type compatibility before applying |
| Skip CA on Awin (advertiser 107752) | REJECTED on 2026-08-26 | Do not reapply now; advertiser stated it does not work with this publisher type |
| impact.com partner account | ACCOUNT EXISTS / MARKETPLACE APPLICATION DECLINED | Do not create a duplicate account or resubmit blindly; inspect any dashboard-specific reason before deciding whether support/reapplication is worthwhile |
| impact.com Marketplace application | DECLINED on 2026-08-25 | Preserve the denial as evidence; do not infer a specific cause from the generic email |
| CJ Affiliate publisher account | ACTIVE | Continue deliberately screened advertiser-level validation; AOSOM remains pending unless newer evidence arrives |
| Brulerie Virgin Hill Coffee Roasters on CJ | REJECTED / NOT APPROVED on 2026-08-24 | Do not reapply now; no advertiser-specific reason was provided |
| Rakuten Advertising publisher account | ACTIVE / DASHBOARD ACCESS CONFIRMED | Product Feed technical enablement/SFTP request submitted to Customer Support |
| Jamieson Vitamins on Rakuten | ACCEPTED / ADVERTISER PARTNERSHIP ACTIVE on 2026-08-26 | Wait for Product Feed technical enablement, then request/verify Jamieson Product Catalog access and inspect the real authorized feed before implementation |
| Well.ca on Rakuten (MID 53166) | PENDING APPROVAL / APPLIED on 2026-08-25 | Wait for advertiser decision; if approved, inspect actual Product Catalog/datafeed and feed-specific rights before implementation |
| Tru Earth on Rakuten (MID 54255) | APPLIED / decision pending unless newer evidence arrives | If approved, inspect actual Product Catalog/feed and rights before implementation |
| Bath Depot / Bain Depot on Rakuten | APPLIED / decision pending unless newer evidence arrives | If approved, inspect actual product feed and rights before implementation |
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

On 2026-08-25 ValuePilot submitted the Well.ca partnership application. The authenticated Rakuten dashboard showed `Partnership pending approval`.

Decision:

**WELL.CA = APPLIED / PENDING ADVERTISER APPROVAL.**

On 2026-08-26 Jamieson Vitamins accepted ValuePilot's Rakuten advertiser partnership application.

Decision:

**JAMIESON VITAMINS = ADVERTISER PARTNERSHIP ACCEPTED.**

This acceptance does not itself authorize a production integration. Jamieson's authenticated profile had shown Product Catalog, Deep Links and downloadable-software allowance, but actual Product Catalog/feed access, schema quality, caching/indexing/display rights and mobile-app catalog-use rights remain separate gates.

After acceptance, ValuePilot opened Rakuten `Links -> Product Feeds`. The account displayed `Unlock product feeds` and instructed the publisher to contact Customer Support with account email, SID and channel name to enable Product Feeds and an FTP/SFTP account. A support request for Product Feeds / Product Catalog technical enablement and the associated SFTP account was submitted on 2026-08-26 and the help center confirmed that the request was sent and updates will arrive by email.

Next Jamieson gate:

1. wait for Rakuten technical Product Feed/SFTP enablement
2. request or verify Jamieson advertiser-level Product Catalog access if a separate approval control is presented
3. inspect the actual authorized feed and any feed-specific terms
4. validate CAD/Canada semantics, IDs, package/count/strength fields, prices, freshness, availability and exact caching/indexing/display/mobile-app rights
5. do not integrate until those gates pass

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

AOSOM Canada remains pending unless newer evidence arrives.

Brulerie Virgin Hill Coffee Roasters was applied to on 2026-08-22. A CJ message dated 2026-08-24 states that the application was not approved. The message does not provide a concrete advertiser-specific reason; it only explains generally that each advertiser decides how many and what types of publishers it accepts.

Decision:

**BRULERIE VIRGIN HILL COFFEE ROASTERS = REJECTED / DO NOT REAPPLY NOW.**

Do not infer that software compatibility, catalog quality, site quality, traffic, or any other specific factor caused the rejection without direct advertiser evidence.

Additional CJ applications are acceptable when each candidate is deliberately pre-screened for a useful Canadian product feed/catalog path, software/mobile compatibility and terms compatible with comparison-shopping use. Do not submit duplicates or random low-information applications merely to accumulate relationships.

Permanent distinction:

**CJ publisher access != advertiser relationship != catalog/feed/API access != permission to cache/index/display catalog data.**

## impact.com checkpoint

The impact.com partner account was created and the Marketplace/media-partner application was submitted on 2026-08-22.

On 2026-08-25 impact.com sent an `Application Update` email stating that the application to join Impact as a media partner has been declined. The available notification did not identify a concrete reason, so do not infer a cause without direct dashboard/support evidence.

Decision:

**IMPACT.COM MARKETPLACE APPLICATION = DECLINED.**

Do not create a duplicate impact.com account or submit a blind duplicate application. First inspect the authenticated impact.com dashboard for any specific rejection reason or remediation path. If no concrete reason is available there, a targeted support inquiry may be justified later.

Permanent distinction:

**impact.com account access != Marketplace approval != advertiser relationship != catalog access != permission to cache/index/display catalog data.**

## Awin / Skip checkpoint

Awin publisher access is active.

ValuePilot's Skip CA advertiser application was rejected on 2026-08-26. The advertiser-provided reason shown in the Awin notification was:

**`Advertiser doesn't work with this publisher type`**

This is materially stronger evidence than the earlier generic pending state. Do not reapply to Skip under the same publisher configuration and do not misrepresent ValuePilot's publisher type merely to gain acceptance.

The rejection does not imply all Awin advertisers are incompatible. It does mean additional Awin applications should be pre-screened for explicit compatibility with ValuePilot's truthful publisher type (Comparison Engine / shopping-intelligence service), product-feed availability and intended software/mobile presentation model before submission.

Detailed Awin feed-quality evidence remains in `PROVIDER_VALIDATION.md`.

## Current next action

1. Wait for Rakuten Customer Support to enable Product Feeds/SFTP; this is the highest-value immediate gate because Jamieson is already advertiser-approved.
2. When enabled, validate the actual Jamieson authorized Product Catalog/feed and feed-specific rights before production integration.
3. Continue waiting on Well.ca, Tru Earth, Bath Depot and AOSOM unless newer evidence arrives.
4. Preserve Skip and Virgin Hill as rejected; do not reapply under the same current configuration without materially new evidence.
5. Preserve impact.com as declined; do not blindly reapply.
6. Additional advertiser applications may be run in a bounded, deliberately pre-screened multi-network batch to reduce calendar risk, but not as random mass applications.
7. Do not implement a Rakuten, CJ, Awin or impact.com production adapter until 5D has validated and deliberately selected a provider and actual data-use rights.
