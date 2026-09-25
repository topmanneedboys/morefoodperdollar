# PRACTICAL SHOPPING SAVED ANDROID EXECUTION CHECKPOINT

## Verified boundary

Latest CI-verified code commit:

`da4fee2ac8db97f2fcb3c18fe5100d196037dda5` — `Own saved persistence execution on Android`

Workflow:

- Run number: 155
- Run id: `33275346318`
- Job id: `99160958866`
- Conclusion: success

The full normal repository gate passed, including browser checks/package, shared-core/app tests, Android lint/APK, JVM summary, Android privacy verification, release/checksums, artifact upload, and post-job cleanup.

## What is now verified

The Saved experience has the following verified layering:

1. Exact saved-preference domain and lifecycle/state management.
2. Exact saved-preference deterministic codec and app-internal AtomicFile persistence.
3. Explicit-confirmation-to-persistence transactions.
4. Separate identity-bound Saved display-metadata boundary, codec, and local persistence.
5. Binder/projector that never treats display metadata as exact product/store authority.
6. Composite Saved experience coordinator where exact storage is authoritative and display metadata is degradable.
7. Pure immutable Saved lifecycle reducer with request ids, stale-completion rejection, typed work, retry/error/degraded states, and mandatory authoritative reload after successful mutation.
8. JVM-testable lifecycle execution host that keeps persistence work off its owner thread and delivers typed completions through an injected dispatcher.
9. Concrete Android Saved session that supplies the real background executor, main-Looper dispatch, app-internal exact/display stores, coordinator gateway, and verified host.

## Android execution-owner invariants

`PracticalShoppingSavedAndroidSession` is an Android adapter only. It does not own Saved business rules, product/store matching, price authority, ranking, networking, labels, or persistence policy.

The session:

- requires lifecycle-owner calls on the Android main thread;
- uses one single-thread worker for Saved persistence/coordinator work;
- posts typed completions to the main Looper;
- constructs the already-verified exact and display metadata stores;
- delegates lifecycle sequencing to the verified Saved host/controller;
- uses graceful executor `shutdown()` on close rather than interrupting a possibly active AtomicFile replacement;
- relies on the host's close semantics to ignore late completions;
- does not remove unrelated main-Looper callbacks;
- adds no network permission, account, telemetry, remote AI, or provider networking;
- is not yet wired into MainActivity or a physical Saved renderer.

## Failure caught before this boundary

The first lifecycle workflow exposed an invalid intermediate `MUTATING` state while transitioning from a successful mutation into the mandatory reload. The lifecycle invariants and unchanged regression tests caught it. The implementation was repaired by transitioning directly into a valid `LOADING` state while carrying display-cleanup degradation explicitly. The repair then passed the full workflow.

This remains an important architectural constraint: do not weaken lifecycle invariants or bypass the mandatory post-mutation authoritative reload.

## Authority boundaries remain unchanged

Saved exact preference identity remains separate from display metadata.

Human-facing labels do not grant:

- product identity authority;
- store identity authority;
- price or availability authority;
- freshness/currentness authority;
- travel authority;
- provider rights or authorization;
- ranking authority.

Raw provider, GTIN/source, merchant, location, Wikidata, OSM, or commerce-channel identifiers must not be used as normal consumer labels merely because they are persisted technical identity.

Missing or stale display metadata must remain explicit/degraded rather than being guessed from internal identifiers.

## Current branch state at checkpoint creation

Verified code parent:

`da4fee2ac8db97f2fcb3c18fe5100d196037dda5`

This checkpoint commit is intended to be docs-only. If no workflow attaches to the docs commit, continue to distinguish branch head from the latest CI-verified code head.

## Next safe direction

The next slice may begin physical Saved presentation integration, but it should remain narrow and reversible.

Before modifying MainActivity or XML, inspect the existing app shell and immutable rendering patterns and define the smallest renderer/surface boundary needed to consume `PracticalShoppingSavedLifecycleState`.

Preferred sequencing:

1. Inspect current Saved route/shell, MainActivity, AppShell, and layout resources.
2. Reuse existing immutable UI-state / typed-action patterns.
3. Build a Saved surface renderer/presenter boundary before direct view logic if one is not already present.
4. Render only UI-ready Saved lifecycle/projection state; views must never parse persistence documents or resolve identities.
5. Route delete-one / clear-all actions back as typed `PracticalShoppingSavedExactPreferenceUiAction` values.
6. Treat loading, retry, error, and display-metadata-degraded states explicitly.
7. Do not expose technical identity keys as fallback labels.
8. Keep all Saved persistence/coordinator I/O off the main thread through the verified Android session.
9. Keep no networking or permission change in the first visible Saved slice.
10. Avoid bundling unrelated Home/Search/provider changes.

Do not claim the physical Saved UI is production-ready until its own focused tests and the full repository workflow pass.
