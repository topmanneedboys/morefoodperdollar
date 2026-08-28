# ValuePilot Provider Account Status

Updated: 2026-08-27

Milestone: 5D — Authorized Real Shopping Data Provider Selection

Purpose: keep fast-changing external account/application status separate from architectural provider research and empirical data-quality evidence.

This file is an operational checkpoint only. It does not authorize production networking, credentials in the app, backend deployment, affiliate-driven ranking, or any provider adapter.

## Current status summary

| Provider / program | Current status | Next meaningful action |
| --- | --- | --- |
| Rakuten Advertising publisher account | ACTIVE | Keep technical Product Catalog access isolated from advertiser/feed authorization |
| Rakuten Product Catalog technical account | ENABLED | Use only after advertiser-level feed approval; never store credentials in repo/app |
| Jamieson Vitamins | PARTNERED; advertiser-level Product Catalog request submitted | Wait for actual feed access/file; then inspect schema, Canada/CAD semantics, quality and exact data-use rights |
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
| Lowvyn | RIGHTS / TECHNICAL INQUIRY SENT | Await written commercial-use, display, caching/indexing, field and downstream-rights clarification before integration |
| Open Prices | VALIDATED SUPPLEMENTAL OPEN PRICE RAIL | Historical/proof-backed observation use only; current Canada coverage is too sparse for primary pricing |
| Open Food Facts | VALIDATED SUPPLEMENTAL PRODUCT-METADATA RAIL | Metadata/package quantity only; preserve ODbL/source provenance and never treat it as retailer current-price evidence |

## Rakuten checkpoint

### Technical Product Catalog account

Rakuten Customer Support created the Product Catalog file-transfer account and confirmed the technical account can access advertiser feeds after advertiser-level approval.

Technical Product Catalog enablement is **not** equivalent to advertiser feed authorization.

Permanent distinction:

**publisher account -> advertiser partnership -> advertiser Product Catalog approval -> actual file availability -> schema/quality validation -> data-use-rights validation -> production authorization**

Each is a separate gate.

No file-transfer username, password, token or other credential belongs in this repository.

### Jamieson Vitamins

Jamieson approved ValuePilot's advertiser partnership on 2026-08-26.

After technical Product Catalog enablement, ValuePilot submitted the Jamieson advertiser-level Product Feed request under Rakuten `Links -> Product Feeds`. The UI changed from `Apply` to `Remove`, which is evidence that the request was submitted, not proof that the feed is approved or available.

Follow-up messages were sent to both the Jamieson affiliate contact and the existing Rakuten Product Catalog support case asking whether the request is pending advertiser approval and whether any additional action is required.

Decision:

**JAMIESON = PARTNERED + PRODUCT CATALOG REQUEST SUBMITTED; WAIT FOR ACTUAL FEED ACCESS.**

When the feed appears, first inspect the complete TXT feed rather than assuming the data is production-ready. Validate:

- Canada/CAD semantics
- row count and coverage
- GTIN/SKU/provider IDs
- package/count/strength/dosage fields
- current/reference/sale-price semantics
- freshness
- availability
- image/deep links
- variant identity
- caching/persistence/indexing/display/mobile rights

Jamieson/supplement guardrail remains factual evidence only: price, count, quantity, dosage form, printed strength, sourced ingredients/label attributes and deterministic unit value. Do not fabricate medical efficacy, treatment or safety claims.

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

Do not add a production adapter merely because a public API exists.

## Open-data checkpoint

Open-data work is independent of affiliate approval and is documented in `OPEN_DATA_INTEGRATION_STATUS.md` and `OPEN_PRICES_CANADA_COVERAGE.md`.

Current decision:

- Open Prices = supplemental proof-backed observed/historical price rail, not a nationwide current-price provider
- Open Food Facts = supplemental GTIN/product/package metadata rail, not current retailer price/stock
- Health Canada = identity/regulatory/nutrition reference where appropriate, never retailer offer
- Statistics Canada = market benchmark/context only, never retailer offer

These sources remain provenance-separated. A weaker source must not overwrite a stronger current merchant fact, and unresolved equal-scope factual conflicts must block Best Value rather than be averaged or guessed.

## Current next actions

1. Wait for actual Jamieson Product Catalog approval/file availability and inspect the real feed when it appears.
2. Wait for TSC, Brother Canada, DAVIDsTEA, Well.ca, Bath Depot and AOSOM decisions rather than submitting more advertiser applications now.
3. Wait for Lowvyn's written rights/technical response before any integration.
4. Continue bounded, network-free open-data engineering only behind provenance/conflict/rankability gates.
5. Do not add Android `INTERNET` or `ACCESS_NETWORK_STATE` permissions yet.
6. Do not implement a production Rakuten/CJ/Awin/impact/Lowvyn adapter until actual data-use rights and field quality have been validated.
7. Never use commission, EPC, payout, sponsorship or provider preference as a ValuePilot ranking input.
