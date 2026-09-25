# ValuePilot — Open-Data Integration Status

Updated: 2026-08-28

Milestone: 5D — provider validation and provenance-safe real-data preparation

## External provider status

- Lowvyn rights/technical inquiry: sent; await written clarification before integration.
- Rakuten/Jamieson is the first actual authorized merchant-feed validation case.
- CJ/Rakuten/Awin provider screening continues independently.
- No Android runtime network permission or production provider networking is authorized by this status.

## Open Prices

Open Prices remains supplemental proof-backed observed/historical Canadian price evidence, not a primary live merchant-price provider.

The existing mapper admits only conservative proof-backed Canadian/CAD physical-store observations with checksum-valid GTIN, exact positive price, location and source observation time. It deliberately carries no package quantity and never invents stock.

Previous real Open Prices × Open Food Facts measurement found:

- 478 strict Canadian/CAD proof-backed price rows
- 358 checksum-valid GTINs
- 323 / 358 Open Food Facts matches
- 266 / 358 usable structured `g`/`ml` quantity joins
- 371 / 478 price rows with usable joined quantity
- very weak recent-price freshness, so the price rail remains historical/reference quality rather than primary live-offer quality.

A cross-source quantity join never upgrades stale/display-only price evidence into current/rankable evidence.

## Open Food Facts quantity semantics

`OpenFoodFactsImportedMetadata.kt` remains a strict network-free metadata mapper.

It may emit `PACKAGE_QUANTITY` as `SOURCE_ASSERTED_METADATA` only from:

1. positive structured whole-product `g` / `ml`; or
2. an exact source-displayed supplement count when the entire `quantity` field is integer + a deliberately narrow allow-listed dose-form noun.

It never parses product titles/descriptions as authoritative quantity. Strengths, ranges, multipliers and mixed expressions remain unknown. It cannot emit retailer price, availability, promotion, merchant identity or a market benchmark.

## Source-isolated evidence architecture

`SourceIsolatedEvidenceIndex.kt`, the conflict resolver and evidence-backed unit-value gate preserve independent source attribution.

Permanent invariants:

- current merchant price != historical observed price
- package quantity != price
- market benchmark != retailer offer
- regulatory fact != retailer offer
- different merchants/locations/channels/currencies coexist
- stronger same-scope authority may defeat weaker evidence
- unresolved equal-strength conflict blocks Best Value
- exact money/quantity fingerprints prevent formatting differences from masquerading as facts

## GTIN representation correction

Cross-source identity must not compare checksum-valid barcode strings only by raw textual representation.

Open Food Facts normalizes documented equivalent leading-zero barcode forms. The historical first Jamieson × Open Food Facts run incorrectly required returned normalized `code` strings to exactly equal raw provider strings and reported 0 / 271 matches.

That historical result is invalid as coverage evidence.

Sanitized feed-level identity distribution:

- valid GTINs: 271
- 12-digit: 248
- 13-digit: 1
- 14-digit: 22
- source representations changed by documented leading-zero canonicalization: 267 / 271
- canonical unique identities: 271
- canonical identity collisions: 0

## Corrected normalized lookup and transport resilience

The stable launcher `tools/run_rakuten_off_quantity_coverage.py` delegates to `tools/measure_rakuten_off_quantity_coverage_v2.py` and `tools/open_facts_barcode.py`.

The corrected research path:

- validates GTIN before normalization
- canonicalizes only documented leading-zero representation equivalence
- never repairs an invalid GTIN
- queries canonical Open Food Facts identities
- canonicalizes API response codes before matching
- maps matches back to exact provider identities in memory
- exposes canonical collisions instead of silently merging them
- emits aggregate output only
- never emits provider GTINs, rows, URLs, credentials or account identifiers.

Commit `5ffa11d6492129cabc33dd0d73816ae86454469b` additionally makes batch search an optimization only. Repeated search HTTP 5xx failures fall back to rate-limited direct product-by-barcode reads without changing identity or quantity rules. The focused merchant-feed qualification workflow passed for that commit.

## Shared-core cross-source identity

`GtinValidation.canonicalOrNull()` provides deterministic canonical identity for checksum-valid equivalent leading-zero representations.

`ImportedSourceIdentity` separates:

- `suppliedGtin`: exact source value
- `validatedGtin`: exact checksum-valid source value
- `canonicalGtin`: cross-source identity

The staged provider import promotes canonical GTIN into `SourceProductIdentity` while retaining the exact source GTIN for provenance/audit.

Tests cover equivalent UPC-A/GTIN-12, GTIN-13 and leading-zero GTIN-14 representations, while preserving EAN-8 and non-zero-indicator GTIN-14 distinctions and refusing invalid-GTIN repair.

## Jamieson count-coverage status — valid result

The corrected normalized Jamieson × Open Food Facts measurement completed successfully on 2026-08-28.

Authoritative aggregate result:

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

This result supersedes the historical invalid 0-match run.

Decision:

Open Food Facts is useful supplemental product metadata but **not sufficient as the Jamieson package-count foundation**. Only 12 valid-GTIN identities are exact-count-ready under the current strict semantics; 259 remain not exact-count-ready.

Do not relax the parser, infer package count from Rakuten text, or treat mass/volume metadata as tablet/capsule/gummy count merely to increase coverage.

## Next metadata-domain assessment

Health Canada's Licensed Natural Health Products Database is authoritative/useful for regulatory licence identity, product/brand names, dosage form, medicinal/non-medicinal ingredients, recommended use and recommended dose.

Its published public schema does not expose GTIN-level package net content/count. Recommended dose is a usage instruction, not package content. LNHPD therefore cannot solve the current Jamieson exact-count gap by itself and must never be misused as package-count evidence.

GS1 Canada ECCnet is the strongest strategic next product-content target because it is GTIN-centric and provides standardized product listing/content data with net-content concepts that can represent weight, volume or count. ECCnet access is controlled for Data Recipients and requires separate authorization/subscription validation.

No ECCnet integration is authorized yet.

## Unit-value gate remains unchanged

A price and quantity from different providers may produce deterministic unit value only when:

1. the price evidence is independently rankable;
2. both claims join through the same canonical stable product identity;
3. the price domain is an accepted price domain;
4. quantity is `PACKAGE_QUANTITY`;
5. exact fingerprints agree with selected values;
6. quantity authority is strong enough;
7. no blocking factual conflict remains.

Canonical barcode normalization fixes identity representation only. It does not upgrade authority, freshness, rights or factual quality.

## Next gate

Do not connect open-data research paths directly to consumer search/ranking UI yet.

Immediate next step:

1. validate whether ValuePilot can qualify for GS1 Canada ECCnet Data Recipient access;
2. verify GTIN-level net-content/count coverage for the relevant product categories and Jamieson scope;
3. establish caching/indexing/search/display/mobile/retention/attribution rights and commercial terms;
4. keep Health Canada LNHPD as a possible separately attributed regulatory metadata rail, not package-count evidence;
5. keep Rakuten Retail/Sale semantics, production rights, freshness and Android networking as separate unresolved gates.
