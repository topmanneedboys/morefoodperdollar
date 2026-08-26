# ValuePilot CJ Candidate Batch

Updated: 2026-08-26

Milestone:
5D — Authorized Real Shopping Data Provider Selection

Purpose:
Select the next high-information CJ advertisers from the authenticated Canada-filtered advertiser directory without mass-applying blindly.

Source evidence reviewed on 2026-08-26:

- 90 CJ advertisers were returned with Advertiser Country = Canada
- AOSOM Canada is already pending and is not duplicated in this batch
- Brulerie Virgin Hill Coffee Roasters was rejected and is not reapplied to
- 123Ink.ca remains deprioritized because its terms contain a downloadable-software/application compatibility risk
- Abracadabra NYC remains unaccepted because its authenticated serviceable area is United States and supported currency is USD only, despite a daily feed and worldwide-shipping statement

## Selected next five CJ screens

### 1. PC Express (CID 7356175) — PRE-SCREEN FAILED / DO NOT APPLY NOW

Why it was strategically attractive:

- category: Groceries
- advertiser: Loblaw Companies Ltd.
- serviceable area: Canada
- supported currency: CAD
- previous-month conversion geography shown as 100% Canada
- description states more than 40,000 grocery products across multiple Loblaw-family grocery banners
- description states in-store prices/deals with no markup, making it potentially extremely valuable Canadian grocery evidence

Authenticated CJ terms reviewed on 2026-08-26:

- `Software: Not allowed`
- affiliate activity is framed around web, email and social channels
- incentivized traffic is not allowed
- branded SEM restrictions and negative matching requirements apply
- only CJ-provided logos/images may be used
- regulated/non-commissionable categories include alcohol, tobacco, prescriptions, gift cards, lottery and other regulated products
- the supplied advertiser details did not establish a product feed/datafeed/catalog path

Material conflict:

ValuePilot's permanent product includes an Android/mobile software client as a presentation surface. The explicit CJ policy `Software: Not allowed` is therefore a direct current-channel incompatibility. Do not apply while intending software/mobile affiliate use, and do not misrepresent ValuePilot as web-only merely to gain advertiser acceptance.

The advertiser is potentially worth reconsidering later because its Canadian grocery coverage is exceptionally relevant. Reconsider only if PC Express/Loblaw gives explicit written permission for the intended ValuePilot software/mobile model or if ValuePilot deliberately creates a separately authorized web-only channel whose use is consistent with all applicable terms. Any product-feed/data-use rights would still need separate validation.

Decision:

**DO NOT APPLY TO PC EXPRESS FOR THE CURRENT 5D SOFTWARE/MOBILE VALIDATION PASS.**

### 2. Natura Market (CID 5370099) — HOLD / DO NOT APPLY YET

Why it is strategically attractive:

- advertiser country: Canada
- serviceable area: Canada
- supported currency: CAD
- prior-month conversion activity shown overwhelmingly in Canada
- physical packaged-food catalog is highly relevant to ValuePilot's unit-value, package-size, ingredient/label and grocery-quality evidence model

Authenticated CJ details reviewed on 2026-08-26:

- Canadian site purchase action
- 7-day referral period
- standard affiliate web/search/coupon restrictions
- description mentions banners and text links
- no explicit product feed, datafeed or Product Catalog availability was present in the supplied advertiser description or terms
- no explicit `Software: Allowed` policy was present in the supplied terms
- no explicit `Software: Not allowed` policy was present either
- no visible language established mobile-app use or catalog caching/indexing/display/redistribution rights

Decision:

**HOLD / DO NOT APPLY YET FOR THE CURRENT 5D DATA-PROVIDER PASS.**

Natura Market is an excellent Canadian product/category fit, but the current evidence does not establish the two things ValuePilot is specifically trying to validate: an authorized structured product-feed/catalog path and software/mobile compatibility. Absence of a `Software: Not allowed` line is not equivalent to permission. Reconsider immediately if CJ exposes a separate Product Catalog/datafeed indicator for Natura Market or the advertiser confirms feed availability plus intended software/mobile use.

### 3. Today's Shopping Choice / TSC (CID 457947) — PRE-SCREEN PASSED / APPLY

Why it is strategically strong:

- advertiser country: Canada
- serviceable area: Canada
- advertiser states it sells to Canadians in Canadian dollars and ships within Canada only
- previous-month conversions shown as 99.96% Canada
- description states more than 15,000 items online across jewellery, beauty, health & fitness, apparel, accessories, home goods, toys and electronics
- most importantly, the advertiser explicitly states: `our full Product Catalog is available in CJ!`
- broad multi-category coverage is useful for Product-vs-Offer, category diversity, price/variant and general-shopping architecture stress testing

Authenticated program terms reviewed on 2026-08-26:

- no explicit `Software: Not allowed` restriction appears in the supplied terms
- no explicit `Software: Allowed` grant appears either
- the visible policy set is primarily branded SEM/direct-link/domain-keyword restrictions
- no visible anti-comparison restriction was supplied
- no visible prohibition on dynamic product-comparison content was supplied
- no visible caching/indexing/display/redistribution license was supplied

Interpretation:

Unlike PC Express, there is no affirmative software prohibition in the supplied terms. Unlike Natura Market, there is an explicit structured-catalog path: the full Product Catalog is stated to be available in CJ. That combination is sufficient for a controlled advertiser application because the purpose of the application is to inspect authorized catalog access and then clarify any software/mobile and catalog-use rights before production integration.

This is not proof that ValuePilot may cache, index, normalize, persist, redistribute or display all TSC catalog content inside a mobile app. Those rights remain unproven and must be validated after advertiser acceptance and actual Product Catalog access.

Decision:

**APPLY TO TODAY'S SHOPPING CHOICE / TSC FOR CONTROLLED 5D VALIDATION.**

After application, record the exact relationship state. If approved, inspect the actual CJ Product Catalog/feed before implementation, including Canadian/CAD semantics, product identifiers, quantities/dimensions, current/reference/sale price fields, availability/freshness, image/deep-link fields, feed-update behavior and exact permitted caching/indexing/display/mobile-software use.

### 4. West Coast Kids - Canada (CID 5753672) — NEXT SCREEN

Why:

- category shown as Babies
- CAD earnings metadata
- broad physical-goods mix can test counts, sizes, bundles, consumables vs durable products and variant identity
- directly Canadian program naming

Pre-screen product-feed/datafeed availability and software/mobile restrictions before application.

### 5. Brother Canada (CID 5267676) — HIGH PRIORITY CROSS-CATEGORY IDENTITY SCREEN

Why:

- category shown as Peripherals
- CAD earnings metadata
- useful non-grocery catalog for model/SKU identity, accessories, consumables, bundles and Product-vs-Offer normalization
- helps prevent provider-neutral architecture from overfitting grocery-only evidence

Do not apply until its current CJ terms and feed/software compatibility are inspected.

## Backups if one of the top five fails

1. DAVIDsTEA (CID 5226617) — packaged consumable/unit-value candidate
2. Zwilling Canada (CID 5470201) — kitchen/durable-goods variant candidate with CAD earnings metadata
3. EcoFlow CA (CID 6175965) — electronics/durable-goods candidate with CAD earnings metadata
4. Dell Canada - Home & Small Business (CID 4380018) — large electronics/model-identity candidate, but potentially higher approval/terms complexity
5. Aeptom (CID 7672100) — Bed & Bath candidate with CAD earnings metadata

## Deliberately not prioritized

- financial services, loans, tax software, travel, dating, hosting and generic SaaS because they do not advance the authorized physical-product catalog milestone
- fashion-only merchants where size/variant evidence is useful but weaker than grocery/broad-catalog candidates for the first production data rail
- regulated or medically sensitive categories where avoidable complexity outweighs early validation value
- advertisers already rejected, already pending or known to have material software-channel conflicts

## Required pre-application gate

For each selected advertiser, inspect and record:

1. More Info / program description
2. explicit product feed/datafeed/catalog availability if stated
3. serviceable area and supported currency, especially Canada/CAD
4. Software / downloadable application / toolbar policy
5. comparison-shopping, content, review or dynamic-presentation restrictions
6. any advertiser-specific disclosure, coupon, paid-search or attribution rules that could conflict with ValuePilot
7. any language governing caching, indexing, display, redistribution or use of advertiser/feed content if visible

Unknown caching/indexing/display rights do not automatically block a controlled application, but they must remain unproven and require later clarification before integration.

## Current action

PC Express failed the current pre-screen because `Software: Not allowed` conflicts with ValuePilot's intended software/mobile channel and no explicit product-feed path was established.

Natura Market remains a strong Canadian grocery candidate but is on hold because the supplied evidence does not establish a structured product-feed/catalog path or explicit software/mobile compatibility.

Today's Shopping Choice / TSC passed the controlled application screen because it explicitly states that its full Product Catalog is available in CJ, is Canada/CAD focused, and the supplied terms contain no explicit software prohibition or anti-comparison restriction. This does not establish production data-use rights.

Next actions:

1. Apply to Today's Shopping Choice / TSC and record the resulting relationship state.
2. Screen West Coast Kids - Canada next.
3. Continue to Brother Canada after that.
