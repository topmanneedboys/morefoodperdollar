# Current state

Updated: 2026-09-04

Branch: `work/valuepilot-android-milestone`

Android version: 101.1.0 (10101)

## Current verified engineering head

The promoted milestone is now `5932449f2c09e72fb677a1e79bf05556fbfcae50` (`Disclose unavailable private history on Home`). Candidate workflow **33856774432** and milestone provenance workflow **33857251205** passed for this exact SHA. Home now carries an explicit typed availability status for its device-only comparison history: an unreadable or invalid store is disclosed in an accessible recovery notice, and history-derived row context is suppressed until the store is readable again. Empty history remains distinct from unavailable history, while the fictional planner result, exact totals and unknown states pass through unchanged. The existing dedicated “Is this a good price?” path still accepts one manually entered product, reuses the exact parser/comparison projection, and gives only private-history context: no matching history, below/equal/above the last observation, or below/within/above a personal range. Exact currency, package quantity, selected current/member price basis and promotion terms remain required; missing member prices, estimated quantities, ambiguous currencies and malformed input stay visibly blocked. A successful check remembers the typed exact observation locally with a source label distinct from Compare Here, bounded atomic storage and the existing integrity fingerprint; it never becomes a store, stock, availability, public-offer, planner or ranking fact. The screen and Home actions are owner-gated, offline, clearly disclose that answers are not live pricing, and expose no raw adapter/core objects. The existing Compare Here history, review-first local photo import, self-contained fictional sample disclosures, 30,000-record shared Canada identity catalog and atomic catalog-release pointer remain intact.

The same verified release carries the catalog milestone `a00f948995300374f953053a53c298546b530a52`: a signed 30,000-record shared Canada identity source is bundled for both GTA and Metro Vancouver, with no prices/offers/stock/availability. Offline catalog workflow **33840204939** and full candidate build **33840205073** passed, followed by milestone catalog workflow **33840982043**. Catalog promotion now writes one immutable generation and atomically replaces one root `active-generation.json` pointer; incomplete releases and orphan generations are ignored, and damaged active generations recover through the embedded last-known-good release. Shared identity records are counted once across regional references.

This adds no Android networking, account, tracking, current-price authority, planner/ranking authority, provider economics, or persistent overlay foundation. Physical-device testing is still required before declaring camera/OCR ergonomics or launch readiness complete. The prior `a5cfa0e...` entry below is historical.

## Previous verified engineering head (superseded)

The promoted milestone was `a5cfa0e609d18f1e3730f934788a72ade88ba37a` (`Rollback coverage report write failures`), verified by candidate build workflow **33829634963** and milestone provenance workflow **33830070695**. The weekly multi-metro refresh kept pointer promotion and diagnostic coverage reporting in one rollback boundary: if any region promotion or the final report write failed, every captured `current.json` and `last-known-good.json` pointer was restored byte-for-byte and the prior coverage report remained unchanged. Deterministic tests covered both a later-region failure and a report-write failure. This added no price, offer, availability, planner/ranking, network, or identity authority. Candidate verification passed the full browser, Android, privacy, signer, and release-bundle checks; offline snapshot tests passed in workflow **33829634993**.

## Previous verified engineering head (superseded)

The promoted milestone is `b462f01250d0987adfb16fcca7b908bc7bce9bd1` (`Rollback multi-region snapshot promotion failures`), verified by candidate build workflow **33829078507** and milestone provenance workflow **33829440551**. The weekly multi-metro refresh now captures each requested region’s `current.json` and `last-known-good.json` bytes before promotion and restores every pointer byte-for-byte if any later region promotion fails; a deterministic test covers a simulated second-region failure and preserves the prior coverage report. This closes the cross-region partial-promotion gap without adding price, offer, availability, planner/ranking, network, or identity authority. The existing signed 5,000-per-metro identity snapshots, `0` / `NOT_INCLUDED` current-offer coverage, rights gates, and Home stale-selection guidance remain unchanged. Candidate verification passed the full browser, Android, privacy, signer, and release-bundle checks; the offline-catalog workflow **33829078425** also passed.

## Previous verified engineering head (superseded)

The promoted milestone is `c05ea098f4f8a6d375817b551befa890b3d250e4` (`Explain stale offline match application failure`), verified by candidate build workflow **33827937894** and milestone provenance workflow **33828361403**. Home’s offline identity-match dialog still names the exact unresolved word being searched, and now explains when a selected identity can no longer be applied because the Home list changed while the bounded lookup was open; the stale action is disabled so the shopper can review and retry. This is presentation/lifecycle-only and adds no price, offer, availability, planner/ranking, network, or identity authority. The existing 5,000-per-metro signed identity snapshots, `0` / `NOT_INCLUDED` current-offer coverage, deterministic refresh quality gates, and no-price/no-availability disclosures remain unchanged. Candidate verification passed the full browser, Android, privacy, signer, and release-bundle checks; the unchanged snapshot inputs remain covered by offline-catalog workflows **33826706509** and **33827098104**.

## Previous verified engineering head (superseded)

The promoted milestone is `ee38c00792aaa2374e470fbd54bee30bfd3beb4e` (`Fail closed catalog quality regressions`), verified by candidate build workflow **33826706479** and milestone provenance workflow **33827098123**. The offline-match dialog still identifies the exact unresolved Home word it will search/replace in its title across loading, empty and selectable-result states, improving clarity and screen-reader context when multiple list words need clarification. The bundled Greater Toronto Area and Metro Vancouver identity snapshots contain 5,000 records each and 4,199 unique canonical identity names within the 1,500–5,000 target; 4,960 records receive a grocery hint and 41 receive a household hint under the deterministic 10% reserve. Current offers remain `0` / `NOT_INCLUDED`. Home’s offline catalog result explains that selecting a bundled identity name only edits the Home list and that the fictional sample planner may still leave that inserted name unresolved. Empty results explicitly say that a miss in this snapshot does not mean the product is unavailable. The existing no-price/no-availability disclosure remains, and catalog choices stay query-only until the separate exact-identity confirmation boundary is deliberately wired. The weekly refresh coordinator now fails closed before import/build/promotion when the selector reports an unsatisfied household reserve or identity-name variety outside the 1,500–5,000 launch target; rejected regressions preserve prior reports and pointers. These do not establish demand, retailer availability, stock, price, package quantity, freshness, or organic-ranking authority. The merchant-feed qualifier still reports identity coverage separately from structural current-offer and unit-value candidates; those counts remain `STRUCTURAL_ONLY` with authority `NONE`. The Android build workflow includes the qualifier paths in its push/PR filters. This adds no prices, offers, availability, planner/ranking authority, Android networking, or new consumer claim. Signed manifests and promotion pointers remain authoritative. Clean-source verification passed 400 shared-core tests and 1,337 Android app tests with zero failures, all 58 Android tasks, 65 Python tests, 30 browser tests, Firefox lint with no findings, APK privacy inspection with no INTERNET/ACCESS_NETWORK_STATE, one-signer verification, exact-SHA release-bundle provenance, and exact-SHA offline-catalog workflows **33826706509** (candidate) and **33827098104** (milestone). Historical superseded entries follow.

The previous promoted milestone was `b6018ab9d08f22558f01c0fe36609dad832b22f5` (`Search offline identity snapshots across supported metros`), verified by candidate workflow **33809477765** and milestone provenance workflow **33809973694**. The offline catalog had an explicit-input, deterministic, offline coordinator for the supported Greater Toronto Area and Metro Vancouver identity snapshots, and Home searched both independently region-bound snapshots through a bounded shared discovery index. Identical identities were deduplicated, conflicting record ids were omitted, and the combined lookup failed closed if either bundled region was not admitted. It required source/rights/timestamp/signing inputs, kept identity data separate from current offers, verified every regional candidate before promotion, and preserved existing pointers on coverage regression; it did not add networking or alter planner/ranking authority. Clean-source verification passed 397 shared-core tests and 1,333 Android app tests with zero failures, all 58 Android tasks, 61 Python tests, 30 browser tests, Firefox lint with zero findings, APK privacy inspection with no INTERNET/ACCESS_NETWORK_STATE, one-signer verification and exact-SHA release-bundle provenance.

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

`6ac3ae2cbc6a6a4a855017911d060c458b795408` (`Fail closed Basket navigation on owner changes`) was the promoted milestone, verified by candidate workflow **33739991354** and milestone provenance workflow **33740563023**. Basket’s Open Home navigation control now updates its enabled state immediately when the typed owner callback is installed or cleared, and remains inert before the first immutable render; a detached Basket surface cannot look actionable. This is presentation/lifecycle-only: no policy, planner, ranking, pricing, evidence, persistence, clock, provider, provider-economics or network authority changed. Clean-source verification passed 1,684 JVM tests (375 shared-core + 1,309 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

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

Latest promoted engineering head: `0cf9b253c7f6f28835cb4f2fd0019099b66ce783` (`Acknowledge Watch setup handoff`), verified by candidate workflow **33674718972** and promoted workflow **33675293225**. The milestone includes the immutable app-level `PracticalShoppingRequestDetailsSession` owner on top of the shared request-details core, plus a typed `PracticalShoppingHomeSession.State` that keeps explicit quantity/package/brand intent separate from the existing sample plan. Home presents progressive per-item details and a bounded editor for explicit package count, brand text and exact-product preference. Local-only cross-session retention remembers the bounded Home list, chicken choice, extra-stop preference and opaque request-details bytes; it restores only the exact established request, drops oversized/corrupt values safely, and clears stale detail bytes while a draft is being edited. Watch setup exposes an immutable selection-progress summary (selected staple count against the existing two-item minimum and usual-store selection) directly on the Saved-backed setup surface, so incomplete selections explain why continuation is unavailable without putting readiness logic in the View. When a configured foreground fact-check handoff is explicitly requested, the setup surface now acknowledges the accepted selection and states that no switch decision exists until current prices, route details and evidence checks are supplied; this feedback clears when the immutable selection projection changes. The Saved Watch entry point now also explains, in renderer-ready copy, that the shopper is choosing recurring saved items and a usual store to check whether a future switch is worth the trip; it does not imply live prices or automatic alerts. Details, Watch identity setup and handoff acknowledgement never feed basket arithmetic, planner, ranking, pricing, evidence, clock, or network authority; persistence remains a local UX concern, and the Home dialog explicitly says the fictional sample total remains unchanged until a verified planner mapping exists. The latest shared result-card refinement maps the projector's existing missing-price marker to an amber caution treatment for known subtotals while retaining the green treatment for complete baskets; it changes no projected copy or decision. Basket check-off now has a local-only Clear check-off control that appears only when marks exist, preserving the immutable plan and eligible item keys while resetting foreground progress. The provenance workflow recognizes the repository's historical `work/valuepilot-*` candidate branches while explicitly excluding the milestone branch itself. The earlier Basket surface remains intact: it presents the recognized list, unresolved tokens, exact already-projected one-store/optional-second-stop result, and selected exact extra-stop rule. Empty and attention-required states return the shopper to Home without inventing a result. Home and Basket share one physical result-card renderer, while presentation boundaries perform no planning, ranking, money formatting, or missing-price inference. Incomplete results remain explicitly labelled known subtotals and the fictional/offline disclosure is repeated prominently. Clean-source verification passed 1,625 JVM tests (375 shared-core + 1,250 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK permission inspection, and one-signer APK verification.

The preceding verified Home/Compare slice remains `c6c36416ab136473d7ddc22eef232de6ed090e55` (`Bound Practical Shopping Home query input`). Home users can remove resolved items or unresolved tokens through typed controller actions, natural `and`/`&` list syntax is supported, and result cards label freshness explicitly. The advanced extra-stop preference no longer competes with the untouched Home screen: immutable presentation keeps it hidden until an actual plan result exists, then exposes the shopper's persisted exact threshold for immediate deterministic replanning. The Home list editor shows its 240-character limit and physically retains at most the existing one-character error sentinel; immutable presentation carries the limit and the View only binds the counter/filter. Compare Here supports removing extra product entries while preserving its minimum two-slot shape and exposes an explicit Current shelf prices / Member prices choice. Its primary action is disabled until two product slots contain text and the user explicitly confirms like-for-like substitutability; pure bounded presentation state owns that readiness, while the existing exact route still owns all parsing, price/quantity validation and ranking. Each editor block is capped at the adapter's existing 4,096-character evidence limit. Oversized legacy/restored content is cleared with an explicit error instead of being silently truncated into partial facts. Changing the price basis invalidates a stale result, the choice survives draft restoration, and member mode never substitutes a current price when member evidence is missing. Manual-entry guidance shows the exact `Current price` and optional `Member price` labels accepted by the parser. If the selected basis lacks enough evidence, the result guidance names that basis; member mode explicitly says current prices are not substitutes. The Android privacy boundary is unchanged.

## Product direction now

ValuePilot remains provider-neutral shopping intelligence, but the startup launch strategy changed on 2026-08-29 after provider/rights/cost research.

Provider-by-provider approval is **no longer the primary launch dependency**. Rakuten/Jamieson/GS1/Walmart and future commercial feeds remain supplementary high-authority rails when they become available, but ValuePilot must provide compelling consumer utility without waiting for hundreds of companies.

The product is NOT primarily "5 million live-priced products." Millions of open/free product identities may support recognition, but the consumer promise is:

> **Given what I actually need, where I am, and how much inconvenience I will tolerate, what is the cheapest sensible way to shop?**

The default is **ONE STORE FIRST**.

A second store is optional and should only be recommended when its incremental savings clearly justify parking, getting out of the car, walking, finding products, checkout/loading and route detour. Initial product design may use a visible/user-controlled minimum extra-stop savings threshold around $15, to be validated empirically. Do not hide a made-up human-friction value inside an opaque score.

A third store is not a normal recommendation and should only be considered in an explicitly aggressive savings mode.

Unknown prices remain UNKNOWN. Never fabricate a complete basket by estimating missing prices as facts.

## UX / performance requirement

The app must be extremely polished, fast, simple, obvious and non-confusing. Treat this as an architectural/product requirement, not final cosmetic work.

Primary consumer experiences:

1. **Plan My Shop** — Home/hero flow. One simple shopping-list request; default result is one best-store recommendation card. Advanced controls stay behind progressive disclosure.
2. **Compare Here** — user is already in a store; barcode/camera/OCR compares visible products/package sizes/promotions with exact unit-value math and immediate benefit even when cross-store coverage is incomplete.
3. **Watch My Staples** — notify only when switching stores is practically worthwhile for recurring items/baskets; no penny-saving spam.
4. **Receipt/import** — optional user-benefit feature for list reconstruction/spending/history/comparison. Receipt crowdsourcing is NOT the core data model.

For every UI change: one obvious primary action; strong defaults; minimal fields/cards/buttons; explicit loading/empty/error/unknown states; no UI jumping; bounded work; smooth realistic item counts; no claim stronger than evidence.

## Permanent architecture

ValuePilot is provider-neutral shopping intelligence. Accessibility, OCR, overlays, browser capture, camera capture and retailer-specific extraction are optional adapters, not the product foundation.

Permanent flow:

`authorized/open/user evidence -> provider adapters -> provenance-preserving claims/import records -> deterministic validation/normalization -> Product identity + multiple Offers -> bounded retrieval -> deterministic decision/ranking -> immutable presentation -> replaceable UI`

Permanent rules:

- Product != Offer.
- Sources contribute separate claims; they never overwrite one shared truth row.
- Acceptance != factual conflict resolution.
- Stronger same-scope evidence may defeat weaker evidence; unresolved equal-strength conflict blocks authoritative comparison.
- Money, quantity, currency and promotion arithmetic are exact/deterministic.
- AI may classify/explain/suggest candidates but may not invent authoritative facts.
- Commission, EPC, payout, sponsorship, affiliate economics and provider preference never affect organic ranking.
- Feed/technical access != publisher authorization != offer geography != production-use rights.
- Dataset recency != per-offer freshness; currency != geography.
- Dataset namespace != snapshot; snapshot lifecycle != namespace disposition/deletion.
- Shared core owns no hidden clock/network/UI/provider credentials.
- Android/UI renders immutable state and owns no provider/ranking business logic.
- Bound/coalesce/cancel/measure expensive work; no unbounded scans, queues, caches, requests or rendered rows.

Primary Android navigation remains Home / Search / Basket / Saved. Compare remains a workflow, not a primary tab. Built-in Search remains fictional/sample evidence and must never be represented as live merchant price, inventory, promotion or availability.

## Free/open data direction

There is no known legitimate free complete dataset of fresh store-specific prices across all Canadian grocery retailers. Do not pretend otherwise and do not build the commercial foundation on unauthorized scraping/private endpoints/anti-bot circumvention.

Potential source-isolated rails where licences/terms permit:

- Open Food Facts — broad packaged-product recognition/metadata; prefer bulk import for scale.
- USDA FoodData Central — public-domain/CC0 branded-food enrichment; never treat US market-country data as proof of Canadian availability.
- validated produce/PLU source — loose-produce identity/category where licensing permits.
- Open Prices — supplemental proof-backed observed/historical prices, not nationwide current-price coverage.
- OpenStreetMap — store/location/routing foundation where appropriate.
- user evidence adapters — barcode, shelf label, camera, product-page share, digital receipt/order evidence; immediate user utility first, contribution second.
- merchant self-service later — stores submit CSV/feed/API because inclusion can send customers.
- authorized commercial/provider/affiliate feeds — higher-authority accelerators only when rights/geography/freshness pass gates.

Keep ODbL/share-alike sources in source-isolated namespaces and do not casually merge them into an incompatible proprietary master database. Resolve evidence through the existing provenance architecture.

Launch depth before breadth: one dense metro area and roughly 1,500–5,000 high-frequency grocery/household concepts for excellent practical coverage, while millions of open identities remain backend recognition rather than a vanity KPI.

## Verified production chain — keep it

Core provider/evidence boundaries:

- `5bb647a8485f257ec51b3eb0fe39b9c7caccb0a0` — provider-neutral current/reference price relationship.
- `a8e98b8ce333a612538841566972d6cab58dde88` — dataset recency separated from per-offer freshness.
- `6aed414bd5f89cf7ac6dfb739464c6f57f5abe78` — fail-closed production authorization.
- `7606ea941f80e3dc6b2ea362bc688c7434215195` — fail-closed geography; CAD is not Canada proof.
- `f58b400533bdf9a0705fb8e88680e4b56ce9d94e` — staged production offer candidate.
- `e546822a448e150674a2769d9899a856124b50fb` — exact-snapshot lifecycle.
- `230b8ae4b6f674979d349320b8e5bd83713db810` — namespace disposition/withdrawal.
- `ebb28a4a506232550b62d08370a9c8935d677603` — point-in-time raw-evidence production price view.
- `5d317810bd6ccf0933ff6432e6e68f88fb865493` — canonical-GTIN/provider-scoped product keys.
- `9a9b5f91948fe505a0ad6b598097bf9b8e50c680` — lifecycle-bound CURRENT_PRICE claims.
- `97bfa3c353e48f15e26e9576cf06f4fa5e1687d1` — unified evidence acceptance/freshness.
- `a1b15cc13df4912fb94893c8952f382f7404db1d` — lifecycle-bound current-price acceptance.
- `c3dcfab539a2d2ef40fe9ce283533141ea9cd246` — current-price factual-conflict eligibility.
- `204c8ae5e0089473f28b7cf6086b73e7a3516ec6` — production price + conflict-resolved package quantity -> exact unit value.
- `ad5f91d4eef54e257a6660d291f14558653a1761` — bounded exact production Best Value ranking.
- `6517fad0ef21daa541d31d0d01d72a5f1980f5d5` — immutable point-in-time production presentation.

Android/application boundaries:

- `617dc128c9df31bbbd2b1835ac243be93e511d97` — exact production Search projector.
- `38942d556958b8abdf3b942bcbdc7bce77f1a0da` — synthetic raw evidence -> presentation -> Android projection regression.
- `6e0d3fc0f6c7c6a61926a8c0e85d2b2e12629022` — internal scope keys removed from UI-ready text.
- `1a53d34bfbb2fdf65b7383c489f75b428defe06e` — raw provider URLs removed from catalog-only UI-ready state.
- `81620dfaeb15aeab48c2e438bd225004190c0a09` — blocker enum codes removed from consumer blocked UI state.
- `7046b725291cc061f1e153e05c4a25539838a28e` — generation-based stale/out-of-order refresh protection.
- `3cba4d96eb91e961772dee3c60d86371e61b1137` — independent 128-candidate UI projection ceiling.
- `8520af97695fb346710649a80c6d95d422da3ee8` — production Search host/renderer separation.
- `e2767b31512d8d5b6b9cd7555d452ae3ec0017e2` — inactive-by-default production renderer.
- `c4661993f9c476e3a44c7d1dc504948e50767d48` — hidden physical production Search view in `activity_shell.xml`.
- `932838f8d5eedda5d17c7a9dadf76ae38f8bcc9f` — display submission re-runs production evaluation from raw inputs/current lifecycle instead of trusting a detached snapshot.

Minimum known durable checkpoint before the 2026-08-29 strategy documentation commits: `deaa478c9b679f8820b47a80d7f43c5b18787677`, whose parent is verified code commit `932838f8d5eedda5d17c7a9dadf76ae38f8bcc9f`. Always inspect the current branch tip before new work.

Recent exact workflows passed browser checks, shared-core/app tests, lint, APK build, JVM summary, Android privacy verification, packaging/checksums and artifact upload.

A temporary empty `__noop__` connector artifact created during the hidden-layout update was removed immediately; do not resurrect it.

## Production Search boundary now

Production arithmetic remains `Money`, `NormalizedQuantity`, `UnitRate`, `DeterministicValueMath`. Never convert verified production evidence back into legacy `Double` ranking.

`ProductionBestValueRankingEvaluator` is bounded to 128 price requests, 128 quantity candidates and 128 ranking candidates. Exact same `(currencyCode, RateUnit)` only; exact ties co-rank; singleton groups do not claim Best Value; provider/affiliate economics absent.

`ProductionBestValuePresentationEvaluator` re-runs ranking from raw evidence at the supplied instant. Presentation snapshots are point-in-time and not durable authorization tokens.

`ProductionSearchUiProjector` formats exact UI-ready values and independently caps rendered ranked+blocked candidates at 128. Consumer UI excludes internal merchant/location/channel keys, raw provider URLs and blocker enum codes.

`ProductionSearchRefreshGate` uses caller-supplied generations and owns no clock/I/O.

`ProductionSearchSurfaceHost.evaluateAndApply(...)` re-runs production presentation from raw bounded inputs/current registries before rendering.

`ProductionSearchSurfaceView` is physically present but starts `GONE`, saves no state, has no link/click/provider behavior and renders sanitized state only.

**MainActivity still does not wire or drive this production surface.** Current visible Search remains fictional/sample. Do not route verified production evidence through `UniversalSearchController.receive()`, `DeterministicProductParser`, `ValueEngine.analyze()`, `RankingModePolicy`, `DeterministicRankingEngine` or `ValueEngine.rank()`.

## Rakuten / Jamieson — updated 2026-08-29

Rakuten Product Catalog technical access is enabled. Jamieson partnership, separate advertiser Product Feed approval and actual complete catalog-file access are proven. Proprietary feed data stays outside source control.

Sanitized feed checkpoint: 273 rows; 273 unique SKUs/Product IDs; GTIN present 271/273 and all supplied GTINs checksum-valid; 273 CAD; 273 in stock; Sale<Retail 48, Sale=Retail 223, Sale>Retail 2; Class ID blank all; Attribute 1 opaque.

Generic Rakuten semantics remain resolved: Sale Price reflects discounts and Retail Price does not. Sale>Retail is a semantic conflict; never swap/repair/infer promotion from those rows.

On 2026-08-29 Rakuten Publisher Support answered the production-use clarification materially:

- Product Catalog files may be downloaded with any FTP client.
- Once downloaded, product data/links may be used in the publisher's system for comparison, but application use requires confirmation from the relevant advertiser.
- Comparison use likewise requires advertiser confirmation.
- If partnership ends, product links become inaccessible while downloaded feed files remain saved in the publisher's system. Physical retention does not itself prove continuing display/use rights.
- Android affiliate-link use requires advertiser permission/terms.

Therefore Rakuten-side storage/comparison ambiguity is substantially reduced. Advertiser-specific Android/application display/comparison permission is the primary rights gate; post-partnership file retention must remain distinct from continued display/use authorization.

A Jamieson permission email has already been sent asking for Android display, search/comparison, cache/index/storage, affiliate-link use, retention/deletion requirements and confirmation that the approved feed represents Canadian consumer products/offers. **Do not resend while waiting unless the reply is incomplete or creates a new question.**

Rakuten Product Catalog file/update timestamps remain dataset/source recency, not trustworthy per-product current-price observation timestamps.

Jamieson is useful supplemental evidence but is no longer a launch dependency under the practical-shopping strategy.

## Walmart Canada

Rakuten Walmart Canada MID 36751 currently shows an advertiser-supplied eligibility pre-filter that prevents application. Do not falsify ValuePilot/account/channel details to bypass it. If Walmart is pursued later, ask Rakuten which exact eligibility condition fails. Walmart is not a core launch blocker.

## Package quantity / GS1 / open data

Valid normalized Jamieson × Open Food Facts result remains: 273 products; 271 valid GTINs; 102 matches; 169 unmatched; 12 exact supplement-count candidates; 2 structured mass/volume-only; 0 quantity conflicts; 88 matched without usable quantity. OFF is supplemental; never infer package count from Rakuten title/description/opaque attributes/images.

Health Canada LNHPD does not solve GTIN-level package count.

GS1 Canada ECCnet inquiry was sent and acknowledged 2026-08-28; no substantive eligibility/rights response yet unless newer evidence exists. GS1 is a potential high-quality package-content accelerator, not a launch dependency.

## Android privacy boundary

At the latest verified engineering checkpoint Android still has no `INTERNET`, no `ACCESS_NETWORK_STATE`, no account requirement, no telemetry, no remote AI dependency and no ValuePilot server dependency. Provider research/networking remains outside Android.

Do not silently change this boundary. Future networking/server additions require an explicit milestone with privacy/security/offline/failure-mode design.

## Immediate next work

Do not keep adding speculative Android production/provider plumbing.

The Practical Shopping MVP fixture/controller, one-store planner/projector, Home/Basket/Saved surfaces, Watch setup/policy presentation, and required consumer correction flows are implemented at the promoted head. The latest slice keeps no-coverage results honest by using state-safe Basket shell copy, hiding recommendation-only controls and generic check-off progress until a primary store plan exists, labeling Basket “Price coverage needed,” and giving guidance that matches the actual evidence state. Saved shell copy is likewise neutral before lifecycle content is available.

Next engineering slice: re-audit the current Home/Basket/Saved consumer surfaces and tests, then choose one small reversible clarity or accessibility improvement that uses existing immutable projection. Keep the following acceptance constraints active:

1. Inspect current Home/Search/Basket/Saved shell and immutable UI-state boundaries before changing them.
2. Reuse existing exact money/quantity/value components; never duplicate arithmetic.
3. Preserve one-store-first and explicit second-stop savings/travel rules; never hide inconvenience in a score.
4. Keep fictional/sample data visibly offline and unknown prices unknown.
5. Keep Views as immutable renderers with typed actions; no Android networking, persistent overlay foundation, or provider economics in organic ranking.
6. Add deterministic failure/unknown/stale/conflict/bounds tests with each relevant slice, then run the full Android/browser/privacy/signature/release verification.

No user/device action is required for this documentation checkpoint.
