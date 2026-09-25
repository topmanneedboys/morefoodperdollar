# Argentina CABA snapshot qualification

This checkpoint is deliberately split into independent evidence gates: the regional artifact is qualified only for the source and policy below; the search audit is a finite relevance measurement, not a universal category claim.

## Regional snapshot status

**GO** — deterministic CABA (`ar-caba`) snapshot verified from the exact qualified SEPA release.

- Source: ARGENTINA_SEPA_PRECIOS_CLAROS, release 2026-09-06
- Outer SHA-256: `e6c08be6a36e5e5b90e6eb0b6f54a07c8bccded929fab2a28ad7f000ed08b305` (325522188 bytes)
- Selector: exact `store.province == AR-C` (no AR-B/locality inference)
- Offers: 3,926,600 across 973 stores
- Product-evidence records: 87,469
- Valid GTINs: 59,563; exact cross-retailer GTINs: 15,086
- Promotions retained separately: 658,900 (eligibility remains UNKNOWN)
- Compact size: 2,705,024,261 uncompressed / 265,250,265 compressed bytes
- Bytes per offer (uncompressed): 688.897331
- Reduction versus prior normalized audit representation: 8.200033x
- Local full-run time: 1257.568 seconds; aggregation is disk-backed and row-streamed
- Scratch SQLite peak file observed: 2,638,827,520 bytes; no generated artifact is committed

The manifest, release record, table descriptors, deterministic gzip streams, and verifier all agree on counts, hashes, stable ordering, exact ARS money, references, and source boundaries.

## Search audit status

**GO** — 25 manually reviewed Argentine-Spanish queries; overall precision@5 **1.0000**.

The committed fixture contains source-matched product identity rows only (no prices, offers, images, or availability). Known false-positive classes are regression-tested and excluded conservatively:

- pan-in-unrelated-name; condimento-para-arroz; fideos-con-huevo; gaseosa-sin-azucar; pasta-rellena-de-queso; yogur-con-cereal

| Query | Precision@5 | False positives in top 5 |
| --- | ---: | --- |
| aceite | 1.0000 | None |
| agua | 1.0000 | None |
| arroz | 1.0000 | None |
| atún | 1.0000 | None |
| azúcar | 1.0000 | None |
| café | 1.0000 | None |
| cereal | 1.0000 | None |
| detergente | 1.0000 | None |
| fideos | 1.0000 | None |
| galletitas | 1.0000 | None |
| gaseosa | 1.0000 | None |
| harina | 1.0000 | None |
| huevos | 1.0000 | None |
| jabón | 1.0000 | None |
| leche | 1.0000 | None |
| manteca | 1.0000 | None |
| pan | 1.0000 | None |
| pañales | 1.0000 | None |
| papel higiénico | 1.0000 | None |
| pasta dental | 1.0000 | None |
| pollo | 1.0000 | None |
| queso | 1.0000 | None |
| shampoo | 1.0000 | None |
| té | 1.0000 | None |
| yogur | 1.0000 | None |

This is a finite precision audit. It makes no recall, completeness, or universal product-category claim.

## Exact product-comparison readiness

- Audited relevant product-evidence identities: **125**
- Relevant identities carrying a valid GTIN: **125**
- Distinct valid GTINs represented by those identities: **124**
- Distinct GTINs with the same value in at least two commerce IDs inside CABA: **73**
- Distinct GTINs without an exact cross-retailer match: **51**

This is exact evidence only; no fuzzy matching is used.

## Safety boundaries and unresolved limitations

- SEPA prices are evidence; `availability = UNKNOWN`.
- Delivery, pickup, inventory, fees, ETA, routing, and geocoding are not provided.
- Coordinates are preserved only as trusted source evidence; distance reasoning is not performed here.
- Android remains offline; this milestone authorizes no UI integration or network permission.
- National input, accepted observations, the full CABA snapshot, and scratch databases remain local ignored provider data.
- The source is attributed as **Precios Claros - Base SEPA**, **Creative Commons Attribution 4.0**; this is not a legal opinion.
