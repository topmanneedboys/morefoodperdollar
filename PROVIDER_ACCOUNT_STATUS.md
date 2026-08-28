# ValuePilot Provider Account Status

Updated: 2026-08-28

Milestone: 5D — Authorized Real Shopping Data Provider Selection

Purpose: keep fast-changing external account/application status separate from architectural provider research and empirical data-quality evidence.

This file is an operational checkpoint only. It does not authorize production networking, credentials in the app, backend deployment, affiliate-driven ranking, or any provider adapter.

## Current status summary

| Provider / program | Current status | Next meaningful action |
| --- | --- | --- |
| Rakuten Advertising publisher account | ACTIVE | Keep technical Product Catalog access isolated from production authorization |
| Rakuten Product Catalog technical account | ENABLED | Credentials remain outside repo/app; no automatic production downloader yet |
| Jamieson Vitamins | PARTNERED + ADVERTISER PRODUCT FEED APPROVED + ACTUAL COMPLETE FEED AVAILABLE | Price schema semantics resolved; next gates are Android/feed-use rights, DSA approval, freshness, Canadian offer scope and broader package quantity |
| GS1 Canada ECCnet | DATA RECIPIENT ELIGIBILITY / RIGHTS INQUIRY SENT 2026-08-28 | Await written eligibility, GTIN net-content scope, mobile/search/cache/display rights and commercial/API terms |
| Well.ca | APPLIED / PENDING unless newer evidence arrives | Wait for advertiser decision |
| Tru Earth | REJECTED on 2026-08-26 | Do not reapply now; no advertiser-specific reason established |
| Bath Depot / Bain Depot | APPLIED / PENDING unless newer evidence arrives | Wait for advertiser decision |
| Giant Tiger | REJECTED on 2026-08-24 | Do not reapply now |
| CJ Affiliate publisher account | ACTIVE | Wait on current deliberately screened applications; do not add random advertisers now |
| Today's Shopping Choice / TSC | PENDING | If approved, inspect actual Product Catalog and mobile/software/data-use rights |
| Brother Canada | PENDING | If approved, inspect actual Product Catalog and mobile/software/data-use rights |
| DAVIDsTEA | PENDING | If approved, Canadian/CAD offer semantics are a hard gate because advertiser profile is USD-oriented |
| AOSOM Canada | PENDING unless newer evidence arrives | Wait for decision |
| Brulerie Virgin Hill Coffee Roasters | REJECTED on 2026-08-24 | Do not reapply now |
| Abracadabra NYC | UNSOLICITED PENDING OFFER | Do not accept without stronger Canada/CAD/feed/software-rights evidence |
| GearUP | UNSOLICITED PENDING OFFER | Skip for current physical-shopping/catalog milestone |
| Awin publisher account | ACTIVE | Do not submit more applications without advertiser-level publisher-type/feed compatibility screening |
| Skip CA on Awin | REJECTED on 2026-08-26 | Do not reapply under the same truthful publisher type |
| impact.com partner account | ACCOUNT EXISTS / MARKETPLACE APPLICATION DECLINED | Do not create duplicate account or blindly reapply |
| Lowvyn | RIGHTS / TECHNICAL INQUIRY SENT | Await written commercial-use, display, caching/indexing, field and downstream-rights clarification; if approved, discuss partner/full-catalog access in the same thread |
| Open Prices | VALIDATED SUPPLEMENTAL OPEN PRICE RAIL | Historical/proof-backed observation use only; current Canada coverage is too sparse for primary pricing |
| Open Food Facts | VALIDATED SUPPLEMENTAL PRODUCT-METADATA RAIL | Jamieson normalized coverage 102/271 matches but only 12 exact supplement counts; supplemental only |

## Rakuten checkpoint

### Technical Product Catalog account

Rakuten Customer Support created the Product Catalog file-transfer account and confirmed technical access. No file-transfer username, password, token or other credential belongs in this repository, the Android app, logs, screenshots, fixtures or documentation.

Permanent distinction:

**publisher account -> advertiser partnership -> advertiser Product Catalog approval -> actual file availability -> schema/quality validation -> data-use-rights validation -> channel/DSA approval -> production authorization**

Each remains a separate gate.

### Jamieson Vitamins — first actual authorized feed

Jamieson approved ValuePilot's advertiser partnership on 2026-08-26.

After ValuePilot submitted the separate Product Feed request, Rakuten Customer Support explicitly confirmed on 2026-08-28 that ValuePilot is approved for Jamieson's advertiser Product Feed and that the Jamieson feed is present in the Product Catalog SFTP account.

The complete compressed TXT catalog was then downloaded and inspected offline. The proprietary catalog itself is not committed to the repository.

Empirical first-feed checkpoint:

- valid HDR/TRL structure
- trailer count matches **273** product records
- **273 / 273** records have the documented 38-field shape
- **273 / 273 CAD**
- **273 / 273 in-stock**
- **273** unique SKUs and **273** unique Product IDs
- **271 / 273** supplied UPC/GTIN values, all **271 / 271** checksum-valid
- **273 / 273** syntactically valid product URLs and image URLs
- manufacturer present as Jamieson on all 273 rows
- descriptions present on 272 / 273 rows
- all 273 Class ID values blank
- Sale Price is below Retail Price on 48 rows, equal on 223 rows, and **above** Retail Price on 2 rows

Decision:

**JAMIESON = FIRST VALUEPILOT PROVIDER WITH ADVERTISER PRODUCT FEED APPROVAL + ACTUAL COMPLETE FILE AVAILABILITY. DATA QUALITY IS PROMISING. RAKUTEN PRICE FIELD SEMANTICS ARE NOW RESOLVED AT THE GENERIC SCHEMA LEVEL, BUT PRODUCTION RIGHTS/FRESHNESS/CHANNEL APPROVAL AND BROAD PACKAGE QUANTITY REMAIN OPEN GATES.**

Rakuten's current Product Catalog Appendix A defines Sale Price as a price reflecting discounts and Retail Price as a price not reflecting discounts.

Therefore:

- Sale < Retail is structurally consistent with a discount;
- Sale = Retail must not produce a savings claim;
- Sale > Retail conflicts with the documented field semantics and must fail closed;
- the 2 inverted rows must not be swapped, silently corrected, or interpreted as markup.

The feed also does not establish a universal structured package quantity. With all Jamieson Class IDs blank, there is currently no validated class-specific Size field. Therefore the feed has **273 structural offer candidates but 0 authoritative unit-value candidates from Rakuten alone** until package quantity/count is established through a validated source.

The corrected Jamieson × Open Food Facts run established:

- **102 / 271** normalized GTIN matches;
- **12** exact supplement-count candidates;
- **2** structured mass/volume-only candidates;
- **0** quantity conflicts;
- **88** matched products with no usable quantity;
- **169** unmatched GTINs.

Open Food Facts is therefore useful supplemental metadata, not the package-count foundation.

A future quantity join may use a separate appropriately licensed source matched by strong identity such as canonical checksum-valid GTIN. Preserve provenance: the quantity source must not be represented as the merchant price source.

Jamieson/supplement guardrail remains factual evidence only: price, count, quantity, dosage form, printed strength, sourced ingredients/label attributes and deterministic unit value. Do not fabricate medical efficacy, treatment or safety claims.

### Rakuten freshness boundary

Rakuten's current Product Catalog guidance says:

- files are generated dynamically when retrieved;
- the file contains the most up-to-date product information currently present in the advertiser's Product Catalog database;
- timeliness depends on how often the advertiser updates that database;
- advertisers may process updates multiple times per day;
- delta files contain new, changed and deleted product records from the advertiser's last processed feed;
- the header timestamp is the time the file was deposited into the publisher SFTP account.

Therefore the dataset has useful retrieval/deposit/update-process evidence, but no universal per-product last-modified timestamp and no guarantee that an individual merchant-site price is live at display time.

Do not promote the HDR timestamp into per-product freshness.

### Rakuten Android / DSA boundary

Rakuten's current Publisher Membership Agreement allows promotion through mobile applications in general, subject to advertiser terms and Network Policies.

However, Rakuten's Network Policies expressly cover installed/mobile applications under Downloadable Software Application controls. Before ValuePilot launches Rakuten network links inside the installed Android app:

1. Rakuten / Network Quality approval and compliance testing must be completed; and
2. the advertiser must approve the new DSA distribution method.

The existing Jamieson Product Feed approval is **not** treated as Android/DSA approval.

Product Catalog documentation also still describes advertiser feed approval for website/blog use, and the Jamieson partnership approval message refers to use on the approved website/marketing channel. Therefore Android feed-display/cache/index rights require written clarification before production integration.

Detailed decision: `RAKUTEN_PRICE_AND_ANDROID_RIGHTS_GATE.md`.

Remaining gates before production:

- intended Canadian offer geography beyond observed CAD and advertiser context
- broader package/count/strength/dosage evidence
- bounded dataset/current-price freshness policy
- caching/persistence/indexing/search/display rights
- mobile/software/catalog-use rights
- Rakuten Network Quality DSA submission/testing before Android affiliate-link use
- advertiser approval for the DSA/mobile distribution method
- privacy/consent/disclosure obligations before production tracking

### GS1 Canada ECCnet

ECCnet is the next strategic package-content/identity candidate because it is GTIN-centric and supports standardized product net-content data including count-style net content.

The ValuePilot Data Recipient eligibility/rights inquiry was sent to GS1 Canada on 2026-08-28.

Decision:

**ECCNET = HOLD UNTIL WRITTEN ELIGIBILITY / RIGHTS / TECHNICAL RESPONSE.**

Do not implement an ECCnet adapter or assume Jamieson coverage until GS1 confirms ValuePilot's eligibility, usable GTIN-level product-content scope, mobile/search/cache/display permissions, restrictions, attribution/retention requirements, API/extract options and commercial terms.

### Tru Earth

Rakuten sent an application-denied notice on 2026-08-26 stating that Tru Earth chose not to accept ValuePilot into its affiliate program at this time. No advertiser-specific reason is established by that generic notice.

Decision:

**TRU EARTH = REJECTED / DO NOT REAPPLY NOW.**

### Well.ca and Bath Depot

Both remain pending unless newer authenticated evidence arrives. Do not infer approval from Product Catalog visibility or software-compatible profile metadata.

### Giant Tiger

Denied on 2026-08-24. Preserve the denial; do not reapply now without materially new evidence.

## CJ Affiliate checkpoint

CJ publisher access is active.

The current deliberately screened pending applications are:

- Today's Shopping Choice / TSC — Canadian/CAD profile and explicit full Product Catalog evidence
- Brother Canada — Canadian/CAD profile and explicit Product Catalog evidence
- DAVIDsTEA — Product Catalog evidence but USD-only profile semantics create a hard Canadian/CAD post-approval validation gate
- AOSOM Canada — older pending application unless newer evidence arrives

Do **not** add more CJ applications now. The highest-value next step is to wait for these decisions and inspect an actual approved catalog/feed.

Permanent distinction:

**CJ publisher access != advertiser relationship != catalog/feed access != permission to cache/index/display catalog data.**

Previously screened failures/holds remain in their dedicated validation files. In particular, Dell Canada Home & Small Business is not a current ValuePilot candidate because its supplied agreement prohibited including price information in product descriptions, which conflicts with the product's core price-comparison purpose.

## Awin checkpoint

Awin publisher access is active.

Skip CA rejected ValuePilot on 2026-08-26 with the advertiser-provided reason that it does not work with this publisher type.

Decision:

**SKIP CA = REJECTED / DO NOT REAPPLY UNDER A MISREPRESENTED PUBLISHER TYPE.**

Open-data/feed experiments previously showed that feed availability alone does not establish correct Canadian currency, semantics or identity quality.

## impact.com checkpoint

The Marketplace/media-partner application was declined on 2026-08-25. The available notice did not provide a concrete reason.

Decision:

**IMPACT.COM MARKETPLACE APPLICATION = DECLINED.**

Do not create a duplicate account or submit a blind duplicate application.

## Lowvyn checkpoint

A targeted ValuePilot integration inquiry has been sent asking for written clarification of:

- consumer display of retailer-specific prices
- cross-retailer comparisons and deterministic derived value metrics
- caching/history retention
- indexing/search rights
- redistributable fields and attribution
- bulk use/rate limits
- GTIN/package quantity/freshness fields
- retailer-specific downstream restrictions
- free-tier versus production/commercial agreement requirements

Decision:

**LOWVYN = HOLD UNTIL WRITTEN RIGHTS/TECHNICAL RESPONSE.**

If Lowvyn approves the initial integration request, the next communication should remain in the same thread and ask about partner-level/full-catalog access, efficient synchronization or bulk access, higher production limits, caching/storage, display rights, attribution and affiliate/commercial routing. Do not ask for ownership of Lowvyn's database and do not make Lowvyn the sole provider dependency.

## Open-data checkpoint

Open-data work is independent of affiliate approval and is documented in `OPEN_DATA_INTEGRATION_STATUS.md` and `OPEN_PRICES_CANADA_COVERAGE.md`.

Current decision:

- Open Prices = supplemental proof-backed observed/historical price rail, not a nationwide current-price provider
- Open Food Facts = supplemental GTIN/product/package metadata rail, not current retailer price/stock
- Health Canada = identity/regulatory/nutrition reference where appropriate, never retailer offer
- Statistics Canada = market benchmark/context only, never retailer offer

These sources remain provenance-separated. A weaker source must not overwrite a stronger current merchant fact, and unresolved equal-scope factual conflicts must block Best Value rather than be averaged or guessed.

## Current next actions

1. Do not spend more time proving generic Rakuten Retail/Sale field meanings; that schema question is resolved.
2. Keep the 2 Jamieson Sale > Retail rows as semantic-invalid price evidence and fail closed rather than auto-correcting them.
3. Obtain written Rakuten clarification of Product Catalog cache/index/display/mobile/retention rights and whether feed use inside the installed Android app needs separate advertiser permission.
4. Before enabling Rakuten network links in Android, complete Rakuten DSA/Network Quality approval and advertiser approval for the mobile distribution method.
5. Define a bounded dataset/current-price freshness policy using retrieval/delta evidence without inventing per-product freshness.
6. Await GS1 Canada ECCnet response for broader GTIN-level package-content/count coverage and rights.
7. Wait for TSC, Brother Canada, DAVIDsTEA, Well.ca, Bath Depot and AOSOM decisions rather than submitting more advertiser applications now.
8. Wait for Lowvyn's written rights/technical response; if approved, discuss partner/full-catalog access before deeper integration.
9. Continue bounded, network-free open-data engineering only behind provenance/conflict/rankability gates.
10. Do not add Android `INTERNET` or `ACCESS_NETWORK_STATE` permissions yet.
11. Never use commission, EPC, payout, sponsorship or provider preference as a ValuePilot ranking input.
