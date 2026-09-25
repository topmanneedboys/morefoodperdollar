# Practical Shopping Saved Display Metadata Transaction Checkpoint

Status: verified code boundary

Latest verified code commit: `a7d0723110c2e3d2fd5efe96a8439d1fe7585dde`
Verified workflow: run 150 / `33273987328`

## What is now verified

Identity-bound Saved display metadata can be upserted transactionally within one display-metadata store instance.

A new product/store display entry is admitted only when the already verified display-metadata binder confirms that its exact product key or exact store scope is current in the supplied validated saved-exact-preference state. A label for a different exact choice is rejected before display storage is read.

The full `load -> category-specific stale prune -> upsert -> AtomicFile replace` sequence is synchronized on the same display-store instance, closing the lost-update window for callers sharing that store.

Product upserts prune only stale/orphan product metadata and preserve all store metadata. Store upserts prune only stale/orphan store metadata and preserve all product metadata. This prevents partial caller state from deleting unrelated presentation metadata.

Failed display writes preserve the prior generation. Corrupt display metadata blocks transactional upsert rather than being partially repaired. Concurrent product-label saves on one store instance preserve both stable keys.

There is intentionally no cross-file transaction with authoritative saved exact preferences. If exact identity changes concurrently, a persisted label can only become stale; the verified binder withholds it on the next read.

## Verification

Workflow 150 completed successfully through browser tests/package, Android JVM tests, lint/APK, JVM summary, Android privacy verification, release/checksum assembly, artifact upload, and post-job cleanup.

## Next safe slice

Add a composite Saved experience coordinator that loads authoritative exact preferences first, treats exact-storage failure as fatal, treats display-metadata failure as presentation degradation, always binds before projection, and orders delete/clear actions exact-first. A display cleanup failure after a successful exact deletion must be reported but must never roll back or resurrect the exact choice. Keep this coordinator free of Android UI/thread ownership; the eventual lifecycle controller must execute its file-I/O methods off the main thread.
