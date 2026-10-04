# ValuePilot complete consumer UI inventory

Status: read-only redesign checkpoint. This document records the UI that exists at
`work/valuepilot-confirmed-purchases` / `acef1dd724840ce4f9ce9588eee5c599a9f54621`.
It is an inventory and disposition plan, not permission to remove business logic.

The inventory was made from the Android layouts, route reducer, surface renderers,
strings, and the 10124 physical-validation evidence. The consumer promise used for
the disposition is:

> Find a product -> add it to the Basket -> shop smarter.

The words **remove from default UI** mean hide behind a contextual action or a
secondary area. They do not mean delete the underlying evidence, persistence, or
domain capability.

## Current shell and route inventory

| Area | Current implementation | Visible responsibility | Disposition |
|---|---|---|---|
| App shell | `AppShell.kt`, `activity_shell.xml`, `MainActivity.kt` | Header, scroll container, primary tabs, route back action, global source/data buttons | KEEP the shell and typed routing; SIMPLIFY the header and make the one current action obvious |
| Primary tab | `HOME` | Private shopping workspace and fictional demo | KEEP as the landing surface; simplify to active basket, search and one useful next step |
| Primary tab | `SEARCH` | Argentina identity search | KEEP as the main product-finding action; make result cards and Add dominant |
| Primary tab | `BASKET` | Private list, coverage and optional trip planning | KEEP; turn list/progress into the main visual |
| Primary tab | `SAVED` | Saved choices and links to history/Price Watch | MERGE at UI level into `YOU`; retain route compatibility while the migration is staged |
| Secondary route | `OBSERVED_PRICE_SAVED_SELECTION` | Choose a saved identity for an observed price | CONTEXTUAL from a product or basket item; never a primary tab |
| Secondary route | `OBSERVED_PRICE_CONFIRMATION_DRAFT` | Enter an explicit personally observed price | CONTEXTUAL from product/basket; preserve explicit currency/evidence requirements |
| Secondary route | `STAPLE_WATCH_SETUP` | Select a staple identity to watch | CONTEXTUAL from a product card; do not expose as a dashboard tile |
| Secondary route | `STAPLE_WATCH_POLICY` | Set exact watch policy | CONTEXTUAL follow-up; explain unknown/current-price limits in a compact disclosure |
| Secondary route | `CONFIRMED_PURCHASE_HISTORY` | Review explicitly confirmed purchases | MOVE under `YOU` and product context; retain display-only historical semantics |
| Secondary route | `PRICE_WATCHES` | Review private Price Watch entries | MOVE under `YOU`; expose a product-level Watch action first |
| Secondary route | `TRIP_PLANNER` | Manual trip cost estimate | MOVE behind Basket/store context; show only when a store/trip decision is relevant |
| Secondary route | `COMPARE` | Manual exact product comparison | CONTEXTUAL from a product or Basket item; not a Home dashboard card |

The reducer and route state remain platform-neutral and authoritative. A UI redesign
must not move parsing, matching, ranking, persistence, or evidence authority into a
View.

## Global shell inventory

| Current element or copy | Current behavior | Disposition and rationale |
|---|---|---|
| `ValuePilot` app name | Always visible | KEEP; brand anchor |
| `Compare the real cost. Build the smarter basket.` | Current tagline | SIMPLIFY to `Find products. Build your basket. Shop smarter.` (or a similarly short, tested equivalent) |
| `screenBackAction` / `Return to the previous ValuePilot screen` | Appears when the route coordinator can navigate back | KEEP; one secondary-route Back action, 44dp minimum, content description retained. It must not duplicate Android system Back semantics or appear on Home |
| `screenEyebrow` | Route-specific all-caps labels | SIMPLIFY; use short context labels only when they clarify location |
| `screenTitle` | Route title | KEEP, but make it the largest current context label |
| `screenBody` | Long route explanations | SIMPLIFY to one sentence; move evidence and limits into `Why?`/Details |
| `primaryAction` | Header action currently hidden by `copyFor` for primary tabs | REUSE for one route-specific action only when a route has a clear next step; do not show a generic Compare action |
| `screenFootnote` | Long caveats at the bottom | KEEP truthful limitations, but collapse into a short status/disclosure near the affected fact |
| `sourcesLicencesButton` | Always-present global button | MOVE to `YOU -> Settings & about`; preserve attribution and licences |
| `dataStatusButton` | Always-present global button | MOVE to `YOU -> Settings & about`; show only a compact freshness/status chip when it affects current Search |
| Bottom nav Home/Search/Basket/Saved | Four persistent destinations | KEEP the navigation model; rename Saved to `You` at the visual layer after compatibility is proven |
| ScrollView shell | Single scroll surface | KEEP for now; preserve scroll position per route and avoid nested scroll regressions |

## Home inventory

Source: `PracticalShoppingHomeSurfaceView.kt`,
`PracticalShoppingHomeRenderer.kt`, `PracticalShoppingHomePresentation.kt`,
`activity_shell.xml`, and the Home strings in `strings.xml`.

| Current element/copy | State or action | Disposition |
|---|---|---|
| `PRACTICAL SHOPPING • SAMPLE DATA` | Demo eyebrow | KEEP only when the fictional fixture is visible; make the word `SAMPLE` visually unavoidable and never combine it with live-looking prices |
| `What do you need?` | Home title | SIMPLIFY/KEEP as the question above the primary Search CTA |
| `Type your grocery list in plain language...` | Home body | SIMPLIFY to one short invitation; detailed clarification behavior belongs in a secondary explanation |
| `Search stays separate for item-by-item value...` | Home explanatory copy | MOVE to Search/Compare details; it competes with the primary action |
| `Try: chicken eggs milk` | Hint | KEEP as a small example below the field, not as a second headline |
| `Try a sample item` | Demo quick action | CONTEXTUAL in clearly labelled Demo mode; never pretend to be live availability |
| `Plan my shop` | Home action | MERGE with `Open basket`/`Plan basket`; one action should lead to the Basket when a list exists |
| `Repeat a past purchase` | Returning-user action | RENAME to `Buy again`; show only when confirmed history exists |
| `Quick answers` | Section/card | REMOVE FROM DEFAULT CONSUMER UI; its actions are contextual and make Home look like a dashboard |
| `On your list` | Command-center label | SIMPLIFY to `Your basket` and use a visual progress/list summary |
| `Your private shopping list` | Private-list label | KEEP privacy meaning but shorten; do not repeat it in every card |
| Quick-add chips | Add an item by known identity | KEEP only when the basket is empty or the query context needs a shortcut; use large, distinct product chips |
| `Shopping Space` / `Plan ready` / `Best Practical Shop` | Demo/planner cards | REMOVE FROM DEFAULT UI; replace with a single understandable basket status graphic |
| Coverage/known-total pills | Complete, incomplete or unknown planner state | KEEP as a primary visual status. Never make an incomplete total look like a complete total |
| `Repeat`/shop-again card | Existing private history action | CONTEXTUAL under Buy again; do not show a full dashboard block by default |
| `Share this plan` | Private plan sharing | MOVE to Basket `More`; preserve privacy disclosure and no automatic sharing |
| `Review private price history` | History action | MOVE to `You`/product context; rename to `Price history` |
| `Your remembered prices` | Memory summary | MOVE to `You` and individual product context; render as a small timeline/last-seen fact |
| `Export private history` | Data export | MOVE to Settings & about; keep explicit and secondary |
| `Forget one observation` | Destructive private-memory action | MOVE to the observation's `More` menu with confirmation and undo where safe |
| `Clear private history` | Destructive bulk action | MOVE to Settings & about; require clear confirmation and explain what remains |
| `See unverified product photos` | Existing photo action | CONTEXTUAL on product identity; label photos as unverified and source-attributed |
| `Add details` / preference dialog | Refinement questions | KEEP only when a real clarification is needed; make the question the one obvious action and retain exact choice state |
| `ONE QUICK QUESTION` / `Needs clarification` | Refinement state | KEEP, but present as a focused inline question, not a generic alert |
| `OFFLINE FICTIONAL DEMO` | Demo state | KEEP and strengthen: use a consistent demo banner/watermark and no live-store language |
| `Scan & compare prices` | Capture/tools entry | MOVE to a product context or `More`; do not lead Home with capture |
| `Is this a good price?` | Good Price entry | MOVE to product/observed-price context; rename `Check price` |
| `Compare` | Manual compare entry | MOVE to product/Basket context; never a Home peer to Search |
| `Shop again` | Planner completion action | CONTEXTUAL after a confirmed/known plan; not a second Home primary |
| `No coverage`/unknown items | Missing evidence state | KEEP prominently and explain `Unknown`, not `Unavailable` or `In stock` |
| Extra-stop controls | Advanced planner decision | MOVE into Basket/Trip progressive disclosure; never show as default Home complexity |

### Home state matrix

| State | What the shopper should see first | Required truthfulness |
|---|---|---|
| New user, empty basket | Brand, short promise, one `Search products` CTA, optional three-step visual `Search -> Basket -> Shop` | No fabricated products, prices or stores |
| Returning user, empty basket | `Search products`, `Buy again` only if history exists, one small contextual insight at most | History is personal and historical, never current inventory |
| Basket with unresolved items | Basket count, unresolved count, `Open basket` | Incomplete total must be visually distinct from a complete total |
| Basket with complete known evidence | Basket count, known total, `Open basket`/`Plan basket` | Exact currency and evidence freshness remain visible |
| Search stale/unavailable | Search CTA plus calm status | Do not show a zero-result screen that looks like “no products exist” |
| Fictional demo visible | Demo banner and sample label | Never imply the fixture is live local coverage |

## Search inventory

Source: `ArgentinaLiveSearchSurface.kt`, Search bindings in `MainActivity.kt`,
`activity_shell.xml`, and the Argentina backend contract.

| Current element/copy | Disposition |
|---|---|
| `ARGENTINA LIVE SEARCH • IDENTITY ONLY` | SIMPLIFY to `Product search` plus a compact `Identity only` badge |
| `What are you shopping for?` | KEEP as Search title |
| Long identity-only explanation | SIMPLIFY to one sentence; move source/limits into Details |
| Search input hint `Try yerba, milk, rice or another product` | KEEP; good discoverability |
| `Search Argentina products` | KEEP as the single primary action; disable while loading and provide immediate feedback |
| `Search again` | KEEP only in failure/stale state |
| yerba/milk/rice/tomato/coffee chips | KEEP as compact examples; do not imply availability |
| `QUALIFIED ARGENTINA IDENTITY RELEASE` | KEEP as a source/status badge, visually secondary |
| Long release notice/dialog | MOVE behind `Details`; show release date/source in the details sheet |
| `Product identities` and result count | KEEP, but use a clear count and list hierarchy |
| Product result card identity/name/package | KEEP and make visually distinct; exact package evidence is more important than decorative badges |
| Save/remove Home | KEEP as a secondary card action with an explicit state |
| `See product photos` | CONTEXTUAL secondary action; never use an image to imply exact identity unless match proof exists |
| `View on map` | CONTEXTUAL only when a valid store/location fact exists; identity-only Search cannot imply a nearby store |
| `View purchase history` | CONTEXTUAL only when history exists |
| `Source: Argentina SEPA / Precios Claros qualified release` | KEEP under `Why this result?`/Details; do not crowd every card |
| `Load more matching identities` | KEEP as the explicit bounded pagination action; never silently stop at five |
| `No more matching identities were returned in this release.` | KEEP as end-of-list state |
| Browser fallback / authorization copy | CONTEXTUAL error Details; never present a browser/network workaround as a live price result |
| Package size | KEEP when known; show `Quantity unknown` when not known |

### Search state presentation

| Internal mode | Consumer presentation |
|---|---|
| IDLE | Search field, examples and one Search button |
| LOADING | Button progress/disabled state, stable previous query, no layout jump |
| RESULTS | Result list, count, Load more, product-level primary Add |
| NO_MATCHES | `No matching identities yet` plus query refinement; do not say the store has no stock |
| TEMPORARILY_UNAVAILABLE | `Search unavailable` + `Try again` + Details |
| AUTHORIZATION_UNAVAILABLE | `Search is not available on this build` + Details; no fake empty result |
| STALE_RELEASE | `Latest product data is being updated` + `Try again` + Details. Replace the current confusing `This release is no longer current. Try again later.` as a later UI-only copy change |
| TIMEOUT | `Search took too long` + `Try again`; retain query |

The 10124 physical test observed `STALE_RELEASE` and no genuine results. Therefore
the >5-result, Load More and Product->Map physical paths were **not run**; this is a
backend freshness block, not evidence that the Search layout is broken.

## Product context inventory (target composition)

The current code distributes product actions across Search cards, Home identity rows,
Saved, Good Price, Price Watch, map and history. The target is one product context
that can be opened from any identity card without duplicating domain logic.

| Product fact/action | Target treatment |
|---|---|
| Name, brand, package/quantity | Primary identity block; show unknown explicitly |
| Add to Basket | One large primary action; immediate state change (`Added`, quantity) |
| Save to Home/You | Secondary toggle; preserve exact identity key |
| Current price | Show only with eligible evidence, currency, store and freshness; otherwise `Price unknown` |
| Unit value | Graphic only when quantity and currency are valid and comparable |
| Nearby/map | Contextual; requires valid store/location evidence, never inferred from a price row |
| Product photos | Contextual; source/match status visible, no invented placeholder as proof |
| Watch | Contextual; private, exact identity and policy shown |
| Purchase history | Contextual and historical/display-only |
| Check price / Compare | Contextual tools, progressive disclosure |
| `Why this answer?` | Compact evidence drawer; keep provenance, freshness, unknown/conflict semantics |

## Basket inventory

Source: `PracticalShoppingBasketSurfaceView.kt`, planner projectors, and the Basket
strings.

| Current element/copy | Disposition |
|---|---|
| `BASKET` / `Review your shopping list.` | KEEP, shorten body |
| `Review the shopping list from Home...` | SIMPLIFY; detailed coverage/travel explanation belongs under a disclosure |
| Known/incomplete subtotal wording | KEEP and strengthen visually; never call a partial total `Total` |
| `ITEMS IN THIS BASKET` | KEEP as a compact section label |
| `UNRESOLVED ITEMS — FINISH ON HOME` | KEEP, but make each unresolved row actionable |
| Check-off / `Clear check-off` | KEEP; show progress as a visual count/ring and preserve accessibility labels |
| `Plan this trip` | KEEP as contextual Basket action, rename `Estimate trip` if it opens manual assumptions |
| `Edit on Home` | KEEP but name the object (`Edit basket item`) |
| Item name/brand/package/source/notice | KEEP; group facts visually and move source to Details |
| `Price Watch is active for this exact identity` | KEEP as a compact status badge |
| `More for this item` | KEEP as progressive disclosure menu |
| `Confirm purchase` | KEEP as an explicit, high-confidence action; never auto-confirm from a price row |
| `Price Watch` | CONTEXTUAL item action |
| Photos | CONTEXTUAL item action |
| Share plan | MOVE to `More` |
| Sample notice | KEEP whenever demo data participates |
| Extra-stop/savings/travel result | GRAPHIC/contextual; show only after the Basket has enough evidence and assumptions |

## You / Saved / history / watch inventory

The existing Saved route is a compatibility surface for several private features.
Visually it should become `You`, while the underlying routes and persistence remain
stable during migration.

| Current feature | Target location | Consumer treatment |
|---|---|---|
| Saved identities | You -> Saved | Keep exact identity and Remove; show an empty state with Search CTA |
| Confirmed purchases | You -> Purchases | Keep explicit confirmation and historical disclaimer |
| Price Watch | You -> Watching | Keep private exact-identity policy; no claim of live retailer stock |
| Remembered prices | You -> Your prices and product context | Use a small timeline, not a management dashboard |
| Export/forget/clear history | You -> Settings & about | Secondary/destructive actions with confirmations |
| Sources & licences | You -> Settings & about | Keep attribution; remove from every screen footer |
| Data Status | You -> Settings & about | Keep diagnostics accessible, not a primary shopping action |
| Privacy | You -> Settings & about | Keep short summary plus details |
| Accessibility settings/capture | You -> Settings & about or product tools | Keep optional and explicit; no persistent overlay foundation |

## Map and trip inventory

| Current surface | Disposition |
|---|---|
| `ProductStoreMapSurfaceView` | CONTEXTUAL from a product/store card. Show store identity, evidence date and distance basis; never imply stock from existence of a location |
| `TripPlannerSurfaceView` | MOVE behind Basket/store context. Keep manual assumptions and clear `no route/no live price` wording |
| `Trip cost estimate` | GRAPHIC primary result when opened; large number plus currency and assumptions |
| `Manual trip estimate only...` | KEEP as concise disclosure |
| `Basket coverage` | GRAPHIC/KEEP; coverage must not be confused with live availability |
| `Trip overview` / destination / starting point | KEEP when relevant; collapse advanced fields behind `Edit assumptions` |
| Break-even/savings | CONTEXTUAL and only when comparable currencies/evidence exist |

## Standalone tools inventory

### ComparisonActivity / Compare Here

Current elements include `Products to compare`, long `Put the product name, package
size and current price in the same box...`, Product 1..N editors, `Add another
product`, like-for-like checkbox, price-basis/member-price radios, `Compare exact
value`, `Clear`, result rows, and optional capture tools.

Disposition: KEEP the exact comparison engine and safeguards. Move entry behind a
product/Basket context; shorten the introduction; make each candidate's package,
currency and unit rate the visual focus. Keep manual currency input explicit (`CA$`
and `US$` are examples, not defaults). `Clear`, OCR/photo, barcode and accessibility
capture remain secondary `More` actions.

### Good Price

Current elements include product/package/price inputs, `Check this price`, privacy
copy, barcode/photo capture and result explanations (`Evidence: this exact price
only`, `Evidence: your private history`, `What this means`).

Disposition: KEEP as a product-context tool named `Check price`. Keep evidence basis,
exact currency and quantity. Remove the standalone dashboard entry from Home. A
Good Price result must never imply a retailer's current price or live inventory.

### Capture and optional accessibility tools

`Take a price photo`, `Import a price photo`, `Scan a package barcode`, OCR review,
`Enable optional live scanner`, and `Open accessibility settings` remain optional
adapters. Keep them behind a product `More` sheet and preserve the warnings that
capture creates evidence to review; it does not grant business authority.

## Auxiliary and non-default surfaces

These surfaces are part of the shipped capability set even though they should not
compete with Search and Basket in the default information architecture.

| Surface/code area | What it currently does | Target disposition |
|---|---|---|
| Observed-price saved selection and confirmation draft | Select an exact saved identity, enter an amount and an explicit ISO currency, then create a personal observation | Keep the explicit fields and evidence boundary; enter from Product/Basket `More`, not Home |
| Staple Watch setup and policy | Choose an exact identity and define a private watch policy | Keep as Product `Watch` follow-up; keep currency/quantity/freshness requirements visible in Details |
| `ConfirmedPurchaseHistorySurfaceView` | Lists purchases explicitly confirmed on this device and supports repeat | Move under You -> Purchases and Product -> History; retain historical/display-only disclaimer |
| `PriceWatchSurfaceView` | Lists private exact-identity watches and personal notes | Move under You -> Watching; keep current offers unknown unless separately authorized evidence exists |
| `ProductStoreMapSurfaceView` | Shows store/location context for a product when a valid location fact exists | Open from Product/Basket context; keep geo-incomplete and no-stock semantics |
| `ComparisonActivity` / `CompareHereManualScreenView` | Manual exact package/price comparison and optional capture | Open from Product/Basket `More`; keep exact quantity/currency and like-for-like validation |
| `GoodPriceActivity` / `GoodPriceCheckSurfaceView` | Checks one explicitly entered product/quantity/price against permitted evidence | Open from Product `More`; keep evidence basis and avoid retailer-current claims |
| `BarcodeCaptureActivity` | Optional package barcode capture | Keep as an optional input method; never require it for Search or Add |
| `ShareToValuePilotActivity` | Receives an Android share and turns it into an observation/draft | Keep as a deep entry path with a clear review step; do not expose as a Home feature |
| `ValueAccessibilityService` | Optional, user-enabled live capture adapter | Keep opt-in and clearly labelled; no persistent overlay foundation, no silent ranking or network authority |
| Sources/licences, Data Status, Privacy and optional accessibility settings | Attribution, diagnostics and policy controls | Move to You -> Settings & about; preserve all content and links |
| Developer/diagnostic-only controls and test labels | Internal verification and fixture visibility | Exclude from the consumer default UI; retain in debug/diagnostic paths only |

No auxiliary surface may manufacture a price, availability, route, image match,
currency, timestamp or promotion condition. Its job is to collect or present an
explicit observation and hand it to the existing provider-neutral/evidence boundary.

## Cross-cutting states and controls

| State/control | Required treatment |
|---|---|
| Loading | Stable layout, immediate feedback, cancel only where safe, no stale result presented as new |
| Empty | Explain what can be done next; include one action, not a dead end |
| Unknown | Say `Unknown`/`Not available from this source`; never substitute zero or “in stock” |
| Stale | Say data is being updated; keep retry/details; do not phrase as no products |
| Conflict | Surface conflict under `Why?`; do not silently choose a winner |
| Demo/sample | Persistent, unmistakable label on every demo card and sample price |
| Destructive actions | Confirmation, exact scope, and safe undo where possible |
| Back | System Back and visible secondary-route Back return to the same previous route/state; no duplicate controls |
| Accessibility | 44dp targets, labels for state and action, logical order, large text, contrast, no color-only status |
| Motion | Bounded transition only; preserve query, result list, scroll and selection |

## Explicit no-change boundary

This inventory does not authorize:

- a new provider, scraper, retailer feed, image source, network permission or backend;
- changing exact money/quantity, identity, promotion, freshness, availability or
  evidence rules;
- deleting persistence/recovery/corruption protection;
- ranking by provider economics or adding sponsored placement;
- replacing the 10124 candidate, signing a new artifact, uploading to Play or
  reinstalling the device;
- calling the stale Search result a UI failure.

The next implementation checkpoint, if separately authorized, should be one small
presentation change at a time with route/state regression tests and the existing
full verification gates.
