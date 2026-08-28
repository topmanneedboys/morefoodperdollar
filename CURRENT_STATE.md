# Current state

Updated: 2026-08-28

Branch: `work/valuepilot-android-milestone`

Android version: 101.1.0 (10101)

## Completed Android/product foundation

Completed milestones:

- 5B1 standalone comparison application layer
- 5B2 standalone comparison screen
- 5B2A real-device comparison hardening
- 5C1 immutable Android application shell state
- parser regression fix preserving names beginning with `reg`
- 5C2 permanent Android app shell
- 5C3A Universal Search application foundation
- 5C3B first consumer Universal Search experience
- SMART ranking fix preferring explicit measurable evidence over heuristic portion fallback
- 5C4A permanent shopping evidence provenance contract
- 5C4B Universal Search migration to typed shopping evidence
- 5C4C deterministic evidence acceptance/freshness policy
- 5C4D Universal Search evidence-trust enforcement
- 5C4E promotion-provenance ranking hardening

Primary navigation remains Home / Search / Basket / Saved. Compare remains a workflow rather than a primary tab.

The first Android Search experience is physically verified on-device. Built-in Search data remains explicitly fictional/sample evidence and is never presented as live merchant pricing, inventory, promotions or availability.

## Permanent architecture

ValuePilot is a provider-neutral shopping-intelligence platform.

Permanent flow:

authorized/open/user evidence -> provider adapters -> provenance-preserving claims/import records -> deterministic validation/normalization -> Product identity + multiple Offers -> bounded retrieval -> deterministic ranking -> immutable presentation -> replaceable UI

Permanent rules:

- Product != Offer.
- Sources contribute claims; they do not overwrite one shared row.
- Stronger same-scope evidence may defeat weaker evidence; unresolved equal-strength conflict blocks Best Value.
- Money, quantity, currency and promotion arithmetic are exact/deterministic.
- AI may assist classification/explanation but may not invent authoritative facts.
- Commission, EPC, sponsorship, payout and provider preference never influence rank.
- No unauthorized scraping/reverse engineering.
- Feed access != production authorization.
- Shared core owns no hidden clock.

## Real Shopping Evidence / cross-source hardening

The shared deterministic layer includes:

- typed `ShoppingEvidence`
- explicit sample/real-world/unknown environment
- explicit acquisition channel and claim kind
- caller-supplied freshness evaluation
- rankable/display-only/rejected evidence dispositions
- checksum-aware GTIN validation
- canonical cross-source GTIN representation handling
- source-isolated evidence namespaces/storage boundaries
- deterministic conflict policy and N-source fact resolution
- evidence-backed unit-value gating
- provider-neutral staged offer import preserving unresolved source fields

Permanent invariant: historical observed price, merchant price, package quantity, benchmark and regulatory fact remain separate factual domains/scopes and are never flattened into one truth value.

## GTIN identity representation

`GtinValidation.kt` distinguishes checksum validation from deterministic cross-source representation.

`canonicalOrNull()` handles documented leading-zero equivalent GTIN representations while refusing invalid-GTIN repair.

Provider staging preserves:

- `suppliedGtin`: exact provider source string
- `validatedGtin`: exact checksum-valid source representation
- `canonicalGtin`: cross-source identity representation

The canonical form is promoted into `SourceProductIdentity`; the raw provider form remains available for provenance/audit.

Tests cover UPC-A/GTIN-12, equivalent GTIN-13, leading-zero equivalent GTIN-14, EAN-8, non-zero-indicator GTIN-14 and invalid inputs.

## Source-isolated evidence index

`SourceIsolatedEvidenceIndex.kt` is the bounded platform-neutral in-memory repository prototype.

It preserves dataset namespaces/storage-boundary metadata, supports product-key lookup, delegates factual resolution to the conflict resolver, rejects claim-ID collisions and permits one dataset namespace to be removed without mutating another provider.

## Open-data evidence rails

Open Prices remains proof-backed observed/historical price evidence, not a primary live Canadian merchant-price provider.

Open Food Facts remains separately attributed product/package metadata. Its strict network-free mapper can emit `PACKAGE_QUANTITY` only from:

- structured positive whole-product `g` / `ml`; or
- an exact full-field supplement count expression using a narrow allow-listed dose-form vocabulary.

Titles/descriptions, dosage strengths, ranges, multipliers and mixed expressions are not authoritative quantity.

Previous Open Prices × Open Food Facts measurement showed useful identity/quantity joins but weak current-price freshness. Open-data joins never upgrade stale price evidence into current/rankable evidence.

## Merchant feed qualification infrastructure

Offline/research tooling includes:

- `tools/qualify_merchant_feed.py`
- `tools/qualify_rakuten_product_catalog.py`
- `tools/measure_rakuten_off_quantity_coverage.py` (first implementation retained for regression/history)
- `tools/measure_rakuten_off_quantity_coverage_v2.py` (barcode-normalized corrected implementation)
- `tools/open_facts_barcode.py`
- `tools/run_rakuten_off_quantity_coverage.py` (stable launcher; routes to v2)

The v2 OFF path now treats batch search as an optimization and falls back to rate-limited direct product-by-barcode reads on repeated search HTTP 5xx failures. Commit `5ffa11d6492129cabc33dd0d73816ae86454469b` passed the merchant-feed qualification workflow.

Raw authorized provider data/reports remain ignored under `local-provider-data/` and `local-feed-reports/` and must not be committed.

## First authorized real merchant feed — Jamieson / Rakuten

Rakuten technical Product Catalog access is enabled. Rakuten Customer Support explicitly confirmed Jamieson advertiser Product Feed approval and actual catalog-file presence.

The complete authorized Jamieson TXT.gz feed was downloaded and audited offline. The proprietary file is never committed.

Sanitized feed checkpoint:

- 273 product rows; trailer count matches
- 273/273 documented 38-field shape
- 273/273 CAD
- 273/273 in-stock
- 273 unique SKUs and 273 unique source Product IDs
- GTIN present 271/273; all 271 supplied values checksum-valid
- product/image URL syntax valid 273/273
- manufacturer Jamieson 273/273
- description 272/273
- Class ID blank 273/273
- Attribute 1 populated 273/273 while Attributes 2–10 are blank; without Class ID this field remains opaque/untyped
- Sale Price < Retail Price: 48
- Sale Price = Retail Price: 223
- Sale Price > Retail Price: 2

Conclusions:

- actual advertiser feed/file access is proven;
- Rakuten's generic Retail/Sale field semantics are now documented and resolved at the schema level;
- `Sale Price` reflects discounts and `Retail Price` does not reflect discounts;
- the 2 Sale > Retail rows conflict with those documented semantics and must fail closed for production price promotion;
- production caching/persistence/indexing/display/mobile rights remain unresolved;
- per-product/current-price freshness remains unresolved;
- Rakuten alone does not establish package count;
- 273 structural offer candidates exist;
- authoritative unit-value candidates from Rakuten alone remain 0;
- do not infer quantity from title/description/SKU/image/price/untyped attributes/neighboring variants.

Detailed gate: `RAKUTEN_PRICE_AND_ANDROID_RIGHTS_GATE.md`.

## Rakuten Android / installed-software rights gate

Rakuten's current Publisher Membership Agreement allows promotion through mobile applications in general, subject to advertiser terms and Network Policies.

However, installed/mobile software is separately governed by Rakuten's Downloadable Software Application controls. Rakuten requires Network Quality approval/compliance testing before a DSA is launched with network links, followed by advertiser approval for the new DSA distribution method.

The Product Catalog implementation documentation also still describes advertiser Product Catalog approval for website/blog use, and the Jamieson partnership approval message refers to links being used on the approved website/marketing channel.

Therefore:

- advertiser Product Feed approval != Android/DSA approval;
- do not ship Rakuten affiliate/network links in the installed Android app yet;
- do not assume Product Catalog feed-display/cache/index rights for Android solely from technical feed access;
- keep provider networking and feed research outside Android until written channel/rights clarification is obtained;
- no Android `INTERNET` or `ACCESS_NETWORK_STATE` permission is added by this milestone.

## Jamieson × Open Food Facts — valid normalized measurement

The historical first run reported `0 / 271` Open Food Facts matches, but that result is invalid because it compared normalized OFF response codes against raw provider GTIN representations.

Sanitized Jamieson GTIN representation distribution remains:

- 12-digit: 248 / 271
- 13-digit: 1 / 271
- 14-digit: 22 / 271
- source representations changed by documented leading-zero canonicalization: 267 / 271
- canonical unique lookup identities: 271
- canonical identity collisions: 0

The corrected normalized run completed successfully on 2026-08-28 and is now authoritative:

- product records: 273
- valid GTINs: 271
- normalized Open Food Facts matches: 102
- unmatched GTINs: 169
- exact supplement-count candidates: 12
- structured mass/volume-only candidates: 2
- quantity conflicts: 0
- matched but no usable quantity: 88
- matches containing expected Jamieson brand text: 90
- matches with source modification timestamp: 102
- successful batch-search requests: 13
- search batches using direct-read fallback: 1
- direct product-read requests: 20
- response codes ignored after canonical validation: 0

Open Food Facts is therefore useful supplemental metadata but **not sufficient as the package-count foundation** for the Jamieson feed. Only 12 valid-GTIN identities are exact-count-ready through the current strict OFF rules; 259 remain not exact-count-ready.

Do not relax the parser or infer package count from Rakuten text to increase coverage.

## Next package-content source decision

Health Canada's Licensed Natural Health Products Database is useful for regulatory identity, licence, dosage form, ingredients and recommended-dose facts, but its public schema does not provide GTIN-level package net content/count. It cannot close the current Jamieson package-count gap by itself.

GS1 Canada ECCnet remains the strongest strategic next product-content target because it is GTIN-centric and carries standardized net-content/product-content data, including count-style net content. Access is controlled for Data Recipients and requires separate authorization/subscription validation.

The ValuePilot ECCnet Data Recipient eligibility/rights inquiry was sent to GS1 Canada on 2026-08-28. Await GS1 Canada's written response before implementing or assuming ECCnet production rights.

No ECCnet production adapter is authorized yet.

## Privacy boundary

Current Android build still has:

- no `INTERNET` permission
- no `ACCESS_NETWORK_STATE` permission
- no account requirement
- no telemetry
- no remote AI dependency
- no ValuePilot server dependency

Research provider networking remains outside Android.

## Current milestone

5D — Authorized Real Shopping Data Provider Selection / validation.

The milestone is beyond first-feed acquisition, beyond the first valid cross-source quantity-coverage measurement, and now has documented Rakuten Retail/Sale schema semantics.

Remaining gates stay separate:

- broader exact package-count/content coverage;
- Rakuten/Jamieson current-price freshness policy and the 2 semantic-conflict rows;
- caching/persistence/indexing/display/mobile rights;
- Rakuten Android/DSA approval before network links are enabled in the installed app;
- Canadian offer-geography confidence beyond currency alone;
- production networking/privacy boundary;
- multiple-provider resilience.

## Immediate next gate

**Wait for GS1 Canada ECCnet eligibility/rights response while obtaining written Rakuten clarification for Product Catalog Android use, caching/indexing/display rights, retention/deletion obligations, and DSA/mobile-app distribution.**

For ECCnet, do not implement integration until GS1 confirms ValuePilot's Data Recipient eligibility, available GTIN-level net-content scope, permitted consumer/mobile/search/cache uses, restrictions and commercial/API terms.

For Rakuten, the generic price-field meanings no longer need further research. The next work is rights/freshness/channel validation. The two inverted Jamieson rows are source-semantic conflicts and must not be auto-corrected or used to derive a discount/current price.

Only after source semantics **and** rights gates pass should ValuePilot implement a production real-data adapter or add network permissions.

5D still does not authorize unauthorized scraping, private-endpoint reverse engineering, checkout/payment, universal cart, subscriptions, affiliate-influenced ranking, remote AI or telemetry.
