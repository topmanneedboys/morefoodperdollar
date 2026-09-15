# Argentina automated release operations

This runbook describes the backend-side release automation. It does not add
Android networking and it does not authorize a production activation.

## Flow

The qualified architecture is split at the verified source archive:

`Argentina-egress acquisition runner` → `official datos.produccion.gob.ar ZIP`
→ `exact source verification / freshness decision` → `private content-addressed
source archive` → `Google Workflows` → `Cloud Build (e2-standard-2 /
E2_STANDARD_2)` → `tools.argentina_release_handoff_worker` → existing
`tools.argentina_release_worker` / `argentina_daily_release` pipeline →
private immutable candidate archive → provider-neutral promotion controller.

Only source acquisition requires Argentina egress. The heavy release build uses
normal Google Cloud networking. Do not route the multi-hour Cloud Build through
the VPN.

The GitHub acquisition runner is configured for three weekday checks at 13:30,
15:30, and 17:30 Argentina time. Scheduled runs remain disabled until
`ARGENTINA_AUTOMATED_ACQUISITION_ENABLED=true` is deliberately set in the
approved environment. The schedule is Monday through Friday only.

Before opening the VPN, `tools.argentina_acquisition_schedule_control` performs
an authenticated metadata/control preflight. The selected CKAN resource is
fingerprinted from stable bounded metadata, excluding the wall-clock observation
time. The control key also includes the Argentina civil date. This produces the
following bounded behavior:

- the first distinct selected-resource metadata fingerprint on an Argentina day
  requires one byte-level provider check;
- later checks on the same day and same fingerprint reuse the immutable daily
  observation and do not open the VPN or download the ~330 MB ZIP again;
- a selected-resource metadata change on the same day forces another byte-level
  check;
- the next Argentina day requires one fresh byte-level check even when CKAN
  metadata did not change, so rotating weekday files cannot be trusted solely
  from metadata;
- a manually forced same-day recheck that finds different source bytes without
  a corresponding metadata-fingerprint change fails closed for review rather
  than silently replacing evidence.

This intentionally treats CKAN metadata as a download optimization, never as
release identity. The downloaded ZIP's internally proven date and SHA-256 remain
the authority.

The acquisition runner also:

- reads a checksum-verified trusted active release manifest to derive the
  current official source date and SHA-256;
- prefers the resource matching the Argentina-local calendar weekday instead
  of trusting stale metadata date hints;
- requires the exact official `datos.produccion.gob.ar` HTTPS host;
- streams and hashes the bounded ZIP and re-inspects ZIP identity/internal date;
- stops without a build for `NO_CHANGE` or `OLDER_REJECTED`;
- fails closed for same-date changed bytes without upstream-change evidence;
- archives only a genuinely newer verified source under
  `source-archive/sha256/<sha256>.zip`;
- records a bounded immutable daily observation under
  `acquisition/daily-observations/<argentina-date>/<metadata-fingerprint>.json`;
- emits the exact archive key/SHA, observation timestamp, source date, resource
  identity, and current trusted source identity for orchestration.

Before a heavy build is launched, the controller verifies the private source
archive again and checks whether the deterministic final candidate is already
present and valid in the private candidate archive. If it is present, no build
is launched. If it is absent, an immutable reservation under
`acquisition/build-attempts/<argentina-date>/<candidate-release-id>.json` limits
automation to one heavy build attempt for that candidate on that Argentina day.
A failed or ambiguous launch is therefore fail-closed instead of being retried
repeatedly and creating duplicate spend.

The GitHub runner starts Google Workflows asynchronously with
`promotionMode: SHADOW`. It does not wait for the multi-hour Cloud Build. This
keeps GitHub execution bounded while Google owns the long-running deterministic
build. The acquisition workflow itself has no ACTIVE/CANARY pointer authority.

A verified archive handoff is stronger evidence than the CKAN metadata
checksum. Therefore `workflows/argentina-release.yaml` may use unchanged CKAN
metadata as a no-build optimization only when there is no archive handoff. An
explicit archive key/SHA bypasses metadata-only `NO_CHANGE` and must be
inspected by the release worker.

`tools.argentina_release_handoff_worker` is a thin compatibility boundary. With
no archive handoff it delegates to the mature worker unchanged. With a verified
archive handoff it restores and hash-verifies the exact content-addressed ZIP,
then passes it to the mature worker as an explicit local `--source`. All
qualification, deterministic build, staging, canary, smoke, CAS, activation,
and rollback authority remains in existing tooling.

The worker derives national routing and SearchPack v2 into a separate immutable
candidate workspace. Promotion remains `SHADOW` by default. After a successful
verified worker result, the deployed Cloud Build wrapper now stages the final
SearchPack release into the private archive bucket, verifies it remotely, and
writes an immutable `candidate-archive/receipts/<execution-id>.json` receipt.
This persistence phase does not select ACTIVE or CANARY. A later promotion phase
can therefore reuse the expensive candidate instead of rebuilding it.

The Cloud Build connector uses a six-hour timeout with bounded polling. The
qualified 2026-09-14 SHADOW build took approximately 3 hours 57 minutes on
`E2_STANDARD_2`; queued/working states are never mistaken for success.

`cloudbuild/argentina-release-worker.yaml` is a template, not a deployed
secret-bearing configuration. Before use, an operator must provide the
approved digest-pinned worker image, immutable build-source object generation,
operator-only archive bucket, scoped builder service account, and explicit
RFC3339 observation time.

## Controlled unattended rollout

1. Keep `ARGENTINA_AUTOMATED_ACQUISITION_ENABLED=false` while code, IAM, and the
   default-branch scheduling path are being qualified.
2. The acquisition identity may read the trusted active runtime state,
   create/reuse private archive/control objects, and invoke the approved release
   workflow. It must not have direct production active-pointer write authority.
3. Run the dedupe preflight and verify that repeated same-day unchanged checks
   do not open the VPN or download provider bytes.
4. For the next genuinely new official source, verify one Windscribe acquisition
   produces a content-addressed source archive and exactly one build reservation.
5. Verify Google Workflow starts asynchronously in `SHADOW`, restores that exact
   source, and starts at most one Cloud Build for the candidate/day.
6. Verify the successful candidate survives builder teardown in the private
   archive and its receipt reports `activePointerWrites: 0`.
7. Repeat the same source and verify the candidate archive suppresses a second
   heavy build.
8. Only after these shadow gates are proven should scheduled acquisition be
   enabled. `AUTO_ACTIVATE` remains disabled until staging/canary/smoke and
   rollback controls are separately qualified.

GitHub scheduled workflows execute from the repository's default branch. The
safe acquisition workflow and its schedule-control dependencies therefore must
be present on the default branch before the environment gate is enabled. Do not
enable the gate while the safe version exists only on a candidate branch.

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
- Daily acquisition observations and build-attempt reservations are bounded,
  immutable private control evidence. They never select ACTIVE/CANARY.
- Verified SearchPack candidates are persisted privately under the existing
  content-addressed `objects/sha256/` and `releases/<release-id>/` layout.
- Releases and manifests are content-addressed; retries reuse identical
  objects. No deletion or garbage collection is introduced by this milestone.

## IAM boundary

- Runtime Cloud Run identity: read-only access to the regional runtime mirror;
  no archive, publisher, R2, Cloud Build, Workflows, or VPN authority.
- Acquisition-runner identity: read trusted active state, create/reuse verified
  private archive/control objects, and invoke the bounded workflow/build path
  only; no direct production active-pointer write.
- Scheduler identity: invoke the approved orchestration entrypoint only.
- Workflow identity: read/write bounded control objects and invoke the release
  build only.
- Cloud Build/publisher identity: scoped access to the archive and the three
  publication stores required for staging/verification/activation.
- R2 credentials remain in Secret Manager and are never written to YAML, logs,
  reports, or Git.
- GitHub Actions uses OIDC / Google Workload Identity Federation rather than a
  long-lived service-account JSON key.

No permanent grant is applied by repository code. Bucket- and
service-account-scoped grants must be reviewed before enabling the schedule.

## Monitoring

Emit structured execution fields: execution ID, resource ID/name, source date
and hash, archive key, metadata fingerprint, Argentina date, build reservation,
candidate release ID, current release ID, stage, status, elapsed time, build ID,
activation result, and rollback result. Credential-like fields are redacted.
Monitor failed/repeated acquisition, stale/older releases, persisted-candidate
verification failures, regional pointer disagreement, canary failures, and
compensation. No notification destination is invented here.

## Current qualification status

Route-dependent source access was proven on 2026-09-14 UTC: a non-Argentina
route received HTTP 403 while the operator-selected Argentina VPN route could
retrieve and verify the official source without changing production.

The unattended Windscribe/GitHub path subsequently acquired the official
2026-09-14 `sepa_lunes.zip` source and published it to the private GCS archive:

- source bytes: `330066495`;
- SHA-256: `f7a8ec5631c06c079b5be66483ec115e12adfd0562884f2fc92f43b7c6c738fd`;
- internally proven release date: `2026-09-14`;
- archive key:
  `source-archive/sha256/f7a8ec5631c06c079b5be66483ec115e12adfd0562884f2fc92f43b7c6c738fd.zip`;
- production remained on the trusted 2026-09-08 release.

A later SHADOW Cloud Build successfully derived and verified
`argentina-sepa-2026-09-14-f7a8ec5631c06c07-routing-v1-search-v2` in about
3 hours 57 minutes with zero active/canary writes. That first successful build
predated candidate persistence, so its ephemeral output disappeared when the
builder exited. The persistence fix is now deployed in Workflow revision
`000007-7f1`; the next genuinely new successful build is expected to persist and
remote-verify its candidate instead of requiring a repeat build.

The permanent schedule remains intentionally disabled until the new same-day
dedupe/build-reservation path is CI-green, present on the default branch, and
proven with controlled SHADOW evidence.
