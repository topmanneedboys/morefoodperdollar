# Practical Shopping Remember Confirmed Choice Checkpoint

## Verified boundary

- Verified code commit: `c3f6cdc70d1c9b3a36b27f24baf3ef87be0128c3` (`Coordinate remembering confirmed saved choices`)
- Workflow: `Build ValuePilot v101 release`
- Workflow run: `160` / `33277776326`
- Job: `99167433103`
- Result: full workflow success, including browser tests/package, Android tests/lint/APK, JVM summary, Android privacy boundary, release/checksums, verified release upload, and cleanup.

## What this boundary adds

`PracticalShoppingRememberConfirmedChoiceCoordinator` is the application-level boundary for persisting an exact choice only after another verified boundary has already established `USER_CONFIRMED_EXACT_PRODUCT` or `USER_CONFIRMED_EXACT_STORE`.

It does not confirm identity itself and does not turn suggestions into exact identity.

Supported composition paths are:

- confirmed product + separately supplied user-facing label;
- confirmed Open Food Facts product + source-bound product name;
- confirmed store + separately supplied user-facing label;
- confirmed OpenStreetMap store + source-bound place name.

## Exact choice remains authoritative

The exact-preference transaction always runs first and independently re-checks the candidate relationship.

Therefore:

- a one-time `EXACT_PRODUCT_REQUEST` barcode candidate is not directly saveable;
- an Open Food Facts catalog suggestion is not directly saveable;
- an OpenStreetMap location suggestion is not directly saveable;
- callers cannot bypass the explicit confirmation adapters merely by calling the remember coordinator.

If exact preference persistence fails, no display-label admission or display-store transaction is attempted.

## Display metadata remains secondary

Only after exact persistence succeeds does the coordinator attempt to create and persist detached display metadata.

Display metadata remains a separate non-authoritative file. There is deliberately no claimed cross-file atomic transaction.

If the label is unavailable, unsafe, source-mismatched, or its display-store write fails:

- the exact user-confirmed preference remains saved;
- it is not rolled back;
- no technical identifier becomes a display fallback;
- the existing Saved binder/projector loads the exact choice as unresolved until safe matching display metadata is available.

This matches the existing Saved deletion/load semantics in which exact preferences define whether a Saved choice exists and display metadata only affects presentation.

## Source-bound labels

Open Food Facts names are admitted only after the existing Open Food Facts identity adapter is re-run and the source candidate resolves to the same confirmed production product key and provenance.

OpenStreetMap place names are admitted only after the existing OSM store suggestion adapter is re-run and the source candidate matches the same confirmed full merchant/location/channel scope and provenance.

Source names remain presentation metadata only. They do not grant product/store identity, price, availability, currentness, geography, rights, travel, promotion, stock, or ranking authority.

## Stable-key replacement safety

If the same logical `ShoppingItemKey` or `ShoppingStoreKey` is later re-confirmed to a different exact identity/scope:

- exact preference persistence replaces the prior exact choice deterministically;
- the display transaction prunes stale same-category metadata when writing the new matching entry;
- independently, the Saved binder still refuses to attach stale detached metadata to the changed exact identity on read.

Thus an old consumer label cannot silently follow a stable logical key to a different exact product or store.

## Verified regression coverage

The coordinator tests exercise the complete existing Saved load path after writes, including:

- confirmed product + user label produces a visible Saved product row;
- confirmed store + user label produces a visible Saved store row;
- one-time barcode request is rejected before display I/O;
- exact write failure prevents display I/O;
- invalid/blank label preserves the exact choice and loads it unresolved;
- display write failure preserves the exact choice and loads it unresolved;
- matching Open Food Facts confirmed identity can supply its source-bound product name;
- matching OpenStreetMap confirmed exact store scope can supply its source-bound place name;
- re-confirming a stable product key to another GTIN cannot retain the previous product label.

## Unchanged product boundaries

This slice adds no visible Save/Remember button and does not make fictional Home/Search sample rows production-saveable.

It does not add or change:

- `INTERNET` permission;
- `ACCESS_NETWORK_STATE` permission;
- provider networking;
- retailer credentials;
- accounts;
- telemetry;
- remote AI;
- ValuePilot backend/server access;
- price/availability authority;
- travel authority;
- ranking or organic ordering;
- Home/Search sample behavior;
- shared-core platform neutrality.

## Next safe boundary

Before wiring a user-facing Remember affordance, inspect the real user-visible exact-confirmation path. A visible Save action must be attached only to an already user-confirmed production identity/scope and must not promote fictional/sample Search or Home rows into production Saved identity.
