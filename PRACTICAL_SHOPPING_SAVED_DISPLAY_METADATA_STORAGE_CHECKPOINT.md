# Practical Shopping Saved Display Metadata Storage Checkpoint

Status: verified code boundary

Latest verified code commit: `3cbbacff1f94de16f62a0d2e9c0d8df9dd52d7c5`
Verified workflow: run 149 / `33273639988`

## What is now verified

Identity-bound Saved display metadata has a separate app-internal AtomicFile persistence boundary.

The display metadata file is independent from the authoritative saved-exact-preference file. It stores only the verified display-metadata codec document and grants no product/store/offer authority.

Reads are streamed with a hard byte bound before codec parsing. Missing, I/O-failed, oversized, and invalid/corrupt storage states remain distinct.

Atomic replacement preserves the prior generation if a write fails. Public store operations are synchronized on one store instance because AtomicFile provides replacement atomicity but not concurrency locking.

Selective display-metadata deletion is idempotent for absent keys and fails closed when the stored display document is corrupt. `clearAll()` can delete corrupt display metadata as a recovery path because it cannot delete or change the separately persisted exact saved preferences.

A stored label whose exact product/store binding no longer matches current saved exact state remains harmless: the verified binder withholds it before presentation.

There is intentionally no cross-file atomicity claim. An interrupted workflow may leave missing or orphan display metadata, but that can only produce unresolved/withheld presentation; it cannot create, change, or relabel an exact saved preference.

## Verification

Workflow 149 completed successfully through browser tests/package, Android JVM tests, lint/APK, JVM summary, Android privacy verification, release/checksum assembly, artifact upload, and post-job cleanup.

## Next safe slice

Add a transactional display-metadata upsert boundary. Admit a product/store display entry only when the verified binder confirms its exact binding is current in the supplied saved-exact state. Before upsert, prune stale/orphan metadata using binder results so detached entries cannot consume bounded metadata capacity. Synchronize `load -> prune -> upsert -> replace` on the display store instance. Keep exact preference persistence primary and do not claim a cross-file transaction.
