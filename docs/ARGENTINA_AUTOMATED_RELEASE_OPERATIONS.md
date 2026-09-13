# Argentina automated release operations

This runbook describes the offline/backend-side release automation. It does
not add Android networking and it does not authorize a production activation.

## Flow

`Cloud Scheduler (America/Argentina/Buenos_Aires)` → `Google Workflows` →
`Cloud Build (e2-standard-2 / E2_STANDARD_2)` → the existing
`tools.argentina_release_worker` and `argentina_daily_release` pipeline.

The workflow performs a bounded national-catalog metadata check first. An
unchanged checksum returns `NO_CHANGE` and starts no build. A changed
checksum starts the worker, which validates the official production host,
streams the ZIP, archives the exact bytes privately, acquires the distributed
GCS lease, and delegates all SEPA parsing and artifact construction to the
existing deterministic tooling. The worker has no activation capability.
The Cloud Build connector waits for the long-running build operation (with a
four-hour bound and bounded polling) and evaluates the final build record;
queued/working states are never mistaken for a successful candidate.

`cloudbuild/argentina-release-worker.yaml` is a template, not a deployed
secret-bearing configuration. Before use, an operator must provide a
digest-pinned worker image, an immutable source object generation, the
operator-only archive bucket, the scoped builder service account, and an
explicit RFC3339 observation time.

## First rollout

1. Run the worker locally in shadow mode with a tiny fixture and inspect the
   immutable execution summary.
2. Run against an accessible, genuinely newer official ZIP. The known local
   `sepa_martes.zip` is the already-verified 2026-09-08 baseline and is not a
   new source for this purpose.
3. Repeat the exact candidate. It must reuse the archive and release ID and
   produce no active-pointer writes.
4. Stage and verify the candidate in R2, `us-east4`, and
   `southamerica-east1`; stage changes no active pointer.
5. Point zero-traffic canary revisions at `control/canary.json` and run the
   documented data/performance smoke set.
6. Only after a human reviews all evidence may the transactional coordinator
   activate US, then South America, then R2. Any failure compensates with
   compare-and-swap and stops.
7. Enable Scheduler only after controlled activation succeeds. Until then,
   leave it disabled and report `SHADOW_ONLY_WAITING_FOR_ACCESSIBLE_NEWER_OFFICIAL_SOURCE`.

## Data-canary gate

Before an automatic stage is eligible for canary selection, the deployment
adapter must submit the bounded `valuepilot-argentina-data-canary-v1` evidence
contract to `evaluate_data_canary`. It must cover both `us` and `sa`, `/readyz`,
`/v1/status`, the `arroz` discovery and interpretation checks, the specific
`7UP` search, 1/5/10-line shopping requests, and CABA, Buenos Aires Province,
Córdoba, and Jujuy location checks. Every observation must identify the
candidate release, be HTTP 200 and `FRESH`, retain `availability: UNKNOWN`,
report zero corpus scans, a non-empty partition identity, no OOM/restart, zero
runtime R2 reads/writes, and zero malformed prices; the deployment adapter must
explicitly mark the partition identity as verified. Both regions must also
report a true 10-line cold measurement at or below 10,000 ms and a bounded warm
smoke p95 at or below 5,000 ms. Missing, stale, conflicting, or out-of-bound
evidence returns `REVIEW_REQUIRED` and must not stage or select a canary.

The validator is offline and summary-only: it does not fetch endpoints, open a
source ZIP, read a product corpus, or write an active pointer. A production
deployment adapter remains responsible for obtaining the evidence from the
same code image and zero-traffic canary revisions.

## Storage and retention

- Raw ZIPs are never committed, shipped to Android, placed in SearchPack, or
  exposed to the runtime service account.
- The worker writes immutable
  `source-archive/sha256/<sha256>.zip` objects and small sidecar manifests.
- Control state is bounded and immutable/CAS-protected under `control/`.
- Releases and manifests are content-addressed; retries reuse identical
  objects. No deletion or garbage collection is part of this milestone.

## IAM boundary

- Runtime Cloud Run identity: read-only access to the two regional runtime
  mirrors only; no archive, publisher, R2, Cloud Build, or Workflows control.
- Scheduler identity: invoke the one workflow only.
- Workflow identity: read/write bounded control objects and invoke the
  release build only.
- Cloud Build/publisher identity: scoped access to the archive and the three
  publication stores required for staging/verification/activation.
- R2 credentials remain in Secret Manager and are never written to YAML,
  logs, reports, or Git.

No permanent grant is applied by this repository change. An operator must
review the bucket- and service-account-scoped grants before deployment.

## Monitoring

Emit structured execution fields: execution ID, resource ID, source date and
hash, candidate release ID, current release ID, stage, status, elapsed time,
build ID, activation result, and rollback result. Credential-like fields are
redacted. Monitor failed/repeated acquisition, stale releases, regional
pointer disagreement, canary failures, and compensation. No email/SMS
notification destination is invented here; choose one explicitly when
enabling alerts.

## Current qualification status

The national catalog metadata contract is recorded in
`ARGENTINA_AUTOMATED_RELEASE_SOURCE_CONTRACT.json`. The catalog is reachable,
but the production API/download probe returned HTTP 403 during qualification,
so live automated acquisition and activation remain unproven. The local
Tuesday baseline remains available for deterministic no-op/shadow tests.
