# Current state

Updated: 2026-09-02

Branch: `work/valuepilot-android-milestone`

Android version: 101.1.0 (10101)

Latest promoted engineering head: `79c754b3468aa6e0927e83a6c9f98ddd3e3c0947` (`Persist Home item intent details`), verified by candidate workflow **33664441555** and promoted workflow **33665119468**. The milestone includes the immutable app-level `PracticalShoppingRequestDetailsSession` owner on top of the shared request-details core, plus a typed `PracticalShoppingHomeSession.State` that keeps explicit quantity/package/brand intent separate from the existing sample plan. Home restores those details only for the exact established request, prunes removed item keys during list edits/replanning, and fails closed on malformed lifecycle payloads without changing the restored plan. Details never feed basket arithmetic, planner, ranking, pricing, evidence, clock, persistence, or network authority. The provenance workflow recognizes the repository's historical `work/valuepilot-*` candidate branches while explicitly excluding the milestone branch itself. The earlier Basket surface remains intact: it presents the recognized list, unresolved tokens, exact already-projected one-store/optional-second-stop result, and selected exact extra-stop rule. Empty and attention-required states return the shopper to Home without inventing a result. Home and Basket share one physical result-card renderer, while presentation boundaries perform no planning, ranking, money formatting, or missing-price inference. Incomplete results remain explicitly labelled known subtotals and the fictional/offline disclosure is repeated prominently. Clean-source verification passed 1,604 JVM tests (375 shared-core + 1,229 Android app), all 58 Android tasks, all 30 browser tests, Firefox lint with zero findings, APK permission inspection, and one-signer APK verification.

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

Next phase is the **Practical Shopping MVP**:

1. Inspect current Home/Search/Basket/Saved shell and immutable UI-state boundaries before changing them.
2. Define the smallest deterministic domain model for shopping request/list, candidate store plan, matched/missing coverage, exact known basket cost, travel metadata, confidence/freshness, and optional second-stop incremental-savings decision.
3. Reuse existing exact money/quantity/value components. Do not duplicate arithmetic.
4. Encode one-store-first as the default invariant. Use an explicit second-stop minimum savings/route constraint rather than a hidden hassle score.
5. Use a tiny fictional/sample multi-store fixture solely to prove the decision flow/UX; never imply live merchant data.
6. Build Home around one primary action and one primary recommendation card. Advanced settings are progressive disclosure.
7. Add performance budgets/tests early: bounded candidates/list size, no heavy main-thread work, stable immutable rendering, smooth scrolling/interactions and no random jump-to-top behavior.
8. After the decision flow is excellent, integrate source-isolated open product/location data.
9. Build `Compare Here` as the second major vertical slice.
10. Build `Watch My Staples` as the third retention slice.
11. Continue Jamieson/GS1/provider replies in parallel; incorporate them only when they unlock real high-authority evidence/commerce value.

No user/device action is required for this documentation checkpoint.
