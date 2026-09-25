# ValuePilot Argentina production storage retention V1

Status: **qualified for offline retention tooling only**.

This milestone adds bounded, operator-controlled retention and garbage
collection over the already verified M9 content-addressed release workspace.
It does not ingest a new national ZIP, change Android, add networking, or
turn historical metadata into price history.

## Verified boundary

- Starting repository SHA: `a887983b8825fd3790eafe4750ca6a043b2c5862`.
- Candidate implementation SHA: `08a65cbf22677a6267c6e30eb91155279e8da5f9`.
- M9 Sunday (`2026-09-06`) and Tuesday (`2026-09-08`, `sepa_martes.zip`)
  source hashes, complete manifests, object references, rollback evidence, and
  the 128 logical / 32 physical mobile-safe design were reused.
- No source-wide national verification was rerun. The Tuesday ZIP is locally
  available and already represented by the verified M9 release root; M10 only
  reads those built roots.

## Retention behavior

The default policy keeps seven complete verified releases, allows a bounded
configuration from two through fourteen, protects active/previous/
last-known-good and operator-pinned releases, and uses a configurable 24-hour
grace period before a release can become `GC_ELIGIBLE`. The two-release safety
floor is enforced. GC is dry-run by default and `--apply` is required for a
destructive sweep.

The lifecycle is explicit: `ACTIVE`, `ROLLBACK_PROTECTED`, `RETAINED`,
`GC_ELIGIBLE`, and `GC_COMPLETED`. A completed sweep leaves a signed-by-hash
metadata-only historical record with `dataPresent=false`; it does not claim
that old data is current price history. Lifecycle events are recorded in an
append-only hash-chained ledger.

## Real M9 dry run

The exact M9 workspace was evaluated at `2026-09-08T19:00:00Z` with retention
7, 24-hour grace, and an 8 GiB example budget. Both complete releases were
protected. All 1,793 referenced objects were reachable, zero objects were
eligible, and zero bytes would be deleted. Storage was 964,334,661 bytes before
and after the no-op plan (964,001,232 content bytes plus manifests/control).
The active pointer, object listing, and release metadata remained unchanged;
no `--apply` operation was performed.

## Deterministic safety simulation

A 30-day fixture simulation covered unique and shared objects, growing and
shrinking release sizes, an operator pin/unpin, a failed activation that left
the active pointer unchanged, rollback, partial-delete recovery, and a budget
pressure fail-closed result. The maximum full-release count was nine (the
seven-release window plus explicit grace/pin protection), and the simulation
reported the bounded result deterministically.

## Boundaries preserved

- No raw national SEPA ZIP or expanded national data is committed to Git or
  shipped to Android.
- No Android `INTERNET` or `ACCESS_NETWORK_STATE` permission was added.
- No provider economics, availability, promotions, or stale records are
  elevated into organic ranking by this milestone.
- Cloud storage is design-ready only; Cloudflare R2 Standard is the first
  candidate for a separately authorized deployment milestone. No cloud was
  deployed and no cost/traffic claim was invented.

The machine-readable evidence is in
`ARGENTINA_PRODUCTION_STORAGE_RETENTION_QUALIFICATION.json`. Candidate
exact-head CI passed with runs `34301505678`, `34301505671`, and `34301505673`.
The promoted branch SHA is recorded in the final handoff after promotion.
