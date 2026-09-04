# ValuePilot Practical Shopping MVP Status

Updated: 2026-09-04

Branch: `work/valuepilot-android-milestone`

Purpose: newest durable product/engineering checkpoint for the Practical Shopping MVP. Newer repository evidence overrides this file.

## Latest verified engineering head

The promoted milestone is now `d206331e2d3927e3b084b0fed1d92b40f16cdc68` (`Show Home personal history coverage`), verified by candidate workflow **33900346836** and milestone provenance workflow **33900879487**. Home’s existing renderer-owned private-memory summary now includes a bounded `Name-matched personal history: X of N list items.` line for the current list when readable device-only comparison observations exist. It counts canonical display-label matches only and explicitly says this is **not current-price coverage**; it never turns history into exact product, package, quantity, price, freshness, stock, availability, offer, planner or ranking facts. Empty/blank names keep the generic summary, unreadable memory remains suppressed, and the fictional planner/demo path is unchanged. This is a reversible consumer-clarity slice with no new persistence authority, Android networking or provider-economics influence. Candidate Linux verification passed Android tests/lint/build/privacy/single-signer/release, browser/Firefox and Python/catalog gates; the local Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and real production Home wiring remain open.

The promoted milestone is now `41a0fc28466cd0ac5b794bcedfaf49ff7d15173a` (`Expose offline product identity search`), verified by candidate workflow **33898457412** and milestone provenance workflow **33898919053**. Search now offers a clearly labelled offline identity lookup over the separately signed 30,000-record Canada catalog. The immutable result contains only product identity names, brands, deterministic match labels and a bounded candidate count; it never supplies package quantity, price, stock, store availability, freshness or ranking. Selecting a name hands it through the existing untrusted `text/plain` route into Scan & compare, where the shopper remains responsible for exact quantity, currency and observed price. Query edits, sample actions, route changes, cancellation and teardown invalidate stale identity work. The fictional sample-value search remains separate and disclosed. No Android networking, new persistence authority or planner/projector duplication was introduced. Candidate Linux Android/browser/privacy/signing/release and Python/catalog verification passed; the Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and production Home wiring remain open.

The promoted milestone is now `713e4396c6643f3b6aa4b06af3a90cea0921416a` (`Add text share handoff to Compare Here`), verified by candidate workflow **33895387933** and milestone provenance workflow **33895985864**. ValuePilot accepts intentional `text/plain` shares through a bounded review surface that labels the content untrusted, shows a preview, and requires an explicit handoff into Scan & compare. The existing editable comparison draft receives one trimmed value in its earliest empty slot; all existing entries remain intact, while blank, oversized and full-draft inputs stay explicit and unchanged. The share path adds no parsing, identity, quantity, price, store, availability, evidence, planner, ranking, networking or new persistence authority; exact comparison and private observation rules remain downstream. Candidate Linux verification passed the 1,432-test Android job, lint/build/privacy/single-signer/release checks, browser/Firefox and Python/catalog gates; the Windows run retains the known 18 line-ending-sensitive boundary failures, physical-device ergonomics remain unverified, and production Home is still the fictional/demo path.

The promoted milestone is now `45f52cdaa5d3933a30b14f0386c9d51310d9a0f3` (`Make Home private history deletion explicit`), verified by candidate workflow **33892635254** and milestone provenance workflow **33893217763**. Home’s bounded review of exact, like-for-like device-only comparison history now includes an explicit `Clear private history` action. The action requires confirmation, deletes only the existing local private store, updates Home only after an accepted deletion, and shows a non-authoritative error state when deletion cannot be confirmed. The confirmation states that the Home list, identity catalog, store locations, live offers and recommendations are unaffected. Empty/unavailable memory keeps the existing recovery route, and the summary still labels observations as personal rather than live store pricing, inventory, retailer offers or guarantees. This reversible local-consumer slice adds no planner, ranking, price, offer, store, availability, network or provider-economics authority. Candidate Linux verification passed the 1,419-test Android job, lint/build/privacy/single-signer/release checks, browser/Firefox and Python/catalog gates; the Windows run retains the known 18 line-ending-sensitive boundary failures, physical-device ergonomics remain unverified, and production Home is still the fictional/demo path.

The promoted milestone is now `7c7be8d5215d2f6261049398700486252dda407c` (`Carry no-coverage summary into Basket`), verified by candidate workflow **33887664746** and milestone provenance workflow **33888215450**. Basket now carries Home’s existing renderer-owned `0 of N items priced yet.` aggregate beside its no-coverage guidance, so the coverage state remains explicit after switching tabs. It is hidden for empty/refinement states and when a projected primary plan already provides exact coverage. This is presentation-only; shared-core planning/projector, exact arithmetic, price/offer/store/availability, network, persistence and provider-economics authority remain unchanged. Focused tests and candidate/milestone Android/browser/privacy/signing/release checks passed. The Windows run retains the known 18 line-ending-sensitive boundary failures; physical-device ergonomics remain open.

The promoted milestone is now `1be6628b3fb48b67fa7ee272a8ed3a3a35984529` (`Clarify Home no-coverage summary`), verified by candidate workflow **33885576989** and milestone provenance workflow **33886216516**. When the existing Home projection has no primary plan, the renderer adds a compact `0 of N items priced yet.` aggregate beside the per-item unknown-price notices. It is hidden for empty/refinement states and when a primary plan already provides coverage. This is presentation-only; shared-core planning/projector, exact arithmetic, price/offer/store/availability, network, persistence and provider-economics authority remain unchanged. Focused tests and candidate/milestone Android/browser/privacy/signing/release checks passed. The Windows run retains the known 18 line-ending-sensitive boundary failures; physical-device ergonomics remain open.

The promoted milestone is now `ec7ed377c27e7e6b4ece9030bedc5e7cbf997188` (`Add signed GTA GVA store directory snapshot`), verified by candidate workflow **33883155939** and milestone provenance workflow **33883690098**. The shell’s `Data status` action reports a signed, ODbL-attributed OpenStreetMap directory of 6,093 source-listed GTA/Metro Vancouver locations (4,311/1,782) and its observed date. The Android loader admits rows only after detached-signature, source-hash, rights, geography and freshness checks; an invalid or stale artifact exposes no directory rows. Directory records are explicitly location-only and cannot establish product identity, package quantity, price, offer, stock, availability or ranking. The existing 30,000-record identity rail, `0` authorized current offers, device-only private observations, flyer absence, offline connectivity and fictional Home/Search data remain explicit. Candidate Linux Android/browser/privacy/signing/release verification and the Python snapshot suite passed. The Windows run retains the known 18 line-ending-sensitive boundary failures; physical-device ergonomics remain open.

The promoted milestone is now `16e1e68af516df2f4c4ac27b7f9105581a18a150` (`Add barcode identity handoff to Compare Here`), verified by candidate workflow **33876560165** and milestone provenance workflow **33877137830**. Scan & Compare now has a first-class user-triggered barcode handoff in addition to photo capture/import. The bounded activity returns one checksum-valid GTIN; the already-signed offline identity snapshots are searched off the main thread and an explicit name choice fills only an empty comparison draft slot. Existing text is never overwritten, and blank/oversized/full-draft states remain explicit. Package quantity, currency, observed price, selected price basis and like-for-like confirmation remain required before exact unit-value comparison or private-memory capture. No barcode identity is treated as a price, package, store, stock, availability, freshness, ranking or live-offer fact. Candidate Linux Android/browser/privacy/signing/release verification passed; the Windows run retains the known line-ending-sensitive boundary failures, and physical-device barcode testing is still required. Production Home remains the fictional/demo planner surface and authorized current-offer coverage remains absent.

The promoted milestone is now `6743181977f1500772444599a667284b8d7ea2e3` (`Add actionable Home private history review route`), verified by candidate workflow **33867138431** and milestone provenance workflow **33867613305**. When Home has readable nonempty private comparison history, its renderer-owned `Review private price history` action opens the existing `Scan & compare prices` screen; empty history stays quiet and unavailable history stays on its recovery notice. Deterministic Home history/renderer/View/lifecycle coverage protects visibility, callback ownership and route wiring while preserving the existing non-live-price boundary. This is a reversible navigation refinement: no planner, ranking, price, offer, store, stock, availability, provider-economics or persistence authority changed; the fictional sample planner and identity-only catalog disclosure remain intact.

## Previous verified engineering head (superseded)

The promoted milestone was `22e45377d40511e5a42aa786264cb562f15a1336` (`Point Home memory summary to review route`), verified by candidate workflow **33864992864** and milestone provenance workflow **33865459380**. When Home had nonempty private comparison history but no current list label matched, its renderer-owned summary pointed the shopper to `Scan & compare prices` to review that device-only history. This was presentation-only and added no action, storage, planner, ranking, price, offer, store, stock, availability, provider-economics or persistence authority.

## Previous verified engineering head (superseded)

The promoted milestone was `08657541629f5e7beb44c046573a8ae5d79be22a` (`Clarify offline catalog list replacement action`), verified by candidate workflow **33864027611** and milestone provenance workflow **33864356368**. The Home offline identity-match dialog now uses the action label `Replace list word` and assigns an explicit assistive-technology description stating that the action edits only the unresolved Home query and does not confirm an exact product, price or availability. Deterministic Home lifecycle-boundary coverage protects the bounded lookup, stale-completion guards, query-only replacement and no-network boundary. No planner, ranking, price, offer, store, stock, availability, provider-economics or persistence authority changed; the fictional sample planner and identity-only catalog disclosure remain intact.

## Previous verified engineering head (superseded)

The promoted milestone is `e02e358eb29ea28e4287ebef717d2d800d2b0720` (`Record Home private memory summary milestone`), verified by candidate workflow **33862278396** and milestone provenance **33862848704**. Scan & Compare now has a direct `Take a price photo` action in addition to photo import. CAMERA permission is requested only after the shopper taps it. The action captures to a cache-scoped FileProvider URI with temporary read/write grants and routes the image through the existing bounded local OCR suggestion/review flow. Import and capture share one in-flight gate; stale or destroyed callbacks are ignored; temporary files are cleaned on success, failure, cancellation and teardown; no-camera, denied-permission and creation-error states leave existing entries unchanged. The OCR status is a polite accessibility live region. Deterministic boundary tests protect the user-initiated permission boundary, optional camera feature, cache-only provider paths, URI grants, lifecycle guards and no-network source boundary. Home also exposes only a bounded count of nonempty private comparison history when no current list label matches, with deterministic helper, renderer and View-boundary coverage.

Home still exposes typed availability for device-only comparison history: unreadable/invalid memory is disclosed and history-derived context is suppressed, while empty history remains distinct. The dedicated “Is this a good price?” path still delegates exact parsing, package normalization, current/member semantics and unit math to the existing Compare Here bridge and presents private history only. Exact currency, package quantity, price basis and promotion facts remain strict; unknown or conflicting facts never become recommendations. The fictional planner, source-labelled private memory, 30,000-record identity rail and atomic catalog-release pointer remain separate from current offers and ranking authority. Physical-device testing remains required for camera/OCR ergonomics and launch readiness.

The same release includes catalog milestone `a00f948995300374f953053a53c298546b530a52`, verified by offline-catalog workflow **33840204939**, candidate build **33840205073**, and milestone catalog workflow **33840982043**. The bundled identity rail is one shared Canada source with exactly 30,000 records referenced by both launch regions. It remains identity-only (`0` / `NOT_INCLUDED` current offers). Its release tooling now records complete immutable generations and swaps one root active pointer atomically, with last-known-good recovery and deterministic crash-injection tests.

Home now surfaces a compact renderer-owned summary when private comparison history is nonempty but none of the current list labels match. Only the bounded observation count is shown; the copy says Home context is name-based, package/promotion details may differ, and the memory is not live store pricing. Empty history stays quiet, while unreadable history remains a separate recovery state with row context suppressed. Deterministic helper, renderer and View-boundary tests cover the new disclosure without changing the fictional planner or any price/offer/ranking authority.

Candidate verification passed browser/Firefox, shared-core and Android tests, lint, APK build, privacy permission inspection, single-signer verification and release-bundle checks. The visible production Home planner remains the deliberately labelled fictional demo; real Home/private-price-book wiring is still unfinished. Physical-device testing remains mandatory before declaring Scan & Compare ready for launch. No Android networking, account, tracking, provider economics or new ranking authority was added.

## Previous verified engineering head (superseded)

The promoted milestone was `a5cfa0e609d18f1e3730f934788a72ade88ba37a` (`Rollback coverage report write failures`), verified by candidate build workflow **33829634963** and milestone provenance workflow **33830070695**. The weekly multi-metro refresh kept pointer promotion and diagnostic coverage reporting in one rollback boundary: if any region promotion or the final report write failed, every captured `current.json` and `last-known-good.json` pointer was restored byte-for-byte and the prior coverage report remained unchanged. Deterministic tests covered both a later-region failure and a report-write failure. This added no price, offer, availability, planner/ranking, network, or identity authority. Candidate verification passed the full browser, Android, privacy, signer, and release-bundle checks; offline snapshot tests passed in workflow **33829634993**.

## Previous verified engineering head (superseded)

The promoted milestone is `b462f01250d0987adfb16fcca7b908bc7bce9bd1` (`Rollback multi-region snapshot promotion failures`), verified by candidate build workflow **33829078507** and milestone provenance workflow **33829440551**. The weekly multi-metro refresh now captures each requested region’s `current.json` and `last-known-good.json` bytes before promotion and restores every pointer byte-for-byte if any later region promotion fails; a deterministic test covers a simulated second-region failure and preserves the prior coverage report. This closes the cross-region partial-promotion gap without adding price, offer, availability, planner/ranking, network, or identity authority. The existing signed 5,000-per-metro identity snapshots, `0` / `NOT_INCLUDED` current-offer coverage, rights gates, and Home stale-selection guidance remain unchanged. Candidate verification passed the full browser, Android, privacy, signer, and release-bundle checks; the offline-catalog workflow **33829078425** also passed.

## Previous verified engineering head (superseded)

The promoted milestone is `c05ea098f4f8a6d375817b551befa890b3d250e4` (`Explain stale offline match application failure`), verified by candidate build workflow **33827937894** and milestone provenance workflow **33828361403**. Home’s offline identity-match dialog identifies the exact unresolved word being searched and now explains when a selected identity can no longer be applied because the list changed while the bounded lookup was open; the stale action is disabled so the shopper can review and retry. This presentation/lifecycle correction adds no price, offer, availability, planner/ranking, network, or identity authority. The existing 5,000-per-metro signed identity snapshots, `0` / `NOT_INCLUDED` current-offer coverage, deterministic refresh quality gates, and no-price/no-availability disclosures remain unchanged. Candidate verification passed the full browser, Android, privacy, signer, and release-bundle checks; the unchanged snapshot inputs remain covered by offline-catalog workflows **33826706509** and **33827098104**.

## Previous verified engineering head (superseded)

`ee38c00792aaa2374e470fbd54bee30bfd3beb4e` — `Fail closed catalog quality regressions`

GitHub Actions candidate build workflow **33826706479** and milestone provenance workflow **33827098123** completed successfully for the exact same SHA. The offline-catalog candidate and milestone workflows **33826706509** and **33827098104** also completed successfully for the exact same snapshot inputs.

Home’s offline catalog result surface still identifies the exact unresolved list word in the dialog title across loading, empty and selectable-result states. It still explicitly says that selecting a bundled identity name only edits the Home list; because the planner is still a tiny fictional sample vocabulary, the inserted name may remain unresolved. The existing identity-only no-price/no-availability disclosure remains, and the shopper still reviews the edited list and presses Plan My Shop themselves. Empty results explicitly say that a miss in this snapshot does not mean the product is unavailable. Catalog selections remain query-only until the separate exact-identity confirmation boundary is deliberately wired. The weekly selector’s default maximum is 5,000 and records 5,000 selected identities, 4,199 unique canonical identity names, 4,960 grocery-hinted records, and 41 household-hinted records in the selection and weekly coverage reports; the deterministic 10% household reserve remains satisfied. The refresh coordinator now fails closed before import/build/promotion when either the household reserve or identity-name variety launch gate regresses, with deterministic coverage tests. These are bounded identity/category measurements only; they do not prove demand, retailer availability, stock, current price, package quantity, freshness, rights, or rankability. The merchant-feed qualifier still measures identity coverage separately from structural current-offer and unit-value candidates; those candidate measurements are `STRUCTURAL_ONLY` with authority `NONE` and do not prove current price, package quantity, stock, freshness, rights, or rankability. The Android build workflow includes the qualifier paths in push/PR filters. The weekly offline catalog coordinator emits an atomic deterministic `coverage-report.json` that separates identity-catalog coverage from current-offer coverage (`0` / `NOT_INCLUDED`) for each requested metro; signed manifests and promotion pointers remain authoritative, candidate current-offer metadata is checked before staging, and rejected regressions preserve the prior report and last-known-good pointers. This hardening adds no prices, offers, availability, planner/ranking authority, Android networking, or a new consumer claim. Clean-source verification passed 400 shared-core tests and 1,337 Android app tests, all 58 Android tasks, 65 Python tests, 30 browser tests, Firefox lint with zero findings, APK privacy inspection with no INTERNET/ACCESS_NETWORK_STATE, one-signer verification, and exact-SHA release-bundle provenance.

## Previous verified engineering head (superseded)

`b6018ab9d08f22558f01c0fe36609dad832b22f5` — `Search offline identity snapshots across supported metros`

GitHub Actions candidate workflow **33809477765** completed successfully, and milestone provenance workflow **33809973694** completed successfully for the exact same SHA.

The offline catalog now has a deterministic, offline refresh coordinator for the supported Greater Toronto Area and Metro Vancouver identity snapshots, and Home’s bounded identity lookup searches both independently region-bound snapshots instead of silently pinning discovery to GTA. The shared discovery index deduplicates identical identities, omits conflicting record ids, and caps merged work; the combined Android lookup fails closed if either bundled region is not admitted. It requires explicit source export, rights evidence, timestamps, and signing keys; selects and imports only bounded Canada-labelled identity fields; builds and verifies every regional candidate before promotion; and preserves existing per-region pointers when a candidate regresses. It does not fetch data, add current prices/offers, claim stock or availability, or change the existing planner/ranking authority. Clean-source verification passed 397 shared-core tests and 1,333 Android app tests with zero failures, all 58 Android tasks, 61 Python tests, 30 browser tests, Firefox lint with zero findings, APK privacy inspection with no INTERNET/ACCESS_NETWORK_STATE, one-signer verification, and exact-SHA release-bundle provenance.

## Previous verified engineering head (superseded)

`cff0a346fb3ae37f59a52d92cdc718cd053ee030` — `Dismiss stale Home item details dialogs`

GitHub Actions candidate workflow **33748458980** completed successfully, and milestone provenance workflow **33748992285** completed successfully for the exact same SHA.

The Home item-details editor is lifecycle-bound to the Home route: opening a new editor dismisses any prior instance, leaving Home or opening Compare dismisses it, and Activity teardown releases it. This prevents an old package-count/brand/exact-product dialog from remaining visibly actionable over another destination or reappearing with stale Home state. This is presentation/lifecycle-only and does not alter the typed Home session, planner, projector, pricing, evidence, persistence, provider, provider-economics or network authority. Clean-source verification passed 1,689 JVM tests (375 shared-core + 1,314 Android app), all 58 Android tasks, 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`8182c1dd6bb6e7aea9c1fc22f5c40371eac1dec1` — `Name observed-price Saved selection actions`

GitHub Actions candidate workflow **33747358617** completed successfully, and milestone provenance workflow **33747877782** completed successfully for the exact same SHA.

Observed-price Saved product/store controls now expose projected, display-name-specific descriptions so assistive technology can identify exactly which saved choice a typed action changes. This is presentation-only and does not alter Saved identity, persistence, prefill, planner, projector, pricing, evidence, notification, provider, provider-economics or network authority. Clean-source verification passed 1,688 JVM tests (375 shared-core + 1,313 Android app), all 58 Android tasks, 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`b2c93b52cb9fa47aac6bad3cdc5b2587b2fc515c` — `Fail closed observed-price Saved selection`

GitHub Actions candidate workflow **33746227407** completed successfully, and milestone provenance workflow **33746657577** completed successfully for the exact same SHA.

The observed-price Saved-selection renderer now updates every product/store/clear-selection and prefill-check button immediately when its typed lifecycle owner is installed or cleared, so detached surfaces cannot leave controls looking actionable. This is presentation/lifecycle-only and does not alter Saved identity, persistence, prefill, planner, projector, pricing, evidence, notification, provider, provider-economics or network authority. Clean-source verification passed 1,688 JVM tests (375 shared-core + 1,313 Android app), all 58 Android tasks, 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`62c931ac136b6b4fd7853069cc1359a545b14bac` — `Dismiss detached Saved confirmations`

GitHub Actions candidate workflow **33745130893** completed successfully, and milestone provenance workflow **33745673251** completed successfully for the exact same SHA.

Saved now dismisses an open “Clear all” confirmation when its lifecycle owner detaches, and when a fresh immutable projection replaces the state that opened it, so a detached or stale dialog cannot remain visibly actionable. The existing typed clear-all action still emits only after explicit confirmation. This is presentation/lifecycle-only and does not alter Saved persistence, identity, planner, projector, pricing, evidence, notification, provider, provider-economics or network authority. Clean-source verification passed 1,687 JVM tests (375 shared-core + 1,312 Android app), all 58 Android tasks, 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`fda5749f1a821f369a6a170c4175e3d9e0c912b5` — `Collapse detached Home extra-stop settings`

GitHub Actions candidate workflow **33743998315** completed successfully, and milestone provenance workflow **33744536925** completed successfully for the exact same SHA.

Clearing the Home extra-stop owner now collapses an already-expanded settings panel immediately, so detached/reused Home surfaces cannot leave stale rule choices visibly open. This is presentation/lifecycle-only and does not alter the existing immutable Home result rendering, planner, projector, pricing, evidence, persistence, clock, provider, provider-economics or network authority. Clean-source verification passed 1,686 JVM tests (375 shared-core + 1,311 Android app), all 58 Android tasks, 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`3e8c94865de43cb0d20fbe353b56b53afb4558c2` — `Release Home owners when activity is destroyed`

GitHub Actions candidate workflow **33743220204** completed successfully, and milestone provenance workflow **33743655775** completed successfully for the exact same SHA.

The previous Home lifecycle refinement closed the Activity teardown gap. MainActivity now releases every Home and Basket callback on destruction, so detached/reused surfaces cannot retain actionable owners or an Activity reference. This is lifecycle-only and does not alter the existing immutable Home result rendering, planner, projector, pricing, evidence, persistence, clock, provider, provider-economics or network authority. Clean-source verification passed 1,685 JVM tests (375 shared-core + 1,310 Android app), all 58 Android tasks, 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`daa70e4c9d62305a83022758b30cdf9072abc38b` — `Fail closed observed price draft editors`

GitHub Actions candidate workflow **33741793189** completed successfully, and milestone provenance workflow **33742341341** completed successfully for the exact same SHA.

The previous observed-price refinement extended the owner-boundary correction across the draft editors themselves. Price amount/currency, observed date/time/offset, and proof-reference/type controls now update immediately when their typed owner callback is installed or cleared; detached evidence drafts cannot remain editable without their owner, and each editor starts disabled. This is presentation/lifecycle-only; no parsing, policy, planner, ranking, pricing, evidence, persistence, clock, provider, provider-economics or network authority changed. Clean-source verification passed 1,684 JVM tests (375 shared-core + 1,309 Android app), all 58 Android tasks, 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`941f2c267f4913a7554ae7593f8df85bb9884af9` — `Fail closed observed price confirmation action`

GitHub Actions candidate workflow **33740867817** completed successfully, and milestone provenance workflow **33741392931** completed successfully for the exact same SHA.

The previous observed-price refinement extended the owner-boundary correction to the confirmation action. Its button updates immediately when the typed owner callback is installed or cleared and remains inert before the first immutable render, so a detached confirmation surface cannot look ready to submit evidence. This is presentation/lifecycle-only; no policy, planner, ranking, pricing, evidence, persistence, clock, provider, provider-economics or network authority changed. Clean-source verification passed 1,684 JVM tests (375 shared-core + 1,309 Android app), all 58 Android tasks, 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`6ac3ae2cbc6a6a4a855017911d060c458b795408` — `Fail closed Basket navigation on owner changes`

GitHub Actions candidate workflow **33739991354** completed successfully, and milestone provenance workflow **33740563023** completed successfully for the exact same SHA.

The previous Basket refinement extended the owner-boundary correction to its Open Home navigation control. The button updates immediately when its typed owner callback is installed or cleared and remains inert before the first immutable render, so a detached Basket surface cannot look actionable. This is presentation/lifecycle-only; no policy, planner, ranking, pricing, evidence, persistence, clock, provider, provider-economics or network authority changed. Clean-source verification passed 1,684 JVM tests (375 shared-core + 1,309 Android app), all 58 Android tasks, 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`bad95f10c83235596532ed1caf05793ad376219c` — `Fail closed Home controls on owner changes`

GitHub Actions candidate workflow **33739072386** completed successfully, and milestone provenance workflow **33739628868** completed successfully for the exact same SHA.

The previous Home refinement extended the owner-boundary correction across query editing, submit, compare, item-detail/remove, refinement, and extra-stop controls. Each control updates immediately when its typed owner callback is installed or cleared, and Home remains inert before the first immutable render; detached surfaces cannot look actionable or accept edits without their owner. This is presentation/lifecycle-only; no policy, planner, ranking, pricing, evidence, persistence, clock, provider, provider-economics or network authority changed. Clean-source verification passed 1,683 JVM tests (375 shared-core + 1,308 Android app), all 58 Android tasks, 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`d03bba4aa08ff1b21889f857aa6e0f1fcf37663d` — `Fail closed Watch setup controls on owner changes`

GitHub Actions candidate workflow **33737614216** completed successfully, and milestone provenance workflow **33738144350** completed successfully for the exact same SHA.

The previous Watch setup refinement extended the owner-boundary correction to the Saved selection and policy surfaces. Selection toggles, policy Apply/no-limit controls and numeric policy editors now update immediately when the typed owner callback is installed or cleared, so detached setup surfaces cannot look actionable or remain editable. This is presentation/lifecycle-only; no policy, planner, ranking, pricing, evidence, persistence, clock, provider, provider-economics or network authority changed. Clean-source verification passed 1,682 JVM tests (375 shared-core + 1,307 Android app), all 58 Android tasks, 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`697c6319d40e4094b607135964f3a791e774c3be` — `Fail closed Saved launchers when owner clears`

GitHub Actions candidate workflow **33736212427** completed successfully, and milestone provenance workflow **33737249953** completed successfully for the exact same SHA.

The previous Saved refinement extended the owner-boundary correction to the Watch My Staples and observed-price launchers. Their rendered buttons now update immediately when the typed owner callback is installed or cleared, so detached Saved entry points cannot continue to look actionable. This is presentation/lifecycle-only; persistence, identity, provider execution, ranking, evidence, clock, provider economics and network authority remain unchanged. Clean-source verification passed 1,682 JVM tests (375 shared-core + 1,307 Android app), all 58 Android tasks, 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`d15fa825dc9e429a4f8043a543bcdba012a571ab` — `Fail closed Saved controls when owner clears`

GitHub Actions candidate workflow **33735195361** completed successfully, and milestone provenance workflow **33735675051** completed successfully for the exact same SHA.

The previous Saved refinement closed a lifecycle clarity gap. Saved action buttons now update immediately when their typed owner callback is installed or cleared, so an already-rendered Saved surface cannot continue to look actionable after detachment. This is presentation/lifecycle-only; persistence, identity, provider execution, ranking, evidence, clock, provider economics and network authority remain unchanged. Clean-source verification passed 1,682 JVM tests (375 shared-core + 1,307 Android app), all 58 Android tasks, 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`f1ad62c4a1c7d76c52e9dae4a7fd84ce16773b7e` — `Gate Home extra-stop disclosure by owner`

GitHub Actions candidate workflow **33731893804** completed successfully, and milestone provenance workflow **33732437637** completed successfully for the exact same SHA.

Home’s advanced extra-stop disclosure now mirrors the existing callback gate used by its option chips: a detached/reused Home renderer cannot open the interactive panel without its typed owner callback, while an attached owner preserves the existing progressive disclosure. This is presentation/lifecycle-only and leaves provider execution, ranking, evidence, persistence, clock, provider economics and network authority unchanged. Clean-source verification passed 1,681 JVM tests (375 shared-core + 1,306 Android app), all 58 Android tasks, 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`7285a35da741eaad71d81914e17279c3ca7e334a` — `Disable duplicate Search quick-entry chip`

GitHub Actions candidate workflow **33730319345** completed successfully, and milestone provenance workflow **33730738420** completed successfully for the exact same SHA.

Quick-entry chips now mirror Search’s immutable lifecycle guard: the chip for the query already `LOADING` is visibly disabled, while different quick queries remain enabled as explicit replacement choices. The click guard and controller no-op remain in place as defense-in-depth. This is presentation/lifecycle-only and leaves provider execution, ranking, evidence, persistence, clock, provider economics and network authority unchanged. Clean-source verification passed 1,681 JVM tests (375 shared-core + 1,306 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`a6260eb2edfe40769013bf05241308d3c500a4e7` — `Reject duplicate Search submits while loading`

GitHub Actions candidate workflow **33728615359** completed successfully, and milestone provenance workflow **33729137572** completed successfully for the exact same SHA.

The preceding Search refinement closed a controller lifecycle gap. The controller rejects a raw `Submit` while an existing request is `LOADING`, preserving the active immutable state and emitting no replacement request. `QueryChanged` remains the intentional cancellation/replacement boundary, and the visible button, keyboard path and quick-entry chips retain their readiness gates. This is lifecycle defense-in-depth only; provider execution, ranking, evidence, persistence, clock, provider economics and network authority remain unchanged. Clean-source verification passed 1,680 JVM tests (375 shared-core + 1,305 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`00f8e3fdff369b4ce3237f2b3a4cbc78984ba035` — `Announce Watch projected warnings`

GitHub Actions candidate workflow **33727646911** completed successfully, and milestone provenance workflow **33728147638** completed successfully for the exact same SHA.

The preceding Watch My Staples refinement closes an accessibility feedback gap. Its projected safety/display-metadata warning is a polite accessibility live region, so the specific warning is announced when it appears or changes instead of relying only on the broader status title. This is a presentation-only accessibility correction; the renderer consumes immutable `StapleWatchUiState`, and Watch policy, economics, notification, evidence, persistence, provider and network authority remain unchanged. Clean-source verification passed 1,679 JVM tests (375 shared-core + 1,304 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

## Previous verified engineering head (superseded)

`3e3862fce53dfca7564c9c3b99764995efe67737` — `Prevent duplicate Search quick entries`

GitHub Actions candidate workflow **33726614855** completed successfully, and milestone provenance workflow **33727114141** completed successfully for the exact same SHA.

The previous Search refinement closed the identical-quick-entry lifecycle gap. Search's visible button, keyboard path and quick-entry chips now share one immutable gate: blank, over-limit and active-loading searches cannot submit, and tapping the same quick-entry chip during loading cannot restart identical provider work. A different quick query remains an explicit replacement choice. This remained presentation/lifecycle-only.

## Previous verified engineering head (superseded)

`6822542020da9a652b8fb067b5d9afde1793b22f` — `Guard Search keyboard duplicate submissions`

GitHub Actions candidate workflow **33725677152** completed successfully, and milestone provenance workflow **33726058966** completed successfully for the exact same SHA.

The previous Search refinement closed a keyboard/lifecycle consistency gap. Search's visible button and keyboard/quick-entry paths shared one immutable readiness gate, so blank, over-limit and active-loading searches could not submit, and an IME action during loading could not clear the active query state or start duplicate provider work. This remained presentation/lifecycle-only.

## Previous verified engineering head (superseded)

`2a31c0d96e733c667a7fa7ed1636506eb423aad6` — `Explain incomplete Basket extra stop`

GitHub Actions candidate workflow **33724167757** completed successfully, and milestone provenance workflow **33724622104** completed successfully for the exact same SHA.

The previous Basket refinement closes a second-stop disclosure gap. When an incomplete known-subtotal result still has a primary plan and the saved extra-stop rule is visible, Basket now carries Home's existing explanation that another stop is not evaluated until every requested item has a usable price. Complete plans remain unchanged, and no-coverage plans keep the rule hidden. This is renderer-only copy from immutable projection; it does not infer prices, alter totals, change planner policy, or add provider/network authority.

## Previous verified engineering head (superseded)

`2690b78d3c8c61b2f844c357c4c096b792ce244a` — `Carry missing price notice into Basket`

GitHub Actions candidate workflow **33723174528** completed successfully, and milestone provenance workflow **33723634750** completed successfully for the exact same SHA.

The latest Basket refinement closes a parity gap with Home's per-item coverage explanation. No-coverage Basket rows now render the already-projected “No usable price yet — not included in this plan.” notice, while incomplete plans retain the existing collection-specific “No usable price yet — not ready to collect” warning and complete plans remain unchanged. This is renderer-only copy from immutable projection; it does not infer prices, alter totals, enable collection, change planner or ranking policy, or add provider/network authority.

## Previous verified engineering head (superseded)

`2bdb750d8d067371ee68bed88cbcfcfca82110f1` — `Explain missing Home item prices`

GitHub Actions candidate workflow **33722478794** completed successfully, and milestone provenance workflow **33722879331** completed successfully for the exact same SHA.

The latest Home refinement closes a per-item coverage clarity gap. Whenever the existing projected result has no store assignment for an item, Home now shows “No usable price yet — not included in this plan.” beside that item. Complete covered rows remain unchanged, and the notice is renderer-ready only; it does not infer prices, repair coverage, recalculate totals, or affect ranking.

The preceding Home refinement closes a consumer-facing ambiguity in the existing extra-stop control. Incomplete known-subtotal results may still expose the saved threshold for future complete coverage, but the expanded panel and accessibility summary now state that another stop is not evaluated until every requested item has a usable price. Complete results remain unchanged. This is renderer-ready guidance only; no money, ranking, missing-price, planner-policy or provider authority moved into Home.

The preceding Saved refinement closes a consumer guidance gap in the observed-price entry point. When saved content exists, the launcher now explains the evidence setup in plain language: choose a saved product and store, then record a personally observed price with proof. If the existing readiness gate is missing a named product, a named store, or both, the surface shows a precise heading and notice while keeping the action unavailable. Empty, loading and error states stay quiet. This is renderer-ready copy only; it does not create current-price authority, infer evidence, change persistence, or alter planner/ranking behavior.

The preceding observed-price confirmation refinement closes an accessibility feedback gap. Asynchronous confirmation, rejection and failure messages are now announced through a polite live region, while the action remains fail-closed unless both the immutable state and its typed owner callback are ready. This is renderer-only behavior; confirmation, evidence, storage, pricing and persistence authority remain unchanged. Clean-source verification passed 1,673 JVM tests (375 shared-core + 1,298 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

The preceding Saved refinement closes a consumer guidance gap. When saved content exists but the established Watch My Staples readiness gate is not met, the Saved-owned launcher now projects a short heading and a precise missing-requirement notice (one more named product, a named store, or both) while keeping navigation unavailable. Fully empty, loading and error states remain quiet. The notice is rendered with a polite accessibility live region. This is immutable projection and rendering only; it does not alter Saved persistence, planner readiness, pricing, evidence, notification, clock, networking or View authority.

The preceding Basket refinement closes an accessibility feedback gap. Basket’s projected local check-off safety disclosure is now a polite accessibility live region, so assistive technology can hear that marking items is only a foreground aid and does not place an order or change the plan. The top-level destination title remains a polite live region; the main Saved surface still announces its projected unresolved display-metadata warning politely; existing Saved lifecycle status, Watch setup selection summary, fail-closed notice and foreground fact-resolution progress remain polite live regions; Watch policy setup still announces its projected validation notice and “Still needed” requirements card politely; Watch My Staples still announces projected readiness/economic status and exposes one coherent switch-candidate summary; Compare Here retains polite input/readiness and evaluated-result announcements with one-summary exact/blocked cards; Search result cards retain complete projected summaries with explicit fictional-sample context; and the shared projected Home/Basket result container remains a polite accessibility live region for newly planned recommendations. Clean-source verification passed 1,668 JVM tests (375 shared-core + 1,293 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, one-signer verification and release-bundle provenance.

The preceding Home refinement closes a fail-closed lifecycle gap. Home now gates the `TextInputLayout` wrapper as well as the editor when no query owner callback exists, preventing the built-in clear-text end icon from remaining active on a detached surface. This is a physical presentation binding only; the shell still consumes immutable state, emits only existing typed actions, and owns no shopping or persistence authority. Clean-source verification passed 1,655 JVM tests (375 shared-core + 1,280 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

The preceding Search refinement closes an accessibility feedback gap. Search’s projected ready/loading/results/error status is now a polite accessibility live region, so assistive technology can hear asynchronous local-search feedback without changing the existing controller, provider, or projection. This is a physical presentation binding only; the shell still consumes immutable state, emits only existing typed actions, and owns no shopping or persistence authority. Clean-source verification passed 1,655 JVM tests (375 shared-core + 1,280 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

The preceding Basket refinement closes an accessibility feedback gap. Basket’s projected local check-off progress is now a polite accessibility live region, so assistive technology can hear the “n of m items collected” update after each typed foreground action or reset. This is a physical presentation binding only; the renderer still consumes immutable state, emits only existing typed actions, and owns no shopping or persistence authority. Clean-source verification passed 1,653 JVM tests (375 shared-core + 1,278 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

The preceding Saved refinement closes an accessibility feedback gap. Saved’s projected refresh, mutation, degradation and error status message is now a polite accessibility live region, so assistive technology can hear lifecycle feedback after a user action. This is a physical presentation binding only; the renderer still consumes immutable state, emits only existing typed actions, and owns no persistence or shopping authority. Clean-source verification passed 1,652 JVM tests (375 shared-core + 1,277 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

The preceding Home refinement closes an accessibility feedback gap. Home’s immutable refinement and validation message is now a polite accessibility live region, so assistive technology can hear state changes such as “Which chicken do you want?” or an over-limit correction after a user action. This is a physical presentation binding only; the renderer still consumes immutable state, emits no actions, and owns no shopping authority. Clean-source verification passed 1,651 JVM tests (375 shared-core + 1,276 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

The preceding Basket refinement closes a draft-state clarity gap. When Home has a nonblank, bounded list that is ready to plan but has not been submitted, Basket now says “Plan this list on Home,” explains that planning starts from Home, and keeps collection disabled; unresolved, over-limit and empty states retain their existing attention guidance. This is renderer-only copy derived from immutable source fields and does not duplicate the shared one-store planner/projector or change any plan result. Clean-source verification passed 1,650 JVM tests (375 shared-core + 1,275 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

The preceding Home refinement closes a replaceable-surface lifecycle gap across every owner-driven control. The query editor, Plan my shop action, compare route, item-removal/detail actions, chicken refinement choices and extra-stop choices now fail closed when their nullable callbacks are absent; the IME submit path applies the same guard as the visible button. This is a presentation-boundary safety correction with deterministic source coverage; callbacks still emit only their existing typed actions, and no planner, ranking, pricing, evidence, persistence, clock, provider or network authority moved into the View. Clean-source verification passed 1,648 JVM tests (375 shared-core + 1,273 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

The preceding Basket refinement closes a replaceable-surface lifecycle gap. The typed “open Home” navigation button starts disabled and is enabled only when the immutable render owner has attached its callback, preventing a detached or standalone Basket view from exposing an enabled no-op control. This remains a presentation-only correction with deterministic boundary coverage; the callback still emits only `PracticalShoppingBasketUiAction.OpenHome`, and no planner, ranking, pricing, evidence, persistence, clock, provider or network authority moved into the View. Clean-source verification passed 1,647 JVM tests (375 shared-core + 1,272 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

The latest Home/Basket result-card refinement completes the accessibility disclosure contract. Home and Basket pass their existing fictional/offline sample notice into the shared primary and optional-second-stop card summaries, so a card focused directly by TalkBack still states that the displayed prices are not live retailer offers. The reusable renderer keeps sample context optional, rejects blank disclosure text, and hides only decorative child labels through Android’s accessibility-tree flag. This is presentation-only and retains every projected field, visual line and exact typed result; no planner, ranking, pricing, evidence, persistence, clock, provider or network authority moved into the View. Clean-source verification passed 1,647 JVM tests (375 shared-core + 1,272 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

The latest Saved-surface refinement protects against accidental destructive clearing. The “Clear all” control now opens a resource-backed confirmation dialog and emits the existing typed clear-all action only after explicit confirmation; individual row removals remain direct. This is a renderer-only safety affordance with deterministic boundary coverage; no Saved lifecycle, persistence, planner, ranking, pricing, evidence, notification, provider or network authority moved into the View. Clean-source verification passed 1,644 JVM tests (375 shared-core + 1,269 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

The latest Home item-preferences refinement removes a misleading no-op action. The dialog now shows “Clear preferences” only for an item that already has a saved detail; a new detail editor exposes only the meaningful Save and Cancel choices. The prior keyboard “Done” readiness gate remains intact. This is a small presentation/lifecycle correction with deterministic Android binding coverage; the Home controller remains the sole owner of query and preference state, and no planner, ranking, pricing, evidence, persistence, clock, provider or network authority moved into the View. Clean-source verification passed 1,643 JVM tests (375 shared-core + 1,268 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

The latest Home surface refinement closes a keyboard-submission readiness gap. The visible Plan my shop button and the keyboard “Done” action now share the same immutable `submitEnabled` gate, and the button starts disabled until its first render state arrives. Blank and over-limit drafts cannot dispatch a submit through the IME path. This is a small presentation-boundary correction with deterministic View-boundary coverage; the Home controller remains the sole owner of query handling, and no planner, ranking, pricing, evidence, persistence, clock, provider or network authority moved into the View. Clean-source verification passed 1,643 JVM tests (375 shared-core + 1,268 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK privacy checks with no network permissions, one-signer verification and release-bundle provenance.

The latest Saved shell refinement removes the last static implication that confirmed choices must already exist. The Saved title is now “Manage saved choices.” and its description invites review or management of choices the shopper chooses to remember, so loading, empty and error states remain honest. A deterministic shell integration test asserts the new copy and rejects the old confirmed-choice wording. This remains a reversible presentation-only correction; planner, ranking, price arithmetic, evidence, persistence, clock, provider and network authority stay outside the renderer/View boundary.

The latest Basket shell refinement removes the last static implication that a usable plan must already exist. The Basket title is now “Review your shopping list.” and its description refers to the shopping list from Home, so no-coverage, empty and attention-required states remain honest while complete plans still show their exact projected result below. Deterministic shell integration tests assert the new copy and reject the old plan-specific wording. This remains a reversible presentation-only correction; planner, ranking, price arithmetic, evidence, persistence, clock, provider and network authority stay outside the renderer/View boundary.

The latest Basket presentation refinement completes the no-coverage correction. A recognized request with no usable prices still exposes the honest planner result headline, but Basket now says “Price coverage needed” instead of “Your current basket plan” and hides the generic check-off progress line until a primary store plan exists. The existing Home extra-stop disclosure and Basket extra-stop summary remain hidden without that primary plan, and guidance continues to direct the shopper back to adjust the sample list. Deterministic renderer and View-boundary tests cover the state while preserving complete and incomplete-primary behavior. This remains a presentation-only fail-closed correction; planner, ranking, price arithmetic, evidence, persistence, clock, provider and network authority stay outside the renderer/View boundary.

The latest Home/Basket refinement closes a no-coverage clarity gap. A recognized request with no usable prices still produces an honest planner result headline, but it has no primary store plan; Home therefore hides the extra-stop rule, and Basket hides the rule summary instead of implying that a recommendation exists. Basket guidance now says that usable price coverage is missing and directs the shopper back to adjust the sample list. Deterministic Home and Basket renderer tests cover this state while preserving the existing complete and incomplete-primary paths. This is a presentation-only fail-closed correction; planner, ranking, price arithmetic, evidence, persistence, clock, provider and network authority remain outside the renderer/View boundary.

The preceding Basket refinement closes a consumer-facing scope gap in the existing check-off flow. When immutable rendering enables local collection marks, Basket now shows a renderer-ready notice: “Check-off is only a local shopping-session aid; it does not place an order or change the plan.” The notice is absent when collection is unavailable, so the UI cannot imply collection authority for incomplete or unresolved plans. Deterministic renderer and View-boundary tests cover both enabled and disabled states. This is presentation-only copy; no planner, ranking, price arithmetic, evidence, persistence, clock, provider or network authority moved into Android, and the existing immutable plan and typed eligible item keys remain unchanged.

The latest Home refinement closes an accessibility gap in the progressive extra-stop control. Assistive technology now hears whether the existing threshold summary will show or hide its settings panel, while the visible summary and typed choice callbacks remain unchanged. This is fixed presentation copy derived from the immutable projected summary and local expanded state; no second-stop policy, planner, ranking, price arithmetic, evidence, persistence, clock, provider or network authority moved into the View.

The latest Basket hardening keeps collection eligibility on the planner's typed boundary. Basket derives its collectible item keys from the already-projected `itemStoreAssignments` list and reconciles those opaque keys against rendered Home rows; it never treats a non-null display-name string as business authority. Incomplete plans continue to show missing-price rows as visible, unassigned and “No usable price yet — not ready to collect,” while complete plans retain full collection. This is a reversible boundary correction with deterministic source-boundary coverage; no planner, ranking, price arithmetic, evidence, persistence, clock, provider or network authority moved into the renderer or View.

The latest Basket refinement closes a practical collection gap in incomplete plans. When the shared projection has usable prices for some requested items but not all, Basket now exposes check-off actions only for the exact assigned item keys. Missing-price rows remain visible without a button and say “No usable price yet — not ready to collect”; progress reads as priced items collected, and guidance explains that unpriced items stay unchecked until verified. Complete plans retain the existing full collection flow. The collectible capability set is emitted by immutable Basket rendering and bound by the View; no display-text parsing, ranking, price arithmetic, planner logic, evidence upgrade, persistence, clock, provider or network authority was added. Deterministic tests cover incomplete-plan eligibility, exact key preservation, guidance and the no-business-authority View boundary.

The latest Home/Basket refinement closes a practical collection-context gap in the existing immutable plan. Each recognized covered item now shows “Buy at [store]” using the exact allocation already produced by the shared one-store/optional-second-stop planner. A recommended second stop moves only its explicitly added item keys; incomplete or unknown-price items remain unassigned rather than being guessed. The renderer maps typed item identities to these projected display names without re-ranking, recalculating, or parsing card text. Basket collection action descriptions include the same store context, while item details and preference-only notices remain visible. Opaque item identities are retained only for typed lookup and are excluded from normal UI/diagnostic text. Deterministic projector, renderer, and View-boundary tests cover complete, incomplete, recommended-second-stop, rejected-stop, no-coverage and collection-description cases.

The latest shared Home/Basket result-card refinement closes a semantic accessibility gap. Primary and optional-second-stop cards now expose one explicit accessibility node whose summary contains every already-projected consumer field: badge, store, basket cost or savings, coverage/allocation, travel, evidence, and any incomplete-result caution. The physical renderer does not calculate or interpret these values; it binds the projected summary while retaining the existing visual lines. Deterministic presentation tests cover complete, incomplete and second-stop summaries, and the View boundary confirms this remains presentation-only.

The latest Basket accessibility refinement closes a semantic context gap in the existing check-off controls. Because a button content description replaces its visible label for assistive technology, each collection action now names the item, its existing detail, the saved preference summary and the preference-only notice, followed by whether the action will mark it collected or not collected. This copy is derived from immutable Home projection plus local foreground collection state; it adds no matching, pricing, eligibility, planner, ranking, evidence, persistence or networking authority. Deterministic tests cover both action directions and the no-business-authority View boundary.

The latest Basket refinement closes a consumer-facing context gap in the existing immutable Home/Basket projection. Check-off and recognized-item rows now render the already-projected request-details summary and optional preference-only notice beneath each item, so saved package count, preferred brand and exact-product intent remain visible during collection. This is renderer-ready copy only: the physical View does not reconstruct details or make matching decisions, and the change does not alter the immutable plan, eligible item keys, totals, ranking, pricing, evidence or persistence.

The latest Watch-selection refinement closes an accessibility context gap in the existing immutable setup surface. Product toggles now identify the saved product they will watch or stop watching, and usual-store toggles identify the saved store they will select or clear. The descriptions are projected alongside the existing typed actions; the physical View binds them without reconstructing identity or interpreting readiness. Deterministic projector and View-boundary tests cover selected and unselected action directions while preserving the no-business-authority boundary.

The latest Saved refinement closes an accessibility context gap in the existing immutable surface. Product and store rows now project item-specific descriptions for their removal actions, so repeated visible “Remove” buttons are unambiguous to assistive technology. Descriptions are emitted only in content/degraded states where the existing lifecycle permits mutation; loading, refreshing, updating and error states still expose no row actions. The physical View binds the supplied descriptions and emits the existing typed preference actions without reading storage, resolving identity, or owning policy/business authority. Deterministic projector and View-boundary tests cover product/store descriptions and busy-state suppression.

The latest Watch policy refinement closes an accessibility context gap in the existing immutable draft form. Each numeric editor now exposes its projected field label and unit to assistive technology, each repeated Apply button identifies the field it applies, and the no-limit control identifies the distance rule it changes. The descriptions are fixed renderer copy only; typed input still flows through the promoted exact adapter, finalization remains the sole policy authority, and no planner, ranking, pricing, evidence, persistence, clock, notification or network capability was added. Deterministic View-boundary tests cover the field-to-control bindings and preserve the no-business-authority boundary.

The latest Home refinement closes a consumer-facing scope gap around explicit item details. When a shopper saves a package count, preferred brand or exact-product preference, the corresponding Home row now shows the compact saved summary plus “Preference only — not applied to this sample plan.” Rows without details remain unchanged. This copy is fixed renderer-ready guidance and does not claim product matching, change the fictional sample total, or grant any planner, ranking, price, evidence, persistence, clock, notification or network authority. Deterministic presentation and View-boundary tests cover present and absent details while asserting that the already-projected plan object remains identical.

The latest Watch policy refinement closes a consumer-facing clarity gap in the existing immutable draft result. When continuation is unavailable, the projector supplies fixed labels in finalization order and the surface renders them in an amber “Still needed” card: Minimum savings, Maximum extra travel time, Maximum extra distance (or no limit), and Minimum watched staples. Complete drafts render no card. The labels are renderer-ready copy only; the existing reducer/finalization remains the sole policy authority, and no planner, ranking, pricing, evidence, persistence, clock, notification or network capability was added. Deterministic projector, adapter and View-boundary tests cover the incomplete, complete and distance-only cases.

The latest Watch setup refinement adds a bounded fact-check progress card after an accepted configured foreground handoff. The active exact session reports only a resolved count and fixed requirement labels to immutable presentation; raw product/store identities, prices, routes, evidence payloads and decision authority remain outside the renderer. The surface says that no switch decision is available yet, and even complete fact coverage explains that policy and display metadata remain separate. Progress is cleared when the immutable selection changes, and late updates cannot attach to a different selection. Deterministic projector, host, presenter and View-boundary tests cover initial, partial, complete, stale and prohibited-authority cases.

The latest Watch setup refinement gives a configured foreground handoff an honest acknowledgement on the existing Saved-backed surface. After the explicit selection is accepted, the user sees that no switch decision has been made and that current prices, route details and evidence checks are still required. Rejected attempts explain the fail-closed setup issue; the message clears when the immutable selection projection changes. This is presentation-only feedback and does not acquire facts, retry work, calculate savings, persist setup, or authorize alerts/notifications.

The latest Basket refinement adds a local-only “Clear check-off” control. It is hidden until at least one planned item is marked collected, then resets only foreground collection marks while preserving eligible item identities and the immutable plan. Pure progress tests and the physical View boundary test cover the behavior; no planner, ranking, pricing, evidence, provider, or network authority changed.

The latest consumer-facing refinement makes incomplete known-subtotal results visibly distinct from complete baskets without changing any decision or projected text. The shared Home/Basket result-card renderer maps the projector's existing missing-price marker to an amber caution background, border and accent; complete baskets retain the confident green treatment. A pure presentation test covers both styles, and the boundary test confirms that the View still consumes immutable strings only.

The latest Saved-tab refinement gives the Watch My Staples launcher renderer-ready explanatory copy: “Choose recurring saved items and a usual store to check whether a future switch is worth the trip.” The copy appears only when the existing navigation-readiness gate has visible saved products and a store. It is explicitly a future-check explanation, not a live merchant or notification claim; the launcher still emits only the existing typed setup-navigation action.

The latest consumer-facing slice closes a Saved-backed Watch My Staples setup gap. Immutable setup presentation now supplies a deterministic selection summary such as “2 staples selected (2 minimum) · Usual store selected,” and the physical setup surface renders it below the guidance. The minimum remains the existing reducer-owned `MIN_WATCHED_SAVED_ITEMS_FOR_HANDOFF` constant, so the View does not infer readiness or duplicate selection policy. No identity, price, travel, evidence, notification, persistence, planner, ranking, or networking authority moved into the renderer.

The latest app-level slice adds bounded local-only cross-session retention around the existing progressive per-item Home details surface and typed `PracticalShoppingHomeSession.State`. The Home session store remembers the last bounded list, chicken choice, extra-stop preference and opaque request-details payload so explicit package count, preferred brand and exact-product intent can survive an app restart. It restores details only for the exact established request, drops oversized/corrupt values safely, and clears stale detail bytes while a draft is being edited. The store reuses the shared-core codec boundary and carries no planner, ranking, price, quantity arithmetic, evidence, clock, networking, or View authority. The existing sample plan object remains unchanged by item intent, and the fictional-plan/no-arithmetic disclosure stays explicit. The milestone provenance check accepts the repository's historical `work/valuepilot-*` candidate branches while excluding the milestone branch itself.

The Basket primary tab is now a real read-only continuation of Plan My Shop rather than placeholder copy. It receives the existing immutable Home presentation, preserves the exact projected plan object, and shows recognized items, unresolved items, complete or incomplete one-store results, any already-approved optional second stop, and the shopper's selected exact extra-stop rule. Empty, draft, refinement, and unresolved states cannot become a false plan and provide one typed action back to Home. Home and Basket share the same result-card View so complete totals, known subtotals, missing-price notices, travel, freshness/evidence, and second-stop details cannot drift between the two surfaces. The fictional/offline disclosure is repeated prominently on Basket. No shared-core planner/projector, provider integration, Android networking, ranking authority, or money calculation was duplicated or moved into a View.

Clean-source verification passed all 1,634 JVM tests (375 shared-core + 1,259 Android app) with zero failures/errors/skips, all 58 Android tasks, all 30 browser tests, Firefox packaging lint with zero findings, APK privacy inspection, and one-signer APK verification.

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
