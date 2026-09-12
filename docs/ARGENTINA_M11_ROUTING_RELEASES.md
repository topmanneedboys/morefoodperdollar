# Argentina M11 national routing releases

M11 adds one compact, immutable national routing object to each already
qualified Argentina release. The object contains only published region/store
identity, coordinates, and geography status. It is derived from the existing
content-addressed store-index evidence; the official SEPA ZIP is not opened or
reparsed by this step.

The derived release IDs are:

- `argentina-sepa-2026-09-06-e6c08be6a36e5e5b9-routing-v1`
- `argentina-sepa-2026-09-08-aeef3399fa20e751-routing-v1`

The `-routing-v1` suffix is intentional. Existing Sunday and Tuesday release
manifests and objects remain immutable and available for rollback.

## Operator publication sequence

Run the derivation on the already-qualified local content-addressed workspace
and keep the output outside Git, for example:

```text
python -m tools.build_argentina_national_routing_artifact \
  --source-workspace F:\ValuePilot-M9-20260908\workspace \
  --output-workspace F:\ValuePilot-M11-routing-derived-YYYYMMDD\workspace \
  --release-id argentina-sepa-2026-09-06-e6c08be6a36e5e5b9 \
  --release-id argentina-sepa-2026-09-08-aeef3399fa20e751
```

Review `ROUTING_RELEASE_PLAN.json`, then build a local publication plan for
both derived IDs. `plan` validates every local immutable object and both
manifest sidecars without contacting the object store or writing anything:

```text
python -m tools.argentina_object_store_publisher plan \
  F:\ValuePilot-M11-routing-derived-YYYYMMDD\workspace \
  --release-id argentina-sepa-2026-09-06-e6c08be6a36e5e5b9-routing-v1 \
  --release-id argentina-sepa-2026-09-08-aeef3399fa20e751-routing-v1 \
  --active-release-id argentina-sepa-2026-09-08-aeef3399fa20e751-routing-v1
```

After reviewing the plan, an operator with separate publication credentials may
stage the exact immutable releases. `stage` never writes or changes
`control/active.json`:

```text
python -m tools.argentina_object_store_publisher stage \
  F:\ValuePilot-M11-routing-derived-YYYYMMDD\workspace \
  --release-id argentina-sepa-2026-09-06-e6c08be6a36e5e5b9-routing-v1 \
  --release-id argentina-sepa-2026-09-08-aeef3399fa20e751-routing-v1 \
  --active-release-id argentina-sepa-2026-09-08-aeef3399fa20e751-routing-v1
```

The staged release can be checked with a read-only runtime credential before
activation:

```text
python -m tools.argentina_object_store_publisher verify \
  argentina-sepa-2026-09-08-aeef3399fa20e751-routing-v1
```

Only the explicit activation operation can change the pointer, and it uses a
compare-and-swap. There are no delete operations. To roll back, activate the
already verified Sunday ID, then activate the Tuesday ID again; no rebuild or
re-upload is needed:

```text
python -m tools.argentina_object_store_publisher activate \
  argentina-sepa-2026-09-08-aeef3399fa20e751-routing-v1
python -m tools.argentina_object_store_publisher activate \
  argentina-sepa-2026-09-06-e6c08be6a36e5e5b9-routing-v1 \
  --expected-current-release-id argentina-sepa-2026-09-08-aeef3399fa20e751-routing-v1
```

## Runtime integrity boundary

The active release manifest pins every logical artifact to an exact SHA-256
and byte count. Complete object materialization streams through a temporary
file, hashes and counts bytes incrementally, fsyncs, and atomically renames
only after verification. Because that stream already proves the exact
content-addressed object, remote runtime no longer fetches a duplicate
`bootstrap.sha256` or regional `manifest.sha256` solely to reread the same
digest. Local qualification still checks those companion files, and range
reads still metadata-verify the selected immutable pack before the exact range
request.

The backend accepts old manifests without `routingArtifact` through the
bounded 24-region compatibility path. A manifest that declares M11 routing but
has a malformed, missing, incomplete, or unpinned routing object fails closed;
it is never interpreted as empty geography.

## Deterministic local access-shape fixture

The representative CABA `arroz` request uses a 1 ms artificial latency per
object operation. It is a structural model, not a Cloud Run performance claim:

| path | operations | streams | HEADs | GETs | ranges | streamed bytes | peak buffered bound |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| old compatibility cold | 57 | 52 | 1 | 3 | 1 | 2,703,835 | <= 4 MiB (four bounded workers; observed overlap is scheduler-dependent) |
| M11 cold | 14 | 9 | 1 | 3 | 1 | 240,159 | 112,486 bytes |
| M11 warm delta | 3 | 0 | 0 | 3 | 0 | 0 | 112,486-byte prior bound |

The new cold request streams one national routing object, two selected region
contracts, and one query-required pack range. It does not fetch the other 22
region store indexes or any unselected pack. The modeled request latency drops
from 57 ms to 14 ms under the fixture's fixed per-operation model.

No command in this document performs a real deployment or changes Android.
