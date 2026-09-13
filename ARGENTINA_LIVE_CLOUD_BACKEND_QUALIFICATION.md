# ValuePilot M11 — final regional data-plane qualification

Status: M11 CLOSED

This report closes the bounded regional runtime milestone. The durable
publication copy remains in Cloudflare R2, while user requests use only the
same-region Google Cloud Storage mirror. The Android application remains
offline and no national source ZIP is shipped to the APK.

## Provenance and evidence boundary

- Candidate branch: candidate/argentina-live-cloud-backend-v1
- M11 starting HEAD: 9b125866fe6e4f8fe5d84d97c503aa053402c756
- Final implementation HEAD: 4b9861d28e9d1dcd597df9ee1a75de1b82502c32
- Runtime image: us-east4-docker.pkg.dev/valuepilot-argentina/valuepilot-backend/valuepilot-backend@sha256:fb36dcf9c40d9af15ea9b8019601c7b0eb585bce1c23587006559f77b95e6707
- Official source: Precios Claros - Base SEPA, datos.produccion.gob.ar
- Active source file: sepa_martes.zip, release date 2026-09-08, 325118492 bytes
- Active source SHA-256: aeef3399fa20e751e80cda46cbb3a38349e378b51832ecffe1f779b986479815
- Source attribution: Precios Claros - Base SEPA; national catalog datos.gob.ar
- Catalog licence recorded by the source: Creative Commons Attribution 4.0
- Rollback source: 2026-09-06, SHA-256
  e6c08be6a36e5e5b90e6eb0b6f54a07c8bccded929fab2a28ad7f000ed08b305

The completed Milestone 5 whole-source equivalence, all four alternative
checks, and the previously built immutable Tuesday/Sunday roots were reused.
The national ZIP was not reopened or reprocessed for M11. The final work
measured only bounded runtime behavior and deployment diagnostics.

## Release and mirror layout

The active release is:

    argentina-sepa-2026-09-08-aeef3399fa20e751-routing-v1-search-v2

The immediately previous proven rollback release is:

    argentina-sepa-2026-09-06-e6c08be6a36e5e5b9-routing-v1-search-v2

Both regional buckets contain identical content-addressed objects, both
release manifests, and a regional control/active.json pointer. The pointer
contains the active Tuesday release, the Sunday rollback release, and schema
valuepilot-active-release-v1. Pointer changes are the only rollback action.

| Runtime region | Cloud Run service | Runtime bucket | Active traffic |
| --- | --- | --- | ---: |
| us-east4 | valuepilot-argentina-us-east4, revision 00038-deq | valuepilot-argentina-runtime-us-east4 | 100% |
| southamerica-east1 | valuepilot-argentina-sa-east1, revision 00029-gef | valuepilot-argentina-runtime-southamerica-east1 | 100% |

The two buckets are Standard regional buckets in their matching regions, with
uniform bucket-level access and public access prevention. Each has only the
runtime service account's bucket-scoped roles/storage.objectViewer grant for
request-time reads. No runtime R2 credential or R2 environment variable is
configured.

The permanent supporting grants are limited to the bucket-scoped runtime
reader permissions above, the compute/build identity's objectViewer grant on
the pre-existing Cloud Build log bucket, and the Cloud Build identity's
repository-scoped Artifact Registry writer grant. No project-wide storage
writer permission was added.

## Inventory

The active release manifest has 1163 content objects totaling 880538444
bytes. Its manifest.json is 227883 bytes and its manifest.sha256 is 80 bytes;
the manifest SHA-256 is
ee928eaf56a35489572c8f40639b42e6cb56ba4baec0c7e853aebe92df705a8e.
The rollback manifest has 1161 content objects totaling 883894895 bytes.

Across both releases, each mirror contains:

- 2323 unique content-addressed objects, totaling 1764433210 bytes;
- four release manifest files, totaling 429676 bytes;
- one 304-byte regional active pointer;
- 2328 objects and 1764863190 bytes including the pointer.

The complete per-object byte and SHA-256 inventory remains in the immutable
release manifests. The following regional aggregates are a compact audit
index. Offer-region bytes include that region's offer packs, indexes, and
regional manifest/integrity files. SearchPack bytes include all eleven
SearchPack objects for that region. The shared national routing object is
37048 bytes compressed.

| Region | Offer objects bytes | SearchPack bytes | Store index bytes | Offer packs | Largest offer pack (compressed) |
| --- | ---: | ---: | ---: | ---: | ---: |
| ar-a | 11348131 | 17507441 | 2792 | 32 | 276342 |
| ar-b | 116298375 | 30830011 | 42646 | 32 | 3721225 |
| ar-caba | 87207865 | 24208743 | 46558 | 32 | 2834083 |
| ar-d | 6492421 | 15015268 | 1039 | 32 | 121905 |
| ar-e | 15528331 | 21572587 | 4463 | 32 | 403592 |
| ar-f | 4407516 | 11947099 | 490 | 32 | 71256 |
| ar-g | 4976763 | 11520042 | 901 | 32 | 95693 |
| ar-h | 5946945 | 15747833 | 904 | 32 | 103076 |
| ar-j | 7739579 | 15186262 | 1293 | 32 | 166274 |
| ar-k | 5383657 | 13646231 | 724 | 32 | 93452 |
| ar-l | 7995783 | 14773757 | 1439 | 32 | 175087 |
| ar-m | 14488199 | 20820081 | 3592 | 32 | 358834 |
| ar-n | 4914249 | 10846187 | 998 | 32 | 101306 |
| ar-p | 5937637 | 12556036 | 552 | 32 | 115702 |
| ar-q | 14114212 | 22315000 | 3026 | 32 | 345259 |
| ar-r | 14165572 | 17574516 | 3177 | 32 | 370515 |
| ar-s | 13246453 | 25071902 | 2629 | 32 | 296381 |
| ar-t | 9896176 | 17046643 | 1471 | 32 | 223444 |
| ar-u | 12945398 | 18749405 | 2418 | 32 | 317691 |
| ar-v | 4843029 | 7936315 | 828 | 32 | 100613 |
| ar-w | 9089044 | 17633458 | 1514 | 32 | 198059 |
| ar-x | 20895666 | 22608392 | 7927 | 32 | 559595 |
| ar-y | 4473952 | 9703473 | 949 | 32 | 86797 |
| ar-z | 7118284 | 9446195 | 1636 | 32 | 168929 |

The largest object overall is input-vocabulary.json at 35336770 bytes. The
largest compressed offer physical pack is
micro-1024/regions/ar-b/packs/pack022.bin at 3721225 bytes (119316757 bytes
uncompressed). Objects above the 1 MiB full-object cache threshold use the
verified bounded range path.

## Runtime configuration and production checks

Both production revisions use CPU 1, 4 GiB memory, concurrency 1, timeout 30
seconds, min scale 0, max scale 1, and startup CPU boost. Both use the same
image digest above and the same runtime service account. The only runtime
configuration is release store gcs, the matching regional bucket, and the
Google Cloud project identifier.

Unauthenticated requests returned 403. Authenticated readiness and status
requests returned 200. The authenticated production shopping smoke check
returned HTTP 200 in each region with the Tuesday release, FRESH freshness,
and evidenceSemantics.availability UNKNOWN. Price publication is not treated
as inventory. The external Cloud Run /healthz URL was still answered by the
Google front end with 404, as it was on the prior service; local and CI
health/readiness checks remain green, and /readyz is the production readiness
probe.

## True cold scale-from-zero

Each row is a fresh, separately pinned canary instance before production
activation. GCS statistics are from the request's application diagnostics.
No R2 request was made.

| Region | Lines | HTTP | External wall ms | App service ms | Cloud Run request ms | Reader total ms | GCS bytes | GCS head/range/stream | Reader object/range reads |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | ---: |
| us-east4 | 1 | 200 | 3987.6 | 3652.055 | 3721.445 | 3261.140 | 14409736 | 17 / 17 / 17 | 10 / 0 |
| us-east4 | 5 | 200 | 5308.9 | 4976.879 | 5039.506 | 4707.164 | 18300943 | 51 / 95 / 17 | 50 / 0 |
| us-east4 | 10 | 200 | 8113.6 | 7784.899 | 7839.360 | 7494.327 | 22913274 | 62 / 182 / 17 | 94 / 0 |
| southamerica-east1 | 1 | 200 | 4932.4 | 4333.646 | 4406.216 | 3968.031 | 14409736 | 17 / 17 / 17 | 10 / 0 |
| southamerica-east1 | 5 | 200 | 6030.2 | 5579.702 | 5653.578 | 5222.483 | 18300943 | 50 / 95 / 17 | 50 / 0 |
| southamerica-east1 | 10 | 200 | 7807.8 | 7221.418 | 7288.621 | 6875.070 | 22913274 | 62 / 182 / 17 | 94 / 0 |

All six fresh requests were HTTP 200 and completed below the 10-second M11
target. All used the active Tuesday release, FRESH evidence, and UNKNOWN
availability. Corpus scans were zero.

## Warm p95 qualification

Twenty sequential samples were collected after one warm-up for each of four
locations (CABA, Buenos Aires Province, Córdoba, and Jujuy) and each 1/5/10
line shape. The table shows service time / external wall time in milliseconds;
the maximum over the four locations is shown first.

| Region | Lines | Worst-location service / wall p95 | Worst location | Target |
| --- | ---: | ---: | --- | ---: |
| us-east4 | 1 | 168.551 / 280.240 | CABA | <= 1000 / 1000 |
| us-east4 | 5 | 326.315 / 442.076 | CABA | <= 2000 / 2000 |
| us-east4 | 10 | 559.107 / 678.978 | CABA | <= 3000 / 3000 |
| southamerica-east1 | 1 | 150.206 / 516.777 | CABA | <= 1000 / 1000 |
| southamerica-east1 | 5 | 282.218 / 732.785 | CABA | <= 2000 / 2000 |
| southamerica-east1 | 10 | 477.139 / 871.152 | CABA | <= 3000 / 3000 |

All 480 warm samples returned 200, FRESH, and UNKNOWN availability. The
complete location matrix is retained in the machine-readable report.

## Safety behavior, concurrency, and operations

- The 64 typo/ambiguity cases exercised through shop-text across both regions
  returned NEEDS_CLARIFICATION with CANDIDATE_HORIZON_REACHED. The bounded
  identity horizon therefore failed closed; no typo correction is claimed.
- Eight-client concurrency samples at 1, 2, 4, and 8 clients returned 200 in
  both regions, with one release ID, FRESH freshness, and UNKNOWN availability
  throughout. Elapsed milliseconds were 654.828 / 204.001 / 351.049 /
  668.709 in us-east4 and 683.542 / 540.774 / 485.456 / 829.240 in
  southamerica-east1.
- The sampled maximum memory-usage distribution mean was 329973760 bytes
  (314.69 MiB) on each 10-line revision. Cloud Monitoring exposes a
  distribution mean rather than an instantaneous peak; the 4 GiB container
  limit was not approached, and no OOM, restart, crash, or termination was
  observed.
- The per-instance verified cache is bounded at 536870912 bytes (512 MiB).
  Objects up to 1048576 bytes may use the verified whole-object cache; larger
  objects remain on the verified range path. A representative warm instance
  reported 32714442 cache bytes, 112 downloads, 205 hits, zero evictions,
  zero in-flight loads, and zero cache writes after warm-up.
- Representative post-activation diagnostics recorded GCS reads only
  (get 3, head 390, range 679, stream 116; bytesRead 64211474) and zero
  GCS writes. R2 reads and writes were zero. The reader reported corpus scan
  zero.

## Architectural result and closeout

R2 remains the durable publication/control-plane copy. Regional GCS holds
immutable runtime mirrors and regional active pointers. A request reads one
regional pointer, pins one immutable release, verifies manifest-derived
objects, and never contacts R2. The 1024 logical / 32 physical server profile
is used only for the backend; the proven mobile 128 logical / 32 physical
profile remains unchanged.

No Android networking, Android data asset, provider image, flyer content,
stock claim, delivery claim, or provider-influenced organic ranking was added.
The national ZIP and full normalized datasets remain local/backend-side and
are not tracked in Git.

The report-only finalization was followed by exact-HEAD repository tests and
Android/browser/release verification, then a fast-forward of the intended
milestone branch. No force push, data rebuild, garbage collection, or new
milestone was started.

M11 result: PASS

M11 CLOSED: YES
