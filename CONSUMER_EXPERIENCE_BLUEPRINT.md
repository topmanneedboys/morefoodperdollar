# ValuePilot consumer experience blueprint

Status: redesign package only; no Android source, backend, signing, Play, or device
state was changed for this checkpoint.

Baseline: branch `work/valuepilot-confirmed-purchases`, commit
`acef1dd724840ce4f9ce9588eee5c599a9f54621`, the 10124 physical-test candidate.

The design target is calm, polished and fast without copying another product:

- calm hierarchy and generous spacing;
- large, readable important numbers;
- one obvious action at a time;
- card/sheet transitions that explain what changed;
- persistent context and reliable Back behavior;
- progressive disclosure for evidence, advanced tools and settings.

The durable mental model is **Find a product -> Add it to your Basket -> Shop
smarter**. Existing domain logic remains the authority; Views only render immutable
state and emit typed actions.

## Information architecture

### Home — “What matters now?”

First visit:

1. ValuePilot and the short promise `Find products. Build your basket. Shop smarter.`
2. One large `Search products` action.
3. A small three-step visual: Search -> Basket -> Shop.
4. No dashboard of Compare, Good Price, history, data status or source controls.

Returning visit:

1. Active basket count/progress and one `Open basket` action.
2. `Buy again` only when confirmed purchase history exists.
3. At most one contextual insight, such as an unresolved item or a stale search.

Home is not a database administration screen. Sample/demo data must remain visibly
fictional and must never look like local live offers.

### Search — “Find products”

Search is the main discovery surface. The result card hierarchy is:

1. exact identity: name, brand and package/quantity;
2. one primary `Add to basket` action;
3. a compact saved state;
4. secondary `More` actions (photos, map, watch, history, compare/check price when
   the evidence supports them);
5. a `Why this result?` details sheet for source, release and identity limits.

Search remains identity-only when that is what the release provides. It does not turn
identity existence into price, stock, pickup, delivery or proximity claims.

### Product context — “What is this?”

Product context is the shared composition for a Search result, Home identity, Basket
row or Buy again item. It prevents the current spread of actions across many screens.
The product context owns no business decisions; it receives projected state and emits
actions to the existing coordinators.

### Basket — “What am I buying?”

The Basket makes quantity, check-off, evidence coverage and unresolved items obvious.
The visual order is:

1. item count and completion/progress;
2. item rows with identity and quantity;
3. known subtotal or an unmistakably labelled incomplete/unknown amount;
4. `Plan basket`/store/trip only when relevant;
5. explicit `Confirm purchase` for historical recording.

### You — “My private information”

The current Saved tab becomes `You` visually, while route compatibility is preserved.
It contains Saved products, Purchases, Watching, Your prices, stores/preferences and
Settings & about. It does not contain a live catalog-management dashboard.

### Settings & about

Sources/licences, Data Status, Privacy, optional accessibility settings, export and
destructive history controls live here. Attribution remains available but is not a
global footer on every shopping screen.

## Feature-placement map

| Feature | Default entry point | Secondary placement | Never do |
|---|---|---|---|
| Search | Home CTA / Search tab | Buy again, empty Basket | Hide the main Search action behind a menu |
| Add to Basket | Search/product card | Home quick add | Require a long form for a known identity |
| Save | Product context | Search card | Treat Saved as current price evidence |
| Photo | Product `More` | Compare/Good Price | Present an unverified photo as exact identity proof |
| Map/store | Product/store context | Basket when a store is selected | Infer stock from store existence |
| Price Watch | Product context | You -> Watching | Put watch management on Home |
| Purchase history | Product context | You -> Purchases | Recreate purchases automatically |
| Check price | Product context | Compare/Good Price result | Claim current retailer pricing from a private observation |
| Compare | Product/Basket `More` | Good Price | Make it a Home peer to Search |
| Trip estimate | Basket/store context | Product/store context | Present manual assumptions as a road route or live saving |
| Sources/licences | You -> Settings & about | Result `Why?` | Repeat long legal text on Home |
| Data Status | You -> Settings & about | Search stale Details | Make diagnostics a shopping CTA |
| Capture/accessibility | Product `More` | Settings & about | Build a persistent overlay foundation |

## Copy reduction table

| Current wording | Target wording | Notes |
|---|---|---|
| `Compare the real cost. Build the smarter basket.` | `Find products. Build your basket. Shop smarter.` | Short promise; no claim of live prices |
| `Repeat a past purchase` | `Buy again` | Familiar and action-oriented |
| `Quick answers` | Remove from Home; contextual `Tools` | Avoid dashboard language |
| `Scan & compare prices` | `Compare prices` | Capture remains a secondary method |
| `Is this a good price?` | `Check price` | Shorter, still cautious |
| `Plan my shop` | `Open basket` or `Plan basket` | Depends on whether a basket exists |
| `Shopping Space` / `Plan ready` | Basket progress/status graphic | Replace invented product vocabulary |
| `Plan this trip` | `Estimate trip` | Signals manual assumptions |
| `Review private price history` | `Price history` | Put privacy in the destination context |
| `Your remembered prices` | `Your prices` | Graphical timeline can carry detail |
| `Observation` | `Recorded price` | Plain-language historical fact |
| `Evidence` | `Why this answer?` | Progressive disclosure |
| `Edit` | Object-specific `Edit basket item`/`Edit assumptions` | Never leave an unexplained generic action |
| `This release is no longer current. Try again later.` | `Search unavailable` / `Latest product data is being updated` | Calm stale state with `Try again` and Details |
| `ARGENTINA LIVE SEARCH • IDENTITY ONLY` | `Product search` + `Identity only` | Reduce technical vocabulary |

## Currency and regional truthfulness audit

The codebase has an intentionally multi-currency exact-money core. Argentina backend
contracts and release tooling explicitly require `ARS` (for example
`ArgentinaBackendContract.kt` parses `totalArs`, `lineTotal`, `packagePrice` and
promotion prices as ARS; `tools/argentina_daily_release.py` rejects a non-ARS offer).
The Argentina source boundary therefore must remain:

`source currency -> exact ARS money -> eligible evidence/unknown -> presentation`.

The codebase also contains many `CAD` fixtures and examples. They are not one single
leak:

1. shared-core and merchant-feed tests use CAD fixtures to exercise exact arithmetic;
2. manual Compare/Good Price examples mention `CA$`/`US$` so a user must enter a
   currency explicitly;
3. the fictional Home planner and some private historical/test data can visibly show
   CAD; that is a demo/local-state presentation concern, not permission to replace it
   globally with ARS;
4. Argentina current-offer paths are separately guarded as ARS and identity Search
   does not claim a price.

Therefore the correct UI work is an explicit source/state audit, not a global string
replacement. Before a future consumer release, add deterministic tests for:

- Argentina `ARS` source evidence rendering with no CAD fallback;
- manual user-entered CAD/other ISO currencies remaining explicit and comparable only
  within the same currency;
- fictional sample cards retaining an unmistakable `SAMPLE`/demo label;
- private observations preserving their recorded currency after restart;
- a persisted pre-existing Canadian private value not being silently reinterpreted
  as Argentine currency.

If a value has no source currency, render `Currency unknown`/the existing safe unknown
state. Never infer ARS from country, device locale, store address or a bare `$`.

## Stale and unavailable Search presentation

The current backend state observed on the 10124 physical test was `STALE_RELEASE`,
rendered as `This release is no longer current. Try again later.` and zero genuine
results. The correct consumer presentation is:

```
Search unavailable
Latest product data is being updated.
[Try again]  [Details]
```

The query, scroll position and prior safe identity state remain intact. `Details`
can expose release/source metadata and the exact reason. `NO_MATCHES` remains a
different state: it means no identity matched this query in the qualified release,
not that a store has no stock.

The >5-results, Load More and Product->Map physical paths were not run because the
release was stale. They require a fresh independently qualified backend release and
must not be simulated by fixture rows or reclassified as a UI pass.

## Visual and interaction rules

### Hierarchy

- One primary filled action per surface; secondary actions are text/icon buttons or
  a bottom sheet.
- Product cards use a stable identity block, a clear primary action and a visually
  separate status/evidence row.
- Important numbers (Basket count, complete total, trip estimate, unit rate) are large;
  explanatory prose is short and expandable.
- Use restrained depth: a single elevation language for cards and sheets, no decorative
  3D or continuous motion.

### State change feedback

- Add/remove/save/check-off updates the affected row immediately and confirms the new
  state in place.
- Preserve Search query, result ordering, scroll position, selected product and Home
  list through tab changes, rotation and recreation.
- Back returns to the previous logical route and restores its state; system Back and
  the visible secondary-route button have one shared reducer path.

### Accessibility

- Minimum 44dp touch targets, including Search, Add, Save, More, Load more, Back and
  confirmation controls.
- Every icon action has a specific content description (`Save product`, `Open map`,
  `More for this item`, not just `Button`).
- Logical focus order follows the visual hierarchy; large-text mode must not hide the
  primary action.
- Status is never conveyed by color alone: pair complete/incomplete/stale/unknown
  with text and an icon/shape.
- Destructive actions name their scope and require an accessible confirmation.

### Motion and performance

- Use short bounded fade/slide/scale transitions for route/card changes and a clear
  pressed state for taps. Respect reduced-motion settings.
- Do not animate every row, run continuous shimmer, or use 3D effects for hierarchy.
- Keep provider/search work off the UI thread, bound result rows and reuse existing
  cancellation/coalescing limits.
- Validate cold launch and first interaction, 100/300/500 visible/result candidates,
  rotation/recreation and large-text layouts before promotion. A visual polish change
  is not complete if it regresses scroll, state restoration, memory or frame time.

## Implementation sequence (only after a separate change authorization)

1. Add presentation-only route/copy fixtures and renderer tests for Home empty,
   returning, incomplete Basket, complete Basket, stale Search and demo disclosure.
2. Simplify the shell/header and move global diagnostics/source controls to You;
   retain route/back compatibility.
3. Simplify Home into the one-action workspace and make Basket progress/unknown totals
   visual; do not alter planner math.
4. Consolidate product context actions (Add, Save, More) without moving authority out
   of the existing coordinators.
5. Simplify Search result/error cards, including the stale state, then validate fresh
   Search pagination separately.
6. Rehouse You/history/watch/settings and the contextual Trip/Compare/Good Price
   entries.
7. Run the existing shared-core, Android, lint, build, privacy and physical gates;
   inspect the diff before any release candidate.

Each step is reversible and should be a separate small commit. No step authorizes a
new provider, scrape, network permission, image claim, backend promotion, Play upload,
or new version code.

## Risk register and acceptance gates

| Risk | Guard |
|---|---|
| Hiding an advanced action makes it unreachable | Keep it in Product `More`, You or Settings; add navigation tests |
| UI implies stale means no products | Dedicated stale/empty/error renderer tests and physical copy check |
| Partial total looks complete | Distinct status component and exact incomplete-total tests |
| CAD is replaced by ARS incorrectly | Currency-source tests; no locale/country inference |
| Photo/map implies an exact product or stock | Evidence/match/availability disclosures remain tied to source state |
| Back loses state | Reducer tests plus rotation/recreation and repeated navigation checks |
| Polished visuals slow the app | Bounded rows, no continuous animation, performance budgets |
| Demo is mistaken for production | Persistent demo banner, sample data labels and no live wording |
| Views acquire business authority | Code review rule: typed actions/state only; no parsing/ranking/persistence in Views |

## Current checkpoint conclusion

The existing ValuePilot behavior is substantially built and physically validated, but
the default presentation is still a dense feature dashboard. The first real consumer
problem is information hierarchy, not missing domain capability. This package defines
the smallest safe direction: make Search, Add and Basket dominant; move advanced tools
to context; preserve every exact evidence and persistence boundary; and explain stale,
unknown, incomplete, sample and historical states honestly.

No implementation, build, signing, Play upload, or device action is part of this
checkpoint.
