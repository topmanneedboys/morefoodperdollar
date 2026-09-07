# Argentina SEPA nationwide shard qualification

**Status: GO for the offline/backend ingestion checkpoint.** This is a provider-edge data foundation only; `productionUiAuthorized = false`.

## Source and contract

- Provider: **Precios Claros - Base SEPA** (`ARGENTINA_SEPA_PRECIOS_CLAROS`)
- Release: **2026-09-06**
- Official outer ZIP SHA-256: `e6c08be6a36e5e5b90e6eb0b6f54a07c8bccded929fab2a28ad7f000ed08b305` (325,522,188 bytes)
- Qualified accepted stream SHA-256: `a5554d60a383acb83cf9f573a0e5f5db834c830a7a92e7db5051b09320e2da8d` (14,209,151 rows)
- Licence/attribution recorded: **Creative Commons Attribution 4.0**, Precios Claros - Base SEPA.
- Distribution contract: one gzip-compressed, indexed SQLite shard per exact ISO province code; verify before atomic activation and retain last-known-good.
- Android networking is **NOT AUTHORIZED**; no provider data is in Android assets or Git.

## Region results

All 24 standard Argentina province codes were emitted. Selection is exact `store.province == provinceCode`; no locality/name inference was used. Counts are from the verified national index.

| Region | Province | Offers | Stores | Product identities | Valid GTINs | Exact cross-retailer GTINs | Promotions | Compressed | Uncompressed | Bytes/offer |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| ar-a | Salta | 317,335 | 46 | 63,675 | 44,108 | 10,412 | 40,393 | 10,434,582 | 57,237,504 | 32.881913 |
| ar-b | Buenos Aires | 5,136,849 | 859 | 116,065 | 69,444 | 18,310 | 706,926 | 119,694,357 | 592,154,624 | 23.301124 |
| ar-caba | Ciudad Autónoma de Buenos Aires | 3,926,600 | 973 | 87,469 | 59,563 | 15,086 | 658,900 | 93,949,219 | 459,247,616 | 23.926353 |
| ar-d | San Luis | 97,000 | 7 | 53,202 | 42,239 | 8,794 | 1,649 | 5,003,070 | 27,926,528 | 51.578041 |
| ar-e | Entre Ríos | 477,224 | 85 | 74,685 | 51,870 | 12,795 | 101,725 | 15,479,030 | 82,579,456 | 32.435565 |
| ar-f | La Rioja | 46,908 | 4 | 42,700 | 35,167 | 5,929 | 395 | 3,180,703 | 19,202,048 | 67.807261 |
| ar-g | Santiago del Estero | 76,868 | 6 | 41,163 | 33,641 | 5,911 | 2,062 | 3,502,561 | 20,975,616 | 45.565918 |
| ar-h | Chaco | 62,326 | 5 | 56,018 | 41,972 | 9,112 | 3,218 | 4,554,836 | 26,230,784 | 73.080833 |
| ar-j | San Juan | 166,280 | 20 | 56,689 | 40,216 | 8,877 | 11,931 | 6,400,622 | 36,786,176 | 38.493036 |
| ar-k | Catamarca | 76,377 | 8 | 49,657 | 39,464 | 8,055 | 1,266 | 4,424,974 | 24,834,048 | 57.935949 |
| ar-l | La Pampa | 148,914 | 14 | 48,090 | 37,799 | 7,649 | 2,714 | 5,650,093 | 30,486,528 | 37.941987 |
| ar-m | Mendoza | 461,594 | 70 | 74,216 | 52,451 | 13,251 | 6,603 | 13,473,989 | 68,132,864 | 29.190130 |
| ar-n | Misiones | 82,206 | 10 | 37,971 | 33,760 | 3,999 | 2,147 | 3,248,264 | 19,906,560 | 39.513709 |
| ar-p | Formosa | 107,884 | 5 | 44,245 | 38,647 | 5,598 | 1,597 | 4,577,255 | 24,608,768 | 42.427561 |
| ar-q | Neuquén | 381,163 | 60 | 82,777 | 51,548 | 14,089 | 15,090 | 13,067,772 | 67,268,608 | 34.283947 |
| ar-r | Río Negro | 424,692 | 63 | 64,766 | 43,543 | 10,844 | 14,311 | 12,238,131 | 62,967,808 | 28.816486 |
| ar-s | Santa Fe | 310,683 | 45 | 86,697 | 55,110 | 14,581 | 24,693 | 11,807,381 | 62,074,880 | 38.004593 |
| ar-t | Tucumán | 256,671 | 23 | 62,129 | 44,772 | 10,589 | 6,517 | 8,466,245 | 45,547,520 | 32.984813 |
| ar-u | Chubut | 361,877 | 45 | 68,712 | 46,616 | 11,727 | 11,054 | 11,403,864 | 58,159,104 | 31.513094 |
| ar-v | Tierra del Fuego, Antártida e Islas del Atlántico Sur | 96,836 | 14 | 23,929 | 19,635 | 4,294 | 4,932 | 3,747,769 | 17,362,944 | 38.702229 |
| ar-w | Corrientes | 196,376 | 24 | 65,412 | 46,992 | 10,610 | 27,162 | 7,883,992 | 44,167,168 | 40.147431 |
| ar-x | Córdoba | 670,005 | 115 | 83,918 | 55,145 | 13,782 | 23,635 | 18,121,306 | 91,750,400 | 27.046524 |
| ar-y | Jujuy | 69,765 | 6 | 34,358 | 31,440 | 2,724 | 0 | 3,013,884 | 17,600,512 | 43.200516 |
| ar-z | Santa Cruz | 187,564 | 30 | 29,176 | 24,433 | 4,743 | 8,342 | 5,867,164 | 27,791,360 | 31.280864 |

**National totals:** 14,139,997 offers; 2,537 stores; 1,447,719 product-evidence identities; 76,177 distinct valid GTINs; 1,677,262 promotions. The accepted stream accounts for 14,209,151 rows: 14,139,997 selected by exact province code plus 69,154 explicitly unpublished province rows.

Unpublished evidence is preserved in the index: **66,977 UNKNOWN** province rows and **2,177 NONSTANDARD** rows whose value is the text `Buenos Aires`. The latter is not silently mapped to `AR-B`.

## Mobile compaction

The selected representation is a normalized SQLite schema with integer IDs for repeated stores, products, money, packages, provider times, references, offers, and promotions. Exact decimal amounts remain canonical text in a deduplicated money table; no rounding or GTIN repair is performed. Product quantity JSON preserves the accepted explicit quantity/unit evidence. Availability remains `UNKNOWN`, and promotions retain raw price/condition with eligibility `UNKNOWN`.

For CABA, the previous audit-friendly representation was **265,250,265 compressed bytes**. The new shard is **93,949,219 bytes**, a reduction of **171,301,046 bytes (64.580914%)**, or **2.823337× smaller**. Nationwide shards total **389,191,063 compressed bytes** (1,984,999,424 uncompressed). The largest shard is `ar-b` (119,694,357 compressed bytes); the smallest is `ar-y` (3,013,884 bytes).

## Verification and measurements

- The production build streamed all 14.2M accepted rows into disk-backed SQLite and atomically exposed the completed root only after every shard, manifest, index, hash, and integrity record was written.
- Local build elapsed time was approximately **2,976 seconds**; the sampled peak Windows working set was **1,679,618,048 bytes**. Visible temporary SQLite pages peaked at approximately **1.6 GiB**; these files were removed after completion.
- Full national verifier: **177.406542 seconds**, including gzip decompression, SQLite integrity/foreign-key checks, canonical metadata, stable IDs, exact-money/quantity/GTIN boundaries, counts, descriptors, and national accounting.
- With one CABA shard connection open, the existing bounded Spanish search returned the same logical top results for all **25** audited queries. Existing audit scan: 22.864497 seconds; mobile projection scan: 31.482375 seconds. This remains a finite precision regression, not a recall claim.
- Indexed CABA offers-by-product query averaged **0.574230 ms** and offers-by-store **10.560662 ms** over 200 local samples.
- The CABA exact-product audit now reports separate cardinalities: 125 relevant product-evidence identities; 125 carrying valid GTINs; 124 distinct valid GTINs; 73 distinct GTINs with exact cross-retailer availability; 51 distinct GTINs without it.

The verified national index SHA-256 is `8449a879442e5437e2f644af8a7aa447576f6435240424ed3c3ddabc815ba46a`. The generated national artifacts remain local ignored provider data; this report contains measurements only.

## Limitations and next boundary

- SEPA publication is price evidence, not live stock. No pickup, delivery, fee, ETA, routing, or inventory claim is made.
- Coordinates are retained only from trusted source evidence; no geocoding or external service is used.
- Only one release was qualified, so delta-transfer performance is not measured.
- Images, universal basket optimization, Android UI integration, Android networking, and community sharing are out of scope.
- `productionUiAuthorized = false` until a separately authorized consumer-integration milestone.
