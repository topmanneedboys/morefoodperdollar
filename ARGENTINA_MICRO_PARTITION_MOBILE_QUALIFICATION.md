# Argentina micro-partitioned mobile qualification

Status: `QUALIFIED_FOR_BACKEND_DISTRIBUTION_ONLY`

Production Android UI authorization: `false`

This is an offline/provider-edge delivery qualification. It does not add
Android networking, Android data assets, live inventory, basket optimization,
or a claim of universal recall.

## Source and architecture

- Provider: `ARGENTINA_SEPA_PRECIOS_CLAROS` / Precios Claros - Base SEPA.
- Release: `2026-09-06`.
- Official outer ZIP: 325,522,188 bytes;
  SHA-256 `e6c08be6a36e5e5b90e6eb0b6f54a07c8bccded929fab2a28ad7f000ed08b305`.
- Accepted-observation SHA-256:
  `a5554d60a383acb83cf9f573a0e5f5db834c830a7a92e7db5051b09320e2da8d`.
- Verified national-index SHA-256:
  `8449a879442e5437e2f644af8a7aa447576f6435240424ed3c3ddabc815ba46a`.
- Licence/attribution: Creative Commons Attribution 4.0; source
  `datos.produccion.gob.ar`.

The benchmark tested deterministic SHA-256 product-evidence partitions with
128, 256, 512, and 1024 logical buckets. Each logical bucket is one canonical
JSONL gzip member; members are concatenated in stable order into 32 physical
pack files per region. A member is independently range-addressable and
verified, so neighbouring pack bytes are not required. Standalone logical
files were retained as a measured comparison only (130 files per region at
128 buckets); packed members keep the operational artifact at 34 payload files
per region (plus manifest/checksum metadata).

The selected design is the smallest passing design: **128 logical partitions,
32 physical packs**. Finer partitions are smaller for dense baskets, but the
explicit selection rule prefers the smallest design that passes the payload
and single-product regression gates.

## Winner artifact

- 24 Argentina regions; 2,537 stores; 1,447,719 product-evidence records;
  14,139,997 offers; 1,677,262 promotions.
- Region payload totals: 398,960,718 compressed bytes and 10,417,825,103
  uncompressed JSONL bytes.
- Complete generated-root total (including bootstrap, manifests, checksums,
  and README): 401,728,155 bytes across 892 files.
- The national ZIP, expanded provider data, and generated roots are local
  ignored inputs and are not in Git or Android assets.
- The first completed run fully verified all four alternatives. The later
  timing pass intentionally did not repeat that source-wide verification;
  the machine-readable report records this explicitly.

## 60-scenario payload matrix

The table below contains all 60 winner scenarios: four regions, three request
sizes, and radii 2/5/10/25/50 km. Exact radius filtering is local, so the
selected byte payload is identical across radii for a fixed request; the
returned offers still use the exact straight-line Haversine filter.

| Region | Products | 2 km | 5 km | 10 km | 25 km | 50 km | M4 50 km | Reduction | Logical partitions | Packs | Query load ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| CABA | 1 | 6,533,545 | 6,533,545 | 6,533,545 | 6,533,545 | 6,533,545 | 12,128,682 | 46.13% | 5 | 5 | 3,479 |
| CABA | 5 | 19,949,005 | 19,949,005 | 19,949,005 | 19,949,005 | 19,949,005 | 41,147,267 | 51.52% | 24 | 17 | 17,931 |
| CABA | 10 | 29,730,911 | 29,730,911 | 29,730,911 | 29,730,911 | 29,730,911 | 59,783,234 | 50.27% | 38 | 21 | 28,436 |
| Buenos Aires | 1 | 8,888,916 | 8,888,916 | 8,888,916 | 8,888,916 | 8,888,916 | 16,037,899 | 44.58% | 5 | 5 | 4,591 |
| Buenos Aires | 5 | 25,882,118 | 25,882,118 | 25,882,118 | 25,882,118 | 25,882,118 | 50,575,297 | 48.82% | 23 | 19 | 21,891 |
| Buenos Aires | 10 | 42,464,132 | 42,464,132 | 42,464,132 | 42,464,132 | 42,464,132 | 81,802,683 | 48.09% | 40 | 24 | 37,961 |
| Córdoba | 1 | 3,702,541 | 3,702,541 | 3,702,541 | 3,702,541 | 3,702,541 | 4,274,812 | 13.39% | 5 | 5 | 590 |
| Córdoba | 5 | 6,121,102 | 6,121,102 | 6,121,102 | 6,121,102 | 6,121,102 | 8,897,757 | 31.21% | 24 | 17 | 2,768 |
| Córdoba | 10 | 8,203,007 | 8,203,007 | 8,203,007 | 8,203,007 | 8,203,007 | 11,768,531 | 30.30% | 40 | 23 | 4,733 |
| Jujuy | 1 | 1,151,790 | 1,151,790 | 1,151,790 | 1,151,790 | 1,151,790 | 1,125,304 | -2.35% | 5 | 5 | 59 |
| Jujuy | 5 | 1,439,785 | 1,439,785 | 1,439,785 | 1,439,785 | 1,439,785 | 1,607,636 | 10.44% | 24 | 17 | 287 |
| Jujuy | 10 | 1,729,735 | 1,729,735 | 1,729,735 | 1,729,735 | 1,729,735 | 2,030,950 | 14.83% | 43 | 27 | 531 |

The payload gate used 50 km: CABA improved 46.13% / 51.52% / 50.27% for
1/5/10 products, and Buenos Aires improved 44.58% / 48.82% / 48.09%.
Córdoba and Jujuy are reported without forcing the major-region thresholds;
the Jujuy single-product result is a small documented regression.

## Alternative selection

| Logical buckets | CABA 5-product | CABA 10-product | Buenos Aires 5-product | Buenos Aires 10-product | Single-product gate | Result |
| ---: | ---: | ---: | ---: | ---: | :---: | :---: |
| 128 | 51.52% | 50.27% | 48.82% | 48.09% | pass | selected |
| 256 | 72.20% | 70.30% | 70.03% | 70.35% | pass | pass |
| 512 | 81.38% | 81.67% | 79.92% | 81.77% | pass | pass |
| 1024 | 84.98% | 86.33% | 84.79% | 87.46% | pass | pass |

Selection rule: choose the smallest power-of-two count meeting at least 40%
reduction for dense (10-product) CABA/Buenos Aires requests, at least 25% for
5-product requests, and no more than 2% single-product regression in those
regions. All alternatives passed; 128 therefore wins on bounded complexity and
file-count safety, not because finer partitions were ignored.

## Equivalence, cache, and safety

- The completed run verified all four alternatives and their full offer,
  product, store, promotion, provenance, and range metadata. Four production
  50 km `leche` checks matched both the Milestone 4 selective result and the
  full province reference exactly: CABA 1,145 offers, Buenos Aires 426,
  Córdoba 150, Jujuy 6.
- Deterministic fixtures also cover zero/tiny radii, same GTINs across
  retailers, adjacent member ranges, unknown quantities, malformed/invalid
  input, and Windows-shaped paths.
- Cache reuse is keyed by stable partition ID plus compressed and uncompressed
  hashes. A member is verified before immutable temp-file-to-target activation;
  a failed activation leaves no partial file and an existing last-known-good
  member is never overwritten by different bytes.
- Existing CABA search audit remains `GO`, precision@5 `1.0000`, with no recall
  claim.
- Availability is `UNKNOWN`; pickup/delivery, road distance, driving time,
  fees, ETA, and stock are not provided. Distance is straight-line Haversine
  only. Promotions retain `UNKNOWN` shopper eligibility.
- Exact ARS strings, provider IDs, valid GTINs, explicit quantities,
  provenance, source rows, freshness, and conflicts remain separate evidence.
  No fuzzy identity, GTIN repair, inferred quantity, ranking economics, or
  provider data in Git was introduced. The 69,154 unpublished province rows
  and 2,177 nonstandard Buenos Aires rows remain accounted for.

## Verification and limitations

The companion JSON contains every scenario’s selected ranges, compressed and
decompressed bytes, physical-pack bytes not fetched, packing overhead, query
timing, provenance, and the first-run verification results. Query timings are
local offline diagnostics, not network latency. Verification timing is marked
`NOT_REMEASURED_PER_USER_DIRECTION`; repeating source-wide correctness work was
intentionally avoided after the first complete green run.

This milestone ends at a backend/tooling distribution contract. It does not
authorize Android integration, networking, national raw-data shipping, live
inventory, flyers, basket optimization, natural-language input, images,
community sharing, or monetization changes.

See `ARGENTINA_MICRO_PARTITION_MOBILE_QUALIFICATION.json` for the complete
machine-readable report.
