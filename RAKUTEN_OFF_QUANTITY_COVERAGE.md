# ValuePilot — Rakuten × Open Food Facts Quantity Coverage Gate

Updated: 2026-08-28

Milestone: 5D — authorized real shopping data validation

## Purpose

The first authorized Jamieson Vitamins Rakuten Product Catalog feed established reliable offer identity/pricing fields for many rows but did not establish authoritative package count. Supplement value comparisons such as price per tablet/capsule therefore remain blocked until count evidence is independently established by a validated source and joined by stable identity.

This document records the bounded research bridge for measuring that gap without weakening ValuePilot's provenance model.

## First real run — result invalidated by barcode-representation mismatch

The authorized complete Jamieson feed was run locally through the first Open Food Facts quantity-coverage tool on 2026-08-28.

The first aggregate result reported:

- product records: **273**
- checksum-valid GTINs: **271**
- Open Food Facts matches: **0**
- exact supplement-count candidates: **0**
- API calls: **4**

**Do not interpret that zero-match result as evidence that Jamieson has zero Open Food Facts coverage.**

A concrete matching bug was found immediately afterward.

Open Food Facts documents barcode normalization: equivalent barcode representations that differ only by leading zeroes are normalized to a canonical code. In particular, UPC-A / GTIN-12 values are represented by adding a leading zero to the equivalent 13-digit code, and the API/database `code` field is normalized.

The original research implementation queried provider codes but then required the returned Open Food Facts `code` string to exactly equal a raw provider code in the batch. A valid Open Food Facts response could therefore be discarded after the server normalized its barcode representation.

Sanitized aggregate inspection of the authorized Jamieson feed shows why this matters:

- checksum-valid source GTINs: **271**
- 12-digit source GTINs: **248**
- 13-digit source GTINs: **1**
- 14-digit source GTINs: **22**
- source GTINs whose Open Food Facts lookup representation changes under the documented leading-zero rule: **267 / 271**
- canonical unique lookup identities: **271**
- canonical identity collisions: **0**

The original exact-string post-filter was therefore unsafe for this feed. The first zero-match result is retained only as a regression/discovery event and is **not valid coverage evidence**.

## Corrected research implementation

Stable command-line entry point:

`tools/run_rakuten_off_quantity_coverage.py`

The launcher now delegates to:

`tools/measure_rakuten_off_quantity_coverage_v2.py`

Supporting normalization helper:

`tools/open_facts_barcode.py`

The corrected path:

- validates source GTIN checksum/shape first;
- canonicalizes only documented leading-zero barcode representations;
- never repairs an invalid GTIN;
- preserves provider source codes in memory for provenance;
- queries Open Food Facts using canonical lookup identities;
- canonicalizes returned API codes before matching;
- maps accepted responses back to the exact provider identities in memory;
- reports canonical collisions instead of silently merging them;
- continues to emit aggregate JSON/Markdown only;
- never emits GTINs, source product rows, source URLs, credentials or private provider account identifiers.

This normalization rule is also being moved into shared-core cross-source identity handling so provider import records can preserve the exact supplied GTIN while promoting a deterministic canonical GTIN for cross-provider joins.

## Quantity classification remains strict

After identity matching, quantity classification remains unchanged and fail-closed.

The tool:

- never parses product titles/descriptions to invent package quantity;
- accepts an exact displayed supplement count only when the complete source `quantity` value is an integer plus an allow-listed dose-form noun;
- otherwise accepts only structured positive `g` / `ml` whole-product quantity;
- detects conflicting duplicate Open Food Facts quantity candidates and rejects the quantity resolution;
- keeps Open Food Facts metadata separately attributed as `SOURCE_ASSERTED_METADATA`.

Examples deliberately rejected as exact count include:

- `60 capsules x 500 mg`
- `30-60 tablets`
- `2 x 60 capsules`
- `100 vegetarian capsules`
- title-only count references

Those may be useful for later semantic research, but they are not deterministic package-count evidence under the current gate.

## Unit-value rule

A Jamieson offer is not eligible for per-tablet/capsule/gummy arithmetic merely because a matching Open Food Facts record exists.

The bounded path remains:

Rakuten authorized offer evidence

→ checksum-valid/canonical stable GTIN identity

→ independently sourced exact package count

→ separate `PACKAGE_QUANTITY` claim

→ existing conflict-resolution gate

→ existing evidence-backed unit-value gate

Only an exact accepted count can make a supplement row an exact-count candidate. Structured mass/volume metadata remains useful metadata but does not substitute for a missing tablet/capsule/gummy count.

Open Food Facts quantity remains `SOURCE_ASSERTED_METADATA`; it does not become merchant-authoritative Jamieson metadata.

## Verification

Synthetic coverage includes:

- accepted exact tablet/capsule/gummy/soft-gel/French count forms;
- rejection of ambiguous count expressions;
- count preference over structured mass when an exact displayed count exists;
- prohibition on title-derived quantity;
- structured mass/volume classification remaining non-count;
- fail-closed duplicate quantity conflicts;
- complete Rakuten trailer/product-count validation;
- checksum-valid GTIN filtering;
- aggregate-report privacy checks;
- direct launcher execution outside the repository root;
- UPC-A / GTIN-12 → equivalent canonical GTIN-13 matching;
- equivalent leading-zero GTIN-14 matching;
- preservation of EAN-8 and non-zero-indicator GTIN-14 distinctions;
- rejection rather than repair of invalid GTINs;
- normalized API response mapping back to the original provider GTIN;
- explicit reporting of canonical identity collisions.

The merchant-feed qualification workflow passed the normalized lookup regression suite on commit `3a949e14be5cdcd10f523aa3a8d20fe463b91d4f`.

## Current empirical status

The first real Jamieson run has occurred, but its **0 / 271** Open Food Facts match result is invalid as a coverage conclusion because it used the pre-normalization matching implementation.

No corrected Jamieson coverage percentage is established yet.

The next empirical step is to rerun the same stable launcher after pulling the corrected branch. The next aggregate report must be the first result used to decide:

- normalized Open Food Facts match coverage;
- exact supplement-count coverage;
- structured mass/volume-only coverage;
- unmatched products;
- quantity conflicts;
- matched products with no usable quantity.

If corrected normalized coverage is still poor, only then should the next provider/domain fallback be investigated. Do not skip over the known barcode bug by inventing quantity from the Rakuten feed.

## Open Food Facts API discipline

Open Food Facts currently documents:

- barcode normalization for API/database product codes;
- 10 search requests/minute/IP;
- support for comma-separated multi-value search filters;
- limiting returned fields to reduce server load.

The research tool continues to enforce at least 6.5 seconds between search batches and a maximum batch size of 80.

This research endpoint choice is not a production-architecture decision. A production data path, if ever authorized and needed, must be evaluated separately for rights, freshness, reliability, storage, attribution, scaling and privacy.

## Rights and production gate remain unchanged

This work proves only that ValuePilot can measure independent quantity coverage safely.

It does **not** prove or grant:

- Rakuten/Jamieson caching rights;
- persistent indexing rights;
- consumer display rights;
- mobile-app rights;
- redistribution rights;
- production Open Food Facts integration approval;
- production Android networking;
- permission to infer supplement medical efficacy, safety or treatment claims.

No production provider adapter should be added until the existing authorization/rights and source-semantic gates are deliberately cleared.
