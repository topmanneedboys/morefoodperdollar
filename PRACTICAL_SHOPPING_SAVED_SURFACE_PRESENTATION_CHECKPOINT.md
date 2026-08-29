# PRACTICAL SHOPPING SAVED SURFACE PRESENTATION CHECKPOINT

## Verified boundary

Latest CI-verified code commit:

`7d7963cbfc1f54691a02b0723aaa4d67b2ca98da` — `Project saved lifecycle for physical surface`

Workflow:
- run number: 156
- run id: `33276077293`
- job id: `99162927864`
- conclusion: success

The complete repository workflow passed: browser checks/package, shared-core/app tests, Android lint/APK, JVM summary, Android privacy verification, release/checksums, artifact upload, and cleanup.

## Verified surface boundary

`PracticalShoppingSavedSurfaceProjector` is a pure, Android-free mapping from verified Saved lifecycle state into immutable renderer-ready state.

It defines explicit modes for idle, initial loading, refreshing retained content, content, empty, degraded, updating, and error states.

Important invariants:
- normal labels come only from the already verified Saved UI projection;
- raw stable/product/store/provider/source/merchant/location identifiers are never reconstructed as display strings;
- technical keys remain inside typed actions only;
- initial loading exposes no destructive actions;
- refresh may retain current labels for continuity but suppresses all mutation/clear actions while work is active;
- mutation may retain current labels but suppresses all actions until the mandatory authoritative reload completes;
- errors expose retry only and do not render stale destructive content;
- degraded display metadata remains separate from exact saved-choice authority;
- empty state never exposes clear-all;
- no persistence, identity resolution, network, clock, provider, price, travel, ranking, or Android View logic lives in this projector.

Focused tests cover all lifecycle modes, action suppression while busy, retry/error copy, degradation behavior, empty/content behavior, and the requirement that internal stable keys never become visible strings.

## Current physical UI state

MainActivity and `activity_shell.xml` are still unchanged. The Saved tab is still placeholder shell copy and is not yet wired to persistence or a physical Saved renderer.

No network permission, provider networking, Home/Search production activation, account, telemetry, or remote AI change was introduced.

## Recreation-safety issue discovered before visible wiring

The currently verified Android Saved session at `da4fee2ac8db97f2fcb3c18fe5100d196037dda5` owns a separate executor/store pair per UI session. Because close deliberately permits an already-running AtomicFile operation to finish, Activity recreation could create a new session before an old session's persistence work has completed. Store synchronization is per instance, so visible Saved wiring should not proceed until same-process cross-session work is serialized.

Next safe slice:
1. use one process-scoped Saved serial worker plus shared exact/display store instances for Saved UI sessions;
2. close only the per-surface host, not the process worker;
3. prove an older queued mutation executes before a recreated session's initial load;
4. keep old-host late completions ignored;
5. retain application context only;
6. do not claim cross-process locking;
7. run the full repository workflow before physical View/MainActivity integration.

Only after that boundary is verified should a thin Saved SurfaceView/presenter be introduced, followed by a separate MainActivity/layout wiring slice.
