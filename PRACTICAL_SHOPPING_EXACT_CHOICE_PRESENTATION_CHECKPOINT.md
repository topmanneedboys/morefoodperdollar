# Practical Shopping Exact Choice Presentation Checkpoint

## Verified boundary

- Verified code commit: `7d83fe8020bf8422b5f54b5a036aef56b9163d9c` (`Project exact choices for explicit confirmation`)
- Workflow: `Build ValuePilot v101 release`
- Workflow run: `162` / `33279691223`
- Job: `99172627934`
- Result: full workflow success, including browser tests/package, Android tests/lint/APK, JVM summary, Android privacy boundary, release/checksums, verified release upload, cleanup, and Complete job.

## What this boundary adds

This slice adds a pure, network-free presentation/selection boundary for source-backed exact-choice confirmation. It does not add an Android View, route, provider client, or Save/Remember button.

Supported recognition sources in this first slice are deliberately narrow:

- Open Food Facts catalog product suggestions;
- OpenStreetMap physical-store suggestions.

The projector accepts candidate + separately decoded source-row pairs that already exist. It re-runs the corresponding verified source adapter and exposes a human label only when the revalidated source suggestion is exactly the same product identity or full store scope/provenance, ignoring only the source candidate id.

A matching visible name never establishes identity by itself.

## Renderer-safe state

Consumer-ready product/store state contains only:

- fixed human-facing headline/guidance/supporting copy;
- validated source-backed human labels;
- omitted-choice count and generic notice/empty copy;
- typed actions containing only a positive numeric presentation generation and bounded integer option id.

Renderer state does not carry:

- source candidate ids;
- GTIN/SKU/provider item ids;
- provider ids or dataset ids;
- merchant/location/channel keys;
- source rows;
- exact product/store candidate objects;
- price, availability, travel, ranking, or provider-economic information.

Exact candidates/source rows remain behind the application projection's private lookup. Internal rejection diagnostics may retain source candidate ids for tests/diagnostics but are outside renderer state.

## Recognition-label safety

Product labels fail closed when missing, blank, too long, control-containing, or exposing the exact candidate's provider/source identifiers including GTIN/SKU/provider item id/dataset id.

Store labels fail closed under the same basic rules and when exposing merchant/location/channel/provider/dataset identifiers. Long suffixes of prefixed merchant/location ids are also blocked, matching the Saved presentation safety approach.

There is no fallback to a technical identifier.

## Selection semantics

A product/store row action is valid only for the projection generation that created it and for a currently bound opaque option id.

- stale generation -> rejected;
- unknown option -> rejected;
- invalid new confirmed-candidate id -> rejected before adapter construction;
- valid action -> retrieves the original exact suggestion from the private lookup and delegates to `PracticalShoppingExactProductConfirmationAdapter` or `PracticalShoppingExactStoreConfirmationAdapter`;
- successful confirmation preserves exact source identity/scope and provenance while changing only the relationship to the existing user-confirmed relationship;
- the result also emits the already-typed `PracticalShoppingRememberConfirmedChoiceRequest` with the matching source recognition row, ready for the separately verified serialized Remember execution path.

No identity is reconstructed from visible copy.

## Verified regressions

Focused JVM tests cover:

- OFF safe label projection and explicit product confirmation;
- OSM safe place-name projection and exact store-scope confirmation;
- renderer state not containing candidate id, GTIN, provider id, merchant/location/channel ids;
- GTIN/provider-shaped product labels rejected;
- merchant/location-id-shaped store labels rejected;
- same visible product name cannot hide a different GTIN/source identity;
- same visible store name cannot hide a different OSM exact scope;
- wrong logical key and non-suggestion relationship omitted;
- invalid source row fails source revalidation;
- stale-generation and unknown-option actions cannot select/reconstruct identity;
- invalid confirmed-candidate id rejected;
- safe and unsafe options may coexist without manufacturing unsafe rows;
- product option set bounded to 32;
- duplicate candidate ids rejected;
- nonpositive presentation generation rejected.

## Boundaries intentionally unchanged

This checkpoint does not activate a user-visible exact-confirmation route. Production Search still contains offer presentation rows rather than these exact source-backed confirmation option payloads, so no Remember button is attached to Search/Home and no identity is reconstructed from an offer row.

Fictional/sample Home/Search remain non-production and non-saveable.

No changes were made to:

- Android `INTERNET` or `ACCESS_NETWORK_STATE` permissions;
- provider networking or credentials;
- production price/currentness/availability authority;
- store/source cross-provider mapping;
- travel acquisition;
- ranking or organic ordering;
- accounts, telemetry, remote AI, or ValuePilot backend access;
- shared-core platform neutrality.

## Next decision boundary

Re-read current milestone/status evidence before extending the UI. A physical confirmation renderer is safe only as a thin client of this state/action contract, but it should not be activated until a genuine upstream production source supplies these source-backed option payloads. Do not create an attractive but disconnected confirmation screen merely to advance commit count.