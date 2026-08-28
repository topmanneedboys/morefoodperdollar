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

The shared deterministic layer now includes:

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

Permanent invariant: a historical observed price, merchant price, package quantity, benchmark and regulatory fact remain separate factual domains/scopes and are never flattened into one truth value.

## GTIN identity representation

`GtinValidation.kt` now distinguishes checksum validation from deterministic cross-source representation.

`canonicalOrNull()` handles documented leading-zero equivalent GTIN representations while refusing invalid-GTIN repair.

Provider staging deliberately preserves:

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
- `tools/run_rakuten_off_quantity_coverage.py` (stable launcher; now routes to v2)

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
- production caching/persistence/indexing/display/mobile rights remain unresolved;
- Retail/Sale price semantics remain unresolved;
- Rakuten alone does not establish package count;
- 273 structural offer candidates exist;
- authoritative unit-value candidates from Rakuten alone remain 0;
- do not infer quantity from title/description/SKU/image/price/untyped attributes/neighboring variants.

## Critical correction — first Jamieson × Open Food Facts run

The first real local quantity-coverage run did occur on 2026-08-28 and produced:

- product records: 273
- valid GTINs: 271
- search API calls: 4
- Open Food Facts matches: 0
- exact supplement counts: 0

**That 0-match result is invalid as a coverage conclusion.**

The first implementation compared normalized Open Food Facts response `code` strings against raw provider GTIN strings. Open Food Facts documents leading-zero barcode normalization, so legitimate responses could be discarded.

Sanitized actual Jamieson GTIN representation distribution:

- 12-digit: 248 / 271
- 13-digit: 1 / 271
- 14-digit: 22 / 271
- source representations changed by documented leading-zero canonicalization: 267 / 271
- canonical unique lookup identities: 271
- canonical identity collisions: 0

The first `0 / 271` result must never be recovered or reported as proof of zero Open Food Facts coverage.

## Corrected normalized quantity-coverage path

The stable launcher now uses the v2 measurement implementation.

The corrected path validates GTIN first, canonicalizes only documented representation equivalence, queries/matches canonical identities, maps results back to provider identity in memory, reports collisions explicitly and remains aggregate-only/privacy-safe.

Normalized Python regression CI passed on commit `3a949e14be5cdcd10f523aa3a8d20fe463b91d4f`.

Shared-core canonical GTIN handling plus provider-import promotion tests are included in commit `be96095e6634b28f93e9add932bf67ac98bb66a3`. Its full GitHub Actions workflow completed successfully: browser checks, Android/shared-core tests, lint/assemble, APK privacy-boundary verification, release assembly and artifact upload all passed.

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

The milestone is beyond first-feed acquisition: Jamieson/Rakuten is now the empirical validation case.

Remaining gates stay separate:

- corrected quantity/count coverage
- exact provider price semantics
- caching/persistence/indexing/display/mobile rights
- source freshness/update model
- production networking/privacy boundary
- multiple-provider resilience

## Immediate next gate

**Rerun the stable barcode-normalized Jamieson × Open Food Facts quantity-coverage command locally and retain only aggregate output.**

The corrected run must establish:

- normalized matched GTINs
- exact supplement-count candidates
- mass/volume-only metadata
- unmatched GTINs
- quantity conflicts
- matched-but-no-usable-quantity
- normalization/collision sanity counts

Only after useful count coverage is proven should separate Open Food Facts quantity claims be assembled through the existing source-isolated conflict/unit-value gates.

If corrected normalized coverage remains poor, then investigate another appropriate metadata domain/provider rather than guessing quantity from Rakuten.

Only after source semantics **and** rights gates pass should ValuePilot implement a production real-data adapter or add network permissions.

5D still does not authorize unauthorized scraping, private-endpoint reverse engineering, checkout/payment, universal cart, subscriptions, affiliate-influenced ranking, remote AI or telemetry.
