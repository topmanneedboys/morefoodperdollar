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
| Rakuten Advertising publisher account | ACTIVE / DASHBOARD ACCESS CONFIRMED | Giant Tiger declined; Well.ca terms/profile under review before any additional Rakuten application |
| Giant Tiger on Rakuten (MID 52823) | DENIED / APPLICATION NOT ACCEPTED | Do not reapply now; reason was not specifically provided; preserve as future reconsideration candidate |
| Flipp | PARTNERSHIP TARGET, NOT YET VALIDATED | Commercial partnership inquiry later |
| GS1 Canada ECCnet | DATA/IDENTITY TARGET, NOT YET VALIDATED | Commercial/data-recipient rights validation later |

## Rakuten Advertising checkpoint

Rakuten publisher registration and dashboard access are active. The public ValuePilot website is the declared primary web property.

Giant Tiger was deliberately selected for a first Rakuten validation attempt because its authenticated profile showed Product Catalog, `Product feed available`, Deep Links, broad Canadian everyday-shopping categories and `Allows downloadable software applications`. Its full advertiser-supplied Terms & Conditions were reviewed before application and did not introduce a blanket mobile/software prohibition, although feed persistence/caching/indexing/display rights remained unproven.

A Giant Tiger partnership application was submitted on 2026-08-22.

On 2026-08-24 Rakuten sent an `Application Denied` notice stating that Giant Tiger chose not to accept ValuePilot into its affiliate program at this time.

The notice did **not** provide a Giant-Tiger-specific reason. It listed generic examples such as inability to access the declared website, website not yet live, low traffic, or content mismatch. Do not record any one of those examples as the actual denial reason.

Decision:

**GIANT TIGER = DENIED / DO NOT REAPPLY NOW.**

Do not contact or pressure Giant Tiger merely to reverse the decision while other high-information candidates remain available. Reconsider later only after ValuePilot has materially stronger public proof, traffic, product maturity, or provider relationships, or if Giant Tiger supplies a concrete reason that can be addressed.

Next Rakuten candidate: **Well.ca (MID 53166)**. Its authenticated profile now shows Product Catalog, Deep Links, Canadian delivery/serviceability, real-time tracking and a DSA policy allowing downloadable software applications. The advertiser-specific agreement also expressly addresses AI/automated promotional content, but the general license remains a narrow referral/marketing license and does not by itself establish blanket catalog caching/indexing/display/redistribution rights. Continue reviewing the complete advertiser terms before applying.

Rakuten permanent distinctions remain:

1. Publisher account access != advertiser relationship.
2. Advertiser relationship != product-feed/API access.
3. Product Catalog visibility != field quality or data-use rights.
4. DSA allowed in profile metadata != network-level DSA approval or unrestricted app use.
5. Advertiser approval != permission to cache/index/display/redistribute catalog data.
6. Feed availability != permission to scrape the advertiser website.
7. Commission/payout must never influence provider-quality scoring or ValuePilot ranking.
8. ValuePilot must never force clicks, cookie-stuff, auto-redirect, overwrite attribution, or drop tracking merely because an offer is displayed.

## CJ Affiliate checkpoint

CJ publisher account is active. The truthful Promotional Property setup is complete as `Services and Tools` with `Product Comparison, Reviews, or Discovery`. The public ValuePilot marketing site states that the product is pre-launch and that ranking is independent of affiliate commissions, advertiser payouts and sponsorship.

The deliberately selected CJ advertiser applications remain pending unless newer evidence arrives. Do not submit duplicates or add random CJ advertisers merely to accumulate relationships.

Permanent distinction:

**CJ publisher access != advertiser relationship != catalog/feed/API access != permission to cache/index/display catalog data.**

## impact.com checkpoint

The impact.com partner account was created and the Marketplace/media-partner application was submitted on 2026-08-22.

On 2026-08-25 impact.com sent an `Application Update` email stating that the application to join Impact as a media partner **has been declined**.

The email says the application was declined for one or more reasons, but the message content available in the notification does not identify a concrete reason. Do not invent or infer a cause such as pre-launch status, traffic, website quality, media-property setup, identity, or policy mismatch without direct dashboard/support evidence.

Decision:

**IMPACT.COM MARKETPLACE APPLICATION = DECLINED.**

Do not create a duplicate impact.com account or submit a blind duplicate application. First inspect the authenticated impact.com dashboard for any specific rejection reason or remediation path. If no concrete reason is available there, a targeted support inquiry may be justified later, but only after preserving the exact account state and avoiding speculative explanations.

The denial closes impact.com as an immediate provider path but does not change the provider-neutral architecture or invalidate the earlier agreement analysis.

Permanent distinction:

**impact.com account access != Marketplace approval != advertiser relationship != catalog access != permission to cache/index/display catalog data.**

## Awin / Skip checkpoint

Awin publisher access is active. The deliberate Skip CA advertiser application remains pending unless newer evidence arrives.

Do not submit a duplicate Skip application.

If approved, inspect the actual authorized Skip product feed before implementation, including merchant/store/channel/geography semantics, product identity, quantity and unit fields, price semantics, availability/freshness and data-use rights.

Detailed Awin feed-quality evidence remains in `PROVIDER_VALIDATION.md`.

## Current next action

The impact.com decision removes one pending provider gate and should not trigger a blind reapplication cycle.

1. Preserve impact.com as declined and inspect its dashboard only for a specific reason/remediation path before deciding whether support is worthwhile.
2. Continue the controlled Well.ca Rakuten review; do not apply until the complete advertiser-specific terms are reviewed.
3. Continue waiting for material updates from Awin/Skip and the selected CJ advertisers.
4. Do not reapply to Giant Tiger now.
5. Do not infer denial reasons from generic network emails.
6. Do not implement a Rakuten, CJ, Awin or impact.com production adapter until 5D has validated and deliberately selected a provider.
