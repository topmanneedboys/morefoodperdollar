# ValuePilot Practical Shopping Checkpoint

Updated: 2026-09-04

Branch: `work/valuepilot-android-milestone`

This checkpoint records the verified Practical Shopping Home slice plus the provider-neutral production evidence-to-decision path after the first execution-complexity hardening. Newer repository evidence overrides this file.

## Latest verified engineering head

`7c7be8d5215d2f6261049398700486252dda407c` — `Carry no-coverage summary into Basket`

Candidate workflow **33887664746** and milestone provenance workflow **33888215450** passed for the exact SHA. Basket now forwards the existing Home renderer-owned `0 of N items priced yet.` summary whenever the already-projected result has no primary plan, keeping coverage clarity intact when the shopper switches destinations. It remains hidden for empty/refinement states and for any projected primary plan that already exposes exact coverage text. This is reversible presentation/accessibility work only; it does not duplicate or alter the shared planner/projector and adds no price, offer, store, availability, network, persistence, or provider-economics authority. Focused tests and full candidate/milestone verification passed; the Windows run retains the known 18 line-ending-sensitive boundary failures and physical-device ergonomics remain open.

`1be6628b3fb48b67fa7ee272a8ed3a3a35984529` — `Clarify Home no-coverage summary`

Candidate workflow **33885576989** and milestone provenance workflow **33886216516** passed for the exact SHA. Home now shows a compact renderer-owned `0 of N items priced yet.` summary when the already-projected result has no primary plan, complementing the existing per-item unknown-price notices. The summary stays hidden for empty/refinement states and for any projected primary plan that already exposes coverage. This is reversible presentation/accessibility work only; it does not duplicate or alter the shared planner/projector and adds no price, offer, store, availability, network, persistence, or provider-economics authority. Focused tests and full candidate/milestone verification passed; the Windows run retains the known 18 line-ending-sensitive boundary failures and physical-device ergonomics remain open.

`ec7ed377c27e7e6b4ece9030bedc5e7cbf997188` — `Add signed GTA GVA store directory snapshot`

Candidate workflow **33883155939** and milestone provenance workflow **33883690098** passed for the exact SHA. The shell’s `Data status` action now reports the signed, ODbL-attributed OpenStreetMap directory summary: 6,093 source-listed locations (4,311 GTA; 1,782 Metro Vancouver) and its observed date. Android verifies the manifest/source hashes, detached signature, rights gates, launch geography and freshness before exposing any row; invalid or stale artifacts expose no records. The directory is explicitly location-only and cannot become a product, package, price, offer, stock, availability or ranking fact. The existing shared 30,000-record identity rail, `0` authorized current offers, private-observation recovery, flyer absence, offline connectivity and fictional Home/Search sample disclosure remain intact. Candidate Linux Android/browser/privacy/signing/release and Python snapshot verification passed; the Windows run retains the known 18 line-ending-sensitive boundary failures. Physical-device ergonomics remain open.

`16e1e68af516df2f4c4ac27b7f9105581a18a150` — `Add barcode identity handoff to Compare Here`

Candidate workflow **33876560165** and milestone provenance workflow **33877137830** passed for the exact SHA. The primary Scan & Compare surface now exposes a user-triggered barcode action alongside its photo routes. The shared capture activity returns one checksum-valid GTIN; the existing signed offline identity rail is searched off the UI thread and an explicit, reviewable name choice can fill only an empty comparison entry. A deterministic draft helper trims and bounds the identity, preserves every existing row, and refuses to overwrite a full draft. The shopper still adds package quantity and observed price and passes the existing currency, price-basis and like-for-like gates. The barcode identity remains identity-only: it cannot establish package quantity, price, store, stock, availability, freshness, ranking, a live offer or network access. Focused tests, candidate verification, lint/build, privacy, signing and release-bundle provenance passed; the Windows run retains the known line-ending-sensitive failures and physical-device barcode ergonomics remain unverified.

`1f3f8763b525d8b6533b9b012ef5a0adc2476946` — `Add offline barcode identity handoff to Good Price`

Candidate workflow **33874530113** and milestone provenance workflow **33875052835** passed for the exact SHA. The first-class Good Price surface now launches a user-triggered, cache-scoped barcode photo/import activity. On-device decoding is bounded and accepts exactly one distinct checksum-valid GTIN; invalid/unrelated codes are ignored and multiple distinct product codes remain blocked. Good Price then searches the already-signed GTA/Metro Vancouver identity snapshots off the UI thread and shows a reviewable identity-name suggestion. The explicit positive action only fills the editable product-name draft; package quantity, observed price, currency and the existing exact/private-memory gates remain required. Identity data cannot become a current offer, store, stock, availability, freshness or ranking fact. The activity is non-exported, CAMERA is optional, temporary captures are cleaned up across terminal and lifecycle paths, and APK privacy inspection still finds no INTERNET or ACCESS_NETWORK_STATE. Deterministic barcode resolver, identity projection and Android source-boundary tests pass. The full Windows JVM task retains the known 18 line-ending-sensitive boundary failures; Linux candidate verification, lint/build, privacy, signing, browser, Python and release-bundle gates are green. Physical-device testing is still required before calling barcode ergonomics complete.

`1b47547ddb04cb06afc5ed8b6400d8429705a5f9` — `Clarify pending extra-stop evaluation on Home`

Candidate workflow **33871829169** and milestone provenance workflow **33872307030** completed successfully for the exact SHA. When a Home result has a missing usable price, the collapsed extra-stop disclosure now says `Not evaluated yet` in its immutable renderer summary, matching the existing expanded notice that another stop is not evaluated until every requested item has a usable price. Complete plans retain the original threshold summary. This is a reversible presentation-only correction; no planner, ranking, price, offer, store, stock, availability, provider-economics or persistence authority moved into the surface.

## Previous verified engineering head (superseded)

`6743181977f1500772444599a667284b8d7ea2e3` — `Add actionable Home private history review route`

Candidate workflow **33867138431** and milestone provenance workflow **33867613305** completed successfully for the exact SHA. When readable nonempty private comparison history exists, Home now exposes a renderer-owned `Review private price history` action that opens the existing Scan & compare screen. Empty history stays quiet and unavailable history stays on its recovery notice. The existing count-only, name/package/promotion mismatch and non-live-price disclosures remain unchanged. Deterministic Home history, renderer, lifecycle and View-boundary coverage protects visibility, callback ownership and route wiring. This is reversible navigation only; no planner, ranking, price, offer, store, stock, availability, provider-economics or persistence authority moved into the surface.

## Previous verified engineering head (superseded)

`22e45377d40511e5a42aa786264cb562f15a1336` — `Point Home memory summary to review route`

Candidate workflow **33864992864** and milestone provenance workflow **33865459380** completed successfully for the exact SHA. When nonempty private comparison history had no matching Home list label, the renderer-owned summary told the shopper to open Scan & compare prices to review it. This was presentation-only and added no action, storage, planner, ranking, price, offer, store, stock, availability, provider-economics or persistence authority.

## Previous verified engineering head (superseded)

`08657541629f5e7beb44c046573a8ae5d79be22a` — `Clarify offline catalog list replacement action`

Candidate workflow **33864027611** and milestone provenance workflow **33864356368** completed successfully for the exact SHA. The Home offline identity-match dialog now labels its positive action `Replace list word` and gives it a matching assistive-technology description that states the action only edits the unresolved Home query and does not confirm an exact product, price or availability. The existing bounded identity lookup, identity-only disclosure, review-before-Plan step, fictional planner and query-only replacement boundary remain unchanged. Deterministic Home lifecycle-boundary coverage protects the new wording and no-network boundary; no planner, ranking, price, offer, store, stock, availability, provider-economics or persistence authority moved into the surface.

## Previous verified engineering head (superseded)

`e02e358eb29ea28e4287ebef717d2d800d2b0720` — `Record Home private memory summary milestone`

Candidate workflow **33862278396** and milestone provenance workflow **33862848704** completed successfully for the exact SHA. Scan & Compare now has a direct `Take a price photo` action in addition to photo import. CAMERA is requested only after the user taps the action. The capture is written to a cache-scoped FileProvider URI with explicit temporary read/write grants and sent through the existing bounded on-device OCR suggestion/review route. Both photo actions share one in-flight gate; stale/lifecycle callbacks are ignored; temporary files are deleted on success, failure, cancellation and teardown; no-camera, denied-permission and camera-error states preserve existing entries. The OCR status is a polite accessibility live region. Home additionally exposes a bounded count of nonempty private comparison observations when no current item name matches, with deterministic helper, renderer and View-boundary coverage. This does not add Home planner/ranking/current-offer authority or Android networking.

The prior verified head was `ed9fc561b01dcdbb04c80bee965535aa8f81752d` (`Add first-class good price check`). The same release includes the signed 30,000-record shared Canada identity catalog and atomic multi-region release pointer described in the current state checkpoint. Real evidence-backed Home/private price-book wiring remains a later milestone; physical-device testing is still required before declaring camera/OCR or launch UX complete.

The latest Home presentation refinement makes automatic private comparison memory discoverable even when no current item name matches. It exposes only a bounded on-device observation count and keeps the name-based context, package/promotion mismatch and non-live-price boundary explicit. Empty memory stays quiet; unreadable memory retains the existing recovery notice and suppresses row context. The change is renderer/View presentation only and is covered by deterministic helper, renderer and boundary tests; it does not alter the fictional plan or add current-offer, store, availability, planner, ranking or network authority.

## Previous verified engineering head (superseded)

`c67286f83bc23f53f31ef72d311ceb22e3716041` — `Reveal extra-stop preference after planning`

GitHub Actions workflow run **33626580579** completed successfully (candidate run **33626116787** also passed).

Since the earlier production-input checkpoint, the visible fictional Home flow now supports typed removal of resolved items and unresolved tokens. Each removal reuses the existing controller -> shared-core planner -> projector path, preserves unknown/ambiguous state, and leaves the Android no-network boundary unchanged. Removal buttons expose item-specific accessibility descriptions, ordinary conjunctions (`and`/`&`) no longer become false unknown groceries, and Home result evidence is labeled “Price freshness” so freshness-unknown is not mistaken for an unknown price.

The advanced extra-stop preference is now hidden until an actual Home result exists, keeping the first screen focused on the single `Plan my shop` action. Complete and incomplete result states expose the persisted exact threshold for immediate typed replanning. The renderer owns this presentation decision; the View only binds immutable visibility and resets its local expansion state when the control becomes unavailable.

The Compare Here manual entry surface now also offers a per-entry Remove action once more than the minimum two product slots exist. Removing an extra slot preserves the other entry text, renumbers the remaining slots, re-enables the bounded add action, and clears the existing like-for-like confirmation through the activity reducer. With exactly two slots, removal clears only the selected slot so the comparison shape remains usable.

The earlier production-input head remains recorded in the historical verification list below.

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

The provider-neutral path is:

`raw provider offer inputs`

`-> ProductionCurrentPriceEligibilityEvaluator`

`-> PracticalShoppingProductionCandidateBridge`

`-> PracticalShoppingProductionPlanCandidateBridge`

`-> PracticalShoppingProductionDecisionEvaluator`

`-> PracticalShoppingPlanner`

The production path is not connected to Home yet.

### Current-price and store binding

For each explicitly supplied shopping-item/store binding, the bridge establishes current production price eligibility at the supplied evaluation instant. It does not trust a detached `Money`, staged offer, old eligibility result or prior shopping candidate as continuing authority.

A usable price must match all of the following after that point-in-time evaluation:

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

### Same-instant current-price batching

The first verified bridge version re-ran the full raw request set separately for every item/store binding. With the hard bounds of 128 bindings and 128 raw requests, that allowed up to `128 × 128 = 16,384` raw production request evaluations inside one decision invocation.

`2e6a71180738bb1d19be64c1eb850d6730bb139e` removes that repeated raw-evaluation path without caching authority across decisions.

`ProductionCurrentPriceEligibilityEvaluator.evaluateAll` now:

1. re-runs each raw current-price request exactly once for the current invocation, against the current lifecycle/disposition registries and supplied evaluation instant;
2. keeps the resulting evaluation set internal to that invocation;
3. derives each candidate-specific current-price eligibility/conflict result from that same immutable set;
4. discards the batch after the call rather than treating it as a durable authorization token.

Worst-case raw production request evaluations therefore drop from 16,384 to at most 128 per production decision invocation.

A semantic-equivalence regression compares batched results with the original one-candidate evaluator for a same-scope conflicting price set and requires identical blockers, factual resolution, acceptance decision, selected evidence claim and final eligibility.

This optimization changes execution shape only. It does not weaken authorization, lifecycle, namespace disposition, freshness, scope or factual conflict rules.

### Evidence freshness semantics

`ShoppingPlanEvidenceSummary` preserves `AGING` separately from `FRESH`, `STALE` and `UNKNOWN`.

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

The pair bridge cannot manufacture a complete basket by borrowing missing items from a second store. It constructs the cheapest exact basket only inside one caller-declared pair; it does not choose which pair or store is best.

### Final production decision boundary

`PracticalShoppingProductionDecisionEvaluator` reruns the production candidate bridge from raw inputs at the same point-in-time decision instant.

Before invoking the planner, it partitions candidates by the exact money specification declared by `PracticalShoppingPolicy.minimumSecondStopSavings`.

Different currency or fraction precision is excluded and retained for audit. There is no exchange-rate lookup, conversion or precision coercion. If no comparable candidate remains, the planner returns no coverage rather than guessing.

The final one-store-first ranking, incomplete-coverage behavior, savings threshold, travel cap and second-stop decision remain entirely in `PracticalShoppingPlanner`.

## Boundedness and threading

Current explicit production bridge bounds:

- stores: at most 64;
- current-price bindings: at most 128;
- raw current-price requests: at most 128;
- ordered store pairs: at most 128;
- shared-core shopping request: at most 128 items;
- raw current-price acceptance/claim evaluations after batching: at most 128 per decision invocation.

No production bridge class owns a network client, hidden clock, geocoder, router, account, telemetry channel or provider-economic ranking signal.

The production path is synchronous shared-core code. When eventually wired into Android, the production evidence-to-decision evaluation must run off the main/UI thread and publish only a completed immutable state back to the view. No claim is made yet that production planning has been benchmarked on the Motorola device.

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
- `2e6a71180738bb1d19be64c1eb850d6730bb139e` — batch same-instant production current-price evaluation while preserving candidate conflict semantics; workflow **131** (`33263933735`) passed

## Next engineering slice

Do not connect this path to real retailer networking or route provider data through the fictional Home controller yet.

The identified raw-evaluation complexity issue is now hardened. The next adapter-facing step is an explicit provider-neutral **Practical Shopping orchestration input contract** that can assemble a production evaluation request without manufacturing evidence.

That contract should carry only already-established facts and raw evidence inputs:

- resolved shopping intent -> exact `ProductionProductEvidenceKey` binding;
- explicit merchant/location/channel store scopes;
- raw current-price eligibility requests and current lifecycle/disposition registries at execution;
- explicit user-to-store and base-to-added-store travel facts;
- explicit planning policy and evaluation instant.

It must validate cross-reference completeness and bounds before invoking `PracticalShoppingProductionDecisionEvaluator`, but it must not:

- resolve products from names/descriptions/images;
- infer merchant/location/channel identity;
- calculate or fetch route facts;
- enable Android networking;
- create provider authorization;
- upgrade open-data/reference evidence into current retailer offers;
- persist a production decision or eligibility result as continuing authority.

After that contract is regression-tested, Android can later gain an off-main-thread coordinator that consumes such an assembled request. Provider networking/rights and actual Home production activation remain separate future gates.
