# ValuePilot Practical Shopping MVP Status

Updated: 2026-09-02

Branch: `work/valuepilot-android-milestone`

Purpose: newest durable product/engineering checkpoint for the Practical Shopping MVP. Newer repository evidence overrides this file.

## Latest verified engineering head

`c2bb5f64d69d617ce898cf6f281ecf78c67d7e21` — `Explain Watch My Staples entry point`

GitHub Actions workflow run **33671233369** completed successfully (candidate run **33670610252** also passed).

The latest Saved-tab refinement gives the Watch My Staples launcher renderer-ready explanatory copy: “Choose recurring saved items and a usual store to check whether a future switch is worth the trip.” The copy appears only when the existing navigation-readiness gate has visible saved products and a store. It is explicitly a future-check explanation, not a live merchant or notification claim; the launcher still emits only the existing typed setup-navigation action.

The latest consumer-facing slice closes a Saved-backed Watch My Staples setup gap. Immutable setup presentation now supplies a deterministic selection summary such as “2 staples selected (2 minimum) · Usual store selected,” and the physical setup surface renders it below the guidance. The minimum remains the existing reducer-owned `MIN_WATCHED_SAVED_ITEMS_FOR_HANDOFF` constant, so the View does not infer readiness or duplicate selection policy. No identity, price, travel, evidence, notification, persistence, planner, ranking, or networking authority moved into the renderer.

The latest app-level slice adds bounded local-only cross-session retention around the existing progressive per-item Home details surface and typed `PracticalShoppingHomeSession.State`. The Home session store remembers the last bounded list, chicken choice, extra-stop preference and opaque request-details payload so explicit package count, preferred brand and exact-product intent can survive an app restart. It restores details only for the exact established request, drops oversized/corrupt values safely, and clears stale detail bytes while a draft is being edited. The store reuses the shared-core codec boundary and carries no planner, ranking, price, quantity arithmetic, evidence, clock, networking, or View authority. The existing sample plan object remains unchanged by item intent, and the fictional-plan/no-arithmetic disclosure stays explicit. The milestone provenance check accepts the repository's historical `work/valuepilot-*` candidate branches while excluding the milestone branch itself.

The Basket primary tab is now a real read-only continuation of Plan My Shop rather than placeholder copy. It receives the existing immutable Home presentation, preserves the exact projected plan object, and shows recognized items, unresolved items, complete or incomplete one-store results, any already-approved optional second stop, and the shopper's selected exact extra-stop rule. Empty, draft, refinement, and unresolved states cannot become a false plan and provide one typed action back to Home. Home and Basket share the same result-card View so complete totals, known subtotals, missing-price notices, travel, freshness/evidence, and second-stop details cannot drift between the two surfaces. The fictional/offline disclosure is repeated prominently on Basket. No shared-core planner/projector, provider integration, Android networking, ranking authority, or money calculation was duplicated or moved into a View.

Clean-source verification passed all 1,618 JVM tests (375 shared-core + 1,243 Android app) with zero failures/errors/skips, all 58 Android tasks, all 30 browser tests, Firefox packaging lint with zero findings, APK privacy inspection, and one-signer APK verification.

The current promoted Home slice keeps the fictional sample flow isolated while allowing users to remove recognized items or unresolved tokens and immediately re-plan. Removal actions carry opaque typed keys/tokens through the controller, preserve unknown and ambiguous states, and expose item-specific accessibility descriptions. Natural conjunctions (`and`/`&`) are treated as list syntax while unknown product words still remain explicit. Result cards label their evidence line “Price freshness,” clarifying that an unknown count refers to freshness rather than price. The list editor now displays the model's existing 240-character limit and physically retains no more than its existing one-character over-limit sentinel, so a very large paste cannot flow through lifecycle state while the honest over-limit error remains reachable. Immutable presentation carries the limit; the Android View binds only the matching counter and filter.

The extra-stop preference is now genuine progressive disclosure. It stays off the untouched, draft, clarification and error surfaces so `Plan my shop` remains the obvious first action. Once an actual complete or incomplete result exists, immutable render state exposes the persisted exact savings threshold; changing it continues through the existing typed controller and shared-core planner. The physical View only binds visibility and owns no status, money, policy or ranking decision.

Compare Here’s manual editor supports bounded per-entry removal and gives the user an explicit choice between Current shelf prices and Member prices. Its primary action stays disabled until two entries contain text and the user explicitly confirms that they are like-for-like alternatives. This readiness is a pure bounded presentation decision and intentionally does not parse price, quantity, currency or promotion evidence; malformed or incomplete evidence still reaches the existing exact route once the draft is ready and remains unknown/blocked rather than being guessed. Each physical editor block is now capped at the evidence adapter's existing 4,096-character limit, preventing arbitrarily large pasted text from being retained or persisted. Oversized legacy/restored blocks fail closed: the entry is cleared and visibly marked too long instead of being silently truncated into potentially rankable partial facts. The selected basis is carried through pure lifecycle state and the existing exact route; changing it invalidates an old comparison, draft restoration preserves it, malformed or legacy stored values safely default to CURRENT, and member mode continues to block products with no member price rather than falling back to current price. The entry instructions demonstrate the parser’s exact `Current price` and optional `Member price` labels. When fewer than two products have the selected evidence, the immutable projection names the missing basis; member mode explicitly states that current prices are not substitutes. No planner, provider, shared-core arithmetic or Android privacy boundary was changed.

The preceding projector implementation and fixture history remain below as historical context.

`87819246c02677198062bbf664213953c5ecfc30` — `Fix practical shopping projector test imports`

The app-level projector implementation was introduced at:

`0ae65ce2881a9812f91b0f92827339fea556f61c` — `Add immutable practical shopping UI projection`

Projector tests were introduced at `6d703f9eb57088fc3d2532cbe1f3562521d6fe04`. That first run failed only because the app test source set uses JUnit 4 while the new test imported `kotlin.test`. No production logic failed compilation. Commit `87819246...` changed only the test harness imports/assertion form to the existing JUnit 4 convention; it did not weaken or change the intended assertions.

GitHub Actions workflow run **112** (`33259058670`) completed successfully for `87819246...`.

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

No Android networking/provider integration was added. The existing privacy boundary remains intact.

The prior verified shared-core planning boundary remains:

- `dec7a4b6e1fab384e0ed62affbe77063c5e60d00` — `Add one-store-first practical shopping policy`
- `c26bc2de9f99e4f8b3bcbf248e4ad2f602259938` — `Test practical one-store-first shopping policy`
- workflow run **109** (`33258451478`) — complete success

## What the shared-core policy establishes

Source:

`android/shared-core/src/main/kotlin/com/valuepilot/core/PracticalShoppingPlan.kt`

Tests:

`android/shared-core/src/test/kotlin/com/valuepilot/core/PracticalShoppingPlanTest.kt`

The planning boundary is deliberately small, deterministic and platform-neutral.

It introduces bounded types for:

- shopping-request item identities
- store identities
- one-store plan candidates
- two-store optional-extra-stop candidates
- explicit travel distance/time metadata
- explicit freshness-summary metadata supplied by upstream policy
- exact known basket cost using existing `Money`
- explicit minimum second-stop savings
- explicit maximum additional travel time/distance
- deterministic one-store-first decision output

Permanent decision rules now tested:

1. **Complete basket beats incomplete basket.** A suspiciously cheap known subtotal cannot make an incomplete store look better than a complete store.
2. Complete baskets compare by exact `Money`, then travel, then stable store key.
3. Incomplete baskets are **not ranked by known subtotal**, because different missing item sets make those totals non-comparable. Best coverage is chosen first, then travel/stable key.
4. A second store is not even evaluated unless the primary one-store plan covers the complete requested basket.
5. A two-store plan must start from the chosen primary store and cover the same complete requested basket.
6. A second stop must clear the explicit savings threshold and explicit route caps. No hidden value-of-time or invented hassle score exists.
7. The highest exact savings wins among second-stop candidates already inside the user's explicit route constraints.
8. Currency/fraction precision mismatch fails closed.
9. Candidate items outside the shopping request fail closed.
10. Requests/candidate collections are bounded: 128 shopping items, 64 one-store candidates, 128 two-store candidates.
11. Fresh/stale/unknown evidence counts must exactly match covered-item count. The planner itself owns no clock and does not secretly decide freshness.

## What the verified UI projection establishes

Source:

`android/app/src/main/java/com/valuepilot/app/PracticalShoppingUiProjector.kt`

Tests:

`android/app/src/test/java/com/valuepilot/app/PracticalShoppingUiProjectorTest.kt`

The projector receives an already-decided `PracticalShoppingDecision` and formats immutable UI-ready state only. It does **not** rank stores, recalculate savings, infer missing prices or invent a convenience score.

Verified presentation rules:

- complete one-store decisions render as a basket total;
- incomplete decisions render only as a **known subtotal**, with an explicit notice that it is not a complete basket total;
- a second-stop card is emitted only when shared-core already decided `RECOMMENDED`;
- a rejected second stop becomes one short `not worth it` message rather than another competing recommendation card;
- no-coverage state does not fabricate a basket;
- internal store keys remain outside normal consumer state strings;
- missing consumer display names fail closed rather than leaking internal identifiers;
- money formatting stays exact using decimal arithmetic and is regression-tested beyond the IEEE-754 exact-integer range;
- travel formatting is deterministic;
- stale and unknown evidence remain visibly stale/unknown rather than being upgraded by presentation.

## Current consumer product model

ValuePilot is now a **shopping decision assistant**, not a giant price-table product.

Primary promise:

> **Tell ValuePilot what you need and it should tell you the cheapest sensible way to get the shopping done.**

The app must save mental effort as well as money.

The app must be hyper-polished, extremely fast, simple and non-confusing. The underlying system may be sophisticated; the normal user should see only the minimum information needed for the decision.

### One-store-first remains permanent default

Do not recommend a second store for small arithmetic savings.

The initial product policy may use a roughly `$15` minimum incremental savings threshold for a second stop, subject to later user testing. The user should eventually be able to change that preference.

Do not hide parking, queueing, walking, loading or human inconvenience inside an invented dollar score. Keep savings and additional travel explicit.

Three-store plans are not a normal MVP behavior.

## Home / Plan My Shop UX target

The Home hero should eventually reduce to one obvious question:

> **What do you need?**

A user may type or speak a natural list such as:

`chicken eggs milk bananas bread`

The UI should turn this into shopping intents without showing hundreds of SKUs immediately.

The first normal result should be **one recommendation card**, not a dashboard:

- recommended nearby store/pickup option
- exact known/estimated-from-valid-evidence basket subtotal with clear coverage
- matched / missing items
- travel time/distance
- freshness/confidence summary
- short `Why this store` explanation
- optional note saying another stop is not worth it

Advanced controls belong behind progressive disclosure.

## Product-intent / brand / quantity / ingredient UX

The UI must distinguish a broad shopping intent from a specific product while making both feel simple.

Examples:

- `eggs` = category intent; sensible default might be 12 large unless the user has a remembered preference.
- `milk` = category intent; the app can use a simple default such as regular 2% / 4 L only where appropriate and visibly editable.
- `chicken` is ambiguous enough to need a tiny one-tap refinement such as breast / thighs / drumsticks / whole / ground.
- `Neilson 2% Milk 4 L` = specific packaged product intent.
- loose bananas = produce/category identity rather than pretending a universal packaged GTIN exists.

The normal list screen should show only concise intent rows. Tapping an item progressively reveals:

- quantity/package preference
- exact brand vs brand-flexible
- product alternatives
- ingredients/allergens/nutrition where relevant
- exact product/package identifiers and evidence details only at deeper detail level

Do not make users answer brand, size, organic, loyalty, substitution, radius and other questions before getting value.

Remember prior choices locally where appropriate so the app asks fewer questions over time.

Unknown ingredient/allergen information remains unknown; never interpret missing evidence as safe.

## Retention / anti-uninstall requirement

The first session must produce value in seconds. If the first experience is missing prices, stale data, dozens of confusing products or a pile of setup questions, the app will be deleted.

The product should progressively require **less** work each week:

- remember normal sizes/brands/flexibility
- remember one-store preference and travel constraints
- offer recent/recurring staples
- let the user rebuild a normal shopping list quickly

The retention engine should eventually be `Watch My Staples`, but alerts must be high-value basket-level decisions, not penny-saving price spam.

A useful notification is closer to:

> several of your normal staples make Store B about $18 cheaper this week and it is within your normal route

not:

> milk is $0.30 cheaper somewhere else

## Compare Here remains the second major vertical slice

When cross-store coverage is incomplete, ValuePilot must still be useful inside the store.

`Compare Here` should let a shopper scan/point at products or shelf labels and immediately compare package sizes/promotions with exact deterministic unit-value math.

The user's immediate comparison benefit comes first. Any evidence contribution is a secondary side effect with consent, not unpaid crowdsourcing work.

Receipt upload/import is likewise optional and must directly benefit the user (list reconstruction, spending/history or comparison); it is not the core data-acquisition strategy.

## Pickup / commerce direction

Pickup is strategically better aligned with ValuePilot than making third-party delivery marketplaces the core product.

Long-term, the same product may have separate commerce offers/channels such as:

- shop in store
- retailer pickup / curbside pickup
- delivery marketplace / retailer delivery

These are not the same Offer. Different prices/fees/availability must remain separate.

MVP commerce should be conservative:

1. ValuePilot recommends the practical store/pickup choice.
2. Phase 1 may hand the user to the retailer's official app/site.
3. Cart transfer/deep links come only where authorized.
4. Reservation/checkout/payment integrations come only under explicit approved integrations.

Do not make payment/checkout a current MVP dependency.

## Geographic launch strategy

Architect for international markets but **launch locally/deeply**.

Do not launch Canada + USA + UK + Australia + New Zealand simultaneously with shallow data and weak QA.

Current strategic sequence is tentative rather than an engineering commitment:

1. GTA / one dense Canadian metro
2. Ontario / additional Canadian metros
3. Canada
4. one U.S. metro to validate international portability
5. broader U.S. expansion if metrics justify it
6. Australia is attractive for a later full-country expansion because major grocery/pickup coverage is concentrated
7. UK later with clear differentiation from established comparison products
8. New Zealand later; technically concentrated but smaller and already has comparison competitors

Core architecture should nevertheless remain country-neutral: currency, locale, units, retailer banners, loyalty systems, route rules and commerce channels must not be hard-coded as Canada-only business logic.

## Free/open data stance

There is still no known legitimate `$0` source containing every fresh store-specific grocery price across Canadian retailers.

Free/open data should solve recognition/location where it is good at doing so, not be misrepresented as complete live pricing:

- Open Food Facts — packaged-product recognition/metadata under its licence
- USDA FoodData Central — public-domain/CC0 enrichment, not Canadian availability proof
- validated PLU/produce source where reuse rights permit
- Open Prices — supplemental proof-backed price observations, not nationwide live coverage
- OpenStreetMap — store/location/routing foundation where appropriate
- user-triggered evidence adapters — immediate user benefit first
- merchant self-service feeds later
- authorized commercial/affiliate/provider feeds as accelerators when available

Company-by-company approvals are supplementary and should not block launch.

## Provider tracks still running in parallel

- Jamieson advertiser permission email already sent after Rakuten clarified that advertiser approval is required for application use/comparison. Do not resend while waiting unless the reply is incomplete.
- GS1 Canada ECCnet inquiry remains pending unless newer evidence arrives.
- Walmart Canada currently has a Rakuten advertiser eligibility pre-filter. Do not falsify account/channel details to bypass it; Walmart is not a launch blocker.

## Next engineering slice

Do **not** jump directly to real retailer data or networking.

The fictional fixture/controller and visible Home consumer path are complete and verified at the latest head. The requirements below are the historical acceptance criteria for that completed boundary; future work should define a separate typed item-detail/quantity contract or provider-neutral production orchestration boundary before changing planner authority.

Requirements:

1. Fixture data must be unmistakably SAMPLE/FICTIONAL and must never look like a real merchant claim.
2. The fixture/controller may resolve only its own small known sample vocabulary; it must not be mistaken for a production product resolver.
3. Ambiguous intents such as bare `chicken` must not silently become an authoritative specific product. Either request one tiny refinement or keep the ambiguity explicit.
4. The controller must call `PracticalShoppingPlanner`; it must not duplicate one-store/second-stop ranking rules.
5. The controller must call `PracticalShoppingUiProjector`; the future renderer receives immutable UI-ready state only.
6. Unknown/unrecognized shopping intents must remain explicit rather than being dropped silently.
7. Preserve bounded input sizes and deterministic behavior.
8. Keep Home layout changes separate until the controller is verified.
9. Preserve Search separation and Android's no-network/no-account/no-telemetry boundary.
10. Add tests for natural list input, ambiguous chicken, duplicate words, unknown items, complete/incomplete fixture coverage, and the second-stop threshold before wiring Home.
11. Run the full existing CI gate before calling the fixture/controller boundary complete.

No device/user action is required for this checkpoint.
