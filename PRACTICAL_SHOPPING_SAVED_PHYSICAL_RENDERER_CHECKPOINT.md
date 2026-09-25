# PRACTICAL SHOPPING SAVED PHYSICAL RENDERER CHECKPOINT

## Verified boundary

Latest CI-verified code:

`527e6e2a105ea43e1b2c489f9a17a0ca968ddc7c` — `Render saved surface from immutable state`

Workflow:
- run number: 158
- run id: `33276699040`
- job id: `99164563238`
- conclusion: success

Full repository CI passed, including browser checks/package, shared-core/app tests, Android lint/APK, JVM summary, Android privacy verification, release/checksums, artifact upload, and cleanup.

## Verified renderer architecture

The Saved experience now has a replaceable physical renderer boundary:
- `PracticalShoppingSavedSurfacePresenter` adapts lifecycle state only through the verified pure surface projector;
- `PracticalShoppingSavedSurfaceView` consumes only immutable `PracticalShoppingSavedSurfaceState`;
- the View emits only typed `PracticalShoppingSavedSurfaceAction` values;
- all section/action labels are already present in immutable surface state, so the View does not invent lifecycle semantics or consumer copy;
- busy refresh/update modes can retain labels for continuity while all destructive actions remain absent;
- internal stable/provider/source/merchant/location identifiers are never rendered as fallback labels;
- the View has no persistence, identity resolution, provider, price, travel, ranking, clock, or network access;
- the View starts GONE and `render()` deliberately never changes its route visibility, preventing a late async completion from resurrecting Saved on another tab.

Focused JVM tests verify presenter pass-through, UI-ready labels, action-label pairing, and busy-state action suppression. Android compile/lint/APK verifies the real View implementation.

## Current shell state

MainActivity and `activity_shell.xml` are still not wired to this renderer. The existing Saved tab still shows placeholder shell copy and does not create a Saved Android session.

## Next safe slice

Wire the already verified pieces into the Saved route only:
1. add `PracticalShoppingSavedSurfaceView` to `activity_shell.xml` as GONE by default;
2. MainActivity keeps a nullable/lazy `PracticalShoppingSavedAndroidSession`;
3. create it only upon first actual Saved-route entry, using `PracticalShoppingSavedSurfacePresenter(savedView)`;
4. map `Refresh` to `session.refresh()` and `Preference` to `session.selectAction(action)`;
5. refresh on each actual transition into Saved, while the lifecycle reducer suppresses duplicate in-flight work;
6. shell routing alone controls Saved view visibility;
7. close the per-Activity session host in `onDestroy()`; do not stop the process-scoped serial persistence runtime;
8. update Saved shell copy so it describes only confirmed exact product/store choices and explicitly does not imply live price/stock/travel authority;
9. make no networking/permission/Home/Search/provider changes;
10. run full CI before calling the visible Saved route verified.
