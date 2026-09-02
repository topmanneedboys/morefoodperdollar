# ValuePilot Continuation Checkpoint

Updated: 2026-09-02

Branch: `work/valuepilot-android-milestone`

## Current verified engineering head

`fe0c284e2ffdf496dad1fc40843b033b40c8ab51` (`Clarify Home preference-only details`) is the promoted milestone, verified by candidate workflow **33679577545** and milestone workflow **33680191624**. Home item rows now pair any saved package, brand or exact-product intent with a fixed “Preference only — not applied to this sample plan.” note, closing the gap between truthful detail editing and the still-fictional sample planner. The note is derived in immutable presentation and rendered mechanically; the View still owns no policy, planning, ranking, pricing, persistence, clock, notification or network authority. Clean-source verification passed 1,631 JVM tests (375 shared-core + 1,256 Android app), all 58 Android tasks, all 30 browser tests, APK privacy checks, one-signer verification and release-bundle provenance.

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
