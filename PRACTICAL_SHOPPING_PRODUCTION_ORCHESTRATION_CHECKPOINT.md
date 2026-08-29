# ValuePilot Practical Shopping Production Orchestration Checkpoint

Updated: 2026-08-29

Branch: `work/valuepilot-android-milestone`

This checkpoint records the first fully verified adapter-facing production Practical Shopping orchestration boundary. Newer repository evidence overrides this file.

## Latest verified engineering head

`82ec42059bb46cf67b7ab54cf02660a8de2e5449` — `Add production Practical Shopping orchestration contract`

GitHub Actions workflow run **132** (`33264748936`) completed successfully.

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

No Android networking, account, telemetry, remote AI, live-retailer Home wiring, geocoder or routing service was added.

## Production path now

The verified provider-neutral production path is:

`already-established adapter facts + raw provider offer inputs`

`-> PracticalShoppingProductionOrchestrationRequest`

`-> PracticalShoppingProductionOrchestrator.validate`

`-> current lifecycle/disposition registries supplied at execution`

`-> PracticalShoppingProductionDecisionEvaluator`

`-> PracticalShoppingProductionPlanCandidateBridge`

`-> PracticalShoppingProductionCandidateBridge`

`-> same-instant ProductionCurrentPriceEligibilityEvaluator batch`

`-> PracticalShoppingPlanner`

The production path is still not connected to visible Home.

## Orchestration contract

`PracticalShoppingProductionOrchestrationRequest` is an immutable adapter-facing request containing only:

- an already-resolved `ShoppingRequest`;
- explicit store scopes (`merchantKey`, optional `locationKey`, `commerceChannelKey`, and caller-supplied user-to-store travel);
- explicit ordered base -> added-store scopes with caller-supplied additional travel;
- explicit shopping-item -> exact `ProductionProductEvidenceKey` -> store -> raw current-price request bindings;
- raw `ProductionCurrentPriceEligibilityRequest` values;
- explicit evaluation instant;
- explicit evidence-acceptance policy;
- explicit Practical Shopping planning policy.

It deliberately does **not** contain lifecycle or namespace-disposition registries. Those are supplied when `PracticalShoppingProductionOrchestrator.evaluate(...)` runs so a request object cannot freeze production authority and later masquerade as a still-authorized decision.

The contract performs no product matching, merchant discovery, geocoding, routing, network I/O, provider authorization, currency conversion, ranking, promotion inference or UI work.

## Validation boundary

`PracticalShoppingProductionOrchestrator.validate(...)` distinguishes malformed orchestration references from ordinary incomplete market evidence.

It reports bounded/reference defects before invoking the lower production bridge, including:

- too many stores, pairs, price bindings or raw price requests;
- duplicate store keys;
- duplicate ordered store pairs;
- duplicate raw price-request ids;
- duplicate item/store bindings;
- reuse of one bound current-price request for multiple shopping bindings;
- duplicate exact product binding inside one store;
- binding item not present in the shopping request;
- binding store not declared;
- binding current-price request not supplied;
- pair base or added store not declared.

Validation aggregates reference defects rather than stopping at the first one.

## Important incomplete-evidence rule

A valid orchestration request does **not** require every requested item to have a price, every store to cover every item, or even any current-price evidence yet.

A valid request with zero price evidence reaches the existing planner and returns `PrimaryShoppingPlanKind.NO_COVERAGE`. Missing market evidence is therefore not mislabeled as a software/orchestration defect.

Unknown prices remain unknown.

## Important conflict-evidence rule

Raw current-price requests are allowed to exist without a direct shopping binding.

This is intentional. A same-product raw claim may be needed by downstream factual conflict resolution even when it is not the candidate price directly bound to a shopping item/store. The orchestrator must not pre-filter such evidence merely because it is not selected as a basket binding.

## Point-in-time authority rule

A successful orchestration validation does not itself authorize a price or persist an old decision as authority.

On every production evaluation:

1. current lifecycle and namespace-disposition registries are supplied fresh;
2. raw current-price inputs are re-evaluated for the supplied decision instant;
3. same-instant batching evaluates each raw request at most once in that invocation;
4. candidate-specific factual conflicts are derived from that same evaluation set;
5. exact product/merchant/location/channel scope is rechecked by the Practical Shopping bridge;
6. incompatible money specs are excluded without conversion;
7. the existing `PracticalShoppingPlanner` alone decides the primary store and optional second stop.

No eligibility batch, candidate, orchestration result or planner decision is a durable production authorization token.

## Performance/threading boundary

The verified batching change at `2e6a71180738bb1d19be64c1eb850d6730bb139e` remains in force:

- at most 128 raw current-price requests per production decision invocation;
- each raw request is evaluated once for that invocation rather than once per binding;
- no cross-call authority cache exists;
- production evaluation is synchronous shared-core work and must remain off Android's main/UI thread when eventually integrated.

Physical-device production-planning frame/latency measurements have not yet been claimed.

## Regression coverage added with the orchestration boundary

The orchestration tests prove:

1. valid zero-price input reaches a legitimate `NO_COVERAGE` decision;
2. broken binding/pair references are aggregated before lower production evaluation;
3. duplicate stores, pairs, raw request ids, item/store bindings, bound request reuse and store/product bindings are rejected deterministically;
4. an over-limit store collection is reported by orchestration validation before the lower bridge can throw on its own preconditions;
5. unbound raw current-price evidence remains permitted for conflict resolution;
6. invalid orchestration returns no detached production decision.

## Verification trail for the production Practical Shopping path

- `622addf724f40dc9d52a18de89c92a78ec384a7b` — preserve explicit `AGING` evidence; workflow **127** passed
- `08c9c5cb83dfc7f8d89c0403ddd43443e51cfc58` — trusted current-price evidence -> one-store candidates; workflow **128** passed
- `7585203a37d215ad64a3d1a078108a46f5351f8a` — ordered two-store candidate construction; workflow **129** passed
- `95e66daf01c5e492b776fb573c705de42ccddd1f` — production candidate partition + existing planner decision; workflow **130** passed
- `2e6a71180738bb1d19be64c1eb850d6730bb139e` — same-instant batched current-price eligibility; workflow **131** passed
- `82ec42059bb46cf67b7ab54cf02660a8de2e5449` — adapter-facing production orchestration contract + validation regressions; workflow **132** passed

## Next safe engineering slice

Do **not** wire real retailer networking into Android and do **not** route production evidence through the fictional Home controller.

The decision/orchestration path is now sufficiently explicit to begin the next source-isolated adapter phase, but only one narrow boundary at a time.

Before implementing a provider/open-data adapter, inspect existing identity/source/licensing boundaries and define the smallest deterministic adapter contract that can supply one of the orchestrator's missing established facts without manufacturing authority.

Priority order:

1. resolved shopping intent -> exact production product identity candidate/binding boundary;
2. explicit source-isolated store/location identity boundary;
3. explicit route/travel fact provider boundary;
4. only after those contracts are tested, connect authorized/open/user evidence sources one at a time.

For open/free data:

- Open Food Facts may support packaged-product recognition/metadata, not current retailer price or Canadian availability proof;
- OpenStreetMap may support store/location/routing facts where its licence and source-isolation requirements are respected;
- Open Prices is supplemental observed/historical proof, not nationwide live-price authority;
- source licensing/share-alike boundaries must remain isolated;
- no title/description/image similarity may silently become an authoritative exact product binding;
- no geocoder/router result may silently become a retailer offer identity;
- no Android `INTERNET` or `ACCESS_NETWORK_STATE` permission change belongs in this slice.

Keep the visible Home fictional until a separate production-activation milestone explicitly defines offline/loading/error/privacy behavior and an off-main-thread Android coordinator.