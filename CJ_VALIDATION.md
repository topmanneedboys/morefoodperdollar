# ValuePilot CJ Affiliate Validation Record

Updated: 2026-08-22

Milestone:
5D — Authorized Real Shopping Data Provider Selection

Status:
CJ publisher account ACTIVE. Advertiser-level validation in progress. No CJ production adapter or production network integration is authorized.

## 123Ink.ca / ShopperPlus Canada validation

Observed through the live CJ publisher account on 2026-08-22.

Advertiser:
123Ink.ca / ShopperPlus Canada

Observed advertiser-level metadata:

- advertiser country: Canada
- serviceable area: Canada
- supported currencies: CAD and USD
- manual application review
- 45-day referral period
- physical-product catalog spanning printer supplies, computers, accessories and related goods
- advertiser description states roughly 26,000 products
- advertiser description states `Daily Datafeed Available`

These are useful discovery signals only. They do not prove row-level Canadian geography, currency, freshness, identifier quality, quantity evidence, caching rights, redistribution rights, or production suitability.

### Program terms observed

Commission structure:

- ShopperPlus Canada Purchase action
- 5% commission
- unlimited occurrences
- standard locking
- reseller sales excluded
- multiple categories/items are explicitly non-commissionable

Search/SEM restrictions include:

- protected brand and competitor bidding restrictions
- negative matching required for protected keywords
- prohibited trademark content in SEM display URLs and ad copy
- direct linking from search campaigns prohibited
- advertiser recommends SEO rather than publisher CPC search marketing

Website/content restrictions include:

- prohibited unlawful, violent, weapon, gambling, discriminatory, sexually explicit and IP-infringing content
- false or misleading expired promotion claims prohibited
- use of corporate logos/trademarks requires prior consent
- supplied logos/images may not be modified
- incentivized traffic, email and social media are allowed
- public or affiliate-provided coupons/promotional codes may be used

### Critical software/application restriction

The terms state that toolbars, downloadable third-party cookies, and related applications requiring users to download software are strictly prohibited, and that promotional or transactional affiliate activity carried out through such toolbars/software/downloadable third-party cookies is not permitted.

For ValuePilot this creates a material compatibility risk because the permanent product includes an Android application as one presentation/client surface.

Do not infer that 123Ink.ca authorizes affiliate promotion inside the ValuePilot Android app merely because the CJ publisher account is active or because the advertiser exposes a daily datafeed.

Before treating 123Ink.ca as a production-fit merchant, obtain advertiser/CJ clarification that the intended ValuePilot promotion channel is allowed, or constrain any future relationship to a clearly permitted web/service channel if that is independently authorized.

### Disclosure requirement

The advertiser requires a disclosure in close proximity to links/reviews involving ShopperPlus.ca, 123ink.ca, primecables.ca and living.ca stating participation in the ShopperPlus.ca Affiliate Program and commission eligibility after linking through the publisher site.

Any future implementation using this advertiser must preserve this advertiser-specific disclosure requirement rather than relying only on a generic site-wide affiliate disclosure.

### 5D decision

123Ink.ca is a useful **data-quality validation candidate** because of its Canadian service area, CAD support, broad physical-product catalog and stated daily datafeed.

However it is **not yet a production-fit candidate** because:

1. the software/downloadable-application restriction may conflict with ValuePilot's mobile-app distribution/promotion model
2. the visible terms do not establish permission to cache, index, normalize or redistribute feed data in ValuePilot
3. the visible metadata does not establish actual feed-field quality or row-level Canadian offer semantics
4. advertiser acceptance has not been obtained

Current recommendation:

**Do not apply merely to accumulate affiliate relationships. Continue inspecting alternative Canadian CJ advertisers for stronger compatibility with product-comparison/services/mobile-app use. Return to 123Ink.ca only if it remains strategically valuable and the software/data-use restrictions can be clarified.**

## Permanent CJ rule reinforced by this validation

**CJ advertiser discovery != advertiser acceptance != datafeed availability != software/mobile permission != permission to cache/index/display product data.**

These must remain separate evidence dimensions in ValuePilot.
