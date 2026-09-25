# Practical Shopping Saved Android Shell Checkpoint

## Verified boundary

- Verified code commit: `43f8ea94d7a5a73051a9c6b0ebaceb689dc49f00` (`Wire saved surface into Android shell`)
- Workflow: `Build ValuePilot v101 release`
- Workflow run: `159` / `33277368938`
- Job: `99166343610`
- Result: full workflow success, including browser tests/package, Android tests/lint/APK, JVM summary, Android privacy boundary, release/checksums, verified release upload, and cleanup.

## What is now active

The Android Saved primary tab now hosts the previously verified `PracticalShoppingSavedSurfaceView` and drives it through the verified Saved lifecycle/session stack.

`MainActivity` remains a sequencing owner only:

- it owns Saved route visibility;
- it wires the physical surface to `PracticalShoppingSavedSurfacePresenter`;
- it forwards typed surface actions through a dedicated route coordinator;
- it does not read or write Saved files directly;
- it does not resolve product/store identity, infer display labels, rank offers, authorize price evidence, or perform provider/network work.

The physical Saved renderer still never changes its own route visibility. A late asynchronous Saved completion therefore cannot make the Saved view reappear after the user navigates to another primary tab.

## Lazy route/session ownership

`PracticalShoppingSavedRouteCoordinator` is a pure route/action boundary.

- No Saved Android session is created before the Saved route is actually entered.
- The first Saved-route entry lazily creates the session and requests an authoritative refresh.
- Repeated shell renders while Saved remains visible do not create duplicate route-entry refreshes.
- Leaving and later re-entering Saved requests another authoritative refresh using the same Activity-owned session.
- A preference/destructive surface action cannot manufacture a Saved session before a real route entry.
- Surface actions are ignored while Saved is hidden or after the route coordinator is closed.

Focused JVM tests cover lazy creation, same-route deduplication, re-entry refresh, hidden-route action suppression, exact typed-action forwarding, and idempotent close behavior.

## Recreation and persistence ordering remain intact

`PracticalShoppingSavedAndroidSession` now implements the narrow `PracticalShoppingSavedRouteSession` contract without changing its persistence semantics.

The previously verified process-scoped Saved runtime remains authoritative for execution ordering:

- exact-preference and display-metadata stores remain app-internal;
- all Saved coordinator/storage work in the process remains serialized on one Saved worker;
- an older Activity/session's queued mutation is ordered before a recreated Activity/session's later load;
- closing an Activity closes only that Activity's lifecycle host, not the process runtime;
- a closed host ignores late completions from already queued/running work.

This checkpoint does not claim cross-process locking and does not introduce another Android process.

## Activity destruction

`MainActivity.onDestroy()` now:

- detaches the physical Saved surface action callback;
- closes the Activity-owned Saved route coordinator/session host;
- leaves the process-scoped Saved persistence runtime alive so queued/running atomic work can finish safely.

No Handler-wide Saved callback removal or process-worker shutdown was introduced.

## Consumer copy and authority boundary

The obsolete Saved placeholder copy was replaced with active, bounded copy describing only exact local choices.

The screen explicitly states that Saved choices are local preferences and do **not** confirm current price, stock, promotions, availability, or travel.

The existing presentation rules remain unchanged:

- consumer labels come only through verified Saved display-metadata boundaries;
- unresolved or unsafe technical labels fail closed;
- raw GTIN/SKU/provider/dataset/merchant/location/channel/stable-key strings are never display fallbacks;
- Saved identity does not grant price, availability, currentness, geography, rights, lifecycle, or travel authority.

## Unchanged boundaries

This slice does **not** add or change:

- `INTERNET` permission;
- `ACCESS_NETWORK_STATE` permission;
- provider networking;
- retailer credentials;
- accounts;
- telemetry;
- remote AI;
- ValuePilot backend/server access;
- ranking or organic ordering;
- Home/Search production-provider activation;
- price or availability authority;
- travel resolution;
- shared-core platform neutrality.

Home/Search sample behavior and existing provider-neutral Practical Shopping architecture remain unchanged.

## Next safe boundary

Before expanding Saved functionality further, inspect the user-visible paths that can create confirmed exact preferences and their bound display metadata. The next implementation slice should be chosen from repository evidence rather than assuming that an active Saved viewer alone completes the end-to-end save workflow.
