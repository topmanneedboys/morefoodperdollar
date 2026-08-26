# ValuePilot CJ Abracadabra NYC Validation

Updated: 2026-08-26

Milestone:
5D — Authorized Real Shopping Data Provider Selection

Status:
CJ advertiser sent ValuePilot a pending offer. ValuePilot has not accepted it. This record does not authorize production integration or catalog use.

## Observed advertiser evidence

Advertiser:
Abracadabra NYC

CJ advertiser ID:
7889430

Observed through the authenticated CJ publisher account on 2026-08-26:

- advertiser country: United States
- category: Collectibles
- stated catalog size: more than 50,000 items
- stated catalog scope: costumes, cosplay, magic, props, gifts, makeup, collectibles, apparel and related products
- datafeed stated to update daily
- 45-day referral period
- Shopify store plus physical retail locations
- advertiser states that it ships worldwide
- CJ serviceable area shown as **United States**
- CJ supported currency shown as **USD only**
- CJ conversion geography for the prior month shown as **United States 100%**
- the visible program terms include protected branded SEM restrictions and prohibit third-party ad-retargeting pixels on the advertiser site
- no explicit software/mobile-app permission was observed in the supplied details

Affiliate economics are irrelevant to ValuePilot provider selection and ranking.

## 5D interpretation

The daily datafeed and large physical-product catalog are useful discovery signals, but they do not solve ValuePilot's current Canadian launch evidence requirement.

The advertiser's statement that it ships worldwide does not establish Canadian offer semantics. In particular, the authenticated CJ metadata currently shows the serviceable area as United States and supported currency as USD only. ValuePilot must not treat worldwide shipping as proof of Canadian consumer pricing, CAD support, local availability or Canadian fulfillment semantics.

The supplied evidence also does not establish:

- CAD pricing
- Canada-specific offer rows
- Canadian taxes/duties/shipping semantics
- mobile-app/software promotion permission
- permission to cache/index/normalize/display/redistribute the datafeed
- feed schema quality, product identifiers, quantity fields or freshness semantics beyond the advertiser's statement that the feed updates daily

## Decision

**DO NOT ACCEPT THE ABRACADABRA NYC PENDING OFFER FOR THE CURRENT CANADA-FIRST 5D VALIDATION PASS.**

Reason:
The relationship would add a large US/USD catalog but comparatively little information toward the present bottleneck: trustworthy, authorized Canadian shopping offers and rights suitable for ValuePilot's comparison model.

Reconsider later only if ValuePilot deliberately expands US coverage or the advertiser exposes clear Canadian/CAD offer semantics and compatible data/software rights.

Permanent rule reinforced:

**Worldwide shipping != Canadian serviceable offer evidence != CAD price evidence != mobile/software permission != catalog display rights.**
