# Practical Shopping Saved Lifecycle Checkpoint

Status: verified code boundary

Latest verified code commit: `aa023a7fa1c1eb59ba075ed3121ff7b2ff3698b8`
Verified workflow: run 153 / `33274675547`

## What is now verified

The Saved experience now has a pure immutable lifecycle reducer above the verified persistence coordinator.

The lifecycle controller performs no Android, filesystem, executor, clock, network, retailer, or provider work. It emits typed `Load` and `Mutate` work with monotonically increasing request ids and accepts only matching typed completions.

Key guarantees:

- only one load/mutation may be active at a time;
- duplicate refreshes while work is active are ignored;
- stale load and mutation completions cannot replace newer state;
- delete actions must exist in the current projected rows before mutation can start;
- Clear All remains available when exact saved choices exist but labels are unresolved;
- fatal exact-preference load/mutation failures clear potentially stale projection state and enter an explicit retryable error state;
- non-authoritative display-metadata failures remain explicit degraded presentation state;
- a successful mutation never patches the old projection locally: it always emits a fresh authoritative load through the verified Saved experience coordinator;
- display-cleanup failure after an exact mutation remains degradation and cannot turn the already-successful exact mutation into failure.

## Workflow 152 failure and repair

Initial lifecycle commit `56b48a0db86cd579bd64f17b1a1db3e36d4087a7` failed workflow 152 in two unchanged lifecycle tests. The state invariants exposed an invalid intermediate `MUTATING` copy created while sequencing successful mutation -> authoritative reload.

Repair commit `aa023a7fa1c1eb59ba075ed3121ff7b2ff3698b8` removed that intermediate state and carries the display-cleanup degradation flag directly into the atomic `LOADING` transition. The repair changed only the lifecycle production file (5 additions / 11 deletions); tests were not weakened or modified.

Workflow 153 then completed successfully through browser tests/package, Android JVM tests, lint/APK, JVM summary, Android privacy verification, release/checksum assembly, artifact upload, and post-job cleanup.

## Next safe slice

Add a narrow Saved lifecycle host/execution boundary that executes emitted coordinator work away from the Android main thread and returns only typed lifecycle completion intents to the owner/UI thread. Keep the host testable by injecting work scheduling/completion dispatch rather than embedding business logic into MainActivity or a view. Do not add the physical Saved renderer or MainActivity wiring in the same slice.
