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

Aggregate-only identity inspection found:

- 12-digit source GTINs: **248 / 271**
- 13-digit source GTINs: **1 / 271**
- 14-digit source GTINs: **22 / 271**
- source representations changed by Open Food Facts' documented leading-zero canonicalization: **267 / 271**
- canonical unique identities after that representation normalization: **271**
- canonical identity collisions: **0**

Exact textual equality of checksum-valid GTIN strings is insufficient for cross-source joins when one source uses UPC-A/GTIN-12 and another returns an equivalent zero-prefixed representation.

ValuePilot preserves the exact source GTIN for audit while using deterministic canonical GTIN for cross-source identity matching. Invalid GTINs are never repaired by normalization.

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

Rakuten's current Product Catalog Appendix A resolves the generic schema meanings:

- `Sale Price` is optional and reflects discounts.
- `Retail Price` is required and does not reflect discounts.

Therefore:

- Sale < Retail is structurally consistent with a discounted price;
- Sale = Retail is structurally consistent with no effective discount and must not produce a savings claim;
- Sale > Retail conflicts with the documented field semantics and must fail closed for production price promotion;
- the 2 inverted rows must not be swapped, reinterpreted as markup, or silently corrected;
- deterministic savings arithmetic may be computed only from accepted price evidence and must be labelled as ValuePilot-computed rather than source-supplied unless the source explicitly supplies that savings fact.

Generic field semantics are now resolved, but production price eligibility still depends on freshness, geography and downstream use rights.

Detailed gate: `RAKUTEN_PRICE_AND_ANDROID_RIGHTS_GATE.md`.

## Quantity / unit-value result from Rakuten alone

The primary Rakuten Product Catalog schema does not provide a universal structured package quantity. All Jamieson Class IDs are blank, so no validated class-specific Size field is available through that mechanism.

The populated but untyped Attribute 1 column does not change that conclusion.

Therefore:

- structural CAD offer candidates: **273**
- authoritative unit-value candidates from Rakuten alone: **0**

Do **not** guess package count/size from title, description, image filename, SKU, price, untyped attributes, or neighboring variants.

A package-quantity claim may come from a separate appropriately licensed source joined by canonical checksum-valid GTIN identity. Provenance must remain separate so the quantity source is never misrepresented as the merchant-price source.

## Open Food Facts coverage — corrected authoritative result

The historical first local quantity-coverage run reported 0 Open Food Facts matches for 271 valid source GTINs. That result is invalid because the first tool compared normalized API response codes against raw provider GTIN strings.

The corrected normalized path canonicalizes only documented leading-zero equivalent representations before matching and preserves exact source identity separately.

The valid normalized rerun completed successfully on 2026-08-28:

- valid GTINs: **271**
- normalized Open Food Facts matches: **102**
- unmatched GTINs: **169**
- exact supplement-count candidates: **12**
- structured mass/volume-only candidates: **2**
- quantity conflicts: **0**
- matched but no usable quantity: **88**
- matches containing expected Jamieson brand text: **90**
- matches with source modification timestamp: **102**
- canonical identity collisions: **0**
- response codes ignored after canonical validation: **0**

Transport resilience was exercised during the valid run: 13 batch-search requests succeeded, one batch used the direct product-read fallback after repeated search 5xx responses, and 20 direct product reads were performed.

Decision:

**Open Food Facts is useful supplemental metadata but not sufficient as the Jamieson package-count foundation.** Only 12 valid-GTIN identities are exact-count-ready under the current strict semantics; 259 remain not exact-count-ready.

Do not relax quantity semantics to increase coverage.

## Next package-content source

Health Canada's Licensed Natural Health Products Database can strengthen regulatory identity/dosage-form/ingredient evidence but its public schema does not provide GTIN-level package net content/count, so it cannot solve the exact-count gap by itself.

GS1 Canada ECCnet is the next strategic product-content/identity target because its GTIN-centric content model includes standardized net-content concepts, including count-style net content. Data Recipient access and permitted use rights must be validated before integration.

The ValuePilot ECCnet Data Recipient eligibility/rights inquiry was sent to GS1 Canada on 2026-08-28.

## Freshness boundary

Rakuten's current Product Catalog implementation guidance establishes more precise source-level behavior:

- the file header timestamp is the time the file was deposited into the publisher SFTP account;
- full Product Catalog files are generated dynamically when retrieved;
- Rakuten says this gives publishers the most up-to-date product information currently present in the advertiser's Product Catalog database;
- the actual timeliness still depends on how often the advertiser updates that database;
- advertisers can process feeds multiple times per day;
- delta files contain new, changed and deleted records from the advertiser's last processed feed;
- Rakuten recommends checking timestamps for updates and periodically pulling a full file.

Therefore the feed can support a provider-dataset retrieval/update timestamp, but it still does **not** provide a per-product advertiser last-modified timestamp or a guarantee that a merchant-site price is live at the instant of display.

ValuePilot must not promote the HDR timestamp into per-product freshness.

A future production freshness policy should explicitly distinguish:

- dataset retrieved/deposited at time T;
- advertiser database state represented by that dataset;
- unknown age of an individual product fact inside the advertiser database;
- consumer display/ranking freshness policy chosen by ValuePilot.

## Android / DSA boundary

Rakuten's general Publisher Membership Agreement permits promotion through mobile applications subject to advertiser terms and Network Policies.

However, installed/mobile applications are also covered by Rakuten's Downloadable Software Application controls. Before ValuePilot ships Rakuten network links inside the installed Android app, Rakuten Network Quality approval/compliance testing and advertiser approval for the DSA distribution method must be treated as separate required gates.

Product Catalog feed approval must not be treated as equivalent to Android/DSA approval.

No Android production networking or Rakuten affiliate links should be added yet.

## Supplement/health guardrail

Descriptions contain advertiser health/marketing language. ValuePilot must not transform those statements into its own claims that a supplement is healthier, more effective, safer, clinically better, or medically preferable.

Initial ranking/comparison evidence remains objective and source-grounded: price, validated package quantity/count, printed strength, sourced label/ingredient facts where permitted, and deterministic value calculations.

## Engineering decision

Current decision:

**DATA QUALITY = PROMISING / STRUCTURALLY VALID. RAKUTEN PRICE FIELD SEMANTICS = RESOLVED AT SCHEMA LEVEL. PRODUCTION USE = NOT YET CLEARED. UNIT-VALUE COVERAGE = STILL INSUFFICIENT FOR BROAD JAMIESON RANKING.**

Next gates:

1. await GS1 Canada ECCnet Data Recipient feasibility, package-content coverage and rights response;
2. preserve raw provider GTIN and canonical cross-source GTIN separately;
3. preserve both Rakuten price fields and source/provenance identifiers in staging;
4. fail closed on Sale > Retail semantic conflicts;
5. establish a bounded source/dataset freshness policy without inventing per-product freshness;
6. establish caching/indexing/display/mobile/retention rights;
7. complete Rakuten DSA/channel approval before enabling network links in the installed Android app;
8. only then consider a production Rakuten/Jamieson adapter or consumer ranking path.

The proprietary feed itself must not be committed as a repository fixture. Use synthetic rows for tests.
