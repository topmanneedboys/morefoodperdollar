# ValuePilot CJ West Coast Kids Validation

Updated: 2026-08-26

Milestone:
5D — Authorized Real Shopping Data Provider Selection

Status:
West Coast Kids - Canada completed pre-application review. It has a strong Canadian physical-goods catalog and explicitly states that a full data feed is available, but the current CJ program terms explicitly state `Software: Not allowed`. No application should be submitted for the current ValuePilot software/mobile validation pass.

## Positive evidence

Authenticated CJ evidence supplied on 2026-08-26 shows:

- advertiser country: Canada
- serviceable area: Canada
- supported currency: CAD
- previous-month conversion geography shown as 100% Canada
- broad baby/parenting physical-goods catalog
- description explicitly states `full data feed available`
- useful product types for identity/variant testing including strollers, car seats, furniture, gear and accessories
- 30-day referral period
- standard CJ cross-device/cookieless tracking indicators

These are strong data-provider signals, especially the explicit data-feed statement.

## Material software-channel conflict

The current CJ policy explicitly states:

- `Software: Not allowed`
- sub-affiliates are not allowed
- program terms additionally discuss restrictions around toolbars and browser extensions in the sub-affiliate context
- email and social-media copy require advertiser approval

For ValuePilot, `Software: Not allowed` is the decisive current blocker because the permanent product includes an Android/mobile software client as a presentation surface.

Do not misrepresent ValuePilot as web-only merely to obtain acceptance. The existence of a full data feed does not override the advertiser's software-channel restriction.

## Data-rights distinction

Even if the advertiser later grants software/mobile permission, the currently visible program terms do not independently establish permission to cache, persist, index, normalize, redistribute or display the entire feed inside ValuePilot. Those rights would require separate validation before production integration.

## Decision

**WEST COAST KIDS - CANADA = PRE-SCREEN FAILED / DO NOT APPLY NOW.**

Reconsider only if:

1. West Coast Kids gives explicit written permission for ValuePilot's intended software/mobile comparison use, or
2. ValuePilot deliberately operates a separately authorized web-only channel that complies with all program terms.

If reconsidered, inspect the actual feed for Canada/CAD semantics, SKU/GTIN identity, variant/size/bundle fields, current/reference/sale-price semantics, availability/freshness, image/deep-link behavior and exact catalog-use rights before integration.

## Next CJ screen

Proceed to Brother Canada (CID 5267676) and inspect More Info + Program Terms for:

- explicit product feed/datafeed/catalog availability
- Canada/CAD serviceability
- `Software` policy
- comparison-shopping restrictions
- any feed/content caching, indexing, display or redistribution terms
