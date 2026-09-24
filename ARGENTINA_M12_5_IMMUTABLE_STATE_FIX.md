# Argentina M12.5 immutable acquisition-state fix

Status: `CODE_READY_FOR_REVIEW`

This checkpoint addresses the verified September 23 `CONTROL_STORE_CONFLICT`
failures without changing IAM, starting a national build, or writing an active
or canary pointer.

## Verified failure

The scheduled run `35911475904` successfully acquired and durably archived the
September 23 official source, then failed while recording the next daily state.
Run `35931479929` failed earlier at same-day deduplication. The archive daily
state object already existed at
`acquisition/daily-state/2026-09-23.json` with generation
`1790192790919843` and status `METADATA_DISCOVERED`.

The archive bucket grants the GitHub OIDC identity `roles/storage.objectCreator`
and `roles/storage.objectViewer`. Object Creator can create an object but cannot
replace one; the old implementation attempted conditional replacement of this
same daily-state object and converted the permanent permission failure into the
misleading `same-day acquisition state CAS did not converge` error.

The September 22 execution `12d9c499-1508-44c3-81e4-e399e60cb3b8` is a separate
observed HTTP 412 after the multi-hour SHADOW build. The deployed Cloud
Workflow revision `000011-094` already rereads and reconciles that conflict
only when every candidate/source/metadata/promotion identity matches; otherwise
it returns `STATE_WRITE_CONFLICT` with zero pointer writes. The new code does
not weaken that boundary.

## Fix

Daily acquisition state now uses a bounded, append-only immutable journal:

* state events are content-addressed below
  `acquisition/daily-state-events/<argentina-date>/`;
* each event is written with the existing immutable create-only storage
  primitive, so concurrent writers never overwrite evidence;
* reads accept the existing v1 daily-state object and merge it with v2 events;
* the journal is capped at 128 immediate objects per day and rejects nested,
  malformed, tampered, conflicting, or over-limit records;
* status ordering remains monotonic, exact candidate/reservation/workflow
  identities remain fail-closed, and late-check source revisions remain
  distinguishable by their immutable observation key.

The shared object-store boundary gained a bounded immediate-prefix listing for
local, S3-compatible, GCS, and read-only adapters. No Android code, networking
permission, active pointer, IAM binding, raw provider archive, or national
dataset changed.

## Verification

The focused acquisition and workflow regressions pass, including:

* the full source-ready → build reservation → duplicate retry sequence on a
  store that rejects every conditional replacement;
* legacy v1 state read and migration to an immutable v2 event;
* deterministic same-slot source-identity conflict rejection;
* journal overflow and event hash/key validation;
* the September 22 HTTP 412 reconciliation contract and fail-closed mismatch;
* late recheck and SHADOW success monotonicity.

The complete checked-in tooling test discovery passes: 85 tests, 0 failures.
No production acquisition or Cloud Workflow execution was triggered by this
change. The active and last-known-good release remains the September 18
release, and observed active-pointer writes remain zero.

## Required next gate

Review and merge this code to `main`, then let one normal scheduled OIDC run
exercise the create-only archive path. Qualify M12.5 only from that real run's
receipt, event-journal state, candidate/SHADOW result, and zero-pointer-write
evidence. Do not grant overwrite/delete permissions to the archive bucket.
