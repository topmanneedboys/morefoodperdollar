# ValuePilot Practical Shopping Checkpoint

Updated: 2026-09-05

Branch: `work/valuepilot-android-milestone`

This checkpoint records the verified Practical Shopping Home slice plus the provider-neutral production evidence-to-decision path after the first execution-complexity hardening. Newer repository evidence overrides this file.

## Latest verified engineering head

`1db1b0d9edaa18e4b78ef11d08d317175d4e2902` — `Add repeat-list action to Practical Shopping Home`

Candidate workflow **33958293679** passed for the exact code SHA, and milestone provenance workflow **33958570310** verified the promoted ref and release artifact lineage. Home now offers an explicit `Shop again` action after a completed nonempty result. The action replays only the existing typed list through `PracticalShoppingHomeSession`, remains hidden for idle/draft/refinement/error states, and is disabled until the owner-driven surface is attached and has rendered state. Activity teardown clears the callback; session guards return the same state for non-result calls. This focused repeat-use improvement adds no planner/projector, exact money/quantity, price/evidence, offer, store/availability, persistence, Android networking, demo-data or provider-economics authority. Candidate Linux verification passed 402 shared-core tests and 1,497 Android tests with zero failures, lint/build/privacy/single-signer/release-bundle and browser/Firefox checks; local Python/catalog verification passed 79 tests. Physical-device ergonomics and lawful production Home activation remain open.

`42f86e7dbd6a1e03a694c85032f30f21b1ab752e` — `Let saved products reopen Good Price`

Candidate workflow **33957008185** passed for the exact code SHA, and milestone provenance workflow **33957294707** verified the promoted ref and release artifact lineage. Saved product rows now expose a typed `Check price` action that opens the existing Good Price route with a bounded, display-only name prefill. The action does not reuse saved identity as package, price, store, availability or evidence; Good Price still requires exact shopper-entered quantity/currency/price and its existing evaluator remains authoritative. Busy Saved states remove the action, and the route coordinator treats it as navigation-only. This repeat-use convenience adds no planner/projector, money, evidence, offer, store/availability, persistence, Android networking, demo-data or provider-economics authority. Candidate Linux verification passed 402 shared-core tests and 1,494 Android tests with zero failures, lint/build/privacy/single-signer/release-bundle and browser/Firefox checks; local Python/catalog verification passed 79 tests. Physical-device ergonomics and lawful production Home activation remain open.

`37cc19cb72cb231831dfecef8fd5eb0c0e5bca2b` — `Add review gate for OCR comparison suggestions`

Candidate workflow **33955298400** passed for the exact code SHA, and milestone provenance workflow **33955607275** verified the promoted ref and release artifact lineage. Scan & Compare now treats camera/photo OCR as a proposal: bounded, safe, de-duplicated snippets appear in a clearly labelled untrusted multi-choice review, and nothing reaches the editable comparison draft until the shopper taps `Add selected`. The existing exact parser, package/price validation, like-for-like confirmation, comparison and private-memory capture remain the only authorities; cancelling, dismissing, stale lifecycle callbacks, unsafe/duplicate text and capacity overflow preserve existing entries. This focused consumer-flow improvement adds no planner/projector, money, evidence, offer, store/availability, persistence, Android networking, demo-data or provider-economics authority. Candidate Linux verification passed 402 shared-core tests and 1,490 Android tests with zero failures, lint/build/privacy/single-signer/release-bundle, browser/Firefox and Python/catalog checks; physical-device camera/OCR ergonomics and lawful production Home activation remain open.

`0e1ffaaf24352ad9652631d84d081cd67db4699d` — `Clarify missing Home item price breakdowns`

Candidate workflow **33952630205** passed for the exact code SHA, and the resulting promoted tree was provenance-verified by milestone workflow **33953296196**, including shared-core/Android tests, lint/build, browser/Firefox, release-bundle, APK privacy and single-signer checks. Home and Basket now make the optional breakdown boundary explicit: a covered item with an exact line value still shows `Included in plan: X CAD`, while a covered subtotal without that optional detail shows `Included in the basket total — exact item price not shown.` The inactive production Home projector and surface carry the same notice, and Basket’s check-off accessibility description does too. No planner, shared projector, money, evidence, offer, store/availability, ranking, network, persistence or provider-economics authority changed; missing/stale/conflicting/unknown states remain honest and the fictional visible Home stays separate. A test-only newline normalizer keeps complete source-boundary assertions stable across Windows and Linux; the full local Windows Android suite is now green. Physical-device ergonomics and lawful production Home activation remain open.

`8025eb6e753d505dc75c19630683fd8f3afc2c29` — `Harden production Home UI handoff`

Candidate workflow **33933221103** passed for the exact SHA, including shared-core/Android tests, lint/build, browser/Firefox, release-bundle, APK privacy and single-signer checks. The production Home host now retains the exact internal projection only for generation identity and hands its renderer a separate demo-free `PracticalShoppingProductionHomeUiState`. That presentation boundary reuses the existing `PracticalShoppingUiState`, exposes bounded requested-item rows, shows exact included line prices when the upstream candidate supplies them, and marks uncovered items with an explicit unknown-price notice. Missing or unsafe labels and malformed assignment keys fail closed; valid partial and no-coverage decisions remain visible. The new production surface is inactive by default and reuses the existing result-card renderer, so the visible Home remains the clearly labelled fictional controller until lawful current-offer/private-evidence inputs and a separate coordinator are available. Candidate Linux verification covered 402 shared-core tests and 1,482 Android tests with zero failures, browser/Firefox, lint/build, release-bundle, APK privacy and single-signer checks; the local Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and production Home activation remain open.

`730794ed111658383ede5350fededfd57c98ece9` — `Add generation-safe production Home handoff`

Candidate workflow **33930515512** passed for the exact SHA, including shared-core/Android tests, lint/build, browser/Firefox, release-bundle, APK privacy and single-signer checks. The production Home seam now has a narrow Android adapter that delegates valid orchestration decisions to the existing `PracticalShoppingUiProjector`, plus a bounded generation-aware surface host that re-evaluates raw orchestration inputs against current lifecycle/disposition registries. Stale, duplicate and same-generation-conflicting refreshes are rejected; structural/reference failures become an explicit unavailable state, while a valid no-coverage decision remains a truthful projected outcome. The renderer receives only the sanitized immutable projection. This is preparation for a future production Home activation: the visible Home remains the clearly labelled fictional controller, with no Android networking, retailer feed, provider economics or demo-data promotion. Candidate Linux verification covered 402 shared-core tests and 1,473 Android tests with zero failures, lint/build/privacy/single-signer/release-bundle, browser (30 tests/Firefox lint) and Python/catalog (79 tests) gates; the local Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and full production Home activation remain open.

`3efdb688d5ff981b4f421bf349d49ef1432679e1` — `Add privacy-safe Good Price result sharing`

Candidate workflow **33927693684** and milestone provenance workflow **33928182141** passed for the exact code SHA. The first-class Good Price route now exposes `Share this price result` only after one exact evaluated result. The user reviews a bounded, generic `text/plain` card before the Android Sharesheet; it contains only the exact entered price, package quantity, selected basis and unit-rate math, says it is not live store pricing, and omits the product name, private history, receipts, location, account and source identifiers. Blocked, incomplete, ambiguous or unsafe display facts never receive a card. This focused consumer handoff does not duplicate the shared planner/projector and adds no money/quantity, evidence, offer, store/availability, persistence, Android networking, demo-data or provider-economics authority. Candidate Linux verification passed 402 shared-core tests and 1,465 Android tests with zero failures, lint/build/privacy/single-signer/release-bundle, browser (30 tests/Firefox lint) and Python/catalog (79 tests) gates; the local Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and production Home wiring remain open.

`d53b4fca70c3e84b32fab49c7b8aad789e4e92e3` — `Let Home save confirmed offline product identities`

Candidate workflow **33923409718** and milestone provenance workflow **33923881317** passed for the exact code SHA. Resolved Home rows now expose an explicit `Choose exact product` action. The existing bounded offline catalog lookup presents identity candidates only; after explicit confirmation, the existing source-revalidated exact-choice projector and asynchronous Saved transaction persist a private identity preference. It does not infer package quantity, price, currency, store, stock, availability or freshness, and unknown rows remain query-only. The fictional sample planner keeps its existing disclosure and result unchanged. This focused consumer selection surface does not duplicate the shared planner/projector or add Android networking, new persistence authority, ranking influence or provider economics. Candidate Linux verification passed 402 shared-core tests, 1,457 Android tests, lint/build/privacy/single-signer/release-bundle, browser (30 tests/Firefox lint) and Python/catalog (79 tests) gates; the local Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and production Home wiring remain open.

`80b9efd5e33839e3f0dfd1b10e273c1a2547cfaf` — `Show private Home price observation date`

Candidate workflow **33919462842** and milestone provenance workflow **33920091991** passed for the exact SHA. Home’s exact name/package private-history row context now includes the latest observation date in stable UTC form. A zero or missing timestamp is shown as `date not recorded`; mixed package, currency, unit-basis or promotion histories still fall back to the conservative name-only notice. The date formatter is shared with the bounded private-history review, and the copy continues to identify these as device-only observations rather than live retailer prices. The planner/projector, exact money/quantity, evidence, offer, store/availability, ranking, persistence, networking and provider-economics boundaries remain unchanged. Candidate Linux verification passed 402 shared-core tests, 1,452 Android tests, lint/build/privacy/single-signer/release-bundle, browser (30 tests/Firefox lint) and Python/catalog (79 tests) gates; the local Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and production Home wiring remain open.

`8fc5629e99cca13ff2aca2b462ddaf5ee1bc1dbf` — `Show comparable personal price context on Home`

Candidate workflow **33917203335** and milestone provenance workflow **33917694467** passed for the exact SHA. Home now carries the existing sample item’s normalized package quantity into its read-only private-history row context. When the normalized name/package observations share currency, unit basis, price-selection and promotion terms, Home shows the deterministic last recorded personal price and remembered unit-rate range. Mixed packages, currencies, bases or promotion shapes fall back to the earlier name-only notice. The copy keeps product identity, brand and store differences visible and says this is not live store pricing. The planner/projector, exact money/quantity, evidence, offer, store/availability, ranking, persistence, networking and provider-economics boundaries remain unchanged. Candidate Linux verification passed 402 shared-core tests, 1,451 Android tests, lint/build/privacy/single-signer/release-bundle, browser (30 tests/Firefox lint) and Python/catalog (79 tests) gates; the local Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and production Home wiring remain open.

`f3afb2b274ad16dbc84454cc615277c2084ff124` — `Show exact item prices in Home plan`

Candidate workflow **33914080876** and milestone provenance workflow **33914604138** passed for the exact SHA. Home and Basket now expose a renderer-only `Included in plan: X CAD` line for each covered item when the existing planner candidate supplies an exact line-item breakdown. The optional map is validated with exact Money arithmetic against the covered set, currency/precision and authoritative subtotal, then ignored by planning/ranking. Production bridges and the fictional demo populate it; incomplete items stay unknown and legacy candidates without a map remain unchanged. Basket includes the displayed amount in its local check-off scope. No product, offer, availability, evidence, planner, ranking, network, persistence or provider-economics authority changed. Candidate Linux Android/browser/privacy/signing/release and Python/catalog gates passed; the local Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and production Home wiring remain open.

`d206331e2d3927e3b084b0fed1d92b40f16cdc68` — `Show Home personal history coverage`

Candidate workflow **33900346836** and milestone provenance workflow **33900879487** passed for the exact SHA. Home now adds a renderer-owned `Name-matched personal history: X of N list items.` summary when the current list has nonblank names and readable private comparison memory. It is derived only from normalized display labels and is explicitly not current-price coverage; it does not establish exact product identity, package quantity, price, freshness, store, stock, availability, offer or ranking. Blank/empty requested names keep the general private-history summary, unreadable memory remains suppressed, and the fictional planner/sample path remains separate. This focused presentation change reuses the existing immutable projection and private store, adds no planner/projector, money, evidence, persistence, network or provider-economics authority, and is covered by deterministic Home history/renderer tests. Candidate Linux Android/browser/privacy/signing/release and Python/catalog gates passed; the local Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and production Home wiring remain open.

`41a0fc28466cd0ac5b794bcedfaf49ff7d15173a` — `Expose offline product identity search`

Candidate workflow **33898457412** and milestone provenance workflow **33898919053** passed for the exact SHA. Search now provides an explicit, user-triggered lookup against the signed 30,000-record offline Canada identity rail. It presents only names, brands, deterministic match labels and bounded candidate counts; it never presents package quantity, price, stock, store availability, freshness or ranking. A chosen identity is handed through the existing untrusted text path into Scan & compare, which remains authoritative for exact quantity, currency and observed price. Query edits, sample actions, route changes, cancellation and teardown dismiss or invalidate stale identity work. The fictional sample value search remains separate and unmistakably disclosed. This additive discovery surface does not duplicate the shared planner/projector and adds no Android networking or persistence authority. Candidate Linux Android/browser/privacy/signing/release and Python/catalog gates passed; the Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and production Home wiring remain open.

`713e4396c6643f3b6aa4b06af3a90cea0921416a` — `Add text share handoff to Compare Here`

Candidate workflow **33895387933** and milestone provenance workflow **33895985864** passed for the exact SHA. The app now exposes an intentional `text/plain` share target for product text a shopper is already viewing elsewhere. The share surface bounds the raw text, shows an untrusted preview and requires an explicit `Open in Scan & compare` action. Compare Here then inserts one trimmed value into its earliest empty editor slot, preserves all existing entries, and reports blank, oversized and full-draft failures without overwriting anything. This is a reversible input convenience only: shared text is not parsed or promoted into identity, package, price, store, availability, evidence, planner or ranking facts, and no Android networking or new persistence authority was introduced. Existing exact manual comparison, private observation and fictional Home/demo boundaries remain unchanged. Full candidate Android/browser/privacy/signing/release verification and milestone provenance passed; the Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and production Home wiring remain open.

`45f52cdaa5d3933a30b14f0386c9d51310d9a0f3` — `Make Home private history deletion explicit`

Candidate workflow **33892635254** and milestone provenance workflow **33893217763** passed for the exact SHA. Home’s existing `Review private price history` action still presents a bounded, read-only projection of the existing private comparison memory: exact like-for-like observations are grouped by the existing rules and show latest/low/high unit rates, package, basis, source and UTC date, with a 32-row cap and omitted-count disclosure. Copy identifies rows as personal observations—not live store prices, inventory, retailer offers or guarantees—and never invents a store. The review now has an explicit `Clear private history` action with a confirmation dialog. Acceptance calls only the existing atomic local store, updates Home after the store accepts deletion, and shows a failure dialog without claiming that data was removed. The confirmation says the Home list, identity catalog, store locations, live offers and recommendations are unaffected. Empty/unavailable memory preserves its recovery behavior and the positive action still opens Scan & compare. This is reversible local-consumer control only: shared planner/projector, exact money and evidence authority are untouched. Full candidate Android/browser/privacy/signing/release verification and milestone provenance passed; the Windows run retains the known 18 line-ending-sensitive boundary failures, while physical-device ergonomics and production Home wiring remain open.

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

The identified raw-evaluation complexity issue is now hardened, Home has an honest action for collecting a missing personal price, and the provider-neutral **exact product/store/quantity/price handoff contract** is implemented by `PracticalShoppingProductionAssembler` and the production orchestration path. The latest slice adds the demo-free production Home UI state and inactive renderer boundary around that path.

Next engineering slice: continue the consumer-first Scan & Compare journey with the next deterministic private-memory/convenience gap and available device ergonomics before activating production Home or adding provider plumbing. Keep the visible Home sample planning separate until concrete local evidence and travel inputs exist; unknown prices remain unknown, one-store-first stays explicit, and Views remain limited to immutable rendering and typed actions.

That contract should carry only already-established facts and raw evidence inputs:

- explicitly confirmed product identity -> exact `ProductionProductEvidenceKey` binding;
- explicitly selected merchant/location/channel store scopes;
- exact package quantity, currency and observed price with proof/source and observation time;
- raw current-price eligibility requests and current lifecycle/disposition registries at execution;
- explicit user-to-store and base-to-added-store travel facts;
- explicit planning policy and evaluation instant.

It must validate cross-reference completeness and bounds before invoking `PracticalShoppingProductionDecisionEvaluator`, but it must not:

- resolve products from names/descriptions/images;
- infer merchant/location/channel identity;
- promote an identity-catalog row or location-directory row into an offer;
- calculate or fetch route facts;
- enable Android networking;
- create provider authorization;
- upgrade open-data/reference evidence into current retailer offers;
- persist a production decision or eligibility result as continuing authority.

The next production-facing gate is now the Android coordinator that supplies assembled requests off the main thread and binds its immutable output into the visible Home surface. `PracticalShoppingProductionHomeSurfaceHost` already provides the bounded generation/stale-result seam for that future coordinator. It must still be wired only after a lawful offer source and a production Home state model replace the fictional controller. Provider networking/rights, private price-book persistence, location/origin handling and actual Home production activation remain separate future gates.
