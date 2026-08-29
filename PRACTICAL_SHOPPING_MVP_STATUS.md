# ValuePilot Practical Shopping MVP Status

Updated: 2026-08-29

Branch: `work/valuepilot-android-milestone`

Purpose: newest durable product/engineering checkpoint for the Practical Shopping MVP. Newer repository evidence overrides this file.

## Latest verified engineering head

`c26bc2de9f99e4f8b3bcbf248e4ad2f602259938` — `Test practical one-store-first shopping policy`

Parent code commit:

`dec7a4b6e1fab384e0ed62affbe77063c5e60d00` — `Add one-store-first practical shopping policy`

GitHub Actions workflow run **109** (`33258451478`) completed successfully for `c26bc2de...`.

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

## What the new shared-core policy establishes

New source:

`android/shared-core/src/main/kotlin/com/valuepilot/core/PracticalShoppingPlan.kt`

New tests:

`android/shared-core/src/test/kotlin/com/valuepilot/core/PracticalShoppingPlanTest.kt`

The new planning boundary is deliberately small, deterministic and platform-neutral.

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

The next focused implementation should be the application/presentation boundary for a tiny **fictional Practical Shopping demo** that consumes `PracticalShoppingDecision` safely.

Requirements for that slice:

1. Keep shared-core planning policy as the sole owner of one-store/second-stop decision rules.
2. Create immutable app/UI-ready state with presentation strings/fields only; UI must not re-rank candidates.
3. Use a tiny clearly labeled fictional multi-store fixture. No real merchant claims.
4. Home should move toward one input / one primary recommendation, but do not destroy the verified Search separation.
5. Explicitly represent complete vs incomplete coverage and `second stop not worth it` without fabricating missing prices.
6. Keep advanced settings out of the default path.
7. Preserve Android's current no-network/no-account/no-telemetry boundary.
8. Add bounded tests before exposing the flow broadly.
9. Verify browser checks, shared-core/app tests, lint, APK, JVM summary, privacy gate, packaging and artifacts before calling the slice complete.

No device/user action is required for this checkpoint.
