# Jamieson Rakuten Product Catalog — Sanitized Feed Audit

Date: 2026-08-28

Milestone: 5D — Authorized Real Shopping Data Provider Selection

Purpose: preserve empirical data-quality findings from the first actual authorized Jamieson Product Catalog without committing proprietary catalog rows, URLs, account identifiers, credentials, or other operational secrets.

## Source and authorization state

Rakuten Customer Support explicitly confirmed that ValuePilot is approved for the Jamieson Vitamins advertiser Product Feed and that the feed is present in the authorized Product Catalog SFTP account.

The complete compressed pipe-delimited TXT feed was downloaded and inspected offline.

This audit establishes structural/data-quality evidence only. It does **not** grant or infer production caching, persistence, indexing, redistribution, mobile-display, or affiliate-link rights.

## File integrity

- HDR record present and structurally valid
- TRL record present and structurally valid
- trailer product count: **273**
- product records scanned: **273**
- trailer count matches scanned rows: **yes**
- records with documented 38-field shape: **273 / 273**
- malformed short records: **0**

## Market / offer-shape observations

- currency `CAD`: **273 / 273**
- availability `in-stock`: **273 / 273**
- positive Retail Price: **273 / 273**
- positive Sale Price: **273 / 273**
- syntactically valid product URL: **273 / 273**
- syntactically valid image URL: **273 / 273**

Rakuten's primary record has a currency field but no universal row-level country field. CAD is therefore supporting evidence, not by itself proof of exact Canadian offer geography.

## Identity quality

- unique source Product IDs: **273**
- unique SKUs: **273**
- UPC/GTIN present: **271 / 273**
- supplied UPC/GTIN values passing checksum validation: **271 / 271**
- missing UPC/GTIN: **2**
- manufacturer name present as Jamieson: **273 / 273**

Repeated product names exist across distinct source rows. Name-only deduplication is unsafe; source Product ID, SKU and strong GTIN evidence must be preserved.

### GTIN representation distribution

A later aggregate-only identity inspection found:

- 12-digit source GTINs: **248 / 271**
- 13-digit source GTINs: **1 / 271**
- 14-digit source GTINs: **22 / 271**
- source representations changed by Open Food Facts' documented leading-zero canonicalization: **267 / 271**
- canonical unique identities after that representation normalization: **271**
- canonical identity collisions: **0**

This is not a product-count change and does not alter provider provenance. It means that exact textual equality of checksum-valid GTIN strings is insufficient for cross-source joins when one source uses UPC-A/GTIN-12 and another returns the equivalent zero-prefixed GTIN-13 representation.

ValuePilot must preserve the exact source GTIN for audit while using a deterministic canonical GTIN for cross-source identity matching. Invalid GTINs must never be repaired by normalization.

## Description / class coverage

- short-description coverage: **272 / 273**
- long-description coverage: **272 / 273**
- Class ID blank: **273 / 273**

All Class IDs being blank matters because the documented class-specific Size mechanism cannot currently be relied on for this advertiser feed.

### Untyped attribute-section observation

- Product field 29 / Attribute 1 populated: **273 / 273**
- Product fields 30–38 / Attributes 2–10 populated: **0 / 273**
- Class ID populated: **0 / 273**

Rakuten documents fields 29–38 as class-dependent attribute fields. Without a Class ID, ValuePilot has no documented semantic mapping for the populated Attribute 1 values.

Therefore Attribute 1 remains **opaque, untyped source data**. Its presence does not establish package quantity, a product identifier, a category, or any other factual domain. ValuePilot must not reverse-engineer its meaning from value shape, neighboring products, or correlations with other fields.

## Price relationship findings

Relationship between supplied Sale Price and Retail Price:

- Sale Price < Retail Price: **48**
- Sale Price = Retail Price: **223**
- Sale Price > Retail Price: **2**

Interpretation:

- a positive Sale Price is not sufficient evidence of a discount;
- equal values must not produce a savings claim;
- inverted values must not produce a savings claim;
- both fields must remain preserved until their exact advertiser/feed semantics are established.

## Quantity / unit-value result

The primary Rakuten Product Catalog schema does not provide a universal structured package quantity. All Jamieson Class IDs are blank, so no validated class-specific Size field is available through that mechanism.

The populated but untyped Attribute 1 column does not change that conclusion.

Therefore:

- structural CAD offer candidates: **273**
- authoritative unit-value candidates from this feed alone: **0**

Do **not** guess package count/size from title, description, image filename, SKU, price, untyped attributes, or neighboring variants.

A future package-quantity claim may come from a separate authorized/appropriately licensed source joined by canonical checksum-valid GTIN identity. Provenance must remain separate so the quantity source is never misrepresented as the merchant-price source.

## Open Food Facts coverage-run correction

The first local quantity-coverage run reported 0 Open Food Facts matches for the 271 valid source GTINs. That result is **not valid coverage evidence** because the first tool compared normalized API response codes to raw provider GTIN strings.

Given the representation distribution above, legitimate matches could be discarded. The corrected coverage tool canonicalizes only documented leading-zero equivalent representations before matching and preserves exact source identity separately.

A corrected normalized rerun is required before any Jamieson Open Food Facts coverage percentage is claimed.

## Freshness limitation

The feed HDR timestamp is file-generation/deposit evidence only. It must not be promoted to per-product price freshness unless provider semantics explicitly establish that meaning.

## Supplement/health guardrail

Descriptions contain advertiser health/marketing language. ValuePilot must not transform those statements into its own claims that a supplement is healthier, more effective, safer, clinically better, or medically preferable.

Initial ranking/comparison evidence remains objective and source-grounded: price, validated package quantity/count, printed strength, sourced label/ingredient facts where permitted, and deterministic value calculations.

## Engineering decision

Current decision:

**DATA QUALITY = PROMISING / STRUCTURALLY VALID. PRODUCTION USE = NOT YET AUTHORIZED. UNIT-VALUE RANKING = BLOCKED UNTIL VALIDATED QUANTITY EXISTS.**

Next gates:

1. rerun the barcode-normalized Open Food Facts quantity-coverage measurement;
2. preserve raw provider GTIN and canonical cross-source GTIN separately;
3. preserve both price fields and source/provenance identifiers in staging;
4. establish package quantity/count through validated identity-matched evidence;
5. establish exact caching/indexing/display/mobile rights;
6. only then consider a production Rakuten/Jamieson adapter or consumer ranking path.

The proprietary feed itself must not be committed as a repository fixture. Use synthetic rows for tests.
