# ValuePilot Practical Shopping Saved Presentation Checkpoint

Updated: 2026-08-29

Branch: `work/valuepilot-android-milestone`

This checkpoint records the verified immutable Saved presentation boundary layered above the already verified transactional local exact-preference persistence chain. Newer repository evidence overrides this file.

## Latest verified code head

`aa42f38ea7e37d3d3bac2c2f508d5c45f383d370` — `Project saved preferences into safe UI state`

GitHub Actions workflow run **145** (`33272065153`) completed successfully.

All normal repository gates passed: browser checks/package, app/shared-core JVM tests, Android lint/APK, JVM summary, Android privacy verification, release/checksums, artifact upload and post-job cleanup.

The underlying verified local persistence chain remains:

- `aa8ada26bde86e8cf58f8f932c6fef4603d7a666` — deterministic bounded schema-1 codec + app-internal AtomicFile storage; workflow 143 passed.
- `70a9132e7bcd87c7b98b9c95a7fbfe3b0c457572` — synchronized confirm -> save -> bounded upsert -> atomic replace transaction path; workflow 144 passed.

## Saved presentation boundary

Source:

`android/app/src/main/java/com/valuepilot/app/PracticalShoppingSavedExactPreferencePresentation.kt`

The projector accepts only:

1. already validated persisted exact-preference state; and
2. separately supplied consumer-facing display metadata keyed by stable Practical Shopping item/store keys.

Persisted identity fields never become fallback labels.

Consumer rows contain only a supplied safe title, a short generic supporting label, and a typed delete action. Stable exact keys are carried inside typed actions/unresolved bindings, not reconstructed into visible strings.

## Display-name safety rules

Display metadata is bounded to at most:

- 128 product labels;
- 64 store labels.

Raw metadata strings are bounded before projection; rendered consumer labels are capped at 160 characters.

A missing, blank, control-character, oversized or unsafe label fails closed for that saved row.

The projector rejects product labels that expose the persisted provider id, provider item id, SKU, GTIN or dataset namespace id.

The projector rejects store labels that expose the persisted merchant key, location key, commerce-channel key, provider id or dataset namespace id. Long suffixes of prefixed merchant/location identifiers such as a raw Wikidata Q-id are also blocked.

Stable logical ShoppingItemKey/ShoppingStoreKey values are not intrinsically forbidden as human labels because a separately supplied real label may legitimately coincide with a logical key. The important invariant is that there is no fallback from a missing label to those keys.

Unsafe/missing rows are not silently discarded: the immutable UI state reports an unresolved display-name count and a generic consumer notice. Exact unresolved stable keys remain outside normal UI strings for a future metadata refresh boundary.

Extra metadata cannot manufacture a saved row because projection iterates persisted state, not the metadata maps.

## Immutable state and typed actions

Saved UI state includes:

- `Saved choices` headline;
- bounded product rows;
- bounded store rows;
- unresolved-display-name count;
- generic unresolved notice when needed;
- calm `No saved choices yet.` empty state;
- `ClearAll` action only when persisted state is non-empty.

Typed actions are:

- `DeleteProduct(ShoppingItemKey)`;
- `DeleteStore(ShoppingStoreKey)`;
- `ClearAll`.

The consumer renderer never needs to parse a title to determine what to delete.

## Persistence-backed action handling

Source:

`android/app/src/main/java/com/valuepilot/app/PracticalShoppingSavedExactPreferenceUiActionHandler.kt`

Every destructive Saved action delegates directly to the verified `PracticalShoppingSavedExactPreferenceLocalStore`:

- product deletion -> `deleteProduct`;
- store deletion -> `deleteStore`;
- clear all -> `clearAll`.

The action handler does not mutate an in-memory document directly and owns no storage codec, identity logic, UI, ranking, clock or network.

Tests prove product/store selective deletion preserves the other preference class, clear-all deletes the persisted document, delete failure remains explicit, and deleting an absent typed key remains idempotent without another write.

## Leakage regression

The projector tests build consumer-visible strings and explicitly assert that persisted examples such as GTIN, provider id, merchant key, Wikidata id, OSM location key and commerce-channel key do not appear.

Raw GTIN/provider/merchant/Wikidata labels are rejected and become unresolved rather than displayed.

## Still intentionally not implemented

This slice does NOT add:

- a Saved Android renderer/view;
- MainActivity Saved wiring;
- human-label persistence;
- label fetching/networking;
- live Open Food Facts or OpenStreetMap clients;
- current price, availability, promotion or travel authority;
- ranking or recommendations;
- INTERNET / ACCESS_NETWORK_STATE;
- account, telemetry, remote AI or ValuePilot server.

The existing Saved primary tab still does not imply that this verified state is physically rendered.

## Exact next safe dependency

Before making Saved visible, establish where durable human-facing labels come from.

The safe direction is a source-/confirmation-bound display-metadata adapter, not a fallback from persisted technical identity. For example:

- a confirmed Open Food Facts product may contribute its separately supplied product name only when the name is tied to the same exact product identity/provenance;
- a confirmed OpenStreetMap store may contribute a separately supplied place/store name only when it is tied to the same exact source location and confirmed saved store scope;
- user-entered labels may be accepted as presentation metadata without granting product/store/price authority.

Keep label metadata non-authoritative. It must never change exact identity, current-price eligibility, travel, ranking or provider rights.

Only after that label-source boundary is verified should the first Saved renderer/host be considered. Storage I/O should remain out of Android view code and should not be introduced as main-thread business logic.

No user/device action is required for this checkpoint.
