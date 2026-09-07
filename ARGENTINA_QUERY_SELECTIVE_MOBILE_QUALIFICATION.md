# Argentina query-selective mobile qualification

Status: QUALIFIED_FOR_BACKEND_DISTRIBUTION_ONLY
Production Android UI authorization: false

This is an offline/static-file qualification artifact. It does not add Android
networking and it does not claim live inventory.

## Source and chosen architecture

- Provider: ARGENTINA_SEPA_PRECIOS_CLAROS; release 2026-09-06.
- Official outer ZIP: 325,522,188 bytes,
  SHA-256 e6c08be6a36e5e5b90e6eb0b6f54a07c8bccded929fab2a28ad7f000ed08b305.
- Accepted stream SHA-256:
  a5554d60a383acb83cf9f573a0e5f5db834c830a7a92e7db5051b09320e2da8d.
- Verified national index SHA-256:
  8449a879442e5437e2f644af8a7aa447576f6435240424ed3c3ddabc815ba46a.
- Licence/attribution: Creative Commons Attribution 4.0; Precios Claros -
  Base SEPA, source datos.produccion.gob.ar.
- Distribution: one bootstrap, one exact-province manifest, one complete
  product-search index, one store index, and 64 stable SHA-256
  product-evidence buckets per province.
- Geographic offer partitions were rejected as the primary key because radius
  queries can cross arbitrary boundaries and otherwise create false negatives.
  Full province shards remain the reference/fallback; product buckets preserve
  all evidence while keeping selected downloads bounded.

## Nationwide artifact

- 24 regions; 1,660 files; 458,412,217 compressed bytes; 10,417,825,103
  uncompressed JSONL bytes.
- Shared bootstrap: 14,255 bytes.
- 2,537 stores; 1,447,719 product-evidence identities; 14,139,997 offers;
  1,677,262 promotions.
- The accepted stream contains 14,209,151 rows (1,079,326,703 bytes). The
  national ZIP and expanded source are local ignored inputs and are not in Git
  or Android assets.
- The generated root passed the full verifier at bootstrap SHA-256
  a72e2ef294b7cee135a9b76a9b6bc8209b6b16bb368cc5361f51bd9e50b8d622.

## Representative payloads at 50 km

The companion JSON contains all 60 combinations of CABA, Buenos Aires,
Córdoba, and Jujuy; radii 2/5/10/25/50 km; and 1/5/10 requested products.
Each payload includes bootstrap + region manifest + complete search/store
indexes + only the selected offer partitions.

| Region | Products | Candidates | Partitions | Payload | Full province | Reduction |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| CABA | 1 | 5 | 5 | 12,128,682 | 93,949,219 | 87.09% |
| CABA | 5 | 25 | 21 | 41,147,267 | 93,949,219 | 56.20% |
| CABA | 10 | 50 | 31 | 59,783,234 | 93,949,219 | 36.37% |
| Buenos Aires | 1 | 5 | 5 | 16,037,899 | 119,694,357 | 86.60% |
| Buenos Aires | 5 | 25 | 20 | 50,575,297 | 119,694,357 | 57.75% |
| Buenos Aires | 10 | 50 | 33 | 81,802,683 | 119,694,357 | 31.66% |
| Córdoba | 1 | 5 | 5 | 4,274,812 | 18,121,306 | 76.41% |
| Córdoba | 5 | 25 | 23 | 8,897,757 | 18,121,306 | 50.90% |
| Córdoba | 10 | 50 | 34 | 11,768,531 | 18,121,306 | 35.06% |
| Jujuy | 1 | 5 | 5 | 1,125,304 | 3,013,884 | 62.66% |
| Jujuy | 5 | 25 | 22 | 1,607,636 | 3,013,884 | 46.66% |
| Jujuy | 10 | 50 | 37 | 2,030,950 | 3,013,884 | 32.61% |

The payload is identical across radii for a fixed product request; the query
layer applies the exact straight-line Haversine filter locally. This avoids
radius-boundary false negatives.

## Equivalence and safety

- Production spot checks matched the full verified province shard exactly for
  one 50 km leche query in every measured region:
  CABA 1,145 offers, Buenos Aires 426, Córdoba 150, Jujuy 6.
- Existing CABA identity search audit: GO, 25 queries, precision@5 1.0000,
  and no recall claim.
- Distance semantics are straight-line Haversine only. No routing, driving
  time, stock, pickup, delivery, service-fee, or ETA claim is made.
- Trusted/missing coordinates: 2,537 / 0. Unpublished province rows: 69,154
  (2,177 nonstandard Buenos Aires; 66,977 unknown), retained as diagnostics.
- Exact ARS strings, provider IDs, valid GTINs, explicit quantities,
  promotions, source rows, package hashes, release/freshness, and
  availability UNKNOWN remain separate evidence. No GTIN repair, fuzzy join,
  inferred quantity, or ranking economics were introduced.

## Limitations and next milestone

This is a backend/tooling distribution qualification only. It does not
authorize Android UI integration, networking, live inventory, flyer ingestion,
basket optimization, or universal category/recall claims. A later milestone
may separately integrate this verified contract into the offline Android
consumer surface.

See ARGENTINA_QUERY_SELECTIVE_MOBILE_QUALIFICATION.json for the complete
machine-readable scenario matrix, file sizes, local query timings, provenance,
and equivalence details.
