# ValuePilot Continuation Checkpoint

Updated: 2026-09-03

Branch: `work/valuepilot-android-milestone`

## Current verified engineering head

The promoted milestone is now `b6018ab9d08f22558f01c0fe36609dad832b22f5` (`Search offline identity snapshots across supported metros`), verified by candidate workflow **33809477765** and milestone provenance workflow **33809973694**. The offline catalog refresh boundary is deterministic and offline for the supported Greater Toronto Area and Metro Vancouver identity snapshots, and Home now searches both independently region-bound snapshots: identical identities are deduplicated, conflicting record ids are omitted, merged work is capped, and the combined lookup fails closed if either region is not admitted. Explicit source, rights, timestamps, and signing keys remain required; all candidates are selected, built, signature-verified, and coverage-checked before promotion; and a regression leaves existing per-region pointers unchanged. It remains identity-only and does not claim current prices, offers, stock, availability, or planner/ranking authority. Clean-source verification passed 397 shared-core tests and 1,333 Android app tests with zero failures, all 58 Android tasks, 61 Python tests, 30 browser tests, Firefox lint with zero findings, APK privacy inspection with no INTERNET/ACCESS_NETWORK_STATE, one-signer verification and exact-SHA release-bundle provenance.

`5f36a19e3467fcf58a0bca84d5864f56dcdf74d6` (`Dismiss stale Saved confirmations on route changes`) is the promoted milestone, verified by candidate workflow **33756824438** and milestone provenance workflow **33756823998**. Saved now dismisses its window-level “Clear all” confirmation whenever the shell leaves Saved, including the Compare route, so a destructive confirmation opened for Saved cannot float over Home, Basket, Search or another destination. This is presentation/lifecycle-only: no planner, ranking, pricing, evidence, persistence, provider, provider-economics or network authority changed. Clean-source verification passed 1,696 JVM tests (375 shared-core + 1,321 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`cff0a346fb3ae37f59a52d92cdc718cd053ee030` (`Dismiss stale Home item details dialogs`) was the promoted milestone, verified by candidate workflow **33748458980** and milestone provenance workflow **33748992285**. The Home item-details editor is lifecycle-bound to the Home route: opening a new editor dismisses any prior instance, leaving Home or opening Compare dismisses it, and Activity teardown releases it. This prevents an old package-count/brand/exact-product dialog from remaining visibly actionable over another destination or reappearing with stale Home state. The change is presentation/lifecycle-only and does not alter the typed Home session, planner, ranking, pricing, evidence, persistence, provider, provider-economics or network authority. Clean-source verification passed 1,689 JVM tests (375 shared-core + 1,314 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`8182c1dd6bb6e7aea9c1fc22f5c40371eac1dec1` (`Name observed-price Saved selection actions`) was the promoted milestone, verified by candidate workflow **33747358617** and milestone provenance workflow **33747877782**. Observed-price Saved product/store controls now expose projected, display-name-specific accessibility descriptions such as “Select saved product Whole Milk,” so assistive technology can identify exactly which safe saved choice a typed action changes. This is presentation-only and does not alter Saved identity, persistence, prefill, planner, ranking, pricing, evidence, notification, provider, provider-economics or network authority. Clean-source verification passed 1,688 JVM tests (375 shared-core + 1,313 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`b2c93b52cb9fa47aac6bad3cdc5b2587b2fc515c` (`Fail closed observed-price Saved selection`) was the promoted milestone, verified by candidate workflow **33746227407** and milestone provenance workflow **33746657577**. The observed-price Saved-selection renderer now updates every product/store/clear-selection and prefill-check button immediately when its typed lifecycle owner is installed or cleared, so a detached surface cannot leave already-rendered controls looking actionable. This is presentation/lifecycle-only and does not alter Saved identity, persistence, prefill, planner, ranking, pricing, evidence, notification, provider, provider-economics or network authority. Clean-source verification passed 1,688 JVM tests (375 shared-core + 1,313 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`62c931ac136b6b4fd7853069cc1359a545b14bac` (`Dismiss detached Saved confirmations`) was the promoted milestone, verified by candidate workflow **33745130893** and milestone provenance workflow **33745673251**. Saved now dismisses an open “Clear all” confirmation when its lifecycle owner detaches, and when a fresh immutable projection replaces the state that opened it, so a detached or stale dialog cannot remain visibly actionable. The existing typed action is still emitted only after explicit confirmation; this is presentation/lifecycle-only and does not alter Saved persistence, identity, planner, ranking, pricing, evidence, notification, provider, provider-economics or network authority. Clean-source verification passed 1,687 JVM tests (375 shared-core + 1,312 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`fda5749f1a821f369a6a170c4175e3d9e0c912b5` (`Collapse detached Home extra-stop settings`) was the promoted milestone, verified by candidate workflow **33743998315** and milestone provenance workflow **33744536925**. Clearing the Home extra-stop owner now collapses an already-expanded settings panel immediately, so detached/reused Home surfaces cannot leave stale rule choices visibly open; the change is presentation/lifecycle-only and does not alter the existing immutable planner result. Clean-source verification passed 1,686 JVM tests (375 shared-core + 1,311 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`3e8c94865de43cb0d20fbe353b56b53afb4558c2` (`Release Home owners when activity is destroyed`) was the promoted milestone, verified by candidate workflow **33743220204** and milestone provenance workflow **33743655775**. MainActivity now releases every Home and Basket callback on destruction, so detached/reused surfaces cannot retain actionable owners or an Activity reference; the change is lifecycle-only and does not alter the existing immutable Home result rendering. Clean-source verification passed 1,685 JVM tests (375 shared-core + 1,310 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`daa70e4c9d62305a83022758b30cdf9072abc38b` (`Fail closed observed price draft editors`) was the promoted milestone, verified by candidate workflow **33741793189** and milestone provenance workflow **33742341341**. The price, observed-time, and proof-reference draft editors now update all text, radio, and Apply controls immediately when typed owner callbacks are installed or cleared; detached evidence drafts cannot remain editable without their owner, and each editor starts disabled. This is presentation/lifecycle-only: no parsing, policy, planner, ranking, pricing, evidence, persistence, clock, provider, provider-economics or network authority changed. Clean-source verification passed 1,684 JVM tests (375 shared-core + 1,309 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`941f2c267f4913a7554ae7593f8df85bb9884af9` (`Fail closed observed price confirmation action`) was the promoted milestone, verified by candidate workflow **33740867817** and milestone provenance workflow **33741392931**. The observed-price confirmation button now updates its enabled state immediately when the typed owner callback is installed or cleared, and remains inert before the first immutable render; a detached confirmation surface cannot look ready to submit evidence. This is presentation/lifecycle-only: no policy, planner, ranking, pricing, evidence, persistence, clock, provider, provider-economics or network authority changed. Clean-source verification passed 1,684 JVM tests (375 shared-core + 1,309 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`6ac3ae2cbc6a6a4a855017911d060c458b795408` (`Fail closed Basket navigation on owner changes`) was the promoted milestone, verified by candidate workflow **33739991354** and milestone provenance workflow **33740563023**. Basket’s Open Home navigation control now updates its enabled state immediately when the typed owner callback is installed or cleared, and remains inert before the first immutable render; a detached Basket surface cannot look actionable. This is presentation/lifecycle-only: no policy, planner, ranking, pricing, evidence, persistence, clock, provider, provider-economics or network authority changed. Clean-source verification passed 1,684 JVM tests (375 shared-core + 1,309 Android app), all 58 Android tasks, 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`bad95f10c83235596532ed1caf05793ad376219c` (`Fail closed Home controls on owner changes`) was the promoted milestone, verified by candidate workflow **33739072386** and milestone provenance workflow **33739628868**. Home query editing, submit, compare, item-detail/remove, refinement, and extra-stop controls now update their enabled state immediately when typed owner callbacks are installed or cleared; a detached Home surface cannot look actionable or accept edits without its owner, and controls remain inert before the first immutable render. This is presentation/lifecycle-only: no policy, planner, ranking, pricing, evidence, persistence, clock, provider, provider-economics or network authority changed. Clean-source verification passed 1,683 JVM tests (375 shared-core + 1,308 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`d03bba4aa08ff1b21889f857aa6e0f1fcf37663d` (`Fail closed Watch setup controls on owner changes`) was the promoted milestone, verified by candidate workflow **33737614216** and milestone provenance workflow **33738144350**. Watch Saved selection controls, policy Apply/no-limit controls, and policy editors now update their enabled state immediately when typed owner callbacks are installed or cleared; detached surfaces cannot look actionable or remain editable without their owner. This is presentation/lifecycle-only: no policy, planner, ranking, pricing, evidence, persistence, clock, provider, provider-economics or network authority changed. Clean-source verification passed 1,682 JVM tests (375 shared-core + 1,307 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`697c6319d40e4094b607135964f3a791e774c3be` (`Fail closed Saved launchers when owner clears`) was the promoted milestone, verified by candidate workflow **33736212427** and milestone provenance workflow **33737249953**. The Saved Watch My Staples and observed-price launchers now update their enabled state immediately when the lifecycle owner installs or clears the typed callback, so already-rendered Saved entry points cannot continue to look actionable after detachment. This is presentation/lifecycle-only: persistence, identity, provider execution, ranking, evidence, clock, provider economics and network authority remain unchanged. Clean-source verification passed 1,682 JVM tests (375 shared-core + 1,307 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`d15fa825dc9e429a4f8043a543bcdba012a571ab` (`Fail closed Saved controls when owner clears`) was the promoted milestone, verified by candidate workflow **33735195361** and milestone provenance workflow **33735675051**. Saved action buttons now update their enabled state immediately when the lifecycle owner installs or clears the typed callback, so an already-rendered Saved surface cannot continue to look actionable after detachment. This is presentation/lifecycle-only: persistence, identity, provider execution, ranking, evidence, clock, provider economics and network authority remain unchanged. Clean-source verification passed 1,682 JVM tests (375 shared-core + 1,307 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`f1ad62c4a1c7d76c52e9dae4a7fd84ce16773b7e` (`Gate Home extra-stop disclosure by owner`) was the promoted milestone, verified by candidate workflow **33731893804** and milestone provenance workflow **33732437637**. Home’s advanced extra-stop disclosure now obeys the same owner-driven fail-closed gate as its option chips: a detached/reused Home renderer cannot open the interactive panel without its typed owner callback, while an attached owner preserves the existing progressive disclosure. This is presentation/lifecycle-only: provider execution, ranking, evidence, persistence, clock, provider economics and network authority remain unchanged. Clean-source verification passed 1,681 JVM tests (375 shared-core + 1,306 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`7285a35da741eaad71d81914e17279c3ca7e334a` (`Disable duplicate Search quick-entry chip`) was the promoted milestone, verified by candidate workflow **33730319345** and milestone provenance workflow **33730738420**. Search’s quick-entry chips now mirror the existing immutable lifecycle guard: the chip for the query already `LOADING` is visibly disabled, while different quick queries remain enabled as explicit replacement choices. The click guard and controller no-op remain in place as defense-in-depth. This is presentation/lifecycle-only: provider execution, ranking, evidence, persistence, clock, provider economics and network authority remain unchanged. Clean-source verification passed 1,681 JVM tests (375 shared-core + 1,306 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`a6260eb2edfe40769013bf05241308d3c500a4e7` (`Reject duplicate Search submits while loading`) was the promoted milestone, verified by candidate workflow **33728615359** and milestone provenance workflow **33729137572**. The Search controller rejects a raw `Submit` while an existing request is `LOADING`, preserving the active immutable state and emitting no replacement request. `QueryChanged` remains the intentional cancellation/replacement boundary, and the visible button, keyboard path and quick-entry chips retain their readiness gates. This is lifecycle defense-in-depth only: provider execution, ranking, evidence, persistence, clock, provider economics and network authority remain unchanged. Clean-source verification passed 1,680 JVM tests (375 shared-core + 1,305 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`00f8e3fdff369b4ce3237f2b3a4cbc78984ba035` (`Announce Watch projected warnings`) was the promoted milestone, verified by candidate workflow **33727646911** and milestone provenance workflow **33728147638**. Watch My Staples marks its projected safety/display-metadata warning as a polite accessibility live region, so the specific warning is announced when it appears or changes instead of relying only on the broader status title. This is a presentation-only accessibility correction: the renderer consumes immutable `StapleWatchUiState`, while Watch policy, economics, notification, evidence, persistence, provider and network authority remain unchanged. Clean-source verification passed 1,679 JVM tests (375 shared-core + 1,304 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`3e3862fce53dfca7564c9c3b99764995efe67737` (`Prevent duplicate Search quick entries`) was the promoted milestone, verified by candidate workflow **33726614855** and milestone provenance workflow **33727114141**. Search's visible button, keyboard path and quick-entry chips now share one immutable lifecycle boundary: blank, over-limit and active-loading searches cannot submit, and tapping the same quick-entry chip during loading cannot restart identical provider work. A different quick query remains an explicit replacement choice. This is a presentation/lifecycle guard only: the helper consumes existing `UniversalSearchState`, while controller request identity, provider execution, ranking, evidence, persistence and network boundaries remain unchanged. Clean-source verification passed 1,678 JVM tests (375 shared-core + 1,303 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`6822542020da9a652b8fb067b5d9afde1793b22f` (`Guard Search keyboard duplicate submissions`) was the promoted milestone, verified by candidate workflow **33725677152** and milestone provenance workflow **33726058966**. Search's visible button and keyboard/quick-entry paths now share one immutable readiness gate: blank, over-limit and active-loading searches cannot submit, so an IME action cannot clear an active request or start duplicate work. This is a presentation/lifecycle guard only: the helper consumes existing `UniversalSearchState`, while controller request identity, provider execution, ranking, evidence, persistence and network boundaries remain unchanged. Clean-source verification passed 1,677 JVM tests (375 shared-core + 1,302 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`2a31c0d96e733c667a7fa7ed1636506eb423aad6` (`Explain incomplete Basket extra stop`) was the promoted milestone, verified by candidate workflow **33724167757** and milestone provenance workflow **33724622104**. Basket now carries Home's existing typed explanation that the extra-stop rule is not evaluated until every requested item has a usable price, so incomplete known-subtotal plans cannot imply that a second stop was evaluated. Complete plans remain unchanged; no-coverage plans keep the rule hidden. This is presentation-only parity: the renderer consumes immutable projected fields, does not infer or calculate prices, and does not change planner, ranking, evidence, persistence, provider or network authority. Clean-source verification passed 1,673 JVM tests (375 shared-core + 1,298 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`2690b78d3c8c61b2f844c357c4c096b792ce244a` (`Carry missing price notice into Basket`) is the promoted milestone, verified by candidate workflow **33723174528** and milestone provenance workflow **33723634750**. Basket now carries Home's existing typed per-item “No usable price yet — not included in this plan.” notice into no-coverage rows, so a shopper can see why an item is not part of the displayed plan. Incomplete plans retain Basket's existing “No usable price yet — not ready to collect” collection warning; complete plans and collectible rows remain unchanged. This is presentation-only parity: the renderer consumes immutable projected fields, does not infer or calculate prices, and does not change planner, ranking, evidence, persistence, provider or network authority. Clean-source verification passed 1,673 JVM tests (375 shared-core + 1,298 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`2bdb750d8d067371ee68bed88cbcfcfca82110f1` (`Explain missing Home item prices`) is the promoted milestone, verified by candidate workflow **33722478794** and milestone provenance workflow **33722879331**. Home’s existing item rows now expose a renderer-ready “No usable price yet — not included in this plan.” notice whenever the already-projected result has no store assignment for that item, including no-coverage results. Covered items retain their exact planned store; complete plans remain unchanged. The notice is presentation-only and cannot infer, repair, rank or calculate any price. The preceding Home extra-stop threshold control explains when a result is incomplete: the shared planner will not evaluate another stop until every requested item has a usable price. That notice appears in the expanded panel and its accessibility summary; complete results remain unchanged, and the saved threshold remains typed and selectable for later complete coverage. This is renderer-ready presentation only and does not recalculate money, rank stores, infer missing prices, or change planner policy. The preceding Saved observed-price entry point explains what a shopper must prepare before recording evidence: choose a saved product and store, then record a personally observed price with proof. When saved content exists but one or both prerequisites are missing, it projects a heading plus a precise missing-product/store notice; the action remains unavailable until the existing gate passes. Empty, loading and error states stay quiet. The new copy is renderer-ready and does not create current-price authority, infer evidence, or alter storage. The preceding observed-price confirmation action renderer still announces asynchronous confirmation, rejection and failure messages through a polite accessibility live region, while its button remains gated on both immutable readiness and the presence of its typed owner callback. Basket’s projected local check-off safety disclosure still announces politely when collection becomes available, so TalkBack can hear that marking items is only a foreground aid and does not place an order or change the plan. The top-level shell still announces its existing destination title politely; the main Saved surface still announces its projected unresolved display-metadata warning politely; existing Saved lifecycle status, Watch setup selection summary, fail-closed notice and foreground fact-resolution progress remain polite live regions; Watch policy setup still announces its projected validation notice and “Still needed” requirements card politely; Watch My Staples still announces projected readiness/economic status and exposes its exact switch candidate as one coherent accessibility summary; Compare Here still announces projected input/readiness and evaluated-result status with one-summary exact/blocked cards; Search result cards still expose complete projected summaries with explicit fictional-sample context; the shared projected Home/Basket result container remains a polite accessibility live region, while Home’s query wrapper, Search’s ready/loading/results/error status, Basket’s local check-off progress, Saved lifecycle status and Home refinement feedback remain gated and announced through their existing live regions. Basket still distinguishes a ready-but-unsubmitted list from an incomplete one with “Plan this list on Home.” No planner, ranking, pricing, evidence, persistence, clock, provider or network authority moved into a View. Clean-source verification passed 1,673 JVM tests (375 shared-core + 1,298 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Prior checkpoint (superseded)

Latest promoted head: `0cf9b253c7f6f28835cb4f2fd0019099b66ce783` (`Acknowledge Watch setup handoff`), candidate workflow **33674718972**, milestone workflow **33675293225**. The current app slice adds presentation-only acknowledgement after a configured Watch setup handoff: the user sees that the explicit selection was accepted, while the surface clearly says that no switch decision exists until current prices, route details and evidence checks are supplied. The acknowledgement is attached to the current immutable setup projection and clears when the selection or validated Saved snapshot changes; rejected attempts explain the fail-closed setup issue. It adds no fact, pricing, planner, ranking, persistence, networking, clock, alert or notification authority. The preceding Basket slice adds a local-only Clear check-off control that appears only when foreground collection marks exist, preserves the immutable plan and eligible item keys, and resets only check-off progress. The shared Home/Basket result-card refinement maps the existing immutable missing-price marker to a calm amber treatment for known subtotals, while complete baskets remain green. The current Watch setup selection summary remains intact: the selected staple count is shown against the existing two-item handoff minimum, alongside whether a usual store has been selected. The Saved Watch entry point explains that recurring saved items and a usual store are used to check whether a future switch is worth the trip, with no live-price, alert, or notification claim. Both summaries and the caution treatment are derived from immutable presentation state; physical Views only render supplied values and emit typed actions. The preceding bounded local-only cross-session persistence around `PracticalShoppingHomeSession.State` remains intact: the last Home list, chicken choice, extra-stop preference and opaque request-details bytes are restored only for the exact established request; missing/oversized/corrupt values fail closed, and unsubmitted drafts do not persist stale details. Existing per-item package count, brand and exact-product controls remain separate from planner arithmetic. No Watch setup copy, handoff acknowledgement, Home details, result-card styling, or Basket check-off reset feeds planner, ranking, pricing, evidence, clock, networking, or View authority. The provenance proof accepts both `candidate/*` and historical `work/valuepilot-*` candidates but excludes the milestone branch. Full candidate Android/browser/privacy/signature/release checks and milestone provenance passed.

Purpose: compact durable recovery point. Newer repository/account evidence overrides this file.

## Startup order

1. `AGENTS.md`
2. `CURRENT_STATE.md`
3. `CONTINUATION_CHECKPOINT.md`
4. `VALUEPILOT_MASTER_CONTINUATION_PROMPT.md`
5. `PROVIDER_ACCOUNT_STATUS.md`
6. `OPEN_DATA_INTEGRATION_STATUS.md`
7. `ARCHITECTURE.md`
8. relevant provider/rights/data-audit files for the task

## Permanent architecture

ValuePilot is provider-neutral shopping intelligence, not an Accessibility/OCR/overlay product.

`authorized/open/user evidence -> provider adapters -> provenance-preserving claims/import records -> deterministic validation/normalization -> Product identity + Offers -> bounded retrieval -> deterministic decision/ranking -> immutable presentation -> replaceable UI`

Rules: Product != Offer; claims stay separate; acceptance != conflict resolution; exact deterministic money/quantity math; affiliate/provider economics never rank; feed access != production rights; dataset recency != offer freshness; currency != geography; shared core owns no hidden clock; no provider credentials in source; no speculative Android networking/provider plumbing.

## 2026-08-29 strategic product decision

Provider-by-provider approval is no longer the primary launch path. It is too slow and fragile for a startup with effectively $0 cash. Rakuten/Jamieson/GS1/Walmart and future commercial feeds remain supplementary high-authority accelerators, not launch blockers.

The consumer proposition is NOT "5 million live-priced products." Open/free product identities can make recognition broad, but the app must optimize practical shopping decisions.

Primary promise:

**Given what I need, where I am, and how much inconvenience I will tolerate, what is the cheapest sensible way to shop?**

Default decision policy is **ONE STORE FIRST**.

- Find the best single-store plan within reasonable user constraints.
- Evaluate one optional second stop only when incremental savings are clearly worth the hassle; initial product default can be around a $15 minimum saving, to be validated empirically.
- Do not hide parking/walking/checkout/extra-stop friction inside an opaque score.
- Keep price, travel time/cost, item coverage, missing data and confidence/freshness visible separately.
- Unknown price remains UNKNOWN.
- A third store is not a normal recommendation; only consider it in an explicitly aggressive savings mode.

## Consumer experience / quality bar

The app must be extremely polished, fast, simple, obvious and non-confusing. Complexity belongs under the hood.

Primary experiences:

1. **Plan My Shop** — Home/hero flow. One simple request/list input; default result is one best-store recommendation card. Advanced controls use progressive disclosure.
2. **Compare Here** — in-store barcode/camera/OCR comparison with exact unit value. Must remain useful even with incomplete cross-store coverage.
3. **Watch My Staples** — retention via meaningful basket/staple savings alerts, not penny-saving notification spam.
4. **Receipt/import** — optional user-benefit feature only (automatic list reconstruction, spending/history/comparison). Never make receipt crowdsourcing the core model.

For every screen/change: one obvious primary action, useful defaults, minimal text/buttons, calm loading/empty/error/unknown states, bounded performance, no UI jumping, and no claim stronger than evidence.

## Free/open data direction

There is no known legitimate free complete dataset of fresh store-specific prices for every Canadian grocery retailer. Do not pretend otherwise and do not rely on unauthorized scraping/private endpoints/anti-bot circumvention.

Use provenance-separated rails where licences permit:

- Open Food Facts — broad packaged-product recognition/metadata.
- USDA FoodData Central — CC0/public-domain enrichment; not Canadian availability proof.
- validated produce/PLU identity source — loose produce/category identity.
- Open Prices — supplemental proof-backed observed/historical prices.
- OpenStreetMap — store/location/routing foundation where appropriate.
- user evidence adapters — barcode/shelf label/camera/product-page share/digital receipt, always giving immediate user utility rather than chores.
- merchant self-service later — stores may submit CSV/feed/API because inclusion can send customers.
- authorized provider/affiliate feeds — higher-authority accelerators when rights/geography/freshness pass gates.

Keep ODbL/share-alike sources source-isolated; do not collapse them into an incompatible proprietary master database.

Launch depth before breadth: one dense metro area, roughly 1,500–5,000 high-frequency grocery/household concepts for strong local utility, while millions of open identities remain backend recognition rather than a vanity KPI.

## Earlier verified engineering baseline

The current promoted baseline is `227ed24059784989bc2ee38845181297f0430d0e` (`Show Home plan on Basket tab`), verified by candidate workflow **33635484179** and promoted workflow **33636063647**. Basket now consumes the existing immutable Home render state and passes its exact projected result through unchanged. It exposes recognized and unresolved items, complete or honestly incomplete one-store results, any shared-core-approved optional second stop, the selected exact extra-stop rule, and the same explicit fictional/offline disclosure. Empty/refinement/unresolved states never fabricate a plan and provide one typed action back to Home. Home and Basket use one shared physical result renderer; neither Basket renderer nor View owns planning, ranking, money arithmetic, provider logic, or missing-fact inference. Independent clean-LF verification passed 1,546 JVM tests with zero failures/errors/skips, all 58 Android tasks, all 30 browser tests, Firefox lint, APK privacy inspection, and one-signer APK verification. The local worktree was clean at code verification.

The preceding verified Home/Compare baseline is `c6c36416ab136473d7ddc22eef232de6ed090e55` (`Bound Practical Shopping Home query input`). Home supports typed removal of resolved items and unresolved tokens, immediate deterministic replanning, preserved unknown/ambiguous states, item-specific accessibility descriptions, deterministic `and`/`&` list syntax, and an explicit “Price freshness” label on result evidence summaries. Its advanced extra-stop preference is progressively disclosed only after an actual result; immutable presentation owns availability, the View binds it mechanically, and the persisted exact threshold still governs the first plan and later replanning. The Home list editor exposes the existing 240-character policy as a visible counter and accepts at most the model's existing 241-character retained error sentinel. Compare Here retains bounded per-entry removal, explicit Current shelf prices / Member prices selection, pure readiness, 4,096-character physical editor bounds, fail-closed restoration, and no member-to-current price fallback.

Minimum known durable code baseline before 2026-08-29 documentation changes: `deaa478c9b679f8820b47a80d7f43c5b18787677` with verified code parent `932838f8d5eedda5d17c7a9dadf76ae38f8bcc9f` (`Re-evaluate production search before display`). Always inspect the current branch tip before work.

Verified production ranking/presentation includes exact unit value, bounded Best Value ranking, immutable presentation, source/UI leakage hardening, generation ordering, 128-item projection cap, renderer isolation, inactive hidden production Search view, and display-time re-evaluation from raw evidence/current lifecycle.

Recent exact workflows passed browser checks, shared-core/app tests, lint, APK build, JVM summary, Android privacy verification, packaging/checksums and artifact upload.

The production Search view is physically present but hidden and MainActivity does not drive it. Visible Search remains fictional/sample. Keep production evidence out of legacy `UniversalSearchController` / `ValueItem` / `Double` ranking paths.

## Current provider/account evidence

### Rakuten / Jamieson

Rakuten Product Catalog technical access is enabled. Jamieson partnership + advertiser Product Feed approval + actual complete 273-row feed availability are proven.

On 2026-08-29 Rakuten Publisher Support clarified:

- downloaded Product Catalog files may be used in the publisher's system for comparison;
- app use/comparison requires confirmation from the relevant advertiser;
- if partnership ends, affiliate/product links become inaccessible but downloaded feed files remain saved in the publisher's system;
- physical file retention does not itself prove ongoing display/use rights after partnership end;
- Android affiliate-link use requires advertiser permission/terms.

This substantially reduces Rakuten-side storage/comparison ambiguity and moves application/display/comparison permission to the advertiser-specific gate.

A Jamieson permission email has already been sent asking for Android display, search/comparison, cache/index/storage, affiliate-link use, retention/deletion requirements and confirmation that the approved feed represents products/offers intended for Canadian consumers. **Do not resend while waiting unless their reply is incomplete/new evidence creates a question.**

Jamieson feed quality checkpoint remains: 273 rows; 271 supplied valid GTINs; all CAD/in stock; Sale<Retail 48, Sale=Retail 223, Sale>Retail 2; Class ID blank; Attribute 1 opaque. Never repair the two inverted rows or infer package quantity from opaque/title/image fields.

### Walmart Canada

Rakuten Walmart Canada MID 36751 currently blocks application through advertiser-supplied eligibility terms before an Apply action is available. Do not falsify account/channel details. If pursued later, ask Rakuten which exact eligibility condition fails. Walmart is not a launch blocker under the new strategy.

### GS1 Canada

ECCnet inquiry sent and acknowledged 2026-08-28; substantive eligibility/rights/technical response remains pending unless newer authenticated evidence exists. GS1 remains a package-content accelerator, not a launch dependency.

## Current Android privacy boundary

At the last verified checkpoint Android had no `INTERNET`, no `ACCESS_NETWORK_STATE`, no account requirement, no telemetry, no remote AI dependency and no ValuePilot server dependency. Do not silently change this boundary. Future networking/server work needs an explicit milestone and privacy/offline/failure design.

## Immediate next engineering work

Do not wire Rakuten/GS1 into production Search and do not keep extending speculative provider plumbing.

The bounded Practical Shopping MVP fixture/controller, Home projection, one-store/second-stop controls, and consumer correction actions are now implemented and verified at the current head. The original implementation sequence is retained below as historical acceptance criteria; do not treat it as unfinished work.

The latest promoted slice keeps no-coverage results honest across Home and Basket. It labels the Basket state “Price coverage needed,” uses state-safe shell copy, and suppresses generic check-off progress when no primary store plan exists, in addition to hiding the recommendation-only extra-stop rule and matching the guidance to the actual evidence state. It also keeps Saved shell copy neutral before lifecycle content is available. It changes no typed collection state, plan, totals, planner, ranking, pricing, evidence, persistence, clock, notification, network, or View authority; the existing Home extra-stop disclosure, Basket typed eligibility/item-preference context and local-only check-off notice remain intact.

The newest promoted slice adds item-specific assistive-technology descriptions to Saved product/store removal actions. The descriptions are projected only when the lifecycle enables mutation; loading, refreshing, updating and error states remain fail-closed with no row actions. It changes no Saved identity, persistence, planner, ranking, pricing, evidence, notification, network, or View authority.

The newest promoted slice extends that context to Watch product and usual-store selection actions. Each toggle names the saved choice it changes, while the selection projector continues to own identity reconciliation, readiness, and typed action direction. It changes no fact, price, route, policy, persistence, planner, ranking, notification, network, or View authority.

Next engineering slice: re-audit the current Home/Basket/Saved consumer surfaces and tests, then choose one small reversible clarity or accessibility improvement that uses existing immutable projection. Keep fictional/sample data visibly offline, unknown prices unknown, one-store-first explicit, and Views limited to rendering and typed actions.

Historical MVP sequence:

1. Inspect the existing Home/Search/Basket/Saved shell and immutable UI-state boundaries first.
2. Define the smallest deterministic shopping-request/store-plan domain model: requested items, candidate store, matched/missing coverage, exact known basket cost, travel metadata, confidence/freshness, and optional second-stop incremental-savings decision.
3. Reuse existing exact `Money` / quantity / deterministic value components; do not duplicate arithmetic.
4. Encode one-store-first as the default invariant and an explicit second-stop savings threshold/route constraint rather than a hidden hassle score.
5. Use a tiny fictional/sample multi-store fixture only to prove the flow. Never represent it as live merchant data.
6. Build the Home experience around one primary action and one recommendation card; progressive disclosure for advanced constraints.
7. Add performance budgets/tests early: bounded candidates/list sizes, no heavy main-thread work, stable immutable rendering, smooth scrolling/interactions, no random jump-to-top behavior.
8. After the decision flow is excellent, add source-isolated open product/location adapters.
9. `Compare Here` is the second major consumer slice; `Watch My Staples` is the third.
10. Provider/account replies continue in parallel and are incorporated only when they unlock real high-authority evidence/commerce value.

## Verification discipline

For each engineering slice: inspect repository first, state invariant, implement smallest durable boundary, add deterministic failure/unknown/stale/conflict/bounds tests as relevant, run existing tests/lint/build/privacy checks, review exact diff, commit only intended changes, and update these durable docs when state materially changes.

## Security

Never repeat, commit, log, screenshot, embed or request operational provider credentials. Production secrets remain outside source control.
