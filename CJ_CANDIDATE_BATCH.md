# ValuePilot CJ Candidate Batch

Updated: 2026-08-26

Milestone:
5D — Authorized Real Shopping Data Provider Selection

Purpose:
Select high-information CJ advertisers from the authenticated Canada-filtered directory without mass-applying blindly. Advertiser acceptance, Product Catalog access, software/mobile permission, and catalog caching/indexing/display rights remain separate gates.

## Current screened candidates

### PC Express (CID 7356175) — PRE-SCREEN FAILED / DO NOT APPLY NOW

Canada/CAD and exceptionally relevant to grocery comparison, but authenticated terms state `Software: Not allowed`. The supplied details also did not establish a Product Catalog/datafeed path.

Decision: **DO NOT APPLY for the current software/mobile validation pass.**

### Natura Market (CID 5370099) — HOLD / DO NOT APPLY YET

Canada/CAD and highly relevant packaged-food catalog, but supplied details did not establish Product Catalog/datafeed availability or explicit software/mobile permission.

Decision: **HOLD pending stronger feed/software evidence.**

### Today's Shopping Choice / TSC (CID 457947) — PRE-SCREEN PASSED / APPLY

Strong signals:
- Canada-focused and CAD-oriented
- more than 15,000 products across multiple categories
- advertiser explicitly states its full Product Catalog is available in CJ
- supplied terms show no explicit software prohibition or anti-comparison restriction

Decision: **APPLY for controlled 5D validation.**

If approved, inspect actual feed and exact mobile/software and catalog-use rights before production.

### West Coast Kids - Canada (CID 5753672) — PRE-SCREEN FAILED / DO NOT APPLY NOW

Strong Canada/CAD/full-data-feed signals, but authenticated policy explicitly states `Software: Not allowed`.

Decision: **DO NOT APPLY for the current software/mobile validation pass.**

### Brother Canada (CID 5267676) — PRE-SCREEN PASSED / APPLY

Strong signals:
- Canada service area and CAD
- 100% Canadian recent conversion geography
- explicit `Product Catalog available`
- supplied terms show no explicit software prohibition or anti-comparison restriction

Decision: **APPLY for controlled 5D validation.**

If approved, inspect actual catalog/feed and exact data-use/mobile rights before production.

### DAVIDsTEA (CID 5226617) — PRE-SCREEN PASSED WITH CURRENCY RISK / APPLY

Authenticated CJ details reviewed on 2026-08-26:

- advertiser country: Canada
- serviceable area: Canada and United States
- account currency and supported currency shown as USD only
- prior-month conversions: 66.75% Canada, 31.68% United States
- physical packaged-consumable catalog with more than 100 loose-leaf teas
- advertiser explicitly states `Product Catalog available`
- supplied terms show no explicit `Software: Not allowed` policy
- supplied terms show no explicit `Software: Allowed` grant either
- visible restrictions are primarily branded SEM/direct-link/domain/coupon rules
- advertiser requires affiliate disclosures when affiliate links are posted
- no visible caching/indexing/display/redistribution license was supplied

Decision: **APPLY FOR CONTROLLED 5D VALIDATION, BUT TREAT ACTUAL CANADIAN/CAD FEED SEMANTICS AS A HARD POST-APPROVAL GATE.**

If the authorized feed exposes only USD offers without trustworthy Canadian pricing, DAVIDsTEA should not be selected as a Canada production data rail even if the advertiser relationship is approved.

### Zwilling Canada (CID 5470201) — HOLD / DO NOT APPLY YET

Canada/CAD, 100% Canadian recent conversions and useful physical-goods coverage, but the supplied description and terms do not establish a Product Catalog/product-feed/datafeed path.

Decision: **HOLD pending stronger structured-feed evidence.**

### EcoFlow CA (CID 6175965) — PRE-SCREEN FAILED / DO NOT APPLY NOW

Canada/CAD and useful physical consumer-electronics coverage, but authenticated policy explicitly states `Software: Not allowed`. The supplied evidence also does not establish a Product Catalog/datafeed path.

Decision: **DO NOT APPLY for the current software/mobile validation pass.**

### Dell Financial Services Canada (CID 3671271) — NOT THE TARGET DELL PROGRAM / HOLD

Authenticated details show a separate refurbished-computer program with an extensive DFS Product Catalog, but the supplied terms were only a partial SEM/search-policy excerpt and did not establish software/mobile compatibility. It should not be substituted for the intended Dell Home & Small Business program.

Decision: **DO NOT APPLY YET.**

### Dell Canada - Home & Small Business (CID 4380018) — PRE-SCREEN PASSED / APPLY

Authenticated CJ details reviewed on 2026-08-26:

- advertiser country: Canada
- serviceable area: Canada
- account currency: CAD
- previous-month conversions: 98.51% Canada
- broad physical electronics/computer catalog covering desktops, laptops, monitors, printers, TVs, phones, accessories and business systems
- advertiser explicitly states it offers a `robust Product Catalog` with direct linking to available products for Dell.ca Home and Dell.ca Small Business
- Home and Small Business purchase actions are specifically tied to Canadian Dell sites and shipped-sale events
- supplied policies primarily govern paid search, trademark/domain use, coupons and incentivized promotions
- direct paid-search linking is not allowed and paid-search entries require Dell approval
- publisher site must identify itself as affiliate-owned/operated
- affiliates may not create a browser or border environment around Dell content
- supplied terms do not show an explicit `Software: Not allowed` policy
- supplied terms also do not provide an explicit `Software: Allowed` grant
- no visible anti-comparison prohibition was supplied
- no visible blanket caching/indexing/display/redistribution license was supplied
- advertiser language limits promotional offers to those communicated through the affiliate program / Dell Home & Home Office site / CJ Marketplace; this must be respected separately from Product Catalog access

Interpretation:

Dell Canada Home & Small Business has the two high-value signals needed to justify a controlled application: a clearly stated robust Product Catalog and a strongly Canadian physical-product program, without an affirmative software prohibition in the supplied terms. The framing/browser-border restriction does not itself prohibit an independent comparison interface; ValuePilot must not embed or frame Dell pages as its own content.

The older `Special Terms and Conditions (September 2014)` label and narrow offer/link language mean approval must not be treated as blanket permission to persist, cache, normalize, republish or display the full catalog in a mobile client. Those rights remain separate post-approval gates.

Decision: **APPLY TO DELL CANADA - HOME & SMALL BUSINESS FOR CONTROLLED 5D VALIDATION.**

If approved, inspect the actual CJ Product Catalog before any implementation, including:

1. CAD/Canadian price semantics
2. model/SKU/MPN/GTIN identity quality
3. Home vs Small Business offer separation
4. current/reference/sale-price semantics
5. configuration/variant handling
6. availability and feed freshness
7. image/deep-link fields
8. catalog update behavior
9. exact permission for caching, indexing, normalization and consumer display
10. exact compatibility with ValuePilot mobile/software presentation
11. no framing/browser-border behavior around Dell pages

## Next backup screen

1. Aeptom (CID 7672100)

## Permanent pre-application gate

For each advertiser, inspect and record:

1. More Info / program description
2. explicit Product Catalog/datafeed availability
3. serviceable area and supported currency
4. Software/downloadable-application policy
5. comparison-shopping/dynamic-presentation restrictions
6. disclosure/coupon/paid-search/attribution rules
7. caching/indexing/display/redistribution rights if visible

Unknown catalog-use rights do not automatically block a controlled advertiser application, but they remain unproven and must be resolved before production integration.

## Current action

1. Apply to TSC if not already submitted and record resulting status.
2. Apply to Brother Canada if not already submitted and record resulting status.
3. Apply to DAVIDsTEA for controlled validation; if approved, treat Canada/CAD row semantics as a hard gate.
4. Apply to Dell Canada - Home & Small Business for controlled validation.
5. Do not apply to PC Express, West Coast Kids or EcoFlow CA while `Software: Not allowed` remains in force.
6. Keep Natura Market, Zwilling Canada and Dell Financial Services Canada on hold pending stronger feed/software evidence.
7. Screen Aeptom next only if another CJ candidate is useful; do not mass-apply blindly.
