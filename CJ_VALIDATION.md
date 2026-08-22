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

## AOSOM Canada validation

Observed through the live CJ publisher account on 2026-08-22.

### Program economics

- `Aosom.ca Purchase`
- 8% default commission
- 14-day referral period
- unlimited occurrences
- standard locking
- 10% commission for the listed Cat Tree item list
- one-time CAD 25 performance incentive at 11 actions
- one-time CAD 50 performance incentive at 21 actions

Affiliate economics are recorded only for completeness. ValuePilot ranking must remain independent of commission rates, incentives or advertiser payout.

### Advertiser/catalog evidence

AOSOM describes itself as a global marketplace founded in 2009 with operations/warehouses in multiple countries including Canada.

The advertiser description identifies a broad private-brand physical-goods catalog including:

- home and garden furniture
- baby products
- pet supplies
- fitness equipment
- outdoor supplies
- office supplies
- DIY tools

Named private brands include Homcom, Outsunny, Pawhut and others.

The description explicitly states:

- regular promotion information is available, including discounts and price drops
- `Product feed availability`
- free delivery
- affiliate account-management/cooperation opportunities

These are materially stronger 5D signals than commission terms alone because AOSOM exposes both broad physical-goods coverage and an explicit product-feed path.

### Publisher/promotion policy evidence

The visible AOSOM policy text states:

- newsletters and content sent to customers that contain AOSOM Canada information must be checked manually by the AOSOM team before being sent
- trademark bidding is prohibited for AOSOM and its named private brands
- certain trademark-plus paid-search combinations are also prohibited
- product/category combinations such as brand + product/category examples are shown as allowed in the PPC guidance

The supplied AOSOM terms do **not** show the same blanket downloadable-software/tool restriction observed in the 123Ink.ca / ShopperPlus terms.

That absence is useful but is not equivalent to explicit mobile-app permission.

### Content-review ambiguity

The requirement that newsletters and `contents send out to customers` containing AOSOM Canada information be manually checked creates an interpretation risk for ValuePilot.

It is unclear from the visible text whether this requirement applies only to authored outbound marketing/newsletters, or whether AOSOM intends it to cover dynamically generated product-comparison content shown inside a service or future app.

Do not silently interpret this clause in ValuePilot's favor. If the relationship is approved, obtain clarification before any production use that exposes AOSOM product information dynamically to users.

### Application status

Application submitted through the live CJ account on 2026-08-22.

CJ confirmation states:

- `Application submitted for review`
- AOSOM Canada approves publishers manually
- application status is `Pending Application`
- advertiser will contact the publisher if approved

Current relationship state:
**PENDING MANUAL ADVERTISER REVIEW**

Do not submit a duplicate AOSOM application while this review is pending.

### 5D decision

AOSOM is currently the strongest CJ advertiser inspected for **empirical feed validation** because:

1. it has a broad Canadian-relevant physical-goods catalog
2. product-feed availability is stated explicitly
3. promotions/price-drop information are stated explicitly
4. the visible terms do not contain the explicit downloadable-software prohibition that makes 123Ink.ca higher risk for ValuePilot

However AOSOM is still not production-authorized. The visible evidence does not establish:

- mobile-app/software promotional permission
- permission to cache/index/normalize/redistribute feed data
- actual feed schema and field quality
- row-level Canadian currency/geography semantics
- freshness granularity
- identifier/GTIN quality
- quantity/unit evidence quality
- whether the content-review clause applies to dynamic ValuePilot presentation

Current recommendation while pending:

**Wait for the AOSOM advertiser decision and do not submit a duplicate application. Continue 5D validation with other high-value Canadian CJ advertisers only if doing so adds materially different evidence; do not accumulate low-value affiliate relationships. If AOSOM is approved, inspect the actual authorized product feed and clarify mobile-app/dynamic product-display rights before any production use.**

Suggested application description if CJ requests one:

`ValuePilot is a pre-launch Canadian shopping-intelligence and product-comparison service. We help consumers discover and compare physical products using price, quantity, unit value, availability, promotions and other measurable shopping information. Our public marketing site is https://topmanneedboys.github.io/morefoodperdollar/. We are interested in AOSOM's product feed for authorized product-discovery/comparison use. Ranking is independent of affiliate commission rates. We do not use paid-search trademark bidding. A mobile app is planned for future distribution; before any mobile-app affiliate use we will confirm that channel and any product-content review requirements with AOSOM.`

## Brulerie Virgin Hill Coffee Roasters validation

Observed through the live CJ publisher account on 2026-08-22.

### Advertiser/catalog evidence

- advertiser country: Canada
- serviceable area: Canada
- supported consumer currency shown as CAD
- category: Gourmet
- specialty coffee catalog includes whole-bean and ground coffee, single origins, blends, espresso, organic and decaf products, plus brewing equipment/accessories
- target audience is Canadian specialty-coffee consumers
- new roasts and seasonal offerings are added regularly

This merchant is especially useful to ValuePilot as a quantity/unit-value validation candidate because packaged coffee should expose measurable weights or sizes if the underlying catalog/feed preserves them correctly.

The separate advertiser-account metadata also displayed `Currency: USD`. Do not infer offer currency from that account-level field; validate actual offer/feed currency row by row.

### Program terms observed

- `Website Purchase`
- 10% commission
- 30-day referral period
- unlimited occurrences
- standard locking
- protected branded SEM bidding prohibited
- negative matching required
- SEM direct linking prohibited
- supplied CJ logos/images only
- incentivized traffic allowed
- email allowed
- **software allowed**
- sub-affiliates allowed
- social media allowed subject to no branded impersonation accounts
- only affiliate-program coupons/promotional codes may be used
- false, misleading, expired or inaccurate promotional claims prohibited
- publisher may not claim to be the official site or an authorized wholesaler

Affiliate economics are recorded only for completeness. ValuePilot ranking must remain independent of commission rates or advertiser payout.

### Software compatibility signal

The explicit `Software: Allowed` policy is materially stronger for ValuePilot than the 123Ink.ca terms, which prohibit promotional/transactional activity through downloadable software.

However `Software: Allowed` is still not equivalent to unrestricted permission to:

- cache/index product-feed data
- normalize or derive product attributes
- redistribute catalog data
- use merchant data in every mobile-app context
- persist prices beyond allowed freshness windows

Those rights remain unverified until advertiser/CJ evidence establishes them.

### Application status

Application submitted through the live CJ account on 2026-08-22.

Current relationship state:
**APPLICATION SUBMITTED / ADVERTISER DECISION PENDING UNLESS CJ LATER SHOWS OTHERWISE**

Do not submit a duplicate Virgin Hill application while a decision is pending.

### 5D decision

Virgin Hill is currently the strongest CJ candidate for **software-channel compatibility testing** and a high-value candidate for **quantity/unit-price feed validation**.

If approved, inspect whether CJ exposes a product feed or product-search/API records for Virgin Hill and validate:

1. package weight/size preservation
2. CAD offer pricing and geography
3. current vs regular/sale price semantics
4. product/variant identifiers and GTIN/UPC/EAN quality
5. availability and freshness
6. image/deep-link fields
7. caching/indexing/display rights
8. any restrictions on dynamic software/mobile presentation

Do not integrate or publicly expose Virgin Hill data until those questions are resolved.

## Permanent CJ rule reinforced by this validation

**CJ advertiser discovery != advertiser acceptance != datafeed availability != software/mobile permission != permission to cache/index/display product data.**

These must remain separate evidence dimensions in ValuePilot.
