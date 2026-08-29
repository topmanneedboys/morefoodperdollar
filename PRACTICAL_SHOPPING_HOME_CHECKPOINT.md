# ValuePilot Practical Shopping Checkpoint

Updated: 2026-08-29

Branch: `work/valuepilot-android-milestone`

This checkpoint records the verified Practical Shopping Home slice plus the first provider-neutral production evidence-to-decision path. Newer repository evidence overrides this file.

## Latest verified engineering head

`95e66daf01c5e492b776fb573c705de42ccddd1f` — `Evaluate production Practical Shopping decisions`

GitHub Actions workflow run **130** (`33263558876`) completed successfully.

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

## Verified Home architecture

The visible Home flow remains deliberately fictional and isolated:

`fictional controller -> PracticalShoppingHomeSession -> PracticalShoppingHomeRenderer -> PracticalShoppingHomeSurfaceView`

Its shopping decision still flows through:

`resolved sample intents -> shared-core PracticalShoppingPlanner -> PracticalShoppingUiProjector -> controller UI state`

Important Home invariants:

1. lifecycle state persists/restores only user-level inputs needed to reproduce state;
2. Android does not persist a detached shopping decision as authority;
3. the renderer passes the already-projected shopping decision through unchanged;
4. the Android view owns no shopping resolution, ranking, basket arithmetic or second-stop threshold;
5. the visible `whyText` is produced from the already-decided `PrimaryShoppingPlanKind`, not from a presentation-layer score;
6. Search remains separate and Compare remains secondary;
7. normal usable query length is at most 240 characters and an over-limit state retains at most 241;
8. a 100,000-character lifecycle restoration regression proves oversized input is reduced before remaining in controller/snapshot state;
9. exposed resolved + unknown intents remain capped at 32 total;
10. typing does not invoke the shopping planner and normal query synchronization avoids unnecessary `setText` cursor resets.

These are code/CI invariants, not a claim of measured physical-device frame timing.

## Verified production evidence-to-decision architecture

The new provider-neutral path is:

`raw provider offer inputs`

`-> ProductionCurrentPriceEligibilityEvaluator`

`-> PracticalShoppingProductionCandidateBridge`

`-> PracticalShoppingProductionPlanCandidateBridge`

`-> PracticalShoppingProductionDecisionEvaluator`

`-> PracticalShoppingPlanner`

The production path is not connected to Home yet.

### Current-price and store binding

For each explicitly supplied shopping-item/store binding, the bridge re-runs the existing production current-price eligibility path at the supplied evaluation instant. It does not trust a detached `Money`, staged offer, old eligibility result or prior shopping candidate as continuing authority.

A usable price must match all of the following after that re-evaluation:

- the requested shopping item is actually in the shopping request;
- the declared store exists;
- the exact current-price request exists and is currently eligible;
- the exact `ProductionProductEvidenceKey` matches;
- merchant scope matches;
- location scope matches, including explicit null vs non-null location;
- commerce channel matches;
- current-price currency scope matches the exact selected `Money`;
- accepted freshness is explicitly `FRESH` or `AGING`.

Blocked, revoked, out-of-stock, conflicting, mismatched or otherwise non-rankable prices stay missing. The bridge never fills an unknown price to make a basket look complete.

One current-price request cannot be reused for multiple shopping bindings, and one exact product cannot be counted twice in the same store basket.

### Evidence freshness semantics

`ShoppingPlanEvidenceSummary` now preserves `AGING` separately from `FRESH`, `STALE` and `UNKNOWN`.

Existing zero-aging fictional output remains unchanged. Production candidate construction counts the exact freshness of only the prices actually selected for that candidate. No aging evidence is relabeled merely to fit an older three-bucket presentation model.

### Single-store candidates

The one-store bridge emits only bounded `SingleStorePlanCandidate` values.

It:

- sums only usable exact prices;
- exposes incomplete coverage as incomplete coverage;
- rejects mixed currency/fraction-precision baskets instead of converting them;
- accepts travel only as explicit caller-supplied `ShoppingTravel`;
- performs no store ranking.

### Ordered two-store candidates

The two-store layer accepts explicit ordered `base -> added` store pairs and explicit additional travel.

A pair candidate is emitted only when:

1. both stores are declared;
2. they are not aliases for the same merchant/location/channel offer scope;
3. the base store already has a complete one-store basket;
4. every requested item therefore has a verified usable base price;
5. an added-store price replaces a base price only when it is usable, has the exact same money specification and is strictly cheaper;
6. equal or incomparable added-store prices remain assigned to the base;
7. the added store actually contributes at least one selected item.

This means the pair bridge cannot manufacture a complete basket by borrowing missing items from a second store. It constructs the cheapest exact basket only inside one caller-declared pair; it does not choose which pair or store is best.

### Final production decision boundary

`PracticalShoppingProductionDecisionEvaluator` reruns the production candidate bridge from raw inputs at the same point-in-time decision instant.

Before invoking the planner, it partitions candidates by the exact money specification declared by `PracticalShoppingPolicy.minimumSecondStopSavings`.

Different currency or fraction precision is excluded and retained for audit. There is no exchange-rate lookup, conversion or precision coercion. If no comparable candidate remains, the planner returns no coverage rather than guessing.

The final one-store-first ranking, incomplete-coverage behavior, savings threshold, travel cap and second-stop decision remain entirely in `PracticalShoppingPlanner`.

## Boundedness

Current explicit production bridge bounds:

- stores: at most 64;
- current-price bindings: at most 128;
- raw current-price requests: at most 128;
- ordered store pairs: at most 128;
- shared-core shopping request: at most 128 items.

No production bridge class owns a network client, hidden clock, geocoder, router, account, telemetry channel or provider-economic ranking signal.

## Verification trail

### Home

- `1a00bd68d58fa32688da87d97019b251e9e9585c` — Home session restoration regressions; workflow **120** (`33260649933`) passed
- `e994404fbbdd2462160f25bcdb36f44c86612a72` — immutable Home renderer; workflow **121** (`33260866925`) passed
- `37a0eb344d022b7f7e4c49d77771a5141068f7f5` — visible Practical Shopping Home; workflow **122** (`33261359768`) passed
- `7a9775a0f2d5e9a3442eab58df90a7482117d213` — remove empty Home host padding; workflow **123** (`33261460772`) passed
- `f9332a8e43a05601d65dfd4972ba06666c63c401` — derive auditable primary-plan explanation; workflow **124** (`33261712379`) passed
- `a627bc2fd459924b3869a65cc0721da2c80e3704` — render the projected explanation; workflow **125** (`33261956215`) passed
- `a93d02daa15cb689235fbc867dedf0ffe47e58b4` — bound retained Home query/lifecycle state; workflow **126** (`33262248609`) passed

### Production bridge

- `622addf724f40dc9d52a18de89c92a78ec384a7b` — preserve explicit aging evidence semantics; workflow **127** (`33262696816`) passed
- `08c9c5cb83dfc7f8d89c0403ddd43443e51cfc58` — bridge trusted current prices into one-store Practical Shopping candidates; workflow **128** (`33262970657`) passed
- `7585203a37d215ad64a3d1a078108a46f5351f8a` — construct bounded ordered two-store candidates without duplicating planner policy; workflow **129** (`33263320859`) passed
- `95e66daf01c5e492b776fb573c705de42ccddd1f` — point-in-time production candidate partition + existing planner decision; workflow **130** (`33263558876`) passed

## Next engineering slice

Do not connect this path to real retailer networking or route provider data through the fictional Home controller yet.

Before production Home wiring, review the new bridge's execution complexity and threading assumptions. In particular, current-price eligibility is deliberately re-established from raw inputs for each explicit binding; keep the trust boundary but verify that worst-case bounded work is suitable for mobile and does not recreate a main-thread lag path.

After that performance/complexity gate, the next adapter-facing work should be an explicit orchestration contract that supplies:

- resolved shopping intent -> exact production product identity bindings;
- explicit merchant/location/channel store scopes;
- current lifecycle-bound raw price requests;
- explicit user/store and base/additional travel facts.

Do not invent product identity from titles, infer route facts, enable Android networking, or treat currently unresolved provider/open-data research rights as production authorization merely to make that orchestration work.