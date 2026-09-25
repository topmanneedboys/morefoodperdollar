# ValuePilot Practical Shopping Identity Confirmation Checkpoint

Updated: 2026-08-29

Branch: `work/valuepilot-android-milestone`

This checkpoint records the verified exact-product confirmation path, the first OpenStreetMap store-discovery suggestion adapter, and the explicit exact-store confirmation path. Newer repository evidence overrides this file.

## Latest verified code head

`62235ee2414a881238828323beb9e352a1dc5400` — `Add exact Practical Shopping store confirmation`

GitHub Actions workflow run **140** (`33267098910`) completed successfully.

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

No Android networking, `INTERNET`, `ACCESS_NETWORK_STATE`, account, telemetry, remote AI, live-retailer Home wiring, live geocoder, routing client, or production price adapter was added.

## Verification trail added after the production-input checkpoint

- `c60dc756e7a78d1d99480e71a59b2719a080768b` — exact Practical Shopping product confirmation; workflow **138** (`33266657695`) passed.
- `e3d86e736c1707552560b9d101653bd5cff0f1dc` — OpenStreetMap store identity suggestions; workflow **139** (`33266869644`) passed.
- `62235ee2414a881238828323beb9e352a1dc5400` — exact Practical Shopping store confirmation; workflow **140** (`33267098910`) passed.

The earlier production-input checkpoint remains `PRACTICAL_SHOPPING_PRODUCTION_INPUTS_CHECKPOINT.md`, with verified code through workflow 137.

## Exact product confirmation

`PracticalShoppingExactProductConfirmationAdapter` is a network-free application boundary for two explicit user-intent actions.

### Exact barcode request

`exactBarcodeRequest(...)` accepts a shopping item plus a barcode captured because the user is requesting that exact packaged product.

Rules:

- surrounding transport whitespace may be removed;
- the GTIN must pass the existing deterministic checksum/shape validation;
- invalid GTIN is rejected and never repaired;
- the exact supplied valid GTIN is retained as the source identity;
- relationship is `EXACT_PRODUCT_REQUEST`;
- the candidate grants no product metadata, package quantity, price, merchant, availability, promotion, or production-use authority;
- the existing `PracticalShoppingProductIdentityResolver` remains the sole layer that creates the automatic production product binding.

The verified regression proves a valid UPC/GTIN representation reaches the existing canonical product-key resolver and becomes the same stable cross-source product key already used by production evidence.

### Explicit product selection

`confirmSelection(...)` accepts an already-present product candidate only when it belongs to the same logical shopping item.

It preserves unchanged:

- provider identity;
- source product identity;
- dataset/source-isolation provenance.

It changes only the relationship to:

`USER_CONFIRMED_EXACT_PRODUCT`

The selected identity must already be capable of producing the existing production product key. Invalid/unresolvable identity fails closed.

A user may explicitly confirm a provider-scoped product identity when no GTIN exists. That remains provider-scoped; ValuePilot does not invent a GTIN or cross-source identity.

Open Food Facts catalog candidates therefore follow the intended path:

`ODbL catalog suggestion -> explicit user selection -> user-confirmed exact product intent`

User selection does not upgrade Open Food Facts package quantity, availability, retailer price, market-country or other metadata authority.

## OpenStreetMap store discovery suggestion

`OpenStreetMapPracticalShoppingStoreSuggestionAdapter` is a network-free adapter over an already-decoded OSM physical feature.

It deliberately does **not** infer merchant identity from:

- place/store name;
- address text;
- coordinates or proximity;
- route results;
- prices;
- fuzzy retailer-name matching.

It accepts only an explicit source `brand:wikidata` and/or `operator:wikidata` Q identifier.

The candidate retains:

- provider: `openstreetmap`;
- source-isolated namespace: `openstreetmap-places`;
- licence id: `ODbL-1.0`;
- storage boundary: `OPEN_SHARE_ALIKE`;
- merchant suggestion key: `wikidata:<QID>`;
- source physical location key: `osm:<node|way|relation>:<id>`;
- channel proposal: `PHYSICAL_STORE`.

Most importantly, its relationship is always:

`SOURCE_LOCATION_SUGGESTION`

Therefore the shared-core store resolver returns `NEEDS_EXPLICIT_SELECTION`; OSM cannot automatically establish production merchant/location/channel offer identity.

### Brand/operator conflict handling

The adapter preserves whether merchant identity came from:

- `BRAND_WIKIDATA`;
- `OPERATOR_WIKIDATA`; or
- `BRAND_AND_OPERATOR_AGREE`.

When explicit brand and operator QIDs disagree, the adapter fails with `AMBIGUOUS_MERCHANT_IDENTITY` rather than preferring the brand, operator, source order, name similarity, or another hidden policy.

Missing explicit merchant identity also fails. Invalid QIDs are rejected rather than normalized/repaired.

This keeps OSM useful for discovery without pretending its place record is already the exact offer scope of an unrelated retailer price source.

## Exact store confirmation

`PracticalShoppingExactStoreConfirmationAdapter` closes only the explicit-selection loop for an already-proposed store candidate.

`confirmSelection(...)` requires the selected candidate to belong to the same logical `ShoppingStoreKey`. It cannot retarget one candidate to a different store.

It preserves unchanged:

- the exact merchant/location/channel scope supplied by the selected candidate;
- provider provenance;
- dataset/source-isolation provenance.

It changes only the relationship to:

`USER_CONFIRMED_EXACT_STORE`

The existing `PracticalShoppingStoreIdentityResolver` remains the sole layer that turns that relationship into an automatic store scope.

The confirmation adapter cannot derive or modify a merchant key from a visible store name, address, coordinates, route, price, or provider economics.

## Confirmation is not production-price authority

Explicit product/store confirmation establishes **what the user means**. It does not establish that a provider price is current, authorized, geographically applicable, or scoped to that product/store.

The lower verified production path still independently rechecks every bound current-price request at the supplied decision instant for:

- stable exact product identity;
- exact merchant scope;
- exact location scope where applicable;
- exact commerce channel;
- exact currency/money specification;
- provider production authorization;
- dataset snapshot lifecycle;
- namespace disposition;
- offer freshness;
- availability/acceptance;
- same-scope factual conflict resolution.

A user choosing a product or store can therefore never make an otherwise invalid price claim rankable.

## Travel remains separate

Neither exact-product confirmation, OSM store suggestion, nor exact-store confirmation creates a travel fact.

The existing `PracticalShoppingTravelResolver` still requires explicit origin/session context, travel mode, freshness, and exact route/travel evidence. No zero/default travel is invented.

No live router or Android location/network integration was added.

## Consumer/UI boundary remains unchanged

Visible Home remains the verified fictional/sample Practical Shopping experience.

These adapters are application/evidence boundaries only. They have not been wired into `MainActivity`, visible Home, legacy Search ranking, `LocalSamplePracticalShoppingDemo`, or Android view logic.

Production Home activation still requires a separate milestone covering off-main-thread orchestration, cancellation/generation ordering, offline/stale behavior, location privacy, loading/error state, and any explicit network/permission change.

## Deliberate limitations retained

- Open Food Facts remains product recognition/catalog metadata, not retailer availability/current-price authority.
- OpenStreetMap remains store/location discovery evidence, not production retailer-price authority.
- Open Prices remains supplemental observed/historical evidence and is not promoted to nationwide current retailer pricing.
- Rakuten/Jamieson/GS1/provider rights and price semantics remain separate gates.
- No source is joined merely because names, addresses, prices, coordinates, images, or descriptions look similar.
- User confirmation establishes intent, not source factual authority outside that intent relationship.

## Next safe engineering work

Do not keep extending generic plumbing merely to advance commit count.

Safe next work should require a concrete product need and preserve these boundaries. Candidates include:

1. **Locally saved exact preferences** derived only from prior explicit product/store confirmation, with clear user control and no price/travel authority.
2. **A concrete store/source mapping** only when source semantics are strong enough to prove the same merchant/location/channel scope; do not fuzzy-join OSM to price providers.
3. **Travel acquisition** only under a separate networking/privacy/off-main-thread milestone.
4. **Current-price evidence** only when source rights, current-price semantics, geography, lifecycle and freshness are proven.
5. **Production Home activation** only after at least one concrete product + store + travel + current-price path can produce truthful useful coverage.

No device/user action is required for this checkpoint.
