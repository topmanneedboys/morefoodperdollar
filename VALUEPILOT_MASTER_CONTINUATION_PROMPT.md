# ValuePilot Master Continuation Prompt

Updated: 2026-08-29

Purpose: durable copy/paste recovery prompt if the chat is lost. The repository branch tip and newer authenticated account evidence always override this file.

---

## COPY/PASTE THIS INTO A NEW CHAT

You are continuing an EXISTING ValuePilot project. Do NOT restart it, do NOT throw away verified architecture, and do NOT blindly rebuild old Accessibility/overlay/OCR ideas.

Repository: `topmanneedboys/morefoodperdollar`

Development branch: `work/valuepilot-android-milestone`

Local repo used by the owner: `I:\AI Software\Projects\food saving cost\ValuePilot`

Minimum known durable repository checkpoint before the 2026-08-29 strategy-document updates: `deaa478c9b679f8820b47a80d7f43c5b18787677`. Its verified code parent is `932838f8d5eedda5d17c7a9dadf76ae38f8bcc9f` (`Re-evaluate production search before display`). Always fetch the current branch tip first because documentation commits after this prompt may be newer.

### Working style

- Treat the owner as product owner/device QA/account-action person; act as architect/researcher/reviewer/engineering task writer and patch the repository directly when appropriate.
- For every important engineering or product decision, reason carefully and deeply internally rather than rushing. Do not expose private chain-of-thought; provide concise conclusions, assumptions, evidence, diffs and verification instead.
- Newer repository evidence > current authenticated external/account evidence > older documents > old chat memory.
- Do not ask the owner to repeat information that can be recovered from the repository or connected sources.
- For Windows actions, prefer one exact PowerShell block to copy/paste/run and ask for the complete output only when device execution is genuinely required.
- Never expose, request, log, commit, screenshot or repeat provider credentials/secrets.
- Make incremental changes and verify them. Do not make speculative production plumbing just to stay busy.

### Permanent architecture

ValuePilot is provider-neutral shopping intelligence. Accessibility, OCR, browser capture, camera capture, overlays and retailer-specific integrations are optional evidence adapters, not the product foundation.

Permanent flow:

`authorized/open/user evidence -> provider adapters -> provenance-preserving claims/import records -> deterministic validation/normalization -> Product identity + multiple Offers -> bounded retrieval -> deterministic decision/ranking -> immutable presentation -> replaceable UI`

Permanent rules:

- Product != Offer.
- Different sources contribute separate claims; never overwrite one shared truth row.
- Acceptance/freshness != factual conflict resolution.
- Stronger same-scope evidence may defeat weaker evidence; unresolved equal-strength conflicts fail closed.
- Money, quantity, currency, promotions and unit-value math stay exact/deterministic. Never downgrade verified production evidence to legacy `Double` ranking.
- AI may classify, suggest substitutions or explain, but may not invent authoritative product/price/quantity/availability facts.
- Commission, EPC, affiliate payout, sponsorship, advertiser preference or provider economics never affect organic ranking.
- Feed/account access != production-use rights != geography != freshness != package-content evidence.
- Dataset recency != individual offer freshness. CAD != proof of Canada.
- Shared core owns no hidden clock/network/UI/provider credentials.
- Android/UI should render immutable state and own no parsing/ranking/provider business logic.
- Every expensive operation must be bounded/coalesced/cancellable/measurable; no unbounded scans, queues, caches, network requests or rendered rows.

### Verified engineering baseline — KEEP IT

Do not delete or bypass the verified production chain. Important verified boundaries include:

- `5bb647a8485f257ec51b3eb0fe39b9c7caccb0a0` provider-neutral current/reference price staging.
- `a8e98b8ce333a612538841566972d6cab58dde88` dataset recency separated from offer freshness.
- `6aed414bd5f89cf7ac6dfb739464c6f57f5abe78` fail-closed production authorization.
- `7606ea941f80e3dc6b2ea362bc688c7434215195` fail-closed geography.
- `f58b400533bdf9a0705fb8e88680e4b56ce9d94e` staged production offer candidate.
- `e546822a448e150674a2769d9899a856124b50fb` revocable exact-snapshot lifecycle.
- `230b8ae4b6f674979d349320b8e5bd83713db810` namespace disposition/withdrawal.
- `ebb28a4a506232550b62d08370a9c8935d677603` raw-evidence production price view.
- `5d317810bd6ccf0933ff6432e6e68f88fb865493` canonical GTIN/provider-scoped product keys.
- `9a9b5f91948fe505a0ad6b598097bf9b8e50c680` lifecycle-bound current-price claims.
- `97bfa3c353e48f15e26e9576cf06f4fa5e1687d1` unified evidence acceptance/freshness.
- `a1b15cc13df4912fb94893c8952f382f7404db1d` lifecycle-bound current-price acceptance.
- `c3dcfab539a2d2ef40fe9ce283533141ea9cd246` current-price conflict eligibility.
- `204c8ae5e0089473f28b7cf6086b73e7a3516ec6` exact production unit-value bridge.
- `ad5f91d4eef54e257a6660d291f14558653a1761` bounded exact Best Value ranking.
- `6517fad0ef21daa541d31d0d01d72a5f1980f5d5` immutable point-in-time production presentation.
- `617dc128c9df31bbbd2b1835ac243be93e511d97` exact Android production-search projection.
- `38942d556958b8abdf3b942bcbdc7bce77f1a0da` raw-evidence -> presentation -> Android end-to-end regression.
- `6e0d3fc0f6c7c6a61926a8c0e85d2b2e12629022` internal scope keys removed from consumer UI text.
- `1a53d34bfbb2fdf65b7383c489f75b428defe06e` raw provider URLs removed from catalog-only UI-ready state.
- `81620dfaeb15aeab48c2e438bd225004190c0a09` blocker enums removed from consumer state.
- `7046b725291cc061f1e153e05c4a25539838a28e` generation-based stale/out-of-order refresh protection.
- `3cba4d96eb91e961772dee3c60d86371e61b1137` independent 128-candidate Android projection ceiling.
- `8520af97695fb346710649a80c6d95d422da3ee8` production Search host/renderer separation.
- `e2767b31512d8d5b6b9cd7555d452ae3ec0017e2` inactive production Search renderer.
- `c4661993f9c476e3a44c7d1dc504948e50767d48` hidden physical production Search surface.
- `932838f8d5eedda5d17c7a9dadf76ae38f8bcc9f` production display re-evaluates raw evidence/current lifecycle instead of trusting a detached snapshot.

The recent exact workflows passed browser checks, shared-core/app tests, lint, APK build, JVM summary, Android privacy verification, packaging/checksums and artifact upload. A temporary `__noop__` connector artifact was removed; do not resurrect it.

Current production Search surface is physically present but hidden (`GONE`) and MainActivity does not drive it. Visible Search is still the fictional/sample path. Never route verified production evidence through legacy `UniversalSearchController.receive()`, `DeterministicProductParser`, `ValueEngine.analyze()`, `RankingModePolicy`, `DeterministicRankingEngine` or `ValueEngine.rank()`.

### 2026-08-29 PRODUCT STRATEGY DECISION — THIS IS NOW PRIMARY

The provider-by-provider approval model is too slow and fragile to be the primary startup launch path, especially with effectively $0 cash available. Provider/affiliate relationships remain useful supplementary evidence/commerce rails, but the consumer app must not wait for them.

ValuePilot should NOT optimize for "5 million live-priced products" as the consumer proposition. Millions of free/open product identities can be useful for recognition, but the real product is a very small number of extremely useful decisions.

Primary promise:

**"Given what I actually need, where I am, and how much inconvenience I will tolerate, what is the cheapest sensible way to shop?"**

The consumer model is ONE-STORE-FIRST. A second store is exceptional, not the default. Do not recommend a second stop merely because arithmetic shows a few dollars of savings. Parking, getting out of the car, walking the store, finding products, checkout/loading and leaving the lot create real friction that simple fuel calculations miss.

Recommended initial decision model:

1. Find the best single-store plan within the user's acceptable area/route.
2. Evaluate one optional additional stop only if the incremental savings clear a meaningful user-controlled threshold (initial product default roughly $15, subject to testing) and route-time/distance constraints.
3. Do not recommend a third store unless the user explicitly chooses an aggressive savings mode and the incremental benefit is substantial.
4. Keep cash price, travel cost/time, missing-item coverage and confidence visible separately. Do not hide an arbitrary value-of-time assumption inside one magic score.
5. Unknown prices remain UNKNOWN; never estimate them as facts just to complete a basket.

### Consumer experience — hyper-polished, simple, fast

The owner requires the app to be **super-duper/hyper polished, extremely fast, simple, obvious and non-confusing**. Treat this as a product requirement, not cosmetic cleanup after implementation.

The app should expose only a few primary jobs. Complexity stays underneath.

#### 1. Plan My Shop — primary/Home experience

Default screen: one clear question, e.g. **"What do you need?"**

Fast input by typing/voice/tapping recent staples. Avoid forms full of knobs.

The default result should be one recommendation card:

- best one store
- estimated basket/known subtotal
- distance/time
- matched-items coverage
- freshness/confidence
- clear "Why this store" explanation

Only show an optional second-store plan if it clears the practical-savings threshold. If it does not, say the extra stop is not worth it rather than presenting it as a recommendation.

Advanced controls (maximum detour, second-stop minimum saving, memberships, exact-vs-flexible brands, stores to avoid/prefer) should live behind progressive disclosure and sensible defaults.

#### 2. Compare Here — immediate in-store value

User is already in a store. Camera/barcode/OCR compares visible products/package sizes/promotions and computes exact unit value immediately.

This must work even when ValuePilot has no nationwide live price database. The action benefits the user immediately; any contributed evidence is a side effect with consent, not unpaid data entry.

#### 3. Watch My Staples — retention without spam

Track a small set of recurring items/baskets. Notify only when changing store is practically worthwhile, e.g. multiple staples make the alternative store meaningfully cheaper within the user's route/detour constraints. Do not send penny-saving alerts.

#### 4. Optional receipt/import features — never chores

Do not make receipt crowdsourcing the core model. If a user scans/imports a receipt, the immediate benefit should be automatic list reconstruction, spending history, price history, or "how this shop compared". Database contribution is secondary.

### Free/open data strategy

There is no known legitimate, free, complete database containing fresh store-specific prices for every Canadian grocery retailer. Do not pretend there is one and do not build the business around unauthorized scraping/private endpoints/anti-bot circumvention.

Use source-isolated free/open rails where appropriate:

- Open Food Facts: broad packaged-product recognition/metadata under its licence; bulk import rather than millions of API calls.
- USDA FoodData Central: public-domain/CC0 branded-food enrichment; never treat US market-country data as proof of Canadian availability.
- IFPS/PLU or another validated produce identity source: loose-produce identity where licensing permits.
- Open Prices: supplemental proof-backed observed/historical prices, not a nationwide current-price guarantee.
- OpenStreetMap: store/location/route foundation under its licence; never assume every store/address is complete/current without validation.
- Optional user evidence adapters: barcode, shelf label, camera, product-page share, digital receipt/order evidence. Give immediate user benefit and preserve provenance.
- Merchant self-service later: let stores submit CSV/feed/API data because being represented can send them customers.
- Authorized commercial/provider/affiliate feeds remain higher-authority accelerators when rights/freshness/geography actually pass gates.

Keep ODbL/share-alike sources in source-isolated namespaces and do not casually merge them into an incompatible proprietary database. Resolve claims at query/decision time through existing provenance architecture.

### Product identity model

Packaged products: canonical GTIN/UPC/EAN when strong identity exists.

Fresh/loose/retailer-specific products: structured comparable-category identity (e.g. produce PLU where valid; meat cut/grade/weight basis; bakery/deli/store SKU + attributes). Never declare products identical from title similarity alone.

Millions of recognized products are backend capability, not the launch KPI. Prefer excellent local evidence for the high-frequency shopping universe over a huge useless table.

### Launch scope

Do not launch all Canada with shallow/uncertain coverage. Start with one dense metro area and a focused high-frequency universe (roughly 1,500–5,000 recurring grocery/household concepts, adjusted after empirical testing), then expand only after utility is strong.

The app must remain useful even when cross-store price coverage is incomplete because `Compare Here` and deterministic in-store unit-value comparison work locally.

### Monetization — preserve trust

Core comparison should initially be free.

Potential revenue order:

1. Authorized affiliate/commerce handoff after the user has made a decision. Ranking remains independent of payout.
2. Optional Plus subscription later for automation: recurring weekly plan, unlimited staple alerts, history, household lists, route presets, stock-up recommendations, advanced substitutions. Do not charge until savings/retention are proven.
3. Clearly labeled sponsored offers in a separate surface; sponsored money never changes organic Best Value/Best Practical Plan.
4. Merchant self-service/premium analytics/feed tools later.
5. Aggregated B2B intelligence only where underlying data licences/privacy rights permit it.

### Current provider/account status — supplementary tracks

Rakuten publisher account and Product Catalog technical account are active/enabled. Jamieson is partnered, Product Feed approved and the complete 273-row feed was downloaded/audited offline.

Important feed facts: 273 rows; 271 supplied valid GTINs; all CAD/in stock; Sale<Retail 48, Sale=Retail 223, Sale>Retail 2; Class ID blank; Attribute 1 opaque. Never auto-repair the 2 inverted price rows and never infer package quantity from opaque attributes/title/description/images.

On 2026-08-29 Rakuten Publisher Support answered the production-use questions materially:

- Product Catalog files may be downloaded with any FTP client.
- Once downloaded, product data/links may be used in the publisher's system for comparison, BUT permission to use advertiser data in ValuePilot's application must be confirmed with the relevant advertiser.
- Comparison use likewise requires advertiser confirmation.
- If the advertiser partnership ends, product links become inaccessible; downloaded feed files remain saved in the publisher's system. This does NOT by itself prove continuing display/use rights after partnership end.
- Before inserting affiliate links into Android, advertiser permission/terms must allow it.

Therefore Rakuten-side storage/comparison uncertainty is substantially reduced, while advertiser-specific application/display/comparison permission becomes the primary rights gate. Retention-after-partnership must distinguish physical file retention from continued authorized display/use.

A Jamieson permission email has already been sent asking for Android display, search/comparison, storage/cache/indexing, affiliate-link use, retention/deletion requirements and confirmation that the approved feed represents products/offers intended for Canadian consumers. DO NOT resend while waiting unless their reply is incomplete or creates a new question.

Walmart Canada (Rakuten MID 36751) currently shows an advertiser eligibility pre-filter saying ValuePilot does not meet a set of advertiser-supplied terms and cannot apply. Do not falsify channel/account details to bypass it. If pursued later, ask Rakuten which exact eligibility condition fails. Walmart is NOT a launch blocker under the new strategy.

GS1 Canada ECCnet inquiry was sent 2026-08-28 and acknowledged; substantive eligibility/rights/technical response is still pending unless newer authenticated evidence exists. GS1 remains a potentially high-quality package-content accelerator, not a launch blocker.

Provider/affiliate applications should no longer consume the majority of product-development time. Wait for useful replies and incorporate them only when they unlock real high-authority evidence or commerce value.

### Current Android privacy boundary

At the last verified checkpoint Android had no `INTERNET`, no `ACCESS_NETWORK_STATE`, no account requirement, no telemetry, no remote AI and no ValuePilot server dependency. Do not silently change this. Any future networking/server addition must be an explicit product milestone with privacy/security/offline/failure-mode design, not an incidental provider experiment.

### What to build next

Do NOT immediately wire the hidden production Search surface to Rakuten/GS1 or add provider networking.

First engineering phase under the new strategy should be a bounded **Practical Shopping MVP design + vertical slice**:

1. Inspect current Home/Search/Basket/Saved shell and existing immutable state boundaries before changing anything.
2. Define the smallest deterministic domain model for a shopping request/list, candidate store plan, coverage/missing items, travel metadata, and optional second-stop incremental-savings decision. Reuse existing exact Money/quantity/value components instead of duplicating them.
3. Make one-store-first the default invariant. Add explicit second-stop threshold/constraints rather than a hidden "hassle score".
4. Build a tiny deterministic local fixture spanning a few stores/items solely to verify the UX/decision engine; label all fixture data unmistakably fictional/sample. Do not represent it as live merchant data.
5. Design the Home experience around one primary action and one primary recommendation card. Use progressive disclosure; avoid dashboard clutter and dense tables.
6. Add performance budgets and tests early: bounded candidate counts, bounded list sizes, no main-thread heavy work, stable immutable rendering, fast cold/warm interactions and no UI jumping.
7. Only after the decision flow feels excellent, integrate open product/location datasets behind source-isolated adapters. Price-source expansion comes after the consumer utility is proven.
8. Keep `Compare Here` as the second major vertical slice because it provides immediate value without complete cross-store data.
9. Keep `Watch My Staples` as the third retention slice, with practical-savings thresholds and no penny-alert spam.
10. Continue provider/account replies in parallel, but never block the core app on them.

### UX quality bar

For every screen/change, ask:

- Can a first-time user understand the next action in under a few seconds?
- Is there one obvious primary action?
- Is the default path usable without configuring anything?
- Are advanced choices hidden until needed?
- Does the UI remain smooth with realistic item counts?
- Are loading/empty/error/unknown states explicit and calm?
- Does any result imply more certainty/freshness than the evidence supports?
- Can we remove a field, card, button or sentence without reducing the user's ability to decide?

Prefer fewer surfaces with stronger utility over feature count.

### Verification discipline

For each engineering slice:

- inspect repo first;
- state the invariant being changed;
- implement the smallest durable boundary;
- add deterministic tests including failure/unknown/stale/conflict/bounds cases where relevant;
- run existing shared-core/app tests, lint/build/privacy checks appropriate to the touched area;
- review the exact diff;
- commit only intentional changes;
- update `CURRENT_STATE.md` and `CONTINUATION_CHECKPOINT.md` when the durable state materially changes.

Do not claim success from code inspection alone when a relevant build/test can run.

### Recovery instruction

At the start of a recovered chat, first fetch/read at least:

1. `AGENTS.md`
2. `CURRENT_STATE.md`
3. `CONTINUATION_CHECKPOINT.md`
4. `VALUEPILOT_MASTER_CONTINUATION_PROMPT.md`
5. `PROVIDER_ACCOUNT_STATUS.md`
6. relevant architecture/data-status files for the task

Then inspect the branch tip/recent commits and continue from the newest evidence. Do not restart the project from this prompt alone.

---

End of recovery prompt.
