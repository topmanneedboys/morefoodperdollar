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

Why this is still worth one controlled application:

DAVIDsTEA provides an explicit structured Product Catalog path and no affirmative software prohibition in the supplied terms. Packaged tea is highly useful for testing weight/quantity/unit-value, variant identity, price semantics and consumable-product normalization.

Material risk:

The account-level and supported-currency fields show USD only. This is a major launch-quality uncertainty for a Canada-first ValuePilot. The Canadian conversion share does not prove that the Product Catalog rows contain Canadian CAD offers. Do not convert USD values into synthetic CAD offers and treat them as retailer prices.

Decision: **APPLY FOR CONTROLLED 5D VALIDATION, BUT TREAT ACTUAL CANADIAN/CAD FEED SEMANTICS AS A HARD POST-APPROVAL GATE.**

If approved, inspect the actual Product Catalog before any implementation. Required checks include:

1. whether Canadian offer rows actually exist
2. whether price currency is CAD, USD, mixed, or market-specific
3. package weight/count/variant fields
4. SKU/GTIN/product identity quality
5. current/reference/sale price semantics
6. availability and freshness
7. image/deep-link behavior
8. exact permission for caching/indexing/display and ValuePilot mobile/software use
9. advertiser-required affiliate disclosure behavior

If the authorized feed exposes only USD offers without trustworthy Canadian pricing, DAVIDsTEA should not be selected as a Canada production data rail even if the advertiser relationship is approved.

### Zwilling Canada (CID 5470201) — HOLD / DO NOT APPLY YET

Authenticated CJ details reviewed on 2026-08-26:

- advertiser country: Canada
- serviceable area: Canada
- account currency and supported currency: CAD
- prior-month conversions: 100% Canada
- broad physical-goods catalog across cookware, cutlery, kitchen electrics, ceramics, glassware and related brands
- supplied terms contain no explicit `Software: Not allowed` policy
- supplied terms contain no explicit `Software: Allowed` grant either
- visible policies are primarily SEM/trademark/domain/content/logo/coupon restrictions
- no explicit Product Catalog, product feed or datafeed availability was present in the supplied description or terms
- no visible caching/indexing/display/redistribution license was supplied

Interpretation:

Zwilling is a strong Canada/CAD physical-goods candidate and would be useful for SKU/model identity, variants, bundles and durable-goods comparison. However, the current 5D objective is to validate an authorized structured shopping-data rail. The supplied evidence does not establish a Product Catalog/datafeed path, so applying now would provide lower information value than candidates that explicitly expose a feed/catalog.

Decision: **HOLD / DO NOT APPLY YET FOR THE CURRENT DATA-PROVIDER PASS.**

Reconsider if CJ exposes a Product Catalog/datafeed indicator for Zwilling or the advertiser confirms structured feed availability and intended software/mobile use.

### EcoFlow CA (CID 6175965) — PRE-SCREEN FAILED / DO NOT APPLY NOW

Authenticated CJ details reviewed on 2026-08-26:

- advertiser country: Canada
- serviceable area: Canada
- account currency and supported currency: CAD
- prior-month conversion geography: 81.25% Canada and 18.75% United States
- broad physical consumer-electronics catalog focused on portable power, solar, home backup and related energy products
- incentivized traffic: allowed
- email: allowed
- sub-affiliates: allowed
- social media: allowed
- `Software: Not allowed`
- visible terms contain substantial branded SEM/direct-link/domain/display-network restrictions
- no explicit Product Catalog, product feed or datafeed availability was present in the supplied description or terms

Material conflict:

The explicit `Software: Not allowed` policy conflicts with ValuePilot's intended Android/mobile software client. This is a direct current-channel incompatibility regardless of the advertiser's otherwise strong Canada/CAD product fit. The supplied evidence also does not establish a structured Product Catalog/datafeed path.

Decision: **DO NOT APPLY TO ECOFLOW CA FOR THE CURRENT SOFTWARE/MOBILE VALIDATION PASS.**

Reconsider only if EcoFlow provides explicit written permission for ValuePilot's intended software/mobile use or if ValuePilot deliberately creates a separately authorized web-only channel consistent with the advertiser's terms. Any feed/catalog rights would still require separate validation.

## Next backup screens if needed

1. Dell Canada - Home & Small Business (CID 4380018)
2. Aeptom (CID 7672100)

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
4. Do not apply to PC Express, West Coast Kids or EcoFlow CA while `Software: Not allowed` remains in force.
5. Keep Natura Market and Zwilling Canada on hold pending stronger feed/software evidence.
6. Screen Dell Canada - Home & Small Business next.
