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

### Dell Canada - Home & Small Business (CID 4380018) — PRE-SCREEN FAILED AFTER FULL AGREEMENT REVIEW / DO NOT APPLY NOW

Initial More Info evidence was attractive:

- advertiser country/service area: Canada
- account currency: CAD
- recent conversions overwhelmingly Canadian
- broad computer/electronics catalog
- advertiser explicitly states it offers a robust Product Catalog with direct linking to available Dell.ca Home and Small Business products

However, the full Dell Canada Affiliates Program Agreement introduces material conflicts with ValuePilot's core comparison model:

- `Site` is defined as a World Wide Web Site identified in the CJ registration; the agreement does not affirmatively authorize a mobile-software presentation channel
- Dell may reject sites with fewer than 50 unique visitors per day
- scraping/spidering Dell sites is expressly prohibited
- the content/mark license is limited, revocable, non-exclusive and oriented to creating links from the affiliate Site to Dell; it does not provide blanket rights to copy, distribute, modify, cache, index or republish Dell catalog content
- most importantly, Section 5.2 states that product prices and availability may vary and explicitly says the affiliate **may not include price information in Product descriptions**

That price-display restriction is directly incompatible with ValuePilot's core job: presenting retailer prices, normalizing them, calculating value, and comparing offers. A Product Catalog is not useful as a production shopping-data rail if ValuePilot cannot lawfully surface the associated product price in its product presentation.

Decision: **DO NOT APPLY TO DELL CANADA - HOME & SMALL BUSINESS FOR THE CURRENT VALUEPILOT 5D PASS.**

Only reconsider Dell if Dell/CJ provides current written terms or direct written authorization that expressly permits ValuePilot's intended comparison use, including display of current Dell prices in the approved property/channel and any required catalog caching/indexing/normalization. Do not infer such permission from Product Catalog availability alone.

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
8. explicit restrictions on displaying price, availability or other core comparison fields

Unknown catalog-use rights do not automatically block a controlled advertiser application, but explicit restrictions on core ValuePilot comparison fields do.

## Current action

1. Apply to TSC if not already submitted and record resulting status.
2. Apply to Brother Canada if not already submitted and record resulting status.
3. Apply to DAVIDsTEA for controlled validation; if approved, treat Canada/CAD row semantics as a hard gate.
4. Do not apply to PC Express, West Coast Kids or EcoFlow CA while `Software: Not allowed` remains in force.
5. Do not apply to Dell Canada - Home & Small Business under the reviewed agreement because Section 5.2 conflicts with ValuePilot price display/comparison.
6. Keep Natura Market, Zwilling Canada and Dell Financial Services Canada on hold pending stronger feed/software evidence.
7. Screen Aeptom next only if another CJ candidate is useful; do not mass-apply blindly.
