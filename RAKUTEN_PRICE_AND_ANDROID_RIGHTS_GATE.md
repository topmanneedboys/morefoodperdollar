# ValuePilot — Rakuten Price Semantics and Android Rights Gate

Updated: 2026-08-28

Milestone: 5D — authorized real shopping data validation

## Purpose

This document separates four questions that must not be conflated:

1. what Rakuten Product Catalog `Sale Price` and `Retail Price` mean;
2. whether ValuePilot may use Jamieson Product Catalog content in a catalog-only installed Android app;
3. what retention/removal obligations apply when rights end; and
4. what extra approvals apply if the Android app exposes Rakuten advertiser/network links.

Technical access, advertiser feed approval, semantic understanding and production distribution rights are separate gates.

## Account-specific evidence

ValuePilot is an approved Jamieson Vitamins Rakuten Advertising publisher partner.

Rakuten Publisher Customer Support separately confirmed on 2026-08-28 that ValuePilot is approved for the Jamieson advertiser Product Feed and that the Jamieson feed is present in the Product Catalog account.

This proves advertiser Product Catalog/feed approval and technical feed access. It does **not** by itself prove catalog-only Android display/cache/index rights, advertiser-feed-revocation retention behavior, or DSA/network-link approval.

Operational provider credentials are deliberately excluded from this repository and must remain outside source control.

## Documented price semantics

Rakuten Advertising's Product Catalog Appendix A defines:

- Product Field 13 — `Sale Price`: optional numeric field; the price reflects discounts.
- Product Field 14 — `Retail Price`: required numeric field; the price does not reflect discounts.

Source:

- Product Catalog Appendix A - File Field Definitions
- https://pubhelp.rakutenadvertising.com/hc/en-us/articles/8191594256013-Product-Catalog-Appendix-A-File-Field-Definitions

Therefore the generic field-name semantic gate is resolved.

For positive Retail and Sale values:

- Sale < Retail -> structurally consistent with a discount;
- Sale = Retail -> structurally consistent with no effective discount;
- Sale > Retail -> conflicts with Rakuten's documented semantics and must fail closed.

ValuePilot must never swap inverted fields, reinterpret them as markup, or invent a promotion explanation.

## Jamieson empirical price relationship

The complete authorized Jamieson feed contains 273 product rows:

- Sale < Retail: 48
- Sale = Retail: 223
- Sale > Retail: 2

The two inverted rows are concrete semantic-integrity failures. They remain structural product records, but their price evidence cannot support a production current-price/discount claim unless corrected or independently resolved through authorized evidence.

## Product Catalog intended use

Rakuten's current Product Catalog documentation describes the program as supporting large product databases, product search, comparison and related product information that is updated frequently/daily. Product files are dynamically generated when retrieved, but the timeliness of product information depends on how often the advertiser updates its Product Catalog database.

Sources:

- Product Catalog Overview
- https://pubhelp.rakutenadvertising.com/hc/en-us/related/click?data=BAh7CjobZGVzdGluYXRpb25fYXJ0aWNsZV9pZGwrCA1rNk4DBDoYcmVmZXJyZXJfYXJ0aWNsZV9pZGwrCA%2FugNRTADoLbG9jYWxlSSIKZW4tdXMGOgZFVDoIdXJsSSI%2BL2hjL2VuLXVzL2FydGljbGVzLzQ0MTIyNDM2MDIxODktUHJvZHVjdC1DYXRhbG9nLU92ZXJ2BjsIVDoJcmFua2kJ--8bbbd832501e70fdc25628e0e1c89156dc7afbeb
- Data Feeds
- https://pubhelp.rakutenadvertising.com/hc/en-us/articles/7145964532877-Data-Feeds
- Download Product Catalog Data Feed Files
- https://pubhelp.rakutenadvertising.com/hc/en-us/articles/4412243880333-Download-Product-Catalog-Data-Feed-Files
- Product Feed in a Specific Language
- https://pubhelp.rakutenadvertising.com/hc/en-us/articles/4415256259981-Product-Feed-in-a-Specific-Language

This supports the database/search/comparison nature of Product Catalog. It does **not** prove per-product freshness or every desired downstream distribution/storage right.

## Catalog-only Android rights ambiguity

Rakuten's Publisher Membership Agreement, last updated July 13, 2026, defines a `Site` as a website, application, social media account, content platform or other consumer-accessible digital property. Section 2.5 also states that publisher promotion channels may include mobile applications, subject to advertiser engagement terms and Network Policies.

Source:

- Publisher Membership Agreement
- https://rakutenadvertising.com/legal-notices/publisher-membership-agreement/

However, the Product Catalog Data Feed Implementation Guidelines still state that participating advertiser partners approve a publisher to use the Product Catalog feed on the publisher's website or blog.

Source:

- Product Catalog Data Feed Implementation Guidelines
- https://pubhelp.rakutenadvertising.com/hc/en-us/articles/11258487715981-Product-Catalog-Data-Feed-Implementation-Guidelines

Therefore:

- Rakuten's general publisher agreement recognizes applications/mobile promotion;
- Product Catalog is clearly intended for searchable/comparison databases;
- but current Product Catalog advertiser-approval wording is still website/blog-specific;
- Jamieson's existing feed approval must **not** be silently extended to catalog-only Android display/cache/indexing without account-specific clarification.

The `MOBILE_APP_AUTHORIZED`, `CONSUMER_DISPLAY_AUTHORIZED`, `CACHE_AUTHORIZED` and `INDEX_AUTHORIZED` gates remain fail-closed for Jamieson until Rakuten answers the existing support clarification or equivalent written account-specific evidence is obtained.

## Retention / deletion baseline

The July 13, 2026 Publisher Membership Agreement provides a real full-account termination baseline. On termination of the agreement/network participation, the publisher must immediately cease using and remove Qualifying Links and other content/materials provided in connection with network participation, and the relevant granted licenses end immediately. Confidential/proprietary information may also need to be returned or destroyed as directed.

This resolves **only the full Publisher Agreement/network termination case**.

It does not yet establish the exact removal/retention requirement when:

- Jamieson removes only Product Feed approval;
- the Jamieson advertiser partnership ends while the Rakuten publisher account remains active; or
- a particular feed/snapshot becomes unavailable while other Rakuten engagements continue.

Therefore `RETENTION_DELETION_POLICY_DEFINED` remains unresolved for the Jamieson production dataset until the narrower account-specific behavior is clarified.

## DSA / network-link gate — stronger current evidence

Rakuten's Affiliate Network Policies and Guidelines, last updated July 13, 2026, expressly state that Downloadable Software Applications can include installable mobile applications and other installable technologies. The policies require publisher software to receive Rakuten approval and compliance testing **before launching a DSA with Rakuten network links**. They also state that participating advertisers may have their own DSA policies and that individual advertiser approval is required/recommended after Rakuten approval.

Sources:

- Affiliate Network Policies and Guidelines
- https://rakutenadvertising.com/legal-notices/affiliate-network-policies/
- Downloadable Software Applications (DSAs)
- https://pubhelp.rakutenadvertising.com/hc/en-us/articles/360061252752-Downloadable-Software-Applications-DSAs
- Features and Services Tab
- https://pubhelp.rakutenadvertising.com/hc/en-us/articles/360048672791-Features-and-Services-Tab

This makes the **link-enabled Android path** materially clearer than the catalog-only path.

### Permanent link-enabled Android rule

Before ValuePilot exposes any Rakuten/Jamieson advertiser/network link inside the installed Android app, all of the following remain required:

- `AFFILIATE_LINK_USE_AUTHORIZED`;
- `INSTALLED_SOFTWARE_NETWORK_APPROVED` — Rakuten/Network Quality approval and compliance testing;
- `ADVERTISER_DISTRIBUTION_APPROVED` — Jamieson approval for the DSA/mobile distribution method;
- `TRACKING_PRIVACY_READY` — applicable privacy/disclosure/consent requirements ready.

Do not add or expose Rakuten network links before those gates pass.

Catalog-only presentation and link-enabled affiliate distribution remain separate activation profiles. A catalog-only build must not gain link/network behavior merely because raw Product Catalog rows contain product URLs.

## Freshness boundary

Rakuten's Product Catalog download documentation says files are dynamically generated when retrieved and their timeliness depends on advertiser updates. This is useful **dataset/source recency** evidence.

It is not a trustworthy per-product price observation timestamp.

Therefore:

- feed retrieval/HDR time != per-offer price freshness;
- latest feed row != automatically current merchant price;
- Jamieson price rows remain non-rankable until a production offer-freshness policy can be supported by authoritative evidence.

## Geography boundary

CAD currency and Canadian brand/context do not prove the exact intended offer geography. Geography remains a separate production gate. Use explicit provider/advertiser delivery or offer-country evidence rather than currency inference.

## Price candidate rule for future provider import

When rights, geography and freshness are eventually cleared:

- positive Sale < Retail -> Sale may be the discounted/current candidate; Retail remains reference/non-discounted price;
- positive Sale = Retail -> the shared value may be a current-price candidate, with no discount claim;
- Sale missing + valid Retail -> Retail may be a non-discounted price candidate subject to the same production gates;
- Sale > Retail -> semantic conflict; reject price promotion/current-price use from that relationship unless corrected/resolved;
- invalid/non-positive amount/currency -> reject;
- any computed discount percentage must be exact deterministic arithmetic and labeled as computed, not source-supplied.

This rule never bypasses production authorization, geography, freshness, conflict, lifecycle or quantity/unit-value gates.

## Existing support clarification — awaiting response

On 2026-08-28 ValuePilot sent a clarification in the existing Rakuten support case asking about:

- Android display/search of Product Catalog fields;
- local/server caching/indexing and retention/refresh rules;
- deletion/removal requirements if feed approval or partnership ends;
- DSA/Network Quality review before affiliate links;
- separate Jamieson approval for the mobile/DSA distribution method.

Current public Rakuten policies now strongly establish the generic DSA/network-link requirements, but the account-specific support response is still useful for Jamieson and remains necessary for catalog-only Product Catalog rights and advertiser-feed-revocation retention behavior.

Do not resend the same inquiry unless the support response is incomplete or introduces a new unresolved point.

## Current decision

**Generic Sale/Retail semantics: CLEARED.**

**Jamieson technical Product Feed access: CLEARED.**

**Catalog-only installed Android Product Catalog use: NOT CLEARED.**

Outstanding: account-specific mobile display/search, cache/index and advertiser-feed-revocation retention/deletion rights.

**Per-offer current-price freshness: NOT CLEARED.**

Dataset recency does not prove product-level freshness.

**Canadian offer geography: NOT CLEARED.**

CAD/context is insufficient.

**Installed Android Rakuten-link distribution: NOT CLEARED, and the required approval path is now explicit.**

Rakuten/Network Quality DSA approval + participating advertiser approval + tracking/privacy readiness must pass before network links are exposed.
