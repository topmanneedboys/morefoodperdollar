# ValuePilot automated official Argentina release pipeline

Status: **AUTOMATED RELEASE PIPELINE: BLOCKED FOR ZERO-TOUCH DEPLOYMENT**

## 2026-09-14 superseding geo-egress addendum

The original checkpoint below recorded official production ZIP access as HTTP
403 and therefore described source access itself as the blocker. Later bounded
real-network qualification refined that conclusion:

- the exact ValuePilot acquisition path still returns HTTP 403 on the baseline
  non-Argentina route;
- an operator-selected Argentina VPN route successfully downloaded and verified
  the official `datos.produccion.gob.ar` ZIP;
- measured successful download: **330,069,965 bytes**;
- SHA-256:
  `5c8912521d6fe2d64e2f096b962acd47c18c247cce7787576ae04880d028b962`;
- the ZIP was verified as `OFFICIAL_SEPA_ZIP` and internally proved source date
  **2026-09-07**;
- comparison result:
  `CANDIDATE_ROUTE_SUCCEEDS_WHERE_BASELINE_403`;
- no archive, build, canary, active pointer, Android, or production state was
  changed by that qualification.

Therefore **official source access is no longer the unresolved blocker in the
abstract**. The remaining production blocker is establishing an approved,
unattended Argentina-egress acquisition runner and deploying the orchestration
around it. Scheduler and workflow templates remain non-production until that
external egress is configured and shadow-qualified.

That real run also exposed stale CKAN weekday hints. The newer candidate work
therefore prefers the Argentina-local weekday endpoint and keeps ZIP-internal
release date/hash authoritative. A bounded acquisition-handoff path now exists:
Argentina-egress acquisition -> exact source verification -> active-source
comparison -> content-addressed private archive -> verified archive restore ->
existing release worker. Metadata-only `NO_CHANGE` cannot suppress an explicit
verified archive handoff. `SHADOW` remains the safe default and the trusted
active Tuesday release is unchanged.

---

The historical checkpoint below is retained for provenance. Statements that
say source access itself is unresolved describe the earlier checkpoint and are
superseded by the addendum above.

This checkpoint implements and verifies the offline/backend-side control plane
for one official Argentina SEPA release. It does not claim a live production
activation. At that checkpoint, the official production resource/download
probes returned HTTP 403, so a newer official source had not been independently
acquired or proven.

## Checkpoint identity

- Starting head: `6ed1cab8300d1ed0ed12fef240194fa7edbb5901`
- Candidate branch: `candidate/argentina-automated-release-v1`
- Implementation head: `8ca30d52a959865c39236dc0a67d2ba2fb92cd7b`
- Scope: offline acquisition, archive, worker, immutable base-release and
  routing/SearchPack candidate derivation, control-plane, staging, canary, and
  explicit operator-controlled promotion adapter tooling only.

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
dataset/resource probes returned HTTP 403. At that checkpoint, the workflow
therefore remained `SHADOW_ONLY_WAITING_FOR_ACCESSIBLE_NEWER_OFFICIAL_SOURCE`;
no guessed mirror, scrape, WAF bypass, or substituted ZIP was allowed.

## What is implemented

- Strict official-host HTTPS acquisition with bounded streaming, size/hash/date
  checks, retry/redirect rules, and explicit source-change classification.
- Content-addressed private source archives with immutable manifests, safe
  restore, duplicate reuse, and no deletion or Git staging of raw data.
- A bounded worker that records discovery fingerprints with immutable/CAS state,
  takes a distributed GCS-generation lease, writes redacted immutable execution
  summaries, publishes a complete immutable base release, derives and verifies
  the existing routing/SearchPack workspace across all 24 regions, and never
  activates a production pointer.
- Reuse of the existing deterministic daily-release/SEPA parser and provider
  boundary; no SEPA parsing or retailer logic moved into shared core.
- Multi-store staging and verification for `us`, `sa`, and `r2`, a fixed
  `control/canary.json` path, and a transactional active-pointer coordinator
  with compare-and-swap, smoke gates, compensation, and rollback review.
  Promotion is explicit and fail-closed: `SHADOW` is write-free,
  `STAGE_CANARY` writes only canary state, and `AUTO_ACTIVATE` requires all
  configured stores plus bounded HTTPS smoke checks. A Windows local
  object-store containment race is fixed and covered by concurrent-write
  regression tests.
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

The final local checks for the historical candidate were green. The source-wide
Tuesday verification was intentionally recorded as `NOT_REMEASURED` rather
than repeated.

- Full Python tools suite: **285 tests, 0 failures/errors, PASS**.
- Backend suite: **81 tests, 0 failures/errors, PASS**.
- Android: **405 shared-core tests** and **1,619 app tests**, all passing;
  `lintDebug` and `assembleDebug` succeeded (58 actionable tasks).
- APK privacy: **PASS**; only CAMERA and the app's non-exported receiver
  permission are present; INTERNET and ACCESS_NETWORK_STATE are absent.
- APK signature: **PASS**, one signer (Android Debug certificate; v1/v2
  verification).
- Browser: **30 tests, 0 failures/skips**, local model check and Firefox
  `web-ext lint` **PASS**.
- Changed Python files: `py_compile` **PASS**; `git diff --check` **PASS**.
- Exact-head GitHub Actions: merchant qualification **34790103706**, Argentina
  backend **34790103703**, and Android/browser/release **34790103697** all passed
  for implementation head `8ca30d52a959865c39236dc0a67d2ba2fb92cd7b`. No
  production promotion occurs from this candidate.

## Explicit blocker and next safe action

The current safe next action is no longer another manual source probe. It is to
configure one unattended, approved Argentina-egress acquisition runner with
least privilege, run the new handoff path in `SHADOW`, and prove that it can:
read the trusted active source identity, acquire/verify the expected weekday
ZIP, stop safely for no-change/older data, archive only a genuinely newer
source, and hand that exact archive identity to the existing Google
Workflow/Cloud Build worker. Only after that end-to-end shadow run is green
should the permanent schedule or `AUTO_ACTIVATE` be considered. Until then, the
trusted Tuesday baseline and all production rollback protections remain
untouched.
