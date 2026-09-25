# ValuePilot Practical Shopping Saved Preference Lifecycle Checkpoint

Updated: 2026-08-29

Branch: `work/valuepilot-android-milestone`

This checkpoint records the verified versioned/bounded lifecycle contract for locally saved exact Practical Shopping preferences. Newer repository evidence overrides this file.

## Latest verified code head

`332a246de07e22d14840c7b35ece115bb2c61fcb` — `Define saved Practical Shopping preference lifecycle`

GitHub Actions workflow run **142** (`33269745206`) completed successfully.

All normal repository gates passed: browser checks/package, shared-core/app JVM tests, Android lint/APK, JVM summary, Android privacy verification, release/checksums, artifact upload and post-job cleanup.

The immediately preceding saved-record code head remains:

`a1f07bfc806aa0720bcf0e7859a37c911bd0a4a9` — `Add saved Practical Shopping exact preferences`; workflow **141** (`33269406433`) passed.

## Saved exact preference trust boundary retained

Only an already `USER_CONFIRMED_EXACT_PRODUCT` candidate may be converted into `PracticalShoppingSavedExactProductPreference`.

Only an already `USER_CONFIRMED_EXACT_STORE` candidate may be converted into `PracticalShoppingSavedExactStorePreference`.

Catalog/semantic product suggestions, raw OSM/store discovery suggestions and a one-time `EXACT_PRODUCT_REQUEST` barcode are not silently remembered.

Restoration keeps the same stable `ShoppingItemKey` / `ShoppingStoreKey`, exact identity/scope and source provenance, while receiving a new invocation-local candidate id.

No product/store text rematching is performed.

## Versioned storage-neutral document

`PracticalShoppingSavedExactPreferenceDocument` now defines the versioned data contract a future storage adapter may encode.

Current schema version: **1**.

This is not a serializer and performs no file/database/SharedPreferences/network I/O.

`PracticalShoppingSavedExactPreferenceStateManager.load(...)` validates a decoded document before producing immutable usable state.

Load fails closed for:

- unsupported schema version;
- more than 128 saved exact product preferences;
- more than 64 saved exact store preferences;
- duplicate stable product item keys;
- duplicate stable store keys;
- a saved product identity that can no longer resolve through `ProductionProductEvidenceKeyResolver`.

No partial state is returned when validation fails.

The product-identity check is important for persistence corruption: a digits/length-shaped GTIN with an invalid checksum is rejected during load rather than becoming a saved exact production binding.

## Deterministic state lifecycle

The validated state is immutable and ordered deterministically by stable keys.

Upsert behavior is explicit:

- same product item key -> replace the prior product preference;
- same store key -> replace the prior store preference;
- replacement does not consume another capacity slot;
- a new product key when already at 128 -> reject without modifying state;
- a new store key when already at 64 -> reject without modifying state.

Remove operations are idempotent.

Clear removes every saved product/store preference and is idempotent on an already-empty state.

Export back to `PracticalShoppingSavedExactPreferenceDocument` always uses schema version 1 and deterministic key ordering.

## Still not persisted authority

Neither the saved record nor its lifecycle state stores or authorizes:

- current price;
- availability;
- promotion;
- package quantity authority;
- travel distance/time;
- route validity;
- provider production-use permission;
- offer geography;
- dataset lifecycle authorization;
- namespace disposition;
- offer freshness;
- factual-conflict resolution.

Restored identities still enter the existing product/store identity resolvers, and current price/travel evidence remains separately re-evaluated downstream.

## Saved UI remains separate

The top-level `SAVED` tab is still only a navigation destination; it does not imply this production preference state is visible or persisted.

No `MainActivity`, Home, Saved renderer, sample controller, legacy ranking or production Home path was modified in workflows 141–142.

A future Saved UI must render immutable UI-ready state and emit typed actions. It must not own storage parsing, identity resolution, source policy or ranking.

## Android privacy boundary unchanged

No `INTERNET` or `ACCESS_NETWORK_STATE` permission was added.

There is still no account requirement, telemetry, remote AI, ValuePilot server, live OSM/geocoder fetch, routing client, production price adapter or live production Home activation in this slice.

## Exact next safe persistence step

Actual on-device persistence remains a separate engineering slice.

Before writing bytes, the storage codec/backend should preserve these verified rules and add focused tests for:

1. deterministic schema-1 encoding/decoding;
2. bounded encoded input size before parsing/allocating large collections;
3. malformed/truncated/corrupt input returning an explicit failure rather than partial recovery;
4. unknown schema version fail-closed behavior;
5. atomic replace semantics so a crash cannot leave a half-written preference document;
6. explicit delete-one and clear-all persistence behavior;
7. no durable candidate ids;
8. no persisted price/travel/current authorization snapshots;
9. source/dataset provenance retained exactly;
10. storage remains local and works without network/account access.

Do not wire this state into the visible Saved surface or production Home in the same change as introducing the first storage backend. Keep persistence mechanics, immutable presentation and production activation separately verifiable.

No user/device action is required for this checkpoint.
