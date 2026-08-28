# ValuePilot — Rakuten Price Semantics and Android Rights Gate

Updated: 2026-08-28

Milestone: 5D — authorized real shopping data validation

## Purpose

This document separates two issues that must not be conflated:

1. what Rakuten Product Catalog `Sale Price` and `Retail Price` mean; and
2. whether ValuePilot is authorized to use Rakuten/Jamieson feed content and affiliate links inside the installed Android application.

A feed may be technically accessible and semantically understandable while still not being cleared for a particular production distribution channel.

## Account-specific evidence

ValuePilot is an approved Jamieson Vitamins Rakuten Advertising publisher partner.

Rakuten Publisher Customer Support separately confirmed on 2026-08-28 that ValuePilot is approved for the Jamieson advertiser Product Feed and that the Jamieson feed is present in the Product Catalog account.

This proves advertiser Product Catalog/feed approval and technical feed access. It does not by itself prove every downstream mobile, caching, indexing, retention, redistribution, or downloadable-software use right.

Operational provider credentials are deliberately excluded from this repository and must remain outside source control.

## Documented Rakuten price semantics

Rakuten Advertising's current Product Catalog Appendix A defines:

- Product Field 13 — `Sale Price`: optional numeric field; the price reflects discounts.
- Product Field 14 — `Retail Price`: required numeric field; the price does not reflect discounts.

Source:

- Rakuten Advertising Publisher Help Center — Product Catalog Appendix A - File Field Definitions
- https://pubhelp.rakutenadvertising.com/hc/en-us/articles/8191594256013-Product-Catalog-Appendix-A-File-Field-Definitions

Therefore the field-name semantic gate is no longer unresolved at the generic Rakuten schema level.

### Deterministic relationship rule

For a positive Retail Price and positive Sale Price:

- `Sale Price < Retail Price` is structurally consistent with a discount.
- `Sale Price = Retail Price` is structurally consistent with no effective discount.
- `Sale Price > Retail Price` conflicts with Rakuten's documented field meanings and must be treated as semantic-invalid/inconsistent evidence.

ValuePilot must not reinterpret an inverted row as a markup, reverse the fields, silently swap values, or invent a discount explanation.

## Jamieson empirical price relationship

The complete authorized Jamieson feed contains 273 product rows:

- Sale Price < Retail Price: 48
- Sale Price = Retail Price: 223
- Sale Price > Retail Price: 2

The two inverted rows are therefore a concrete provider-data semantic-integrity failure class.

They remain structural product records, but their price evidence must fail closed for any production current-price / discount claim until corrected by the source or independently corroborated through an authorized source.

## What is now resolved

Resolved:

- the generic meaning of Rakuten `Sale Price`;
- the generic meaning of Rakuten `Retail Price`;
- that an inverted Sale > Retail relationship is inconsistent with those documented semantics;
- that Product Catalog is designed for advertiser-approved publisher use and can be retrieved automatically through SFTP;
- that Rakuten describes product feeds as supporting search, comparison, product-link and e-commerce aggregation use cases.

Sources:

- Product Catalog Data Feed Implementation Guidelines
- Product Catalog Appendix A - File Field Definitions
- Data Feeds
- Download Product Catalog Data Feed Files

## What is still unresolved for production price evidence

Still unresolved:

- whether Jamieson's feed timestamp/update cadence is sufficient for ValuePilot's chosen current-price freshness policy;
- whether each product price can be represented as currently available rather than merely latest feed-provided value;
- exact Canadian geographic offer scope beyond CAD plus Jamieson's Canadian context;
- how stale rows should be downgraded when advertiser updates are delayed;
- whether ValuePilot may persist/cache/index/display the feed fields for the desired duration and production architecture;
- whether consumer Android display of Product Catalog data is covered by the existing advertiser approval or needs additional written permission.

The Rakuten Product Catalog download documentation says files are generated dynamically when retrieved and the timeliness of product information depends on how often the advertiser updates the Product Catalog database. That is useful source-level freshness evidence but is not a per-product last-modified timestamp.

Therefore the existing HDR/file timestamp must continue to be treated as file-generation/deposit evidence, not proof that every individual offer is fresh.

## Android / mobile channel distinction

Rakuten's current Publisher Membership Agreement states that publisher promotion channels may include mobile applications, subject to the applicable advertiser engagement terms and Network Policies. It also defines a `Site` broadly enough to include an application.

However, the Product Catalog implementation documentation still describes advertiser approval to use a Product Catalog feed on a website or blog, and the Jamieson partnership approval email states that supplied links are to be used on the website listed in the marketing channel indicated above.

This creates a channel-specific ambiguity that must be resolved before production Android use.

## Downloadable Software Application (DSA) gate

Rakuten's current Network Policies expressly include installed/mobile applications in Downloadable Software Application controls.

Rakuten requires:

1. Rakuten / Network Quality approval and compliance testing before a DSA is launched with Rakuten network links; and
2. advertiser approval for the new DSA distribution method after Rakuten approval.

Relevant sources:

- Rakuten Advertising Affiliate Network Policies and Guidelines
- https://rakutenadvertising.com/legal-notices/affiliate-network-policies/
- Downloadable Software Applications (DSAs)
- https://pubhelp.rakutenadvertising.com/hc/en-us/articles/360061252752-Downloadable-Software-Applications-DSAs
- After DSA Approval
- https://pubhelp.rakutenadvertising.com/hc/en-us/articles/4412103334285-After-DSA-Approval

### Permanent Android rule

Until the DSA/channel gate is cleared:

- do not ship Rakuten affiliate/network links inside the installed Android app;
- do not add Android production networking merely because Product Catalog access exists;
- do not assume advertiser Product Feed approval equals advertiser DSA/mobile-app approval;
- do not represent Rakuten/Jamieson feed content as production-cleared mobile data solely because the feed can be downloaded;
- keep research validation outside Android.

## Price candidate rule for future provider import

When rights and freshness are later cleared, the provider adapter should still fail closed:

- positive Sale < Retail -> discounted-price candidate may be Sale Price; Retail Price remains undiscounted comparison/reference price;
- positive Sale = Retail -> current-price candidate may equal that shared value, with no discount claim;
- Sale missing with valid Retail -> Retail may be a non-discounted price candidate;
- Sale > Retail -> price-semantic conflict; reject price promotion from that row unless corrected/independently resolved;
- invalid/non-positive currency or amount -> reject;
- no inferred discount percentage unless exact arithmetic is based on accepted price evidence and the UI labels it as computed rather than source-supplied.

This rule still does not make the price rankable. Freshness, geography, rights and evidence-disposition gates remain separate.

## External clarification sent — awaiting response

On 2026-08-28, ValuePilot sent a written clarification request to the existing Rakuten Publisher Customer Support case covering:

- whether advertiser-approved Product Catalog fields may be displayed and searched inside the installed Android app;
- whether Product Catalog fields may be cached/indexed locally or server-side and what retention/refresh requirements apply;
- what deletion/removal obligations apply if feed approval or the advertiser partnership ends;
- whether the native Android app must complete Rakuten DSA / Network Quality review before any Rakuten/Jamieson affiliate links are used;
- whether Jamieson must separately approve the mobile/DSA distribution method after Rakuten approval.

No further Rakuten rights email should be sent unless the support response is incomplete or introduces a new unresolved point. Await the written response and preserve it as account-specific evidence before changing the production authorization state.

## Current decision

**Price-field semantics: PARTIALLY CLEARED.**

Rakuten's schema meaning is clear and the two inverted Jamieson rows are semantic-invalid for price promotion.

**Production price evidence: NOT CLEARED.**

Freshness, geography and downstream use rights remain unresolved.

**Installed Android affiliate-link distribution: NOT CLEARED.**

DSA/compliance approval and advertiser mobile-distribution approval must be treated as separate required gates.
