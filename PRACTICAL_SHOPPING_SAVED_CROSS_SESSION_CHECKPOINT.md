# PRACTICAL SHOPPING SAVED CROSS-SESSION SERIALIZATION CHECKPOINT

## Verified boundary

Latest CI-verified code:

`f03b849f87bd3e29dfc16d8916000e993c74478b` — `Serialize saved work across Android sessions`

Workflow:
- run number: 157
- run id: `33276385339`
- job id: `99163733664`
- conclusion: success

The complete normal repository gate passed, including browser checks/package, shared-core/app tests, Android lint/APK, JVM summary, Android privacy verification, release/checksums, artifact upload, and cleanup.

## Verified recreation-safety boundary

Saved UI sessions in one Android app process now share:
- one app-internal exact-preference store instance;
- one app-internal display-metadata store instance;
- one serial Saved persistence/coordinator executor.

A surface/session close closes only that session's lifecycle host. It does not interrupt or shut down process-owned Saved work already queued/running.

The focused regression proves this ordering:
1. an older Saved UI session queues a destructive exact mutation;
2. that old session closes before the worker runs it;
3. a recreated/new session queues its initial load on the same worker;
4. the older mutation executes first;
5. the new session load executes second;
6. the old host ignores its eventual late completion and therefore cannot enqueue a stale post-mutation reload;
7. the new session receives authoritative state produced after the older mutation.

Only application-scope storage state is retained. No Activity/View is held by the process runtime.

This closes the same-process Activity recreation overlap that existed with a separate executor/store pair per Saved session. It does not claim cross-process database/file locking, and no second process is introduced here.

## Boundaries unchanged

- Exact saved preference identity remains authoritative separately from display metadata.
- No raw technical identifier may become a normal consumer label.
- No price, availability, currentness, travel, provider-rights, or ranking authority is granted by Saved state.
- No networking, INTERNET permission, account, telemetry, remote AI, or provider activation was added.
- MainActivity/layout remain unchanged; Saved is not physically wired yet.

## Next safe slice

Add a thin replaceable Saved renderer/presenter without MainActivity wiring:
- presenter adapts lifecycle state through the already verified pure Saved surface projector;
- physical view consumes only immutable `PracticalShoppingSavedSurfaceState`;
- view emits only typed `PracticalShoppingSavedSurfaceAction`;
- route visibility remains owned by the app shell, never by async render callbacks;
- busy states expose no destructive actions;
- renderer has no persistence, identity, provider, price, network, or ranking logic;
- compile/lint/APK and focused presenter tests must pass before MainActivity/layout integration.
