# ValuePilot automated official Argentina release pipeline

Status: **AUTOMATED RELEASE PIPELINE: BLOCKED**

This checkpoint implements and verifies the offline/backend-side control plane
for one official Argentina SEPA release. It does not claim a live production
activation. The exact blocker is source access: the official production
resource/download probes returned HTTP 403 during qualification, so a newer
official source has not been independently acquired or proven.

## Checkpoint identity

- Starting head: `6ed1cab8300d1ed0ed12fef240194fa7edbb5901`
- Candidate branch: `candidate/argentina-automated-release-v1`
- Implementation head: `f8282fe605ae95a781a172e29e4bb30fc44da05a`
- Scope: offline acquisition, archive, worker, control-plane, staging and
  canary tooling only.

## Source truth

The national catalog contract is recorded in
`ARGENTINA_AUTOMATED_RELEASE_SOURCE_CONTRACT.json` and attributed to
**Precios Claros - Base SEPA**, **Creative Commons Attribution 4.0**, from
`datos.produccion.gob.ar` / `datos.gob.ar`.

The local file `Downloads\\sepa_martes.zip` is real and was checked against the
already-verified Tuesday evidence:

- release date: **2026-09-08**
- bytes: **325,118,492**
- SHA-256:
  `aeef3399fa20e751e80cda46cbb3a38349e378b51832ecffe1f779b986479815`
- Git/raw-data status: **not committed**

This Tuesday ZIP is a retained, immutable baseline for no-op/shadow checks. It
is not described as a newly acquired release in this milestone. The national
source-wide verification and immutable-root rebuild were **NOT_REMEASURED**;
the completed evidence was reused as requested.

The catalog metadata endpoint was reachable (HTTP 200), but the production
dataset/resource probes returned HTTP 403. The workflow therefore remains
`SHADOW_ONLY_WAITING_FOR_ACCESSIBLE_NEWER_OFFICIAL_SOURCE`; no guessed mirror,
scrape, WAF bypass, or substituted ZIP is allowed.

## What is implemented

- Strict official-host HTTPS acquisition with bounded streaming, size/hash/date
  checks, retry/redirect rules, and explicit source-change classification.
- Content-addressed private source archives with immutable manifests, safe
  restore, duplicate reuse, and no deletion or Git staging of raw data.
- A bounded worker that records discovery fingerprints with immutable/CAS state,
  takes a distributed GCS-generation lease, writes redacted immutable execution
  summaries, and never activates a production pointer.
- Reuse of the existing deterministic daily-release/SEPA parser and provider
  boundary; no SEPA parsing or retailer logic moved into shared core.
- Multi-store staging and verification for `us`, `sa`, and `r2`, a fixed
  `control/canary.json` path, and a transactional active-pointer coordinator
  with compare-and-swap, smoke gates, compensation, and rollback review.
- Cloud Build, Workflows, and paused Scheduler templates with bounded four-hour
  worker/build timeouts. They are templates only; no cloud resource, secret,
  IAM grant, permanent schedule, or deployment was applied.
- Workflow path coverage and deterministic fixture tests for nesting, empty or
  corrupt packages, missing files, exact GTIN/quantity/price handling, store
  joins, geography, freshness, promotions, UTF-8/accents, malformed rows,
  deterministic repeats, leases, CAS races, canary isolation, rollback, and
  Windows paths.

## Safety boundary

- Android remains offline: no `INTERNET` or `ACCESS_NETWORK_STATE` permission was
  added, and no provider artifact or raw ZIP is an Android asset.
- Product identity, offers, availability, freshness, promotion eligibility,
  and source rights remain separate facts. SEPA publication never becomes
  `IN_STOCK`; unknown remains unknown.
- `control/active.json` is never written by acquisition, worker, staging, or
  canary preparation. Production active pointers and regional deployments were
  not changed.
- Raw provider data and full national normalized output are local/operator-side
  only. No raw ZIP, national products file, accepted-observation output, or
  quarantine output is committed.

## Verification record

The final local checks for this candidate are green. The source-wide Tuesday
verification is intentionally recorded as `NOT_REMEASURED` rather than
repeated.

- Full Python tools suite: **267 tests, 0 failures/errors, PASS**.
- Backend suite: **80 tests, 0 failures/errors, PASS**.
- Android: **405 shared-core tests** and **1,619 app tests**, all passing;
  `lintDebug` and `assembleDebug` succeeded (58 actionable tasks).
- APK privacy: **PASS**; only CAMERA and the app's non-exported receiver
  permission are present; INTERNET and ACCESS_NETWORK_STATE are absent.
- APK signature: **PASS**, one signer (Android Debug certificate; v1/v2
  verification).
- Browser: **30 tests, 0 failures/skips**, local model check and Firefox
  `web-ext lint` **PASS**.
- Changed Python files: `py_compile` **PASS**; `git diff --check` **PASS**.
- Exact-head GitHub Actions: the required merchant, backend/container, Android,
  browser, and release-bundle workflows are configured to run for this report
  commit. Their final exact-SHA run IDs are recorded in the closeout response;
  no production promotion occurs from this candidate.

## Explicit blocker and next safe action

`AUTOMATED RELEASE PIPELINE: BLOCKED` is an access/evidence status, not a code
failure. The safe next action is to run the same worker in shadow mode only when
an independently accessible, genuinely newer official SEPA ZIP is supplied or
its official resource becomes readable. Then repeat archive reuse, stage,
verification, canary, human review, and only afterward consider transactional
activation. Until that happens, the Tuesday baseline and all production
rollback protections remain untouched.
