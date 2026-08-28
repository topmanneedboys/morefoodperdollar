# ValuePilot — Rakuten × Open Food Facts Quantity Coverage Gate

Updated: 2026-08-28

Milestone: 5D — authorized real shopping data validation

## Purpose

The first authorized Jamieson Vitamins Rakuten Product Catalog feed established reliable offer identity/pricing fields for many rows but did not establish authoritative package count. Supplement value comparisons such as price per tablet/capsule therefore remain blocked until count evidence is independently established by a validated source and joined by stable identity.

This document records the bounded research bridge for measuring that gap without weakening ValuePilot's provenance model.

## Implemented research tool

Implementation:

`tools/measure_rakuten_off_quantity_coverage.py`

Stable command-line entry point:

`tools/run_rakuten_off_quantity_coverage.py`

The launcher exists so the research command works reliably when invoked directly from Windows/Linux outside Python's repository-package import context. It only establishes the repository module path and delegates to the implementation; it does not read or log provider data itself.

The measurement tool:

- accepts a local authorized Rakuten Product Catalog `.txt` or `.txt.gz` feed;
- validates the complete Rakuten trailer and exact parsed product-row count;
- extracts only checksum-valid GTINs into memory;
- queries the public Open Food Facts structured-search API in controlled batches;
- requests only the narrow metadata projection needed for quantity coverage;
- never parses product titles/descriptions to invent package quantity;
- accepts an exact displayed supplement count only when the complete source `quantity` value is an integer plus an allow-listed dose-form noun;
- otherwise accepts only structured positive `g` / `ml` whole-product quantity;
- detects conflicting duplicate Open Food Facts quantity candidates and fails them closed;
- keeps Open Food Facts metadata separately attributed as source-asserted metadata;
- emits aggregate JSON/Markdown only;
- never emits GTINs, product rows, product URLs, provider credentials, or private provider account identifiers;
- does not add Android networking and does not create a production provider adapter.

The tool deliberately rejects ambiguous count-like text such as:

- `60 capsules x 500 mg`
- `30-60 tablets`
- `2 x 60 capsules`
- `100 vegetarian capsules`
- title-only count references

Those may be useful for later semantic research, but they are not deterministic package-count evidence under the current gate.

## Unit-value rule

A Jamieson offer is not eligible for per-tablet/capsule/gummy arithmetic merely because a matching Open Food Facts record exists.

The current bounded path is:

Rakuten authorized offer evidence

→ checksum-valid GTIN

→ independently sourced exact package count

→ separate `PACKAGE_QUANTITY` claim

→ existing conflict-resolution gate

→ existing evidence-backed unit-value gate

Only an exact accepted count can make a supplement row an exact-count candidate. Structured mass/volume metadata remains useful metadata but does not substitute for a missing tablet/capsule/gummy count.

Open Food Facts quantity remains `SOURCE_ASSERTED_METADATA`; it does not become merchant-authoritative Jamieson metadata.

## CI coverage

Synthetic quantity/aggregation tests live in:

`tools/tests/test_qualify_rakuten_off_quantity_coverage.py`

Direct-launch regression coverage lives in:

`tools/tests/test_qualify_rakuten_off_quantity_cli.py`

Coverage includes:

- accepted exact tablet/capsule/gummy/soft-gel/French count forms;
- rejection of ambiguous count expressions;
- count preference over structured mass when an exact displayed count exists;
- prohibition on title-derived quantity;
- structured mass/volume classification remaining non-count;
- fail-closed duplicate quantity conflicts;
- complete Rakuten trailer/product-count validation;
- checksum-valid GTIN filtering;
- aggregate-report privacy, including explicit proof that tested GTINs and source product text are absent from serialized output;
- launching the stable CLI from outside the repository root so the documented local command does not depend on accidental Python import-path behavior.

The `Test merchant feed qualification` workflow compiles both the implementation and stable launcher and runs the full `test_qualify_*.py` suite. The launcher-inclusive workflow completed successfully on commit `c84213eb326212b60c961938c9d5695f579478f6`.

## Current empirical status

The real Jamieson feed has **not yet been run through this Open Food Facts quantity-coverage measurement**, because the proprietary authorized feed is intentionally not stored in GitHub and the measurement requires outbound Open Food Facts metadata requests.

Therefore do not claim any real Jamieson Open Food Facts count-coverage percentage yet.

The next empirical step is local/research-only:

1. run the stable launcher against the existing authorized complete Jamieson `.txt.gz` file;
2. allow only the bounded Open Food Facts metadata calls;
3. keep raw GTINs and returned product metadata local/in memory;
4. retain only the generated aggregate report;
5. review exact-count coverage, unmatched GTINs, conflicts, and non-count metadata coverage;
6. commit only a sanitized aggregate conclusion if useful.

## Open Food Facts API discipline

Open Food Facts' current public API documentation states that search queries are rate-limited to 10 requests/minute/IP. The tool uses the v2 structured-search endpoint because structured search is currently available there and deliberately enforces at least 6.5 seconds between batches, with a maximum batch size of 80.

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

No production provider adapter should be added until the existing authorization/rights gates are deliberately cleared.
