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

## Description / class coverage

- short-description coverage: **272 / 273**
- long-description coverage: **272 / 273**
- Class ID blank: **273 / 273**

All Class IDs being blank matters because the documented class-specific Size mechanism cannot currently be relied on for this advertiser feed.

## Price relationship findings

Relationship between the supplied Sale Price and Retail Price fields:

- Sale Price < Retail Price: **48**
- Sale Price = Retail Price: **223**
- Sale Price > Retail Price: **2**

Interpretation:

- a positive Sale Price is not sufficient evidence of a discount
- equal values must not produce a savings claim
- inverted values must not produce a savings claim
- ValuePilot must preserve both supplied price fields and validate their semantics rather than silently choosing one based only on the label

The offline Rakuten qualifier has been hardened to report these relationships separately and to keep production authorization false.

## Quantity / unit-value result

The primary Rakuten Product Catalog schema does not provide a universal structured package quantity. In this Jamieson feed, all Class IDs are blank, so no validated class-specific Size field is available through that mechanism.

Therefore:

- structural CAD offer candidates: **273**
- authoritative unit-value candidates from this feed alone: **0**

Do **not** guess package count or size from product title, description, image filename, SKU, price, or neighboring variants.

A future package-quantity claim may come from a separate authorized/appropriately licensed source joined by strong identity such as checksum-valid GTIN. Provenance must remain separate so the quantity source is not misrepresented as the merchant-price source.

## Freshness limitation

The feed HDR timestamp is file-generation/deposit evidence only. It must not be promoted to per-product price freshness unless the provider's semantics explicitly establish that meaning.

## Supplement/health guardrail

Descriptions contain advertiser health/marketing language. ValuePilot must not transform those statements into its own claims that a supplement is healthier, more effective, safer, clinically better, or medically preferable.

Initial ranking/comparison evidence should remain objective and source-grounded: price, validated package quantity/count, printed strength, sourced label/ingredient facts where permitted, and deterministic value calculations.

## Engineering decision

Current decision:

**DATA QUALITY = PROMISING / STRUCTURALLY VALID. PRODUCTION USE = NOT YET AUTHORIZED. UNIT-VALUE RANKING = BLOCKED UNTIL VALIDATED QUANTITY EXISTS.**

Next gates:

1. keep the hardened offline qualifier regression-tested
2. define provider-neutral offline import mapping preserving both price fields and all source/provenance identifiers
3. establish package quantity/count through validated identity-matched evidence
4. establish exact caching/indexing/display/mobile rights
5. only then consider a production Rakuten/Jamieson adapter or consumer ranking path

The proprietary feed itself must not be committed as a repository fixture. Use synthetic rows for tests.