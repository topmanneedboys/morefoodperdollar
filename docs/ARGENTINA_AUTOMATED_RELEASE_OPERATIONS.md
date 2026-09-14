# Argentina automated release operations

This runbook describes the backend-side release automation. It does not add
Android networking and it does not authorize a production activation.

## Flow

The qualified architecture is now split at the verified source archive:

`Argentina-egress acquisition runner` → `official datos.produccion.gob.ar ZIP`
→ `exact source verification / freshness decision` → `private content-addressed
source archive` → `Google Workflows` → `Cloud Build (e2-standard-2 /
E2_STANDARD_2)` → `tools.argentina_release_handoff_worker` → existing
`tools.argentina_release_worker` / `argentina_daily_release` pipeline →
provider-neutral promotion controller.

Only the small acquisition step requires Argentina egress. The heavy release
build must use normal Google Cloud networking. Do not route the four-hour
Cloud Build through the VPN.

The acquisition runner:

- reads a checksum-verified trusted active release manifest to derive the
  current official source date and SHA-256;
- queries official CKAN metadata;
- prefers the resource matching the Argentina-local calendar weekday instead
  of trusting stale metadata date hints;
- still requires the exact official `datos.produccion.gob.ar` HTTPS host;
- streams and hashes the bounded ZIP and re-inspects ZIP identity/internal date;
- stops without a build for `NO_CHANGE` or `OLDER_REJECTED`;
- fails closed for same-date changed bytes without upstream-change evidence;
- archives only a genuinely newer verified source under
  `source-archive/sha256/<sha256>.zip`;
- emits the exact archive key/SHA, observation timestamp, source date, resource
  identity, and current trusted source identity for orchestration.

A verified archive handoff is stronger evidence than the CKAN metadata
checksum. Therefore `workflows/argentina-release.yaml` may use unchanged CKAN
metadata as a no-build optimization only when there is no archive handoff. An
explicit archive key/SHA bypasses metadata-only `NO_CHANGE` and must be
inspected by the release worker.

`tools.argentina_release_handoff_worker` is a thin compatibility boundary. With
no archive handoff it delegates to the mature worker unchanged. With a verified
archive handoff it restores and hash-verifies the exact content-addressed ZIP,
then passes it to the mature worker as an explicit local `--source`. All
qualification, lease, deterministic build, staging, canary, smoke, CAS,
activation, and rollback authority remains in existing tooling.

The worker derives national routing and SearchPack v2 into a separate immutable
candidate workspace. Repeated runs reuse a verified candidate; interrupted or
mismatched workspaces fail closed. Promotion remains `SHADOW` by default. An
operator may explicitly select `STAGE_CANARY` or `AUTO_ACTIVATE` only with the
existing required destinations/evidence/smoke endpoints/current-release guard.
Missing or invalid inputs fail before a pointer write.

The Cloud Build connector waits for the long-running build operation with a
four-hour bound and bounded polling; queued/working states are never mistaken
for a successful candidate.

`cloudbuild/argentina-release-worker.yaml` is a template, not a deployed
secret-bearing configuration. Before use, an operator must provide the
approved digest-pinned worker image, immutable build-source object generation,
operator-only archive bucket, scoped builder service account, and explicit
RFC3339 observation time.

## First unattended rollout

1. Configure one approved unattended acquisition runner whose network route is
   already Argentina egress. Do not put VPN credentials in Git or logs.
2. Give that runner only the permissions it needs: read the trusted active
   runtime state, create/reuse objects in the private source archive, and invoke
   the one release workflow/build path. It must not have direct active-pointer
   write authority.
3. Run acquisition once in shadow orchestration. It must select the
   Argentina-local weekday resource, verify official host/hash/ZIP/internal
   date, and either stop safely or produce `HANDOFF_READY`.
4. If `HANDOFF_READY`, invoke Google Workflow/Cloud Build with the exact archive
   key/SHA and current trusted source identity from the same acquisition run.
5. Run the heavy worker in `SHADOW`; verify the exact archived source was
   restored and that `activePointerWrites` remains `0`.
6. Repeat the same source. It must reuse immutable archive/release identities and
   produce no active-pointer writes.
7. Only after shadow is stable should a newer candidate be staged/verified in
   R2, `us-east4`, and `southamerica-east1`, followed by the existing zero-
   traffic canary/data/performance smoke process.
8. Enable the permanent schedule only after controlled staging/canary evidence
   is green. Keep `AUTO_ACTIVATE` disabled until explicitly reviewed.

The known Tuesday 2026-09-08 source remains the trusted active baseline. A
newly acquired source must never be allowed to downgrade it.

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
- The acquisition path writes immutable
  `source-archive/sha256/<sha256>.zip` objects and small sidecar manifests only
  after official-source verification and freshness classification.
- Control state is bounded and immutable/CAS-protected under `control/`.
- Releases and manifests are content-addressed; retries reuse identical
  objects. No deletion or garbage collection is introduced by this milestone.

## IAM boundary

- Runtime Cloud Run identity: read-only access to the regional runtime mirror;
  no archive, publisher, R2, Cloud Build, Workflows, or VPN authority.
- Acquisition-runner identity: read trusted active state, create/reuse verified
  private archive objects, and invoke the bounded workflow/build path only; no
  direct production active-pointer write.
- Scheduler identity: invoke the approved orchestration entrypoint only.
- Workflow identity: read/write bounded control objects and invoke the release
  build only.
- Cloud Build/publisher identity: scoped access to the archive and the three
  publication stores required for staging/verification/activation.
- R2 credentials remain in Secret Manager and are never written to YAML, logs,
  reports, or Git.
- If GitHub Actions is selected for acquisition, use OIDC / Google Workload
  Identity Federation rather than a long-lived service-account JSON key.

No permanent grant is applied by this repository change. An operator must
review bucket- and service-account-scoped grants before deployment.

## Monitoring

Emit structured execution fields: execution ID, resource ID/name, source date
and hash, archive key, candidate release ID, current release ID, stage, status,
elapsed time, build ID, activation result, and rollback result. Credential-like
fields are redacted. Monitor failed/repeated acquisition, stale/older releases,
regional pointer disagreement, canary failures, and compensation. No
notification destination is invented here.

## Current qualification status

Real route-dependent acquisition was proven on 2026-09-14 UTC:

- baseline/non-Argentina route: official ZIP download HTTP 403;
- operator-selected Argentina VPN route: `PASS`, final host
  `datos.produccion.gob.ar`, 330,069,965 bytes, SHA-256
  `5c8912521d6fe2d64e2f096b962acd47c18c247cce7787576ae04880d028b962`,
  internally proven date 2026-09-07;
- comparison:
  `CANDIDATE_ROUTE_SUCCEEDS_WHERE_BASELINE_403`;
- no production state changed.

The remaining blocker is not another manual source probe. It is one-time
configuration and shadow qualification of an **unattended approved Argentina
egress** for the acquisition runner. The exact VPN/provider integration is
intentionally not guessed by repository code.
