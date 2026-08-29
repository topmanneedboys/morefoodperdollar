# ValuePilot Practical Shopping Saved Preference Storage Checkpoint

Updated: 2026-08-29

Branch: `work/valuepilot-android-milestone`

This checkpoint records the first verified on-device persistence boundary for saved exact Practical Shopping preferences. Newer repository evidence overrides this file.

## Latest verified code head

`aa8ada26bde86e8cf58f8f932c6fef4603d7a666` — `Persist saved Practical Shopping exact preferences`

GitHub Actions workflow run **143** (`33271262272`) completed successfully.

All normal repository gates passed:

- browser model/engine/UI/Firefox checks;
- browser packaging;
- shared-core and app JVM tests;
- Android lint;
- Android APK build;
- JVM test summary;
- Android privacy-boundary verification;
- release/checksum assembly;
- verified artifact upload;
- post-job cleanup.

The prior verified lifecycle code head remains:

`332a246de07e22d14840c7b35ece115bb2c61fcb` — `Define saved Practical Shopping preference lifecycle`; workflow **142** passed.

## Files added in workflow 143

Production/application:

- `android/app/src/main/java/com/valuepilot/app/PracticalShoppingSavedExactPreferenceCodec.kt`
- `android/app/src/main/java/com/valuepilot/app/PracticalShoppingSavedExactPreferenceLocalStore.kt`

Tests:

- `android/app/src/test/java/com/valuepilot/app/PracticalShoppingSavedExactPreferenceCodecTest.kt`
- `android/app/src/test/java/com/valuepilot/app/PracticalShoppingSavedExactPreferenceLocalStoreTest.kt`

No shared-core file was modified.

## Deterministic schema-1 codec

`PracticalShoppingSavedExactPreferenceCodec` is the only bytes/document codec in this slice.

The outer document is ASCII-only. Every variable string field is encoded as UTF-8 bytes and then lower-case hexadecimal. `~` is the dedicated null marker, so delimiters/newlines cannot be confused with field content.

The persisted fields are limited to:

### Saved exact product

- stable `ShoppingItemKey`;
- provider id;
- provider item id / SKU / GTIN where present;
- optional dataset namespace id/display/licence/storage-boundary provenance.

### Saved exact store

- stable `ShoppingStoreKey`;
- exact merchant key;
- exact optional location key;
- exact commerce-channel key;
- optional provider id;
- optional dataset namespace id/display/licence/storage-boundary provenance.

The codec has no field for candidate ids, display ranking, price, availability, promotion, quantity authority, route/travel, offer freshness, production authorization, or current evidence snapshots.

## Bounds and corruption handling

Codec limits are explicit:

- maximum encoded file: **524,288 bytes**;
- maximum record count before document validation: **192**;
- maximum persisted stable item/store key: **512 UTF-8 bytes**;
- maximum other individual encoded source/provenance field: **4,096 UTF-8 bytes**.

The existing state manager still owns semantic document limits of 128 product preferences and 64 store preferences.

Decode fails closed for:

- file larger than the encoded-byte ceiling;
- non-ASCII outer representation;
- invalid/missing header;
- too many records;
- malformed/truncated records;
- odd/non-hex field encoding;
- invalid UTF-8 field bytes;
- partially present dataset provenance;
- invalid enum/type construction.

After structural decode, the existing `PracticalShoppingSavedExactPreferenceStateManager.load(...)` is always called. Therefore unsupported schema, duplicate keys, semantic capacities and product-identity revalidation remain centralized.

A GTIN that is modified in storage but still looks like digits of a valid GTIN length is rechecked through `ProductionProductEvidenceKeyResolver`; a bad checksum remains unusable rather than becoming a saved automatic product binding.

## App-internal crash-safe file storage

The Android byte-storage adapter uses framework `android.util.AtomicFile` in `Context.filesDir` with the fixed application-internal file name:

`practical-shopping-saved-exact-preferences.v1`

It does not use external/shared storage.

Writes use `startWrite()` followed by `finishWrite()` only after the full codec document is written. Failed writes call `failWrite()` when a write stream had been opened.

Reads deliberately do **not** call `AtomicFile.readFully()`. They stream through an 8 KiB buffer and stop once the codec's 524,288-byte ceiling is exceeded, preventing an oversized/corrupt file from causing an unbounded pre-validation allocation.

`AtomicFile` provides atomic replacement, not file locking. `PracticalShoppingSavedExactPreferenceLocalStore` therefore synchronizes all public operations on the store instance. No production/UI instance is wired yet, so there is currently no competing writer path.

## Local-store behavior

Verified operations:

- `load()`;
- `replace(state)`;
- `deleteProduct(itemKey)`;
- `deleteStore(storeKey)`;
- `clearAll()`.

Missing storage loads as validated empty preference state.

Present-but-corrupt and present-but-oversized storage remain distinct from missing storage.

Selective delete fails closed if the existing document cannot be read/decoded/validated. It never partially salvages a corrupt document and then overwrites the original.

Delete-one is idempotent for an absent item/store key and does not perform another write when nothing changed.

`clearAll()` can delete a corrupt document without decoding it so the user always has a recovery path.

The injectable byte-storage tests prove that a failed replacement does not replace the prior bytes. Android lint/build additionally compile the thin real `AtomicFile` adapter against the app's minSdk/targetSdk configuration.

## Saved exact trust boundary unchanged

Persistence does not make a preference stronger than the verified identity relationship that created it.

Only prior explicit `USER_CONFIRMED_EXACT_PRODUCT` / `USER_CONFIRMED_EXACT_STORE` choices can enter the saved-preference record boundary established before this checkpoint.

Restored preferences still enter the existing product/store identity resolvers as `SAVED_EXACT_PREFERENCE` / `SAVED_EXACT_STORE` candidates.

Persistence never authorizes:

- a retailer price;
- current availability;
- promotion validity;
- package quantity;
- travel or route validity;
- source production-use rights;
- offer geography;
- dataset lifecycle/disposition;
- offer freshness;
- factual conflict resolution.

Those facts remain independently re-evaluated downstream.

## UI and privacy remain unchanged

The top-level Saved route is still only a navigation destination. Workflow 143 did not connect storage to `MainActivity`, Home, Search, Basket, Saved rendering, sample controllers, legacy ranking or production Home.

No Android permission was added.

The verified privacy boundary remains:

- no `INTERNET`;
- no `ACCESS_NETWORK_STATE`;
- no account requirement;
- no telemetry;
- no remote AI;
- no ValuePilot server;
- no live geocoder/router;
- no live production retailer-price networking.

## Exact next safe engineering step

Do not wire persistence and a new Saved screen in one change.

The local store currently exposes whole-state replacement plus deletion. A concrete next persistence slice may add **transactional save/upsert operations** that perform `load -> bounded state-manager upsert -> atomic replace` under the same store lock, so callers cannot create a lost-update window by composing those operations externally.

That slice should prove:

1. product upsert uses only `PracticalShoppingSavedExactProductPreference` already admitted by the confirmation/save adapter;
2. store upsert uses only `PracticalShoppingSavedExactStorePreference` already admitted by the confirmation/save adapter;
3. same stable key replaces deterministically;
4. capacity rejection leaves persisted bytes untouched;
5. read/decode failure leaves persisted bytes untouched;
6. write failure leaves the previous AtomicFile generation intact;
7. no UI, price, travel, network or production activation is introduced.

Only after persistence operations are complete should a separate immutable Saved presentation model be designed. Saved UI must not expose raw internal merchant/location/channel identifiers as consumer labels merely because they are available in the persisted identity record.

No user/device action is required for this checkpoint.
