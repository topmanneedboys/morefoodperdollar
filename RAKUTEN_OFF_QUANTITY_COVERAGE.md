# ValuePilot — Rakuten × Open Food Facts Quantity Coverage Gate

Updated: 2026-08-28

Milestone: 5D — authorized real shopping data validation

## Purpose

The first authorized Jamieson Vitamins Rakuten Product Catalog feed established strong offer identity/transport fields but did not establish authoritative package count. Supplement value comparisons such as price per tablet/capsule therefore remain blocked unless count evidence is independently established by a validated source and joined through stable product identity.

This document records the bounded research bridge used to measure that gap without weakening ValuePilot's provenance model.

## Historical first run — invalidated

The first local Jamieson × Open Food Facts quantity-coverage run reported:

- product records: **273**
- checksum-valid GTINs: **271**
- Open Food Facts matches: **0**
- exact supplement-count candidates: **0**
- API calls: **4**

**That zero-match result is invalid as coverage evidence.**

The first implementation compared Open Food Facts response `code` strings against raw provider GTIN representations. Open Food Facts normalizes documented leading-zero equivalent barcode representations, so legitimate matches could be discarded.

Sanitized Jamieson identity distribution:

- 12-digit source GTINs: **248 / 271**
- 13-digit source GTINs: **1 / 271**
- 14-digit source GTINs: **22 / 271**
- source representations changed by documented leading-zero canonicalization: **267 / 271**
- canonical unique lookup identities: **271**
- canonical identity collisions: **0**

The historical `0 / 271` result is retained only as a regression/discovery event and must never be recovered as the Jamieson coverage conclusion.

## Corrected normalized implementation

Stable launcher:

`tools/run_rakuten_off_quantity_coverage.py`

Corrected implementation:

`tools/measure_rakuten_off_quantity_coverage_v2.py`

Barcode helper:

`tools/open_facts_barcode.py`

The corrected path:

- validates source GTIN checksum/shape first;
- canonicalizes only documented leading-zero barcode representations;
- never repairs an invalid GTIN;
- preserves provider source representation separately for provenance;
- queries/matches canonical identities;
- canonicalizes returned response codes before matching;
- maps accepted responses back to source identities in memory;
- reports canonical collisions explicitly;
- emits aggregate JSON/Markdown only;
- never emits GTINs, source rows, URLs, credentials or private provider account identifiers.

Shared core separately preserves `suppliedGtin`, `validatedGtin` and `canonicalGtin`, promoting only canonical identity into cross-source matching while retaining the exact source value for audit.

## Transport hardening

The corrected normalized rerun initially encountered repeatable HTTP 503 failures from the Open Food Facts batch-search endpoint even after reducing batch size.

Commit `5ffa11d6492129cabc33dd0d73816ae86454469b` hardened the research path so batch search remains an optimization only. Repeated search HTTP 5xx failures fall back to rate-limited direct product-by-barcode reads without changing GTIN identity, quantity semantics or privacy boundaries.

The merchant-feed qualification workflow passed for that transport-hardening commit.

## Valid normalized empirical result

The first valid corrected Jamieson × Open Food Facts run completed successfully on 2026-08-28.

Aggregate result:

- product records: **273**
- rows with checksum-valid GTIN: **271**
- missing/invalid GTIN rows: **2**
- normalized matched GTINs: **102**
- unmatched GTINs: **169**
- exact supplement-count candidates: **12**
- structured mass/volume-only candidates: **2**
- quantity conflicts: **0**
- matched but no usable quantity: **88**
- matches containing expected Jamieson brand text: **90**
- matches with source modification timestamp: **102**
- total successful data API calls counted by the harness: **33**

Normalization/transport sanity:

- source GTIN representations changed by normalization: **267**
- canonical unique lookup GTINs: **271**
- canonical identity collisions: **0**
- successful batch-search requests: **13**
- search batches using direct-read fallback: **1**
- direct product-read requests: **20**
- response codes ignored after canonical validation: **0**

This is now the authoritative Open Food Facts coverage result for the current Jamieson feed.

## Quantity classification remains strict

Identity matching does not make quantity trustworthy by itself.

The tool:

- never parses product titles/descriptions to invent authoritative package quantity;
- accepts an exact displayed supplement count only when the complete source `quantity` value is an integer plus a narrow allow-listed dose-form noun;
- otherwise accepts only structured positive whole-product `g` / `ml` quantity;
- detects conflicting duplicate Open Food Facts quantity candidates and rejects the quantity resolution;
- keeps Open Food Facts metadata separately attributed as `SOURCE_ASSERTED_METADATA`.

Mass/volume metadata does not substitute for a missing tablet/capsule/gummy count.

## Decision from the valid run

Open Food Facts is a useful supplemental product-metadata rail for Jamieson, but it is **not sufficient as the package-count foundation** for this merchant feed.

Only **12** canonical Jamieson GTIN identities currently have exact source-displayed supplement-count candidates under ValuePilot's strict rules. The remaining **259** valid-GTIN products are not exact-count-ready through Open Food Facts.

Therefore:

- do not relax the parser to increase coverage;
- do not infer count from Rakuten titles, descriptions, images, SKUs, price, opaque attributes or neighboring variants;
- do not promote the 2 mass/volume-only rows into per-tablet/capsule/gummy arithmetic;
- do not treat the 102 identity matches as 102 unit-value-ready offers;
- keep the 12 exact-count candidates separately attributed and behind the existing conflict/unit-value gates;
- investigate the next appropriate package-content source/domain.

## Next source/domain conclusion

Health Canada's Licensed Natural Health Products Database is useful for regulatory identity, licence, dosage form, ingredients and recommended-dose facts, but its public schema does not provide GTIN-level package net content/count. It therefore cannot solve the current 259-product exact-count gap by itself.

GS1 Canada ECCnet remains the strongest strategic next product-content target because it is GTIN-centric and carries standardized product-content/net-content information, including count-style net content. Access is controlled for Data Recipients and requires separate authorization/subscription validation.

This does not authorize an ECCnet production adapter. The next external gate is to establish whether ValuePilot can qualify as an ECCnet Data Recipient and obtain the required permitted fields/use rights at acceptable commercial terms.

## Unit-value rule

The bounded path remains:

Rakuten authorized offer evidence

→ checksum-valid canonical stable GTIN identity

→ independently sourced exact package count

→ separate `PACKAGE_QUANTITY` claim

→ source-isolated conflict-resolution gate

→ evidence-backed unit-value gate

Open Food Facts quantity remains `SOURCE_ASSERTED_METADATA`; it does not become merchant-authoritative Jamieson metadata.

## Rights and production gate remain unchanged

This measurement proves metadata coverage only. It does **not** prove or grant:

- Rakuten/Jamieson caching rights;
- persistent indexing rights;
- consumer display rights;
- mobile-app rights;
- redistribution rights;
- production Open Food Facts integration approval;
- production Android networking;
- production GS1/ECCnet access;
- permission to infer supplement medical efficacy, safety or treatment claims.

No production provider adapter should be added until the existing authorization, rights, source-semantic and privacy gates are deliberately cleared.
