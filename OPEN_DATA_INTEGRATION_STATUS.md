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

## Critical GTIN representation correction

Cross-source identity must not compare checksum-valid barcode strings only by raw textual representation.

Open Food Facts documents normalization of equivalent leading-zero barcode forms. UPC-A / GTIN-12 can be represented by its equivalent zero-prefixed GTIN-13, and leading-zero representations can otherwise collapse to the same stored `code`.

The first real Jamieson × Open Food Facts coverage tool had a bug: after querying raw provider GTINs, it required returned normalized Open Food Facts `code` strings to exactly equal one of the raw provider strings. That could discard valid matches.

The first Jamieson run reported 0 / 271 matches, but **that result is invalid as coverage evidence**.

Sanitized feed-level identity distribution explains the problem:

- valid GTINs: 271
- 12-digit: 248
- 13-digit: 1
- 14-digit: 22
- source representations changed by documented leading-zero canonicalization: 267 / 271
- canonical unique identities: 271
- canonical identity collisions: 0

## Corrected normalized lookup

The stable launcher `tools/run_rakuten_off_quantity_coverage.py` now delegates to `tools/measure_rakuten_off_quantity_coverage_v2.py` and `tools/open_facts_barcode.py`.

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

Normalized lookup regression CI passed on commit `3a949e14be5cdcd10f523aa3a8d20fe463b91d4f`.

## Shared-core cross-source identity

The same representation issue is now handled in platform-neutral shared core.

`GtinValidation.canonicalOrNull()` provides deterministic canonical identity for checksum-valid equivalent leading-zero representations.

`ImportedSourceIdentity` now separates:

- `suppliedGtin`: exact source value
- `validatedGtin`: exact checksum-valid source value
- `canonicalGtin`: cross-source identity

The staged provider import promotes canonical GTIN into `SourceProductIdentity` while retaining the exact source GTIN for provenance/audit.

Tests cover equivalent UPC-A/GTIN-12, GTIN-13 and leading-zero GTIN-14 representations, while preserving EAN-8 and non-zero-indicator GTIN-14 distinctions and refusing invalid-GTIN repair.

The Android workflow for commit `be96095e6634b28f93e9add932bf67ac98bb66a3` has completed browser checks, shared-core/app tests, Android lint/assemble, APK network-permission privacy verification, release assembly and artifact upload successfully. Post-job cleanup was still running at the last observed checkpoint.

## Jamieson count-coverage status

The authorized Jamieson feed has now been run once through the old research path, but the old 0-match result is invalid due the normalization bug.

Therefore:

- no valid corrected Open Food Facts match percentage exists yet;
- no valid Jamieson exact-count coverage percentage exists yet;
- no cross-source Jamieson unit-value candidates should be claimed yet.

The next bounded empirical action is a corrected normalized rerun. Only that result should be used to decide whether Open Food Facts supplies useful quantity/count coverage for Jamieson.

## Unit-value gate remains unchanged

A price and quantity from different providers may produce deterministic unit value only when:

1. the price evidence is independently rankable;
2. both claims join through the same canonical stable product identity;
3. the price domain is an accepted price domain;
4. quantity is `PACKAGE_QUANTITY`;
5. exact fingerprints agree with selected values;
6. quantity authority is strong enough;
7. no blocking factual conflict remains.

Canonical barcode normalization fixes identity representation only. It does not upgrade authority, freshness, rights, or factual quality.

## Next gate

Do not connect open-data research paths directly to consumer search/ranking UI yet.

Immediate next step:

1. rerun the normalized Jamieson × Open Food Facts tool locally;
2. retain aggregate output only;
3. measure matched, exact-count, mass/volume-only, unmatched, conflict and no-usable-quantity counts;
4. if normalized coverage is useful, assemble separately attributed claims through the source-isolated conflict/unit-value gates;
5. if coverage remains poor, then investigate another appropriately licensed/public metadata domain/provider rather than guessing quantity;
6. keep production rights, Retail/Sale semantics, freshness and Android networking as separate unresolved gates.
