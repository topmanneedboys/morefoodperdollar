# Argentina production backend foundation v1

Status: `QUALIFIED_FOR_BACKEND_DISTRIBUTION_ONLY`

This milestone adds a portable, read-only backend boundary over the already
qualified Argentina release. It does not acquire data, add Android networking,
ship national data to the APK, or change the Milestone 6 shopping engine.

## Profile decision

The existing mobile/offline contract remains **128 logical partitions / 32
physical packs**. The backend uses the already-verified **1024 / 32** root as a
server profile because a bounded direct reader comparison produced the same M6
decision for the CABA↔Buenos Aires request while reducing cold and warm work.
The M5 source-wide equivalence and all four alternative verification results
are reused; this milestone did not run the national verifier or reparse the
government ZIP.

The one-item direct backend measurements were:

| region | cold | warm | cold bytes | range reads |
| --- | ---: | ---: | ---: | ---: |
| CABA | 7,717 ms | 29 ms | 933,100 | 10 |
| Buenos Aires | 4,696 ms | 19 ms | 513,738 | 5 |
| Córdoba | 3,221 ms | 11 ms | 89,322 | 5 |
| Jujuy | 3,348 ms | 11 ms | 38,019 | 10 |

Dense 5/10-line service timings are explicitly `NOT_REMEASURED`; the existing
M5/M6 selected-slice diagnostics remain the evidence for those payloads. No
claim is made that the unremeasured dense targets were met.

## Runtime contract

`backend/object_store.py` provides local and S3-compatible HEAD, GET, exact
range GET, immutable PUT, and conditional pointer primitives. `backend/release.py`
pins one immutable release per request and enforces fresh evidence; stale data
returns `CURRENT_PRICE_EVIDENCE_UNAVAILABLE`. `backend/cache.py` verifies member
length/hash before bounded 128 MiB activation and single-flights concurrent
loads. `backend/reader.py` routes across exact published province codes and
uses Haversine distance, then delegates the result to the existing M6 adapter
and engine. `backend/app.py` exposes `/healthz`, `/readyz`, `/v1/status`,
`/v1/search`, and `/v1/shop` with strict bounded JSON.

The CABA boundary sample queried both `ar-caba` and `ar-b`; its maximum returned
distance was 0.704189 km inside a 2 km radius. Availability remains `UNKNOWN`,
promotions with unknown eligibility do not enter base totals, and no stock,
delivery, ETA, or road-distance claim is made. No valid two-store savings case
was observed in the bounded 2/5/10/25/50 km sample, so none is fabricated.

## Safety and deployment boundary

The container is non-root with pinned Python dependencies, no bundled data or
credentials, disabled docs/CORS, health/readiness endpoints, and a single
process. Runtime credentials are read-only; publication is an operator-only
single-writer operation that uploads an immutable generation and changes the
active pointer last. The free-first R2/Cloud Run layout is documented only—no
cloud deployment or cost claim is made. Automated official SEPA acquisition is
`NOT_PROVEN`.

The complete machine-readable record is in
[ARGENTINA_PRODUCTION_BACKEND_QUALIFICATION.json](ARGENTINA_PRODUCTION_BACKEND_QUALIFICATION.json);
the bounded diagnostics were generated from the local qualified root and the
root itself is local and ignored.

## Exact-HEAD verification and promotion

The implementation was verified at the promoted HEAD
`72b75d14306a880de9077aea4684b493a9eef4be` without rerunning the national
SEPA verifier. The existing completed Milestone 5 equivalence and all-
alternative evidence were reused; dense diagnostics remain explicitly
`NOT_REMEASURED` above where another source-wide pass would have been required.

- Backend/tooling CI: [run 34204096115](https://github.com/topmanneedboys/morefoodperdollar/actions/runs/34204096115) — green.
- Android/browser/release CI: [run 34204096188](https://github.com/topmanneedboys/morefoodperdollar/actions/runs/34204096188) — green.
- Promoted branch: `work/valuepilot-android-milestone` at the same exact SHA.

This report records the already-proven 128 logical partition / 32 physical
pack mobile winner as preserved. No new data design or Android networking was
introduced by the report finalization.
