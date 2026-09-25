# ValuePilot Practical Shopping Saved Preference Transactions Checkpoint

Updated: 2026-08-29

Branch: `work/valuepilot-android-milestone`

This checkpoint records the verified local `confirm -> save -> persist` boundary for exact Practical Shopping preferences. Newer repository evidence overrides this file.

## Latest verified code head

`70a9132e7bcd87c7b98b9c95a7fbfe3b0c457572` — `Save exact preferences transactionally`

GitHub Actions workflow run **144** (`33271595755`) completed successfully.

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

The immediately preceding verified storage code head remains:

`aa8ada26bde86e8cf58f8f932c6fef4603d7a666` — `Persist saved Practical Shopping exact preferences`; workflow **143** (`33271262272`) passed.

The storage checkpoint immediately before this transaction commit is:

`b97e39f6ad9f6565bf539af6b0b7cdb788041e4c` — `Checkpoint saved Practical Shopping local storage`.

## Transaction boundary added

Production/application:

- `android/app/src/main/java/com/valuepilot/app/PracticalShoppingSavedExactPreferenceTransactions.kt`

Tests:

- `android/app/src/test/java/com/valuepilot/app/PracticalShoppingSavedExactPreferenceTransactionsTest.kt`

No previously verified codec, AtomicFile storage, shared-core, UI, manifest or provider file was modified by workflow 144.

## Confirmation remains mandatory before persistence

The transaction API deliberately does **not** accept a freely constructed saved-preference record.

It accepts the exact product/store identity candidate produced by the user action:

- `saveConfirmedProduct(...)`
- `saveConfirmedStore(...)`

Each call first reuses the already-verified `PracticalShoppingSavedExactPreferenceAdapter` admission boundary.

Therefore:

- only `USER_CONFIRMED_EXACT_PRODUCT` may become a persisted saved product preference;
- only `USER_CONFIRMED_EXACT_STORE` may become a persisted saved store preference;
- a catalog suggestion cannot be persisted directly;
- an OpenStreetMap/source-location suggestion cannot be persisted directly;
- a one-time barcode `EXACT_PRODUCT_REQUEST` cannot be silently converted into a durable preference.

The tests prove rejected unconfirmed candidates do not even read or write the local preference store.

## Product identity still fails closed

A candidate carrying the `USER_CONFIRMED_EXACT_PRODUCT` relationship is not sufficient by itself if its source identity is invalid.

Before persistence, product upsert still runs through `PracticalShoppingSavedExactPreferenceStateManager.upsertProduct(...)`, which requires the exact source product identity to resolve through `ProductionProductEvidenceKeyResolver`.

A checksum-invalid GTIN therefore returns the explicit transaction issue:

`PRODUCT_IDENTITY_INVALID`

and no write occurs.

No malformed identity is repaired or guessed.

## Transactional critical section

The verified storage layer already synchronizes public `PracticalShoppingSavedExactPreferenceLocalStore` operations on the store instance and uses app-internal `AtomicFile` for crash-safe generation replacement.

Workflow 144 closes the remaining caller-side lost-update window by synchronizing the complete sequence on that same store object:

`admit confirmed choice -> load current persisted state -> bounded state-manager upsert -> atomic replace`

JVM monitor locking is re-entrant, so calling the store's synchronized `load()` / `replace()` inside the outer `synchronized(store)` transaction retains one critical section for the whole operation on that store instance.

A regression launches two concurrent confirmed-product saves against the same local-store instance and requires the final persisted state to contain both distinct stable item keys. This proves the former externally composable load/replace lost-update window is closed for the supported single-store instance boundary.

This is not a claim of cross-process or independently constructed multi-instance database locking. No such writer topology is wired into the application.

## Deterministic upsert behavior retained

The existing lifecycle manager still owns the bounded preference state:

- maximum 128 product preferences;
- maximum 64 store preferences;
- same stable item key replaces the prior exact product preference;
- same stable store key replaces the prior exact store preference;
- replacement does not consume another capacity slot;
- stable deterministic state ordering remains unchanged.

Workflow 144 proves:

- same product key replacement remains one record;
- same store key replacement remains one record;
- product-capacity rejection leaves persisted bytes untouched;
- store-capacity rejection leaves persisted bytes untouched.

## Storage failures remain non-destructive

The transaction maps lower storage/codec/document failures into typed `STORAGE_FAILURE` results while retaining the lower failure detail.

Verified cases include:

- corrupt existing document blocks a save instead of being partially repaired;
- corrupt bytes remain untouched after that failed transaction;
- failed AtomicFile-generation replacement is surfaced as `WRITE_FAILED` through the storage result;
- the previously persisted generation remains intact after simulated write failure.

Selective persistence never treats read/decode failure as empty preferences.

The already-verified `clearAll()` recovery path remains available separately for corrupt local state.

## Complete verified local saved-preference chain

The current verified chain is:

`explicit product/store confirmation`

`-> PracticalShoppingSavedExactPreferenceAdapter admission`

`-> PracticalShoppingSavedExactPreferenceTransactions`

`-> PracticalShoppingSavedExactPreferenceStateManager`

`-> PracticalShoppingSavedExactPreferenceCodec`

`-> PracticalShoppingSavedExactPreferenceLocalStore`

`-> app-internal android.util.AtomicFile`

The chain preserves stable identity/scope plus provider/dataset provenance only.

It does not persist or authorize:

- current price;
- availability;
- promotions;
- package quantity authority;
- route/travel values;
- provider production-use permission;
- offer geography;
- dataset lifecycle/disposition authorization;
- current offer freshness;
- factual-conflict resolution;
- ranking output.

Those remain separately re-evaluated by the existing production evidence path.

## UI and privacy remain unchanged

Workflow 144 did not modify:

- `MainActivity`;
- Home;
- Search;
- Basket;
- Saved rendering;
- sample/fictional controllers;
- legacy ranking;
- production Home orchestration;
- Android manifest permissions.

The top-level `SAVED` destination remains navigation-only. The new persistence chain has no visible consumer surface yet.

The verified Android privacy boundary remains:

- no `INTERNET`;
- no `ACCESS_NETWORK_STATE`;
- no account requirement;
- no telemetry;
- no remote AI;
- no ValuePilot server;
- no live geocoder/router;
- no live production retailer-price networking.

## Exact next safe engineering direction

Do **not** wire the persisted identity records directly into a Saved screen simply because storage is now complete.

The persisted records intentionally lack trustworthy consumer display labels. Raw GTINs, provider ids, Wikidata merchant ids, OSM location ids and commerce-channel keys are implementation/audit identities, not acceptable default consumer labels.

Before visible Saved wiring, inspect existing Saved-shell behavior and define a separate immutable Saved presentation boundary that can receive safe human-facing metadata without weakening identity truth.

That presentation slice should preserve these rules:

1. persistence parsing remains outside views;
2. identity resolution remains outside views;
3. views render immutable UI-ready state and emit typed actions only;
4. raw internal merchant/location/channel/provider keys do not leak as normal labels;
5. missing display metadata stays explicit rather than being guessed from identity strings;
6. delete-one / clear-all actions route through the verified local persistence boundary;
7. no price/travel/current-authority claim is created by the Saved screen;
8. no networking or permission change is bundled with the first Saved presentation slice.

If safe human-facing metadata is not yet available for a record, first build a presentation-metadata boundary rather than inventing labels from technical identifiers.

No user/device action is required for this checkpoint.
