# ValuePilot Practical Shopping Production Inputs + Open Identity Checkpoint

Updated: 2026-08-29

Branch: `work/valuepilot-android-milestone`

This checkpoint records the verified production-input resolution/assembly phase and the first concrete source-isolated open-data identity bridge. Newer repository evidence overrides this file.

## Latest verified code head

`6c36017d0ee6eacb8a8660f91d3a6aa350d31e04` — `Bridge Open Food Facts identity suggestions safely`

GitHub Actions workflow run **137** (`33266111704`) completed successfully.

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

No Android networking, `INTERNET`, `ACCESS_NETWORK_STATE`, account, telemetry, remote AI, live-retailer Home wiring, geocoder or routing client was added.

## Verified implementation trail

- `bf502c0ca5c3109987903869835c90d8de1385b5` — explicit Practical Shopping product-identity resolver; workflow **133** (`33265173533`) passed.
- `218f21b69d6523b1e220885106aa23324425bd44` — explicit Practical Shopping store/offer-identity resolver; workflow **134** (`33265404146`) passed.
- `98afd1bdf2752b53831f1e730e5a6d4f67d6dfcb` — explicit Practical Shopping travel-fact resolver; workflow **135** (`33265642536`) passed.
- `e05fb0f77a3c6789f8d4cbe6d9cf3d2aa2bdf245` — production fact assembler; workflow **136** (`33265884111`) passed.
- `6c36017d0ee6eacb8a8660f91d3a6aa350d31e04` — Open Food Facts identity-suggestion bridge; workflow **137** (`33266111704`) passed.

The earlier verified production orchestration checkpoint remains `82ec42059bb46cf67b7ab54cf02660a8de2e5449` / workflow **132** (`33264748936`).

## Production path now

The provider-neutral production path is now explicit from adapter facts through the existing planner:

`already-decoded authorized/open/user facts`

`-> PracticalShoppingProductIdentityResolver`

`-> PracticalShoppingStoreIdentityResolver`

`-> PracticalShoppingTravelResolver`

`-> PracticalShoppingProductionAssembler`

`-> PracticalShoppingProductionOrchestrationRequest`

`-> PracticalShoppingProductionOrchestrator with current lifecycle/disposition registries`

`-> PracticalShoppingProductionDecisionEvaluator`

`-> PracticalShoppingProductionPlanCandidateBridge`

`-> same-instant current-price eligibility/conflict evaluation`

`-> PracticalShoppingPlanner`

The visible Home remains fictional/sample and is not connected to this production path.

## Product identity boundary

`PracticalShoppingProductIdentityResolver` receives already-supplied source identities plus an explicit relationship to one requested shopping item.

Only these relationships may auto-bind:

- `EXACT_PRODUCT_REQUEST`
- `USER_CONFIRMED_EXACT_PRODUCT`
- `SAVED_EXACT_PREFERENCE`

These remain selection-required suggestions and can never silently become production product truth:

- `CATALOG_SUGGESTION`
- `SEMANTIC_SUGGESTION`
- `UNKNOWN`

The boundary contains no product names, descriptions, images, prices or similarity scores. It resolves identity through the existing `ProductionProductEvidenceKeyResolver`: checksum-valid canonical GTIN first, then provider-scoped item id/SKU.

Multiple exact candidates may corroborate one product identity. Conflicting exact identities require explicit selection rather than provider preference, price, source ordering or fuzzy matching.

## Store / offer-scope identity boundary

`PracticalShoppingStoreIdentityResolver` operates on complete explicit offer scopes:

- merchant key
- location key where applicable
- commerce-channel key

It has no store names, addresses, coordinates, route distance/time, prices or fuzzy score.

Only already-exact store relationships can auto-bind. Name/geocoder/location discovery suggestions never become retailer offer identity automatically.

A source-asserted exact offer scope must carry explicit provider + dataset provenance, but provenance alone is not enough to make a suggestion exact.

This prevents an Open Prices location id, OpenStreetMap feature, geocoder hit, or similar location fact from silently being promoted into merchant/offer authority.

## Travel fact boundary

`PracticalShoppingTravelResolver` keeps routing/travel independent from store identity.

Each candidate is bound to:

- an explicit requested leg;
- an opaque origin/session context id;
- a travel-mode key;
- exact `ShoppingTravel` distance/time;
- source relationship/provenance;
- source observation/calculation time.

The caller supplies the evaluation instant and freshness policy. Shared core owns no clock.

Permanent rules now tested:

- wrong-origin/wrong-mode facts cannot be reused;
- stale/future route facts cannot auto-resolve;
- straight-line/approximate discovery distance is not route authority;
- multiple usable facts may corroborate one exact travel value;
- conflicting usable travel values require explicit selection rather than automatically choosing the shorter/faster route;
- route evidence cannot confer merchant/store identity.

## Production assembler boundary

`PracticalShoppingProductionAssembler` composes only independently resolved facts into the already-verified orchestration request.

Current bounds align with the lower verified boundaries:

- up to 64 target stores;
- up to 128 requested ordered store pairs;
- up to 128 item/store price links;
- up to 128 raw price requests;
- at most 192 generated travel legs (64 user-to-store + 128 pair legs), matching the travel resolver cap.

The assembler is deliberately partial-coverage friendly:

- unresolved product identity -> affected binding is omitted and reported;
- unresolved store identity -> store/bindings are omitted and reported;
- unresolved user-to-store travel -> store is omitted rather than inventing zero/default travel;
- unresolved pair travel -> both valid single-store scopes remain available while that optional pair is omitted;
- every raw current-price request is preserved even if its shopping link cannot be assembled, so downstream factual conflict evaluation does not lose contradictory evidence.

Structural defects remain fail-closed:

- duplicate stores/pairs/links/request ids;
- price links to non-requested items/non-target stores/missing raw requests;
- reuse of one raw current-price request across multiple shopping bindings;
- two shopping intents resolving to the same exact product inside one store (double-counting risk);
- any final orchestration reference defect.

The assembler never inspects a price row to infer the intended product or merchant. The lower production bridge still rechecks the raw price claim against the independently assembled exact product/store scope, current lifecycle/disposition state, freshness and conflicts.

## First concrete open-data product bridge

`OpenFoodFactsPracticalShoppingIdentityAdapter` is a network-free bridge from an already-decoded `OpenFoodFactsImportedProduct` row to a Practical Shopping product-identity candidate.

It uses only a checksum-valid source GTIN plus source provenance.

It deliberately ignores product name, brand, package quantity, image, description, nutrition, price and any search similarity for identity binding.

A valid Open Food Facts GTIN is always emitted as:

`PracticalShoppingProductIntentRelationship.CATALOG_SUGGESTION`

Therefore Open Food Facts cannot automatically decide that a broad user intent such as `eggs` means a particular packaged product.

Regression coverage proves:

1. a valid GTIN becomes a source-isolated catalog suggestion;
2. package quantity may remain unknown and identity suggestion still works, because identity and quantity authority are separate facts;
3. the production product resolver still returns `NEEDS_EXPLICIT_SELECTION` and no automatic binding;
4. equivalent checksum-valid UPC/GTIN representations resolve to the same canonical cross-source product key without becoming auto-bindable;
5. invalid GTIN is rejected rather than repaired;
6. the candidate retains an explicit `OPEN_SHARE_ALIKE` Open Food Facts products namespace (`ODbL-1.0`).

The existing `OpenFoodFactsImportedMetadataMapper` quantity rules remain unchanged and stricter. Identity recognition does not upgrade missing/weak package quantity into quantity evidence.

## Deliberate source limitations

### Open Prices

Do not promote an Open Prices physical `locationId` into merchant identity merely because it has a location name. Open Prices remains supplemental proof-backed observed/historical price evidence, not a complete current retailer-price feed.

No new production current-price bridge was created from Open Prices in this slice.

### OpenStreetMap / geocoders

No OpenStreetMap/geocoder adapter was created merely to fill the store resolver. A feature/name/coordinate result may support discovery, but it does not by itself prove the exact merchant/location/channel offer scope required for production price matching.

### Routing

No live router was added. A future router may produce `PracticalShoppingTravelCandidate` values only under a separate networking/privacy/offline/failure milestone. Routing must remain off the Android main thread and cannot grant store identity.

### Commercial/provider feeds

Rakuten/Jamieson, GS1 and other provider/account tracks remain separate. Feed access, current-price semantics, geography, advertiser/app rights, lifecycle and freshness gates remain independent and are not bypassed by this production-input work.

## Android / consumer boundary remains unchanged

Visible Home is still the verified fictional Practical Shopping experience.

Do not route production evidence through `LocalSamplePracticalShoppingDemo`, legacy `UniversalSearchController`, `ValueItem`/`Double` ranking paths, or Android view code.

The current Android privacy boundary remains no network/account/telemetry/remote-AI dependency. A future production Home activation requires a separate milestone defining:

- off-main-thread orchestration;
- bounded loading/cancellation/generation ordering;
- offline and stale-data behavior;
- location/origin privacy behavior;
- network failure/error states if networking is introduced;
- explicit permission changes if any;
- immutable UI projection with no ranking/business logic in views.

## Next safe engineering work

Do not keep extending generic shared-core plumbing without a concrete evidence source need.

The next source-specific work should happen only when the source can supply a fact without manufacturing authority. Safe candidates are:

1. **Exact product confirmation path** — an explicit barcode/product request or user selection may create `EXACT_PRODUCT_REQUEST` / `USER_CONFIRMED_EXACT_PRODUCT` independently of Open Food Facts catalog authority.
2. **Store/location discovery** — add a source-isolated adapter only when merchant/banner/location/channel semantics are explicit enough to preserve exact offer scope; name/geocoder/OSM candidates must remain suggestions until confirmed.
3. **Travel acquisition** — introduce a routing adapter only under an explicit Android networking/privacy/off-main-thread milestone, preserving origin context and freshness.
4. **Price evidence** — connect a source only after current-price semantics, rights, geography, lifecycle and freshness gates are satisfied. Historical observations remain historical.
5. **Production Home activation** — only after at least one concrete product + store + travel + price path is safe enough to produce truthful useful coverage. Keep fictional Home as fallback/test evidence until then.

No device/user action is required for this checkpoint.
