# ValuePilot Provider Account Status

Updated: 2026-08-29

Purpose: fast-changing external account/application status. This file is operational only; it does not itself authorize production networking, mobile use, caching, affiliate links, ranking or provider integration.

## Strategic role changed 2026-08-29

Provider/affiliate approvals are now **supplementary accelerators, not the primary ValuePilot launch dependency**. The core product proceeds through the Practical Shopping MVP/open-first strategy documented in `CURRENT_STATE.md`, `CONTINUATION_CHECKPOINT.md` and `VALUEPILOT_MASTER_CONTINUATION_PROMPT.md`.

Do not spend most engineering/product time chasing advertiser-by-advertiser approvals. Incorporate provider evidence when it materially improves authoritative product/price/package/commerce coverage.

## Current status summary

| Provider / program | Current status | Next meaningful action |
| --- | --- | --- |
| Rakuten Advertising publisher account | ACTIVE | Keep technical/catalog access separate from advertiser-specific app/display/link authorization |
| Rakuten Product Catalog technical account | ENABLED | Credentials remain outside repo/app; no automatic production downloader yet |
| Jamieson Vitamins | PARTNERED + PRODUCT FEED APPROVED + COMPLETE FEED AVAILABLE + ADVERTISER APP-USE PERMISSION REQUEST SENT | Wait for Jamieson response; do not resend unless incomplete/new issue |
| Walmart Canada (Rakuten MID 36751) | ELIGIBILITY PRE-FILTER BLOCKS APPLICATION | Do not falsify account/channel data; if pursued later ask Rakuten which advertiser term fails |
| GS1 Canada ECCnet | INQUIRY SENT + ACKNOWLEDGED | Await substantive eligibility/rights/technical/commercial response; not a launch blocker |
| Well.ca | PENDING unless newer evidence | Wait |
| Tru Earth | REJECTED 2026-08-26 | Do not reapply now |
| Bath Depot / Bain Depot | PENDING unless newer evidence | Wait |
| Giant Tiger | REJECTED 2026-08-24 | Do not reapply now |
| CJ Affiliate account | ACTIVE | Do not add random applications; wait for deliberately screened pending programs |
| Today's Shopping Choice / TSC | PENDING | If approved, inspect actual feed + mobile/data-use rights |
| Brother Canada | PENDING | If approved, inspect actual feed + mobile/data-use rights |
| DAVIDsTEA | PENDING | Canadian/CAD offer semantics remain a hard gate if approved |
| AOSOM Canada | PENDING unless newer evidence | Wait |
| Brulerie Virgin Hill | REJECTED 2026-08-24 | Do not reapply now |
| Awin publisher account | ACTIVE | No broad application campaign |
| Skip CA on Awin | REJECTED 2026-08-26 for publisher type | Do not misrepresent/reapply under same conditions |
| impact.com | ACCOUNT EXISTS / MARKETPLACE DECLINED 2026-08-25 | No duplicate account/blind reapply |
| Lowvyn | RIGHTS/TECHNICAL INQUIRY SENT | Await written response; not a launch dependency |
| Open Prices | VALIDATED SUPPLEMENTAL OPEN PRICE RAIL | Use as proof-backed observed/historical evidence only |
| Open Food Facts | VALIDATED OPEN PRODUCT-METADATA RAIL | Use source-isolated; not proof of current retailer price/stock |

## Rakuten / Jamieson checkpoint

### Proven technical/feed state

Rakuten Product Catalog technical access works. Jamieson approved the ValuePilot advertiser relationship and separate Product Feed access. The complete proprietary feed was downloaded and inspected offline; the feed itself remains outside source control.

Sanitized feed facts:

- 273 product rows; valid HDR/TRL structure
- 273 unique SKUs and source Product IDs
- 271/273 supplied UPC/GTIN values; all 271 checksum-valid
- 273/273 CAD
- 273/273 marked in stock in the feed
- product/image URLs structurally present on all 273
- manufacturer Jamieson on all 273
- descriptions on 272/273
- Class ID blank on all rows; Attribute 1 therefore remains opaque/untyped
- Sale Price < Retail Price: 48
- Sale Price = Retail Price: 223
- Sale Price > Retail Price: 2

Rakuten generic schema semantics remain: Sale reflects discounts and Retail does not. Therefore Sale>Retail is a semantic conflict; never swap/repair/infer promotion from those two rows.

The feed still lacks a universally validated package quantity/count. Rakuten feed alone therefore does not establish authoritative unit-value coverage.

### 2026-08-29 Rakuten Publisher Support clarification

Rakuten answered the previously open Product Catalog/application questions materially:

1. Product Catalog FTP files may be downloaded using any FTP client.
2. Once downloaded, product data/links may be used in the publisher's system for comparison.
3. **Permission to use a particular advertiser's data in the ValuePilot application must be confirmed with that advertiser.**
4. Comparison use likewise requires advertiser confirmation.
5. If partnership with an advertiser ends, product/affiliate links become inaccessible while previously downloaded feed files remain saved in the publisher's system.
6. Physical retention of downloaded files does **not** by itself prove continued post-partnership display/use rights.
7. Before inserting affiliate links into Android, advertiser permission/terms must allow that use.

Decision:

**RAKUTEN-SIDE STORAGE/COMPARISON AMBIGUITY IS SUBSTANTIALLY REDUCED. ADVERTISER-SPECIFIC APP/DISPLAY/COMPARISON PERMISSION IS NOW THE PRIMARY RIGHTS GATE.**

The feed/file timestamp still does not establish trustworthy per-product current-price freshness.

### Jamieson permission request already sent

A follow-up email to the Jamieson advertiser contact has already been sent asking whether Jamieson approves ValuePilot to:

- display authorized Jamieson Product Catalog fields in Android;
- search/compare Jamieson products;
- store/cache/index the downloaded feed to operate the comparison service;
- use authorized Rakuten/Jamieson affiliate links in Android subject to applicable requirements;
- retain/delete data after partnership/feed approval ends; and
- treat the approved feed as representing products/offers intended for Canadian consumers, including the CAD prices in the feed.

**Do not resend while waiting.** If Jamieson replies, record the exact written scope first and map each capability independently. Do not infer more rights than the email actually grants.

Jamieson is now a supplemental high-quality evidence rail, not a blocker for the Practical Shopping MVP.

## Walmart Canada checkpoint

Rakuten's Walmart Canada advertiser page (MID 36751) currently states that ValuePilot does not meet advertiser-supplied eligibility terms and therefore cannot partner/apply.

Decision:

- this is a pre-application eligibility blocker, not a normal post-application rejection;
- do not modify or misrepresent publisher/channel/country/traffic details merely to bypass it;
- if Walmart is revisited later, ask Rakuten support which exact advertiser-defined requirement is failing;
- do not hold ValuePilot product development while waiting for Walmart access.

## GS1 Canada ECCnet

Data Recipient/rights inquiry sent 2026-08-28 and acknowledged. No substantive answer is established yet unless newer authenticated evidence exists.

Questions outstanding include:

- ValuePilot eligibility as a Data Recipient/consumer comparison company;
- GTIN-level net content/count/package fields;
- Jamieson publication/subscription scope;
- consumer-facing comparison/search/mobile/cache/sync rights;
- attribution/retention/redistribution restrictions;
- Item Centre vs API/extract options;
- startup/commercial fees.

ECCnet remains a potentially strong package-content accelerator, not a launch dependency.

## Open/free rails

Open-source/open-data work now matters more strategically, but must remain licence/provenance aware.

- Open Food Facts: broad packaged-product recognition/metadata; source-isolated; never current retailer price/stock proof.
- Open Prices: supplemental proof-backed observed/historical price evidence; current Canadian coverage is incomplete.
- USDA FoodData Central: possible CC0/public-domain enrichment; never Canadian availability proof merely because a GTIN/product exists.
- Produce/PLU data: use only from a validated source with acceptable reuse terms.
- OpenStreetMap: possible store/location/routing foundation under its licence; validate practical completeness/accuracy.

Do not collapse ODbL/share-alike sources into an incompatible proprietary database. Preserve source namespaces/claims and resolve at decision time.

## Remaining provider programs

Existing CJ/Awin/impact/other statuses remain informational. Do not start a broad advertiser-application campaign. Wait for current replies/decisions and incorporate only opportunities that produce meaningful authoritative evidence or commerce value.

## Current next actions

1. Wait for Jamieson advertiser-use response; do not resend now.
2. Wait for GS1 Canada substantive response.
3. Walmart eligibility is optional follow-up, not a blocker.
4. Provider/network work runs in parallel with, not ahead of, the Practical Shopping MVP.
5. Keep Android production Search hidden/unwired until a specific source/use passes the relevant rights/geography/freshness/package-content gates.
6. Do not add Android `INTERNET`/`ACCESS_NETWORK_STATE`, affiliate links, checkout/payment, telemetry, remote AI or provider credentials as incidental provider experiments.
7. Never use commission, EPC, payout, sponsorship or provider preference as an organic ValuePilot ranking input.
