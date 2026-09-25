# ValuePilot Merchant Feed Qualification

Updated: 2026-08-27

Milestone: 5D — Authorized Real Shopping Data Provider Selection

## Purpose

ValuePilot now has an offline qualification layer for evaluating an authorized merchant product feed before any production adapter or Android network permission is added.

This layer exists because feed access, feed quality, field semantics, legal/data-use rights, and production rankability are separate gates.

A downloaded feed must never become trusted shopping evidence merely because:

- an advertiser approved the publisher relationship;
- a network exposed Product Catalog access;
- the file downloaded successfully;
- a price-like field exists;
- a SKU or UPC exists;
- a feed contains many rows.

## Current tooling

### Generic merchant feed qualifier

`tools/qualify_merchant_feed.py`

Supports local CSV, TSV, pipe/semicolon/comma-delimited text, XML, and gzip-compressed input.

Design rules:

- no network access;
- bounded row processing;
- exact conservative header aliases only;
- ambiguous field mappings are not guessed;
- XML requires an explicit record tag;
- positive price, currency, identity, title, quantity, URL, availability, timestamp, duplicate, and conflict coverage are measured independently;
- the caller supplies any evaluation time; the tool does not read a hidden clock;
- a structural candidate is not production authorization;
- rights/caching/indexing/display/redistribution permission is never inferred by the tool.

For a provider with a normal header row, run the generic qualifier first. If auto-mapping is incomplete or ambiguous, create an explicit local JSON field mapping after the provider schema has been reviewed.

### Rakuten Product Catalog text qualifier

`tools/qualify_rakuten_product_catalog.py`

This is a dedicated offline profiler for Rakuten Advertising Product Catalog pipe-delimited `.txt` / `.txt.gz` files.

It is based on Rakuten Advertising's published Product Catalog Data Feed implementation guide and is deliberately narrower than a production adapter.

The published format establishes that:

- the text format is pipe-delimited;
- the first record is an `HDR` record containing advertiser identity and a file timestamp;
- the product primary section contains 28 fields;
- standard full records contain placeholders through 38 fields when generic attributes are included;
- the final `TRL` record declares the number of product records;
- Product Name, SKU Number, Primary Category, Product URL, Product Image URL, Retail Price, and Currency are documented required fields;
- Sale Price is optional;
- Availability is optional and documented around `in-stock`, `out-of-stock`, `preorder`, and `backorder` values;
- the UPC field may contain UPC/EAN/JAN-style product identifiers;
- Currency is a three-character field and may include CAD;
- class-specific attributes can carry different meanings; for Food & Drink class 110, the documented Size attribute is class-specific rather than a universal package-quantity field.

The qualifier therefore measures the feed without silently promoting all fields to ValuePilot truth.

## Important Rakuten interpretation rules

### Header timestamp is not product freshness

Rakuten documents the header timestamp as the time the file is deposited/generated for the publisher. It also states that the timeliness of product information depends on how often the advertiser updates its Product Catalog information.

Therefore ValuePilot must not copy the `HDR` timestamp onto every product row as if it proved that every price was updated at that moment.

The qualifier may measure the age of the file header when the caller supplies an explicit evaluation timestamp, but this remains file-level evidence only.

### Sale Price is not automatically a production current price

For qualification coverage, a positive Sale Price is preferred over Retail Price as a price candidate. This is useful for measuring whether a row contains usable price-like evidence.

That does not yet establish that the value is the exact current consumer price ValuePilot should display or rank. The real advertiser feed, its actual date/promotion behavior, and any feed-specific terms must still be inspected.

### CAD is not enough to prove Canadian geography

The primary product row contains currency but no universal row-level country field. Rakuten can also expose global/additional feed variants.

Therefore a `CAD` row is not automatically treated as proof that the offer is a Canadian consumer offer. Feed path/variant, advertiser program geography, and actual product-link behavior remain separate evidence.

### Quantity remains a separate hard gate

Rakuten's core primary schema does not provide one universal structured package quantity that ValuePilot can safely use across product classes.

Some class-specific attributes can contain a Size value, but the meaning varies by class. The qualifier measures class-specific Size presence where the published schema defines it, but it does not automatically parse that value into grams, millilitres, or count.

For ValuePilot unit-value ranking, package quantity must come from one of these paths:

1. a provider field whose semantics are explicitly validated for the advertiser/feed;
2. a deterministic class-specific parser backed by documented semantics and regression tests;
3. another authorized/open metadata source joined by strong product identity such as a checksum-valid GTIN.

Title or description text must not silently override stronger explicit quantity evidence.

## Integrity checks

For a complete Rakuten text file, the qualifier checks:

- `HDR` record presence and shape;
- product row field counts;
- required-field coverage;
- positive Retail Price coverage;
- optional Sale Price coverage and suspicious sale/retail relationships;
- expected currency coverage;
- availability values;
- UPC/GTIN checksum validity when possible;
- product and image URL syntax;
- duplicate/conflicting SKU and Product ID observations;
- class ID distribution;
- `TRL` record presence and declared product count.

A missing/malformed trailer, trailer count mismatch, or too-short primary product row causes a feed-integrity failure unless the run was deliberately truncated by the configured row bound.

## Authorization boundary

Neither qualifier can set `production_authorized=true`.

The following remain external gates:

- advertiser-level Product Catalog approval;
- exact feed data-use rights;
- permitted caching/persistence duration;
- indexing/search rights;
- consumer display rights;
- mobile/software rights;
- redistribution/export restrictions;
- attribution/disclosure requirements;
- permitted use of product/image/tracking URLs;
- any network-specific DSA or software approval.

Ranking remains independent of commission, EPC, payout, sponsorship, or provider economics.

## Local data handling

Authorized merchant feeds and generated reports may contain provider-restricted data and must not be committed to the public repository.

The repository ignores:

- `local-provider-data/`
- `local-feed-reports/`

When a real feed arrives, place the downloaded file under `local-provider-data/` and generated qualification output under `local-feed-reports/`.

Never place FTP/SFTP credentials, passwords, API keys, advertiser account secrets, or private publisher identifiers in the repository or generated reports.

## Example: qualifying a Rakuten text feed

```bash
python tools/qualify_rakuten_product_catalog.py \
  --input local-provider-data/merchant_feed.txt.gz \
  --expected-currency CAD \
  --evaluated-at 2026-08-27T21:00:00-07:00 \
  --json local-feed-reports/merchant_feed.json \
  --markdown local-feed-reports/merchant_feed.md
```

Use the actual caller-supplied evaluation time when freshness/file-age measurement matters. Do not replace it with a hidden clock inside deterministic core logic.

## When the first real Jamieson feed appears

Run this sequence before implementation:

1. preserve the original `.txt.gz` file locally and do not commit it;
2. run the Rakuten qualifier against the complete file;
3. verify the HDR/TRL integrity and actual row count;
4. inspect CAD coverage and whether the feed is the intended Canadian variant;
5. inspect real Product ID, SKU, UPC/GTIN, title, brand, availability, Retail Price, Sale Price, URLs, Class ID, and attribute coverage;
6. measure how many rows have a checksum-valid GTIN;
7. determine whether package count/strength/size is genuinely structured anywhere in the Jamieson feed;
8. if quantity is absent, test a GTIN-based metadata join without allowing the metadata source to overwrite merchant price provenance;
9. inspect feed-specific legal/contractual rights for caching, indexing, display, mobile/software use, attribution, images, and links;
10. only after the data and rights gates pass, design the first production provider adapter;
11. only then consider adding a network/storage boundary to the shipping application.

## Current decision

The qualification infrastructure is ready before the first merchant feed arrives.

ValuePilot should continue waiting for a real authorized merchant feed or a provider response rather than exposing open-data observations as if they were current nationwide merchant prices.
