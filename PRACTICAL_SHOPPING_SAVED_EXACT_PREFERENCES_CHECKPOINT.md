# ValuePilot Practical Shopping Saved Exact Preferences Checkpoint

Updated: 2026-08-29

Branch: `work/valuepilot-android-milestone`

This checkpoint records the verified storage-neutral saved exact-preference boundary. Newer repository evidence overrides this file.

## Latest verified code head

`a1f07bfc806aa0720bcf0e7859a37c911bd0a4a9` — `Add saved Practical Shopping exact preferences`

GitHub Actions workflow run **141** (`33269406433`) completed successfully.

Verified gates all passed:

- browser model/engine/UI/Firefox checks
- browser extension packaging
- shared-core tests
- Android app JVM tests
- Android lint
- Android APK build
- JVM test summary
- Android privacy-boundary verification
- release files/checksums
- verified release artifact upload

The prior identity-confirmation trail remains:

- `c60dc756e7a78d1d99480e71a59b2719a080768b` — exact product confirmation; workflow 138 passed.
- `e3d86e736c1707552560b9d101653bd5cff0f1dc` — OpenStreetMap store suggestions; workflow 139 passed.
- `62235ee2414a881238828323beb9e352a1dc5400` — exact store confirmation; workflow 140 passed.

## What this slice adds

`PracticalShoppingSavedExactPreferenceAdapter` defines typed, storage-neutral records for remembering an exact product or store choice that the user already explicitly confirmed.

It does **not** implement a database, SharedPreferences, files, cloud sync, account storage, or Saved-screen UI.

The purpose of this slice is to freeze the identity/trust semantics before choosing any persistence technology.

## Product saved preference

`PracticalShoppingSavedExactProductPreference` retains only:

- stable `ShoppingItemKey`;
- original `EvidenceProviderId`;
- exact `SourceProductIdentity`;
- optional source-isolated dataset provenance.

A product preference may be created only from a candidate whose relationship is:

`USER_CONFIRMED_EXACT_PRODUCT`

The following are deliberately rejected as save sources:

- `CATALOG_SUGGESTION`;
- `SEMANTIC_SUGGESTION`;
- `UNKNOWN`;
- a one-time `EXACT_PRODUCT_REQUEST` barcode capture;
- any other candidate that was not separately user-confirmed.

A successful restore creates a new invocation-local candidate id and changes only the relationship to:

`SAVED_EXACT_PREFERENCE`

It preserves the same stable item key, provider, source identity and dataset provenance.

The existing `PracticalShoppingProductIdentityResolver` remains the sole layer that evaluates the restored candidate and resolves its production product key.

No product name, brand, description, image, price, package-size text or similarity score is stored or used to rematch a future request.

## Why one-time barcode intent is not automatically saved

`EXACT_PRODUCT_REQUEST` proves that the current request refers to that exact packaged product. It does not prove the user wants ValuePilot to remember that choice indefinitely.

Therefore the saved-preference adapter requires a separate `USER_CONFIRMED_EXACT_PRODUCT` relationship before saving.

This keeps one-time intent distinct from persistent preference and preserves explicit user control.

## Store saved preference

`PracticalShoppingSavedExactStorePreference` retains only:

- stable `ShoppingStoreKey`;
- already-confirmed exact merchant/location/channel scope;
- optional provider provenance;
- optional source-isolated dataset provenance.

A store preference may be created only from a candidate whose relationship is:

`USER_CONFIRMED_EXACT_STORE`

Raw OSM/source-location/name/geocoder suggestions cannot be saved as exact preferences merely because they contain a proposed scope.

A successful restore changes only the relationship to:

`SAVED_EXACT_STORE`

and preserves the exact same store key, merchant/location/channel scope and source provenance.

The existing `PracticalShoppingStoreIdentityResolver` remains the sole layer that evaluates the restored candidate.

## Candidate ids are deliberately ephemeral

Candidate ids are not part of either saved record.

They are invocation-local audit/assembly ids and a restored candidate receives a fresh caller-supplied candidate id.

Durable identity comes from the stable shopping key plus exact product/store identity and preserved provenance, not from recycling a prior candidate id.

## Stable-key rule

Saved preferences restore only to their original stable `ShoppingItemKey` / `ShoppingStoreKey`.

There is no text/name rematching step in this boundary.

This means a saved product for one stable item identity cannot silently migrate to a different shopping intent because words happen to look similar, and a saved store cannot be retargeted based on name/address similarity.

Any future intent-key migration must be explicit, versioned and separately tested.

## Saving is not source factual authority

A saved product preference establishes what exact product the user previously confirmed for that stable shopping item.

A saved store preference establishes what exact store scope the user previously confirmed for that stable store identity.

Neither establishes:

- current price;
- availability;
- package quantity;
- promotion validity;
- provider production rights;
- geography;
- snapshot lifecycle;
- namespace disposition;
- current offer freshness;
- travel distance/time;
- current route validity.

The lower production path still independently re-evaluates all current-price evidence and travel facts at the relevant decision instant.

A saved preference therefore cannot make an invalid/stale/unauthorized price claim rankable.

## Storage and UI remain intentionally unimplemented

No persistence technology was selected in this slice.

Before actual on-device persistence is introduced, define and regression-test at minimum:

1. an explicit schema version;
2. bounded maximum saved product/store records;
3. deterministic duplicate/update semantics;
4. migration behavior between schema versions;
5. corrupt/unknown-version fail-closed behavior;
6. explicit deletion / forget controls;
7. clear-all behavior;
8. source/dataset provenance preservation;
9. behavior if a dataset/provider namespace becomes withdrawn or deleted;
10. no background network dependency;
11. no text/fuzzy rematching during restoration;
12. no durable price/travel/authorization snapshot hidden inside a preference record.

The top-level `SAVED` route currently remains navigation state only. This production preference model has not been wired into a visible Saved feature, `MainActivity`, Home, legacy ranking, or sample controllers.

Any future Saved presentation should consume immutable UI state and emit typed actions. Views must not own identity resolution, storage policy, ranking or source-policy logic.

## Android privacy boundary remains unchanged

This slice adds no Android networking and no new permission.

The current boundary remains:

- no `INTERNET`;
- no `ACCESS_NETWORK_STATE`;
- no account requirement;
- no telemetry;
- no remote AI;
- no ValuePilot server dependency;
- no live geocoder/router;
- no production Home activation.

## Next safe work

Do not turn the existence of a typed saved-preference record into automatic persistence or UI wiring without defining lifecycle/user-control semantics first.

Safe next choices are:

1. **Versioned bounded local persistence contract** for these exact records, including migration/corruption/deletion/clear-all rules, still with no Saved UI and no networking.
2. **Immutable Saved presentation model** only after persistence semantics are stable; it must remain separate from production price/travel authority.
3. **Concrete source-specific merchant/current-price mapping** only when exact product/store scope, rights, geography, lifecycle and currentness are proven without fuzzy joins.
4. **Travel acquisition** only under the separate networking/privacy/off-main-thread milestone.
5. **Production Home activation** only after a truthful complete product + store + travel + current-price path exists.

No user/device action is required for this checkpoint.
